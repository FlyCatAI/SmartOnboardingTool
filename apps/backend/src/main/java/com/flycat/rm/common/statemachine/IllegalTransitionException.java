package com.flycat.rm.common.statemachine;

public class IllegalTransitionException extends RuntimeException {

    private final Enum<?> from;
    private final Enum<?> event;
    private final String reason;

    public IllegalTransitionException(Enum<?> from, Enum<?> event, String reason) {
        super("illegal transition: from=" + from + " event=" + event + " reason=" + reason);
        this.from = from;
        this.event = event;
        this.reason = reason;
    }

    public Enum<?> from() {
        return from;
    }

    public Enum<?> event() {
        return event;
    }

    public String reason() {
        return reason;
    }
}
