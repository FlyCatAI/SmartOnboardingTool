# Design - annual-performance-summary

## Context

本设计覆盖 T-005「年度业绩汇总区」技术方案，严格基于 `annual-performance-summary` OpenSpec、业务补充文档 v0.2、Q1-Q10 最新确认口径，以及 T-002 复审通过结论。

当前仓库仍是 `rm-mvp-prd` 脚手架形态：

- 前端候选形态为小程序前端，`apps/miniprogram/src/services/http.ts` 已提供平台无关请求层占位。
- 后端候选形态为 Java 服务，`apps/backend/src/main/java/com/flycat/rm` 已按 capability 拆分，并已有 RBAC、审计、商户主数据 SPI、收单系统 SPI 骨架。
- 仓库中尚未实现 `/history-performance` 路由、`type=qualified|active` 查询参数解析、年度业绩汇总接口或聚合模型。

本设计只产出技术方案和任务拆解，不进入 UI 视觉和开发实现。

**评审门禁状态**：`state["tech_review_status"] = "pending"`。技术负责人批准前，开发 Agent 不应开始实现。

## Goals / Non-Goals

**Goals:**

1. 定义 `/history-performance` 顶部年度业绩汇总区的前后端契约，覆盖 `period_type=current_year|all_time`、4 个 P1 指标和 P2 `aum_total` 可空策略。
2. 定义后端聚合边界和领域模型，使入网、达标、有效、收入的口径在服务层可测试，且核心逻辑不感知 Web 框架。
3. 落地 Q1-Q10：AUM 方案 B、达标/有效曾达到去重、入网时间锚点、迁移后计数随迁但收入不迁移、T+1、取消 `type=new`、金额单位元、历史汇总标注自 2026 年起、前端预留接口。
4. 给出性能、缓存、权限、审计、批处理和回滚策略。
5. 把任务拆成可分配给前端、后端、数据和测试 Agent 的独立可验证单元。

**Non-Goals:**

1. 不设计高保真视觉，不替代 T-003/T-004 交互和视觉输出。
2. 不新建 `/income-details` 页面，按 Q-6 视为现存页面并由路由层兜底。
3. 不实现主管或支行行长查看下属汇总。
4. 不支持导出、同比、月/季/任意区间筛选、币种切换。
5. 不把 AUM 真实聚合设为 P1 阻塞项，接口字段预留，后端可返回 `null`。

## Architecture Overview

### C4 Context

```text
[客户经理小程序]
    |
    | HTTPS / Bearer session
    v
[RM 后端 BFF]
    |
    +--> [SSO / 会话上下文]
    +--> [商户主数据 / 商户状态历史]
    +--> [收单收入数据 / 收入快照]
    +--> [AUM 数据源，可选]
    +--> [审计日志]
```

### C4 Container

```text
apps/miniprogram
  pages/history-performance
  components/AnnualPerformanceSummary
  services/performance-summary.ts
  utils/money-format.ts

apps/backend
  performance.api
    AnnualPerformanceController
    AnnualPerformanceQueryService
  performance.domain
    PeriodType
    SummaryMetric
    OwnershipInterval
    AnnualPerformanceAggregator
  performance.spi
    PerformanceSnapshotRepository
    MerchantOwnershipRepository
    AumSnapshotRepository
  common.rbac / common.audit
```

### Dependency Rule

依赖向内流动：

- Controller / HTTP DTO 依赖 Application Service。
- Application Service 依赖 Domain Aggregator 和 Repository 接口。
- Domain Aggregator 只处理时间窗、归属区间、状态去重、收入归属等核心规则，不依赖 Spring、数据库、HTTP 或小程序框架。
- Repository 实现、缓存、批处理、审计和 Web 框架适配位于外层。

## Component Model

### Frontend Components

| Component | Responsibility |
|---|---|
| `pages/history-performance` | 页面路由、角色兜底、`type` 查询参数解析、明细区域锚点滚动 |
| `AnnualPerformanceSummary` | 汇总区容器，管理 `period_type`、加载/错误/空态、请求竞态 |
| `SummaryMetricCard` | P1 卡片展示与点击跳转 |
| `AumSummaryCard` | P2 卡片展示，`null` 降级为「暂无数据」，不可点击，hover tooltip「开发中」 |
| `performance-summary.ts` | 调用后端汇总 API，不做明细求和 |
| `money-format.ts` | `¥1,234.00 元` 格式化，单位固定为元 |

### Backend Services

| Service | Responsibility |
|---|---|
| `AnnualPerformanceController` | HTTP 入参校验、认证主体读取、响应 DTO 映射 |
| `AnnualPerformanceQueryService` | 权限校验、缓存读取、聚合协调、审计记录 |
| `AnnualPerformanceAggregator` | 领域规则：周期窗口、去重、迁移规则、收入归属 |
| `PerformanceSnapshotRepository` | 读取 T+1 聚合快照或物化视图 |
| `MerchantOwnershipRepository` | 查询客户经理和商户归属区间，支撑 Q-4 迁移 |
| `AuditLogService` | 记录越权访问和异常访问 |

### Database / Snapshot Tables

| Table / View | Purpose |
|---|---|
| `rm_perf_summary_snapshot` | T+1 后的客户经理维度汇总结果，API 主读表 |
| `rm_merchant_ownership_interval` | 商户归属区间，用于计数随迁和收入不迁移 |
| `rm_merchant_status_event` | 商户状态事件，支撑「周期内曾达标/曾有效」去重 |
| `rm_income_fact` | 收单收入事实，按业务发生时归属员工号累计 |
| `rm_aum_snapshot` | AUM 上一日时点值，可缺省 |
| `audit_log` | 越权访问和关键查询审计 |

### Queue / Batch

T+1 刷新由批处理任务完成，优先使用行内调度平台；如后端需消费完成事件，使用轻量事件：

- Topic: `rm.performance-summary.snapshot-ready`
- Key: `biz_date`
- Payload: `{ "biz_date": "2026-05-19", "batch_finished_at": "2026-05-20T02:30:00+08:00" }`

API 不在请求链路内实时扫描全量事实表，避免历史汇总在高峰期拖慢页面。

## API Contract

```yaml
openapi: 3.0.3
info:
  title: Annual Performance Summary API
  version: 1.0.0
paths:
  /api/v1/performance/annual-summary:
    get:
      summary: Query current RM annual performance summary
      operationId: getAnnualPerformanceSummary
      security:
        - bearerAuth: []
      parameters:
        - name: period_type
          in: query
          required: true
          schema:
            type: string
            enum: [current_year, all_time]
        - name: employee_id
          in: query
          required: false
          description: Optional compatibility parameter. If present, it must equal current session employeeId.
          schema:
            type: string
      responses:
        "200":
          description: Summary values for current relationship manager
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiAnnualPerformanceSummary"
              examples:
                currentYear:
                  value:
                    code: "0000"
                    slug: "ok"
                    data:
                      period_type: "current_year"
                      employee_id: "RM001"
                      new_merchants: 12
                      qualified_merchants: 8
                      active_merchants: 6
                      income: "1234567.89"
                      aum_total: null
                      updated_at: "2026-05-20T02:30:00+08:00"
                      data_delay: false
                      history_start_year: null
                allTime:
                  value:
                    code: "0000"
                    slug: "ok"
                    data:
                      period_type: "all_time"
                      employee_id: "RM001"
                      new_merchants: 135
                      qualified_merchants: 92
                      active_merchants: 70
                      income: "9876543.21"
                      aum_total: "8560000.00"
                      updated_at: "2026-05-20T02:30:00+08:00"
                      data_delay: false
                      history_start_year: 2026
        "400":
          description: Invalid period_type
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiError"
        "403":
          description: Role forbidden or employee_id mismatch
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiError"
              example:
                code: "E_RM_PERF_FORBIDDEN"
                slug: "rm_perf_forbidden"
                message: "无权查看其他客户经理业绩"
        "504":
          description: Snapshot store timeout
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ApiError"
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
  schemas:
    ApiAnnualPerformanceSummary:
      type: object
      required: [code, slug, data]
      properties:
        code:
          type: string
        slug:
          type: string
        data:
          $ref: "#/components/schemas/AnnualPerformanceSummary"
    AnnualPerformanceSummary:
      type: object
      required:
        - period_type
        - employee_id
        - new_merchants
        - qualified_merchants
        - active_merchants
        - income
        - updated_at
        - data_delay
      properties:
        period_type:
          type: string
          enum: [current_year, all_time]
        employee_id:
          type: string
        new_merchants:
          type: integer
          minimum: 0
        qualified_merchants:
          type: integer
          minimum: 0
        active_merchants:
          type: integer
          minimum: 0
        income:
          type: string
          format: decimal
          description: Amount in CNY yuan, 2 decimal places.
        aum_total:
          type: string
          format: decimal
          nullable: true
          description: Optional AUM amount in CNY yuan. Null means placeholder.
        updated_at:
          type: string
          format: date-time
          description: T+1 batch finish timestamp, Asia/Shanghai.
        data_delay:
          type: boolean
        history_start_year:
          type: integer
          nullable: true
          example: 2026
    ApiError:
      type: object
      required: [code, slug, message]
      properties:
        code:
          type: string
        slug:
          type: string
        message:
          type: string
```

## Data Model

### `rm_perf_summary_snapshot`

| Field | Type | Notes |
|---|---|---|
| `id` | BIGINT | Primary key |
| `biz_date` | DATE | T-1 business date included by snapshot |
| `employee_id` | VARCHAR(64) | Customer manager employee id |
| `period_type` | VARCHAR(16) | `current_year` or `all_time` |
| `new_merchants` | BIGINT | Distinct merchant count |
| `qualified_merchants` | BIGINT | Distinct merchant count,曾达标 |
| `active_merchants` | BIGINT | Distinct merchant count,曾有效 |
| `income_amount` | DECIMAL(18,2) | Yuan, income does not migrate with merchant ownership |
| `aum_total` | DECIMAL(18,2) NULL | Yuan, optional P2 |
| `history_start_year` | INT NULL | `2026` for `all_time`, null for `current_year` |
| `batch_finished_at` | TIMESTAMP WITH TIME ZONE | Source of `updated_at` |
| `is_delayed` | BOOLEAN | True when latest available batch is older than expected |
| `created_at` | TIMESTAMP | Snapshot write time |

Unique index: `(biz_date, employee_id, period_type)`.

### `rm_merchant_ownership_interval`

| Field | Type | Notes |
|---|---|---|
| `merchant_id` | VARCHAR(64) | Merchant identity |
| `employee_id` | VARCHAR(64) | Owner RM during interval |
| `start_at` | TIMESTAMP WITH TIME ZONE | Ownership effective start |
| `end_at` | TIMESTAMP WITH TIME ZONE NULL | Exclusive end, null means current owner |
| `change_reason` | VARCHAR(32) | `onboarded`, `transfer`, `resigned`, `role_changed` |

Relationship: one merchant has many ownership intervals, non-overlapping by time.

### `rm_merchant_status_event`

| Field | Type | Notes |
|---|---|---|
| `merchant_id` | VARCHAR(64) | Merchant identity |
| `status` | VARCHAR(32) | Includes `qualified`, `active` |
| `occurred_at` | TIMESTAMP WITH TIME ZONE | Status event time |
| `source_event_id` | VARCHAR(128) | Idempotency key |

Aggregation rule: count distinct merchants whose qualifying event occurred within the selected period and while owned by the target RM.

### `rm_income_fact`

| Field | Type | Notes |
|---|---|---|
| `income_id` | VARCHAR(128) | Source transaction / income id |
| `merchant_id` | VARCHAR(64) | Merchant identity |
| `employee_id_at_event` | VARCHAR(64) | Owner frozen at income event time |
| `amount` | DECIMAL(18,2) | Yuan |
| `occurred_at` | TIMESTAMP WITH TIME ZONE | Income business time |

Aggregation rule: sum by `employee_id_at_event`, not by current merchant owner. This enforces Q-4 income non-migration.

## Technical Decisions

### Decision 1: Read from T+1 materialized snapshots, not live aggregate queries

**Selected**: Build/read `rm_perf_summary_snapshot` generated by T+1 batch, with `updated_at` from batch completion.

**Alternatives considered:**

- Live SQL aggregation over merchant/status/income facts on each request. Rejected because `all_time` spans from 2026 onward and would create unstable P95 latency under mobile page load.
- Frontend aggregating from detail pages. Rejected by spec: 总收入 must be returned by backend, and detail list may be filtered/paginated.

**Rationale**: Matches Q-5 T+1 baseline, keeps API P95 stable, and makes delay states explicit.

### Decision 2: One summary endpoint with `period_type`, not separate annual/history endpoints

**Selected**: `GET /api/v1/performance/annual-summary?period_type=current_year|all_time`.

**Alternatives considered:**

- Separate `/current-year-summary` and `/all-time-summary`. Rejected because response shape and authorization are identical, increasing duplicate client and test work.
- Pass arbitrary date ranges. Rejected because OpenSpec explicitly limits scope to two tabs only.

**Rationale**: Mirrors the UI state and keeps future extension controlled by enum expansion.

### Decision 3: Optional AUM in the same response

**Selected**: Return `aum_total` in the same summary response, nullable.

**Alternatives considered:**

- Separate AUM endpoint. Rejected for MVP because it adds an extra request and state machine for a P2 placeholder.
- Omit AUM until real data exists. Rejected because Q-1 chose方案 B: front-end reserves optional interface.

**Rationale**: Keeps the P2 card contract stable. `null` gives front-end a deterministic placeholder path.

### Decision 4: Enforce self-only access on the server

**Selected**: Controller/service ignores any attempt to query another employee unless `employee_id` equals current principal; non-RM roles return `E_RM_PERF_FORBIDDEN`.

**Alternatives considered:**

- Hide menu only on front-end. Rejected because direct URL/API calls would bypass the UI.
- Support supervisor data scope now. Rejected because主管/行长视角 is out of scope.

**Rationale**: Aligns with data minimization and the spec's self-only requirement.

### Decision 5: Model merchant ownership as intervals

**Selected**: Use ownership intervals to evaluate count migration and income attribution.

**Alternatives considered:**

- Store only current owner on merchant. Rejected because it cannot distinguish income before/after migration.
- Recalculate historical owner from audit text logs. Rejected because audit logs are not a reliable analytical model.

**Rationale**: Q-4 has asymmetric rules: merchant counts migrate to new owner, income stays with event-time owner. Intervals make both testable.

### Decision 6: Route `type` is front-end list state, not summary API input

**Selected**: `type=qualified|active` only controls history detail filtering and anchor scroll. Summary API remains independent.

**Alternatives considered:**

- Add `type` to summary API. Rejected because summary always returns all P1 cards.
- Keep `type=new`. Rejected by Q-7:入网卡跳转 without `type`.

**Rationale**: Prevents card routing requirements from leaking into summary aggregation.

## Frontend Behavior

- Default `period_type=current_year`.
- On tab click, update selected tab immediately, show skeletons, issue a new request.
- Use `AbortController` if the chosen platform adapter supports it; otherwise attach a monotonic request sequence and discard stale responses.
- Do not show cached numeric values after 5xx/timeout.
- Treat `0` as valid data and render cards.
- Render「自 2026 年起」only when `period_type=all_time`.
- Money format: `¥{amount with thousands and 2 decimals} 元`.
- Route mapping:
  - 入网商户: `/history-performance`
  - 达标商户: `/history-performance?type=qualified`
  - 有效商户: `/history-performance?type=active`
  - 总收入: `/income-details`
  - 资产总计: no navigation, tooltip「开发中」

## Backend Aggregation Rules

### Period Windows

- `current_year`: `YYYY-01-01T00:00:00+08:00` through current snapshot `biz_date` end.
- `all_time`: `2026-01-01T00:00:00+08:00` through current snapshot `biz_date` end.

### New Merchants

Count distinct merchants whose onboarding time is in the period and whose ownership/transfer state makes them belong to the current RM for the selected period. For `all_time`, migrated-in merchants that are currently under the RM and already onboarded count for the receiver; migrated-out merchants no longer count for the original RM.

### Qualified / Active Merchants

Count distinct merchants with at least one qualifying status event in the selected period while under the RM's ownership interval. Repeated status changes count once.

### Income

Sum `rm_income_fact.amount` by `employee_id_at_event`. Merchant migration does not move historical income to the receiver.

### AUM

Read latest previous-day `aum_total` when available. If missing or not yet implemented, return `null` without failing P1 cards.

## Non-Functional Strategy

### Performance / SLA

| Item | Target |
|---|---|
| Open `/history-performance` route | P95 ≤ 2s after authenticated click |
| Summary API | P95 ≤ 500ms, P99 ≤ 1s against snapshot store |
| Snapshot freshness | T+1, normally available before next business day 08:00 Asia/Shanghai |
| Frontend retry | one user-triggered retry, then collapse after 3 consecutive failures |
| Payload size | Summary response ≤ 8 KB |

### Cache

- API may cache per `(employee_id, period_type, snapshot_biz_date)` for 5 minutes in process or Redis.
- Cache must not cross employee ids.
- When snapshot-ready event arrives, invalidate keys for affected `biz_date` or advance cache namespace.
- Do not use stale cache after HTTP 5xx to populate numeric values on front-end.

### Security Threats And Mitigations

| Threat | Mitigation |
|---|---|
| Direct API query for another employee | Server compares `employee_id` with `Principal.employeeId`; mismatch returns 403 and audit log |
| Non-RM role direct route/API access | Server role check plus front-end role guard |
| Query parameter tampering `type=new` or unknown value | Frontend treats as default; backend summary API does not consume `type` |
| Cache data leakage | Cache key includes employee id and role; no shared public cache |
| Snapshot poisoning / duplicate events | Source event idempotency keys and batch reconciliation counts |
| Decimal precision loss | Use `DECIMAL(18,2)`/string DTO; front-end formats decimal strings |
| Operational ambiguity during delayed batch | Return `data_delay=true` and batch timestamp, not request time |

### Observability

- Metrics: `annual_summary_api_latency`, `annual_summary_api_error_count`, `annual_summary_forbidden_count`, `annual_summary_snapshot_lag_hours`.
- Logs: request id, employee id hash, `period_type`, snapshot `biz_date`, data delay flag, error code.
- Audit: only forbidden attempts and sensitive cross-employee attempts require audit entries; normal self-query can remain access log unless compliance requires full read audit.

## Migration / Deployment Plan

1. Add backend domain/repository interfaces and DTOs behind a disabled feature flag `annualPerformanceSummary.enabled=false`.
2. Add snapshot DDL/materialized view and T+1 batch in lower environment.
3. Run reconciliation scripts comparing snapshot counts against source facts for mock RMs and migration edge cases.
4. Enable API in lower environment and run contract tests with front-end service client.
5. Enable front-end route behind menu/config flag for internal test users.
6.灰度 one branch/RM cohort; monitor API latency, forbidden count, snapshot lag, and user-visible error rate.
7. Rollback: disable front-end menu flag and API feature flag. Existing PC/reporting flows remain unchanged.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| Q-10 says data exists but concrete tables are unnamed | Put data-source mapping as the first back-end task; block implementation of repository adapters until table owners confirm |
| Q-4 migration semantics differ from source system history | Add ownership interval reconciliation and acceptance examples before coding aggregators |
| AUM source lags P1 metrics | Keep `aum_total` nullable and visually independent from P1 load success |
| Existing role model currently lacks branch manager but spec only allows RM | Implement endpoint allow-list for `RELATIONSHIP_MANAGER` only; future supervisor scope must be separate change |
| `/income-details` route may be unavailable in some deployments | Follow Q-6: summary component navigates; route layer handles 404/403 |

## Open Questions

1. Q-10 具体上游表名、字段名、批次完成信号和数据 owner 仍需后端/数据团队确认。
2. 「迁入后已处于入网状态」是否需要以迁入时间计入接收方本年度，还是仅在历史汇总计入接收方。当前设计按 spec 文案处理：历史汇总计入接收方，本年度仍按入网时间窗口计算。
3. AUM 的最终源表、口径字段和批次 SLA 未确认；本设计只要求 nullable 字段。
