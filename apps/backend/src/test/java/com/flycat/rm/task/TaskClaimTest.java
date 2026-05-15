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
    void 非待认领态_认领被状态机拒绝() {
        // 已经被认领过的任务（PENDING_HANDLE）再次 claim 应被状态机拒
        Task task = new Task("T-5", "team-1", TaskStatus.PENDING_HANDLE, TaskVisibility.team().lock());

        // 注意：此时 visibility 已 lock，但 isVisibleTo 仍按规则放行团队成员；
        // 状态机层会拦下「PENDING_HANDLE 不能 CLAIM」的非法流转。
        assertThrows(IllegalTransitionException.class,
                () -> task.claim("emp-A", teamMembers));
    }
}
