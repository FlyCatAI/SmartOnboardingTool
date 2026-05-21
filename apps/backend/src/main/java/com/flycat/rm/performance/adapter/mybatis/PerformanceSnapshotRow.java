package com.flycat.rm.performance.adapter.mybatis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * MyBatis result row for {@code rm_latest_perf_summary_snapshot}. Mutable POJO
 * because MyBatis populates fields via setters. Translated to the immutable
 * {@link com.flycat.rm.performance.spi.PerformanceSnapshot} by the adapter.
 */
public class PerformanceSnapshotRow {
    private String employeeId;
    private String periodType;
    private LocalDate bizDate;
    private long newMerchants;
    private long qualifiedMerchants;
    private long activeMerchants;
    private BigDecimal incomeAmount;
    private OffsetDateTime batchFinishedAt;
    private boolean delayed;

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String v) { this.employeeId = v; }
    public String getPeriodType() { return periodType; }
    public void setPeriodType(String v) { this.periodType = v; }
    public LocalDate getBizDate() { return bizDate; }
    public void setBizDate(LocalDate v) { this.bizDate = v; }
    public long getNewMerchants() { return newMerchants; }
    public void setNewMerchants(long v) { this.newMerchants = v; }
    public long getQualifiedMerchants() { return qualifiedMerchants; }
    public void setQualifiedMerchants(long v) { this.qualifiedMerchants = v; }
    public long getActiveMerchants() { return activeMerchants; }
    public void setActiveMerchants(long v) { this.activeMerchants = v; }
    public BigDecimal getIncomeAmount() { return incomeAmount; }
    public void setIncomeAmount(BigDecimal v) { this.incomeAmount = v; }
    public OffsetDateTime getBatchFinishedAt() { return batchFinishedAt; }
    public void setBatchFinishedAt(OffsetDateTime v) { this.batchFinishedAt = v; }
    public boolean isDelayed() { return delayed; }
    public void setDelayed(boolean v) { this.delayed = v; }
}
