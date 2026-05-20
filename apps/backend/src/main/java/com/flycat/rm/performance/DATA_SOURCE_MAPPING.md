# Annual Performance Summary — Backend Data Source Mapping

This document closes OpenSpec task **2.1**: Q-10 数据源映射. It freezes the
contract between data engineering's physical model (`migrations/V202605201441__annual_performance_summary.sql`,
`docs/data/annual-performance-summary.md`) and the backend SPI ports
(`com.flycat.rm.performance.spi.*`).

The query service path is intentionally read-only against a serving view. Live
aggregation lives on the data side and only writes into
`rm_perf_summary_snapshot`.

## 1. Read path (request-time)

| SPI port | Backing object | Read mode | SLA expectation |
|---|---|---|---|
| `PerformanceSnapshotRepository.findLatest(employeeId, periodType)` | View `rm_latest_perf_summary_snapshot` | `SELECT … WHERE employee_id = ? AND period_type = ?` (≤1 row) | P95 ≤ 50ms; backed by `idx_rm_perf_summary_employee_period_biz_desc` |
| `AumSnapshotRepository.findLatest(employeeId)` | Table `rm_aum_snapshot` | `SELECT aum_total FROM rm_aum_snapshot WHERE employee_id = ? ORDER BY biz_date DESC LIMIT 1` | Optional. Missing row → `Optional.empty()`, never throws (Q-1 方案 B) |

### 1.1 `rm_latest_perf_summary_snapshot` column → DTO field

| View column | Type | `PerformanceSnapshot` field |
|---|---|---|
| `employee_id` | VARCHAR(64) | `employeeId` |
| `period_type` | VARCHAR(16) `current_year|all_time` | `periodType` (mapped via `PeriodType.fromWire`) |
| `biz_date` | DATE | `bizDate` |
| `new_merchants` | BIGINT | `newMerchants` |
| `qualified_merchants` | BIGINT | `qualifiedMerchants` |
| `active_merchants` | BIGINT | `activeMerchants` |
| `income_amount` | NUMERIC(18,2) | `income` (BigDecimal, scale=2) |
| `batch_finished_at` | TIMESTAMPTZ | `batchFinishedAt` (OffsetDateTime) |
| `is_delayed` | BOOLEAN | `isDelayed` |

Notes:
- `aum_total` and `history_start_year` live in the same physical row but are
  **not** carried by `PerformanceSnapshot`. AUM goes through the dedicated SPI
  (Q-1 方案 B); `history_start_year` is computed in the application layer
  (constant 2026 for `all_time`) so the snapshot record stays focused on T+1
  aggregation outputs.
- Money values stay as `BigDecimal(scale=2)` end-to-end. JSON serialization
  must use a string format to satisfy the DECIMAL precision requirement
  (`design.md` §Decision 7 / §Security Threats).

### 1.2 `rm_aum_snapshot` column → SPI

| Table column | Mapped value |
|---|---|
| `aum_total` (NUMERIC(18,2), nullable) | `Optional<BigDecimal>` — `null` columns or missing rows both return `empty` |

The query service treats `empty` as the user-visible "暂无数据" path. Adapter
implementations **must not** throw on missing AUM.

## 2. Write path (T+1 batch)

This service does not write. The write side is owned by data engineering and
codified in `docs/data/annual-performance-summary.md` §Snapshot Build SQL.
Backend repositories assume:

- Exactly one `rm_perf_summary_snapshot` row per `(biz_date, employee_id, period_type)`
  — enforced by `uq_rm_perf_summary_biz_employee_period` and validated by check
  query #2 in the data doc.
- Latest per `(employee_id, period_type)` is exposed via
  `rm_latest_perf_summary_snapshot` (`SELECT DISTINCT ON` over `biz_date DESC,
  batch_finished_at DESC, id DESC`). Repositories read this view instead of
  joining the table directly to avoid skewing on backfill-day duplicates.
- `is_delayed` is materialized at batch time
  (`biz_date < (batch_finished_at_local - 1)`). Backend just transports the
  boolean; UI shows the data-delay banner.

## 3. Upstream domain ownership (Q-10 closure)

| Source domain | Canonical fact table | Owner team | Refresh cadence |
|---|---|---|---|
| RM roster | `rm_relationship_manager_snapshot` | HR/People Data | Daily T+1 |
| Merchant ownership intervals | `rm_merchant_ownership_interval` | Merchant Onboarding | Event-driven append (with overlap exclusion constraint) |
| Merchant status events | `rm_merchant_status_event` | Merchant Risk/Status | Event-driven append |
| Acquiring income facts | `rm_income_fact` | Acquiring Platform | Daily T+1 |
| AUM snapshot | `rm_aum_snapshot` | Wealth / 数据中台 | Daily T+1 (P2) |
| Serving snapshot | `rm_perf_summary_snapshot` + view | Data Engineering | Generated nightly from the above |

Frozen by data engineering (`110a2c1 chore(data): add migration for annual
summary`). Backend depends on the **serving snapshot** + AUM table only;
direct reads on fact tables are not part of the request path and are not
permitted from this service module.

## 4. Adapter responsibilities

The JDBC adapter (`com.flycat.rm.performance.adapter.jdbc.*`) is responsible
for:

1. Mapping NULL `aum_total` to `Optional.empty()`.
2. Translating SQL `period_type` (`current_year` / `all_time`) via
   `PeriodType.fromWire` and rejecting unexpected values with a
   `BusinessException(NOT_FOUND, ...)` rather than letting the adapter return
   a half-formed snapshot.
3. Reading TIMESTAMPTZ as `OffsetDateTime` (NOT `Instant`) so the wire format
   in §Decision 7 / OpenAPI example (`2026-05-20T02:30:00+08:00`) is
   preservable; controller serialization concerns are deferred to 2.5.
4. Treating empty result sets as `Optional.empty()` (not throwing). The query
   service decides whether `empty` is a 404 (snapshot missing) or `null` AUM
   (degraded P2).
5. Never silently fabricating zero rows — the **roster-driven zero rows** are
   created upstream by the data batch via `rm_relationship_manager_snapshot`,
   not by the backend.

## 5. Out of scope for this mapping

- Cache layer (task 2.7) — keys must include `employee_id + period_type +
  biz_date`, but cache wiring is deferred until 2.5 picks a framework.
- T+1 batch SQL — owned by data engineering; backend only references it for
  acceptance scenarios.
- `type=qualified|active` query param — `design.md` §Decision 6 keeps it on
  the detail-list page; it is **not** an input to the summary repository.
