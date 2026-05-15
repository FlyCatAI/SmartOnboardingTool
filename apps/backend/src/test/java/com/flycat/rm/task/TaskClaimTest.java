package com.flycat.rm.task;

import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;
import com.flycat.rm.common.statemachine.IllegalTransitionException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 覆盖 {@link Task#claim} 三条不变量的原子性：状态机 + 可见范围 + 锁定。
 * 对应 task-management/spec.md「客户经理认领任务」「任务认领冲突」「可见范围外的越权访问」三条 Scenario。
 */
class TaskClaimTest {

    private final Set<String> teamMembers = Set.of("emp-A", "emp-B", "emp-C");

    @Test
    void team_可见_认领成功_状态推进且可见范围被锁定() {
        Task task = new Task("T-1", "team-1", TaskStatus.PENDING_CLAIM, TaskVisibility.team());

        task.claim("emp-A", teamMembers);

        assertEquals(TaskStatus.PENDING_HANDLE, task.status());
        assertEquals("emp-A", task.assigneeEmployeeId());
        assertTrue(task.visibility().isLocked(), "claim 后 visibility SHALL 锁定");

        // 锁定后主管再改可见范围必须被拒
        BusinessException ex = assertThrows(BusinessException.class,
                () -> task.updateVisibility(TaskVisibility.custom(Set.of("emp-B"))));
        assertEquals(ErrorCode.TASK_VISIBILITY_LOCKED, ex.errorCode());
    }

    @Test
    void custom_可见_仅名单内可认领() {
        Task task = new Task("T-2", "team-1",
                TaskStatus.PENDING_CLAIM,
                TaskVisibility.custom(Set.of("emp-A", "emp-B")));

        task.claim("emp-B", teamMembers);
        assertEquals(TaskStatus.PENDING_HANDLE, task.status());
        assertEquals("emp-B", task.assigneeEmployeeId());
    }

    @Test
    void custom_可见_名单外认领被拒_状态不变() {
        Task task = new Task("T-3", "team-1",
                TaskStatus.PENDING_CLAIM,
                TaskVisibility.custom(Set.of("emp-A", "emp-B")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> task.claim("emp-C", teamMembers));
        assertEquals(ErrorCode.TASK_VISIBILITY_DENIED, ex.errorCode());

        // 失败后状态与可见范围都不应被修改
        assertEquals(TaskStatus.PENDING_CLAIM, task.status());
        assertNull(task.assigneeEmployeeId());
        assertFalse(task.visibility().isLocked());
    }

    @Test
    void team_可见_非团队成员认领被拒() {
        Task task = new Task("T-4", "team-1", TaskStatus.PENDING_CLAIM, TaskVisibility.team());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> task.claim("emp-X", teamMembers));
        assertEquals(ErrorCode.TASK_VISIBILITY_DENIED, ex.errorCode());
        assertEquals(TaskStatus.PENDING_CLAIM, task.status());
    }

    @Test
    void 已被A认领_B再认领_抛TASK_CLAIM_CONFLICT_并携带原认领人() {
        // spec「任务认领冲突」：任务 T 已被 A 认领，B 再点认领 →
        // 系统 SHALL 拒绝并提示「该任务已被 [A 姓名] 认领」。
        // 业务错误码 TASK_CLAIM_CONFLICT，异常必须携带 A 的工号供 service 层查名展示。
        Task task = new Task("T-5", "team-1", TaskStatus.PENDING_CLAIM, TaskVisibility.team());
        task.claim("emp-A", teamMembers);
        assertEquals(TaskStatus.PENDING_HANDLE, task.status());
        assertEquals("emp-A", task.assigneeEmployeeId());

        TaskClaimConflictException ex = assertThrows(TaskClaimConflictException.class,
                () -> task.claim("emp-B", teamMembers));
        assertEquals(ErrorCode.TASK_CLAIM_CONFLICT, ex.errorCode());
        assertEquals("T-5", ex.taskId());
        assertEquals("emp-A", ex.currentAssigneeEmployeeId(),
                "spec 要求异常携带原认领人以便 UI 提示「该任务已被 [A 姓名] 认领」");

        // B 认领失败不得影响既有状态：A 仍是负责人，状态保持 PENDING_HANDLE。
        assertEquals(TaskStatus.PENDING_HANDLE, task.status());
        assertEquals("emp-A", task.assigneeEmployeeId());
    }

    @Test
    void 状态非PENDING_CLAIM且无认领人_认领被状态机拒绝() {
        // 边界场景：理论上不应通过合法路径出现（任务一旦推进必有 assignee），
        // 但状态机层仍兜底拦截，避免被绕过。该场景与认领冲突区分开：
        // 冲突 → TASK_CLAIM_CONFLICT；非冲突的非法状态 → IllegalTransitionException。
        Task task = new Task("T-6", "team-1", TaskStatus.IN_PROGRESS, TaskVisibility.team().lock());

        assertThrows(IllegalTransitionException.class,
                () -> task.claim("emp-A", teamMembers));
    }
}
