package com.flycat.rm.task;

import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 任务池可见范围的值对象（task-management/spec.md 「任务认领与任务池可见范围」）。
 *
 * 不变量：
 * <ul>
 *   <li>{@code scope == TEAM}：{@code visibleUserIds} 必须为空集（同团队可见，无需显式名单）。</li>
 *   <li>{@code scope == CUSTOM}：{@code visibleUserIds} 至少含 1 个、至多含「团队全员」个成员；
 *       本类只校验「至少 1 个」，团队成员合法性由 service 层结合组织架构系统校验。</li>
 *   <li>{@code locked == true}：任务已被认领，scope/visibleUserIds 不可再修改；
 *       由 {@link Task#claim} 状态机事件原子地设置。</li>
 * </ul>
 *
 * 校验失败抛 {@link BusinessException}：
 * <ul>
 *   <li>{@link ErrorCode#TASK_VISIBILITY_INVALID} 自定义范围名单为空 / 非法。</li>
 *   <li>{@link ErrorCode#TASK_VISIBILITY_LOCKED} 已认领任务尝试改可见范围。</li>
 * </ul>
 */
public final class TaskVisibility {

    private final TaskVisibilityScope scope;
    private final Set<String> visibleUserIds;
    private final boolean locked;

    private TaskVisibility(TaskVisibilityScope scope, Set<String> visibleUserIds, boolean locked) {
        this.scope = scope;
        this.visibleUserIds = Collections.unmodifiableSet(new LinkedHashSet<>(visibleUserIds));
        this.locked = locked;
    }

    /** 同团队范围。MVP 默认值。 */
    public static TaskVisibility team() {
        return new TaskVisibility(TaskVisibilityScope.TEAM, Set.of(), false);
    }

    /**
     * 自定义可见范围。{@code visibleUserIds} 必须非空，否则抛
     * {@link ErrorCode#TASK_VISIBILITY_INVALID}。
     */
    public static TaskVisibility custom(Set<String> visibleUserIds) {
        if (visibleUserIds == null || visibleUserIds.isEmpty()) {
            throw new BusinessException(ErrorCode.TASK_VISIBILITY_INVALID,
                    "custom visibility requires at least one member");
        }
        return new TaskVisibility(TaskVisibilityScope.CUSTOM, visibleUserIds, false);
    }

    public TaskVisibilityScope scope() {
        return scope;
    }

    public Set<String> visibleUserIds() {
        return visibleUserIds;
    }

    public boolean isLocked() {
        return locked;
    }

    /**
     * 主管在任务被认领前编辑可见范围。已锁定则拒绝。
     */
    public TaskVisibility update(TaskVisibility next) {
        if (locked) {
            throw new BusinessException(ErrorCode.TASK_VISIBILITY_LOCKED,
                    "visibility cannot be changed after claim");
        }
        return next;
    }

    /**
     * 状态机 CLAIM 事件回调：锁定可见范围，后续 service 层不得再调用 {@link #update}。
     */
    public TaskVisibility lock() {
        if (locked) {
            return this;
        }
        return new TaskVisibility(scope, visibleUserIds, true);
    }

    /**
     * 判断当前用户是否在可见范围内（用于列表过滤、详情访问、认领前置校验）。
     *
     * @param employeeId 待校验的用户工号
     * @param teamMemberIds 任务所属团队的成员工号集合（由 service 层从组织架构系统取，
     *                      用于 TEAM 范围的成员判断）
     */
    public boolean isVisibleTo(String employeeId, Set<String> teamMemberIds) {
        return switch (scope) {
            case TEAM -> teamMemberIds.contains(employeeId);
            case CUSTOM -> visibleUserIds.contains(employeeId);
        };
    }
}
