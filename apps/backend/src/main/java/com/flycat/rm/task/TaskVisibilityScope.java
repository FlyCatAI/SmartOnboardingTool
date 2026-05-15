package com.flycat.rm.task;

/**
 * 任务池可见范围（task-management/spec.md 「任务认领与任务池可见范围」）。
 *
 * <ul>
 *   <li>{@link #TEAM} 同团队（默认）：任务对发布团队的全部成员可见可认领。</li>
 *   <li>{@link #CUSTOM} 自定义：仅对 {@code visibleUserIds} 中明确选中的客户经理可见可认领。</li>
 * </ul>
 *
 * <p>非任务池任务（主管直接派单或草稿）也带此字段，固定为 {@link #TEAM}，避免下游消费方需要判空。
 */
public enum TaskVisibilityScope {
    TEAM,
    CUSTOM
}
