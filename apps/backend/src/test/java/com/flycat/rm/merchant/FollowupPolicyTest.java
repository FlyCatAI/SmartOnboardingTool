package com.flycat.rm.merchant;

import com.flycat.rm.common.clock.Clock;
import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FollowupPolicyTest {

    @Test
    void editable_within_24h() {
        Instant now = Instant.parse("2026-05-15T12:00:00Z");
        FollowupPolicy p = new FollowupPolicy(Clock.fixed(now));
        assertTrue(p.isEditable(now.minus(Duration.ofHours(23))));
        assertTrue(p.isEditable(now.minus(Duration.ofMinutes(1))));
    }

    @Test
    void not_editable_after_24h() {
        Instant now = Instant.parse("2026-05-15T12:00:00Z");
        FollowupPolicy p = new FollowupPolicy(Clock.fixed(now));
        assertFalse(p.isEditable(now.minus(Duration.ofHours(24))));
        assertFalse(p.isEditable(now.minus(Duration.ofHours(48))));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> p.requireEditable(now.minus(Duration.ofHours(25))));
        assertEquals(ErrorCode.FOLLOWUP_EDIT_WINDOW_EXPIRED, ex.errorCode());
    }

    @Test
    void image_count_limit() {
        FollowupPolicy p = new FollowupPolicy(Clock.system());
        p.requireImageCountWithinLimit(0);
        p.requireImageCountWithinLimit(9);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> p.requireImageCountWithinLimit(10));
        assertEquals(ErrorCode.FOLLOWUP_IMAGE_LIMIT_EXCEEDED, ex.errorCode());
    }
}
