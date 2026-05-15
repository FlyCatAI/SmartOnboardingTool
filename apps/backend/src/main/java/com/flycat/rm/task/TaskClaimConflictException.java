package com.flycat.rm.task;

import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;

/**
 * 任务认领冲突（task-management/spec.md「任务认领冲突」）。
 *
 * <p>当客户经理 B 尝试认领一个已被客户经理 A 认领的任务时，service 层捕获本异常后
 * 必须按 spec 要求向 B 展示「该任务已被 [A 姓名] 认领」。本异常携带原认领人工号
 * （非姓名），姓名查询由 service 层结合组织架构系统完成；本类只承担「冲突」语义
 * 与「原认领人是谁」的契约，使 UI 提示能携带必要上下文。
 */
public final class TaskClaimConflictException extends BusinessException {

    private final String taskId;
    private final String currentAssigneeEmployeeId;

    public TaskClaimConflictException(String taskId, String currentAssigneeEmployeeId) {
        super(ErrorCode.TASK_CLAIM_CONFLICT,
                "task " + taskId + " already claimed by " + currentAssigneeEmployeeId);
        this.taskId = taskId;
        this.currentAssigneeEmployeeId = currentAssigneeEmployeeId;
    }

    public String taskId() {
        return taskId;
    }

    public String currentAssigneeEmployeeId() {
        return currentAssigneeEmployeeId;
    }
}
