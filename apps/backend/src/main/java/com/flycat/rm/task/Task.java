package com.flycat.rm.task;

import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;
import com.flycat.rm.common.statemachine.StateMachine;

import java.util.Objects;
import java.util.Set;

/**
 * 任务聚合根骨架。当前仅承载 spec_delta 强制要求的状态与可见范围两类不变量；
 * 商户关联、负责人、优先级、截止时间等业务字段在 4.7-4.12 主线实施时补齐。
 *
 * <p>对外只暴露受保护的状态机入口（{@code claim} / {@code dispatch} / ...），
 * 避免下游直接 set status 绕过校验。
 */
public final class Task {

    private static final StateMachine<TaskStatus, TaskEvent> SM = TaskStateMachineFactory.create();

    private final String id;
    private final String teamId;
    private TaskStatus status;
    private TaskVisibility visibility;
    private String assigneeEmployeeId;

    public Task(String id, String teamId, TaskStatus initialStatus, TaskVisibility visibility) {
        this.id = Objects.requireNonNull(id, "id");
        this.teamId = Objects.requireNonNull(teamId, "teamId");
        this.status = Objects.requireNonNull(initialStatus, "initialStatus");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
    }

    public String id() {
        return id;
    }

    public String teamId() {
        return teamId;
    }

    public TaskStatus status() {
        return status;
    }

    public TaskVisibility visibility() {
        return visibility;
    }

    public String assigneeEmployeeId() {
        return assigneeEmployeeId;
    }

    /**
     * 主管在任务被认领前编辑可见范围。spec：「主管 SHALL 可在任务被认领前编辑可见范围；
     * 任务一旦被认领，可见范围 SHALL 锁定不可再修改」。
     */
    public void updateVisibility(TaskVisibility next) {
        this.visibility = visibility.update(next);
    }

    /**
     * 客户经理认领任务。把以下四条 spec 不变量原子化到一处，避免下游 service 分散校验导致漏掉：
     *
     * <ol>
     *   <li>可见范围校验：当前用户必须在可见范围内（spec「可见范围外的越权访问」）。
     *       失败抛 {@link BusinessException}，错误码 {@link ErrorCode#TASK_VISIBILITY_DENIED}。</li>
     *   <li>认领冲突校验：任务已被他人认领时（assigneeEmployeeId 非空），按 spec「任务认领冲突」
     *       拒绝并抛 {@link TaskClaimConflictException}，携带原认领人工号供 UI 提示
     *       「该任务已被 [A 姓名] 认领」。该校验必须先于状态机，避免冲突场景被
     *       {@code IllegalTransitionException} 吞掉。</li>
     *   <li>状态机校验：当前必须可触发 {@link TaskEvent#CLAIM}（即 PENDING_CLAIM）；其他
     *       非冲突类非法状态（如 IN_PROGRESS / DONE / CLOSED 直接 claim）仍由状态机
     *       抛 {@code IllegalTransitionException}。</li>
     *   <li>锁定可见范围：CLAIM 成功后 visibility 立即 lock，禁止后续编辑。</li>
     * </ol>
     *
     * @param employeeId 当前认领用户的工号
     * @param teamMemberIds 任务所属团队全部成员的工号集合（用于 TEAM 范围判断）
     */
    public void claim(String employeeId, Set<String> teamMemberIds) {
        if (!visibility.isVisibleTo(employeeId, teamMemberIds)) {
            throw new BusinessException(ErrorCode.TASK_VISIBILITY_DENIED,
                    "task " + id + " is not visible to " + employeeId);
        }
        if (assigneeEmployeeId != null) {
            throw new TaskClaimConflictException(id, assigneeEmployeeId);
        }
        this.status = SM.fire(this.status, TaskEvent.CLAIM);
        this.visibility = this.visibility.lock();
        this.assigneeEmployeeId = employeeId;
    }
}
