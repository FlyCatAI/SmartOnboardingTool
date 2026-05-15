package com.flycat.rm.task;

import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 覆盖 task-management/spec.md「任务认领与任务池可见范围」的 Scenario：
 * <ul>
 *   <li>同团队默认范围 / 自定义范围创建与不变量校验</li>
 *   <li>可见范围外越权访问拒绝</li>
 *   <li>认领后锁定可见范围、再编辑被拒</li>
 * </ul>
 */
class TaskVisibilityTest {

    @Test
    void team_visibility_是默认值_且对团队成员可见() {
        TaskVisibility v = TaskVisibility.team();
        assertEquals(TaskVisibilityScope.TEAM, v.scope());
        assertTrue(v.visibleUserIds().isEmpty());
        assertFalse(v.isLocked());

        assertTrue(v.isVisibleTo("emp-A", Set.of("emp-A", "emp-B")));
        assertFalse(v.isVisibleTo("emp-C", Set.of("emp-A", "emp-B")),
                "non-team member should not be visible");
    }

    @Test
    void custom_visibility_仅对名单内成员可见() {
        TaskVisibility v = TaskVisibility.custom(Set.of("emp-A", "emp-B"));
        Set<String> teamMembers = Set.of("emp-A", "emp-B", "emp-C", "emp-D");

        assertTrue(v.isVisibleTo("emp-A", teamMembers));
        assertTrue(v.isVisibleTo("emp-B", teamMembers));
        // C 是团队成员但不在自定义名单 → 越权拒绝
        assertFalse(v.isVisibleTo("emp-C", teamMembers));
        // D 同上
        assertFalse(v.isVisibleTo("emp-D", teamMembers));
    }

    @Test
    void custom_visibility_空名单_拒绝() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> TaskVisibility.custom(Set.of()));
        assertEquals(ErrorCode.TASK_VISIBILITY_INVALID, ex.errorCode());
    }

    @Test
    void update_前可改_lock_后拒改() {
        TaskVisibility v = TaskVisibility.team();
        TaskVisibility custom = TaskVisibility.custom(Set.of("emp-A"));

        // 认领前可改
        TaskVisibility updated = v.update(custom);
        assertEquals(TaskVisibilityScope.CUSTOM, updated.scope());

        // 锁定后再改抛 LOCKED
        TaskVisibility locked = updated.lock();
        assertTrue(locked.isLocked());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> locked.update(TaskVisibility.team()));
        assertEquals(ErrorCode.TASK_VISIBILITY_LOCKED, ex.errorCode());
    }
}
