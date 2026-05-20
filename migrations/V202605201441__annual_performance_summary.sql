-- Annual performance summary physical model.
-- Target engine: PostgreSQL 14+.
-- OpenSpec change: annual-performance-summary.
--
-- Migration strategy:
-- 1. Create append-only/canonical fact tables first.
-- 2. Backfill facts and ownership intervals from upstream systems in a separate data job.
-- 3. Build T+1 snapshots into rm_perf_summary_snapshot.
-- 4. Switch the API to read rm_latest_perf_summary_snapshot after reconciliation passes.

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS rm_relationship_manager_snapshot (
    id BIGSERIAL PRIMARY KEY,
    biz_date DATE NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    employee_name VARCHAR(128),
    is_active BOOLEAN NOT NULL DEFAULT true,
    source_batch_id VARCHAR(128) NOT NULL,
    batch_finished_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_rm_relationship_manager_snapshot_biz_employee
        UNIQUE (biz_date, employee_id)
);

CREATE INDEX IF NOT EXISTS idx_rm_relationship_manager_employee_biz_desc
    ON rm_relationship_manager_snapshot (employee_id, biz_date DESC);

CREATE TABLE IF NOT EXISTS rm_merchant_ownership_interval (
    id BIGSERIAL PRIMARY KEY,
    merchant_id VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ,
    change_reason VARCHAR(32) NOT NULL,
    source_event_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_rm_ownership_interval_time
        CHECK (end_at IS NULL OR end_at > start_at),
    CONSTRAINT ck_rm_ownership_interval_reason
        CHECK (change_reason IN ('onboarded', 'transfer', 'resigned', 'role_changed')),
    CONSTRAINT uq_rm_ownership_interval_source_event
        UNIQUE (source_event_id),
    CONSTRAINT ex_rm_ownership_interval_no_overlap
        EXCLUDE USING gist (
            merchant_id WITH =,
            (tstzrange(start_at, COALESCE(end_at, 'infinity'::timestamptz), '[)')) WITH &&
        )
);

CREATE INDEX IF NOT EXISTS idx_rm_ownership_employee_window
    ON rm_merchant_ownership_interval (employee_id, start_at, end_at);

CREATE INDEX IF NOT EXISTS idx_rm_ownership_merchant_window
    ON rm_merchant_ownership_interval (merchant_id, start_at, end_at);

CREATE TABLE IF NOT EXISTS rm_merchant_status_event (
    id BIGSERIAL PRIMARY KEY,
    merchant_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    source_event_id VARCHAR(128) NOT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'merchant_status',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_rm_status_event_status
        CHECK (status IN ('qualified', 'active')),
    CONSTRAINT uq_rm_status_event_source_event
        UNIQUE (source_event_id)
);

CREATE INDEX IF NOT EXISTS idx_rm_status_event_status_time_merchant
    ON rm_merchant_status_event (status, occurred_at, merchant_id);

CREATE INDEX IF NOT EXISTS idx_rm_status_event_merchant_time
    ON rm_merchant_status_event (merchant_id, occurred_at);

CREATE TABLE IF NOT EXISTS rm_income_fact (
    id BIGSERIAL PRIMARY KEY,
    income_id VARCHAR(128) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    employee_id_at_event VARCHAR(64) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    source_system VARCHAR(64) NOT NULL DEFAULT 'acquiring_income',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_rm_income_fact_income_id
        UNIQUE (income_id)
);

CREATE INDEX IF NOT EXISTS idx_rm_income_employee_time
    ON rm_income_fact (employee_id_at_event, occurred_at);

CREATE INDEX IF NOT EXISTS idx_rm_income_merchant_time
    ON rm_income_fact (merchant_id, occurred_at);

CREATE TABLE IF NOT EXISTS rm_aum_snapshot (
    id BIGSERIAL PRIMARY KEY,
    biz_date DATE NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    aum_total NUMERIC(18, 2),
    batch_finished_at TIMESTAMPTZ NOT NULL,
    source_batch_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_rm_aum_snapshot_non_negative
        CHECK (aum_total IS NULL OR aum_total >= 0),
    CONSTRAINT uq_rm_aum_snapshot_biz_employee
        UNIQUE (biz_date, employee_id)
);

CREATE INDEX IF NOT EXISTS idx_rm_aum_snapshot_employee_biz_desc
    ON rm_aum_snapshot (employee_id, biz_date DESC);

CREATE TABLE IF NOT EXISTS rm_perf_summary_snapshot (
    id BIGSERIAL PRIMARY KEY,
    biz_date DATE NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    period_type VARCHAR(16) NOT NULL,
    new_merchants BIGINT NOT NULL DEFAULT 0,
    qualified_merchants BIGINT NOT NULL DEFAULT 0,
    active_merchants BIGINT NOT NULL DEFAULT 0,
    income_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    aum_total NUMERIC(18, 2),
    history_start_year INTEGER,
    batch_finished_at TIMESTAMPTZ NOT NULL,
    is_delayed BOOLEAN NOT NULL DEFAULT false,
    source_batch_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_rm_perf_summary_period_type
        CHECK (period_type IN ('current_year', 'all_time')),
    CONSTRAINT ck_rm_perf_summary_non_negative
        CHECK (
            new_merchants >= 0
            AND qualified_merchants >= 0
            AND active_merchants >= 0
            AND income_amount >= 0
            AND (aum_total IS NULL OR aum_total >= 0)
        ),
    CONSTRAINT ck_rm_perf_summary_history_start
        CHECK (
            (period_type = 'all_time' AND history_start_year = 2026)
            OR (period_type = 'current_year' AND history_start_year IS NULL)
        ),
    CONSTRAINT uq_rm_perf_summary_biz_employee_period
        UNIQUE (biz_date, employee_id, period_type)
);

CREATE INDEX IF NOT EXISTS idx_rm_perf_summary_employee_period_biz_desc
    ON rm_perf_summary_snapshot (employee_id, period_type, biz_date DESC);

CREATE OR REPLACE VIEW rm_latest_perf_summary_snapshot AS
SELECT DISTINCT ON (employee_id, period_type)
    id,
    biz_date,
    employee_id,
    period_type,
    new_merchants,
    qualified_merchants,
    active_merchants,
    income_amount,
    aum_total,
    history_start_year,
    batch_finished_at,
    is_delayed,
    source_batch_id,
    created_at
FROM rm_perf_summary_snapshot
ORDER BY employee_id, period_type, biz_date DESC, batch_finished_at DESC, id DESC;

COMMENT ON TABLE rm_perf_summary_snapshot IS
    'T+1 annual performance summary snapshot read by /api/v1/performance/annual-summary.';
COMMENT ON TABLE rm_relationship_manager_snapshot IS
    'Daily relationship manager roster used to emit zero-valued performance snapshots for RMs with no facts.';
COMMENT ON TABLE rm_merchant_ownership_interval IS
    'Non-overlapping merchant ownership intervals. Counts migrate by current/period ownership; income does not.';
COMMENT ON TABLE rm_merchant_status_event IS
    'Merchant status events used to count once per merchant for qualified/active within the selected period.';
COMMENT ON TABLE rm_income_fact IS
    'Acquiring income facts. employee_id_at_event is frozen at business event time to enforce income non-migration.';
COMMENT ON TABLE rm_aum_snapshot IS
    'Optional previous-day AUM snapshot. Missing rows map to aum_total = null in the API response.';
