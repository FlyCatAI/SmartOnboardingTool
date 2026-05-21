package com.flycat.rm.performance.adapter.mybatis;

import com.flycat.rm.performance.spi.AumSnapshotRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * MyBatis-backed implementation of {@link AumSnapshotRepository}.
 *
 * <p>The mapper SQL filters out NULL {@code aum_total} rows, so missing AUM data
 * and explicit NULL both produce {@link Optional#empty()} (Q-1 方案 B).
 */
@Repository
public class MyBatisAumSnapshotRepository implements AumSnapshotRepository {

    private final AumSnapshotMapper mapper;

    public MyBatisAumSnapshotRepository(AumSnapshotMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public Optional<BigDecimal> findLatest(String employeeId) {
        Objects.requireNonNull(employeeId, "employeeId");
        return mapper.findLatest(employeeId);
    }
}
