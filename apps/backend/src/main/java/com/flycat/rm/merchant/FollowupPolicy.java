package com.flycat.rm.merchant;

import com.flycat.rm.common.clock.Clock;
import com.flycat.rm.common.error.BusinessException;
import com.flycat.rm.common.error.ErrorCode;

import java.time.Duration;
import java.time.Instant;

/**
 * 跟进记录的编辑窗口与附件上限策略（merchant-management/spec.md）。
 * 单独拎出来，便于覆盖单测、便于未来从 spec 调整。
 */
public final class FollowupPolicy {

    public static final Duration EDIT_WINDOW = Duration.ofHours(24);
    public static final int MAX_IMAGES = 9;
    public static final int MAX_CONTENT_CHARS = 500;
    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final Clock clock;

    public FollowupPolicy(Clock clock) {
        this.clock = clock;
    }

    public boolean isEditable(Instant createdAt) {
        return Duration.between(createdAt, clock.now()).compareTo(EDIT_WINDOW) < 0;
    }

    public void requireEditable(Instant createdAt) {
        if (!isEditable(createdAt)) {
            throw new BusinessException(ErrorCode.FOLLOWUP_EDIT_WINDOW_EXPIRED,
                    "followup created at " + createdAt + " is past 24h edit window");
        }
    }

    public void requireImageCountWithinLimit(int count) {
        if (count > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.FOLLOWUP_IMAGE_LIMIT_EXCEEDED,
                    "max " + MAX_IMAGES + " images, got " + count);
        }
    }
}
