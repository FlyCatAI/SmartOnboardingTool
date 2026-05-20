package com.flycat.rm.performance.domain;

/**
 * 「本年度」与「历史汇总」两档统计周期，对应 spec `period_type`。
 * Tab 切换时由前端透传，后端不接受其它取值。
 */
public enum PeriodType {

    CURRENT_YEAR("current_year"),
    ALL_TIME("all_time");

    private final String wire;

    PeriodType(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static PeriodType ofWire(String wire) {
        for (PeriodType pt : values()) {
            if (pt.wire.equals(wire)) {
                return pt;
            }
        }
        throw new IllegalArgumentException("Unknown period_type: " + wire);
    }
}
