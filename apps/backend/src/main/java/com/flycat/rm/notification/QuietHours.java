package com.flycat.rm.notification;

import java.time.LocalTime;

/**
 * 免打扰时段。设置页可关闭；默认 22:00-08:00（design.md Decision 3，design.md Risks 行写 20:00 系笔误，
 * 以 risks/design.md 实施时一致性以 spec 为准。两侧实施时确认一次）。
 * 免打扰期间订阅消息不下发，仅站内入站。
 */
public record QuietHours(LocalTime startInclusive, LocalTime endExclusive, boolean enabled) {

    public static QuietHours defaultPolicy() {
        return new QuietHours(LocalTime.of(22, 0), LocalTime.of(8, 0), true);
    }

    public boolean isQuiet(LocalTime t) {
        if (!enabled) {
            return false;
        }
        if (startInclusive.isBefore(endExclusive)) {
            return !t.isBefore(startInclusive) && t.isBefore(endExclusive);
        }
        // 跨午夜：22:00-08:00
        return !t.isBefore(startInclusive) || t.isBefore(endExclusive);
    }
}
