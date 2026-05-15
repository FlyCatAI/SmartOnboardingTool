package com.flycat.rm.notification;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class QuietHoursTest {

    @Test
    void default_policy_covers_22_to_08() {
        QuietHours q = QuietHours.defaultPolicy();
        assertTrue(q.isQuiet(LocalTime.of(22, 0)));
        assertTrue(q.isQuiet(LocalTime.of(23, 30)));
        assertTrue(q.isQuiet(LocalTime.of(0, 0)));
        assertTrue(q.isQuiet(LocalTime.of(7, 59)));
        assertFalse(q.isQuiet(LocalTime.of(8, 0)));
        assertFalse(q.isQuiet(LocalTime.of(12, 0)));
        assertFalse(q.isQuiet(LocalTime.of(21, 59)));
    }

    @Test
    void disabled_means_never_quiet() {
        QuietHours q = new QuietHours(LocalTime.of(22, 0), LocalTime.of(8, 0), false);
        assertFalse(q.isQuiet(LocalTime.of(23, 0)));
    }

    @Test
    void same_day_range_works() {
        QuietHours q = new QuietHours(LocalTime.of(12, 0), LocalTime.of(14, 0), true);
        assertFalse(q.isQuiet(LocalTime.of(11, 59)));
        assertTrue(q.isQuiet(LocalTime.of(12, 0)));
        assertTrue(q.isQuiet(LocalTime.of(13, 0)));
        assertFalse(q.isQuiet(LocalTime.of(14, 0)));
    }
}
