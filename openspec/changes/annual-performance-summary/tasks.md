# Tasks - annual-performance-summary

> 技术设计产出后，`state["tech_review_status"] = "pending"`。技术负责人批准前，开发 Agent 不得开始实现。

## 1. Design Gate

- [ ] 1.1 技术负责人审查 `design.md` 的接口、数据模型、缓存、权限和迁移规则。Estimate: S. Depends on: proposal/spec approved. Acceptance: 审查结论明确为 approve/revise；approve 后才允许进入开发。
- [x] 1.2 UX 设计师输出 T-003 交互方案并与本技术契约对齐。Estimate: S. Depends on: proposal/spec approved. Acceptance: 交互方案覆盖 Tab、卡片跳转、AUM 占位、错误态和「自 2026 年起」标注。
- [x] 1.3 UI 设计师在技术/UX 审查后输出视觉规格。Estimate: S. Depends on: 1.1, 1.2. Acceptance: 视觉规格覆盖 P1/P2 卡片、元/千分位/2 位小数、加载/空/错/不可点击态。

## 2. Backend And Data

- [x] 2.1 确认 Q-10 数据源映射。Estimate: S. Depends on: 1.1. Acceptance: 明确商户入网、状态事件、收入事实、AUM 快照、批次完成时间的上游表/接口、owner、SLA 和字段映射。落地：`apps/backend/src/main/java/com/flycat/rm/performance/DATA_SOURCE_MAPPING.md`，对齐 `migrations/V202605201441__annual_performance_summary.sql` 与 `docs/data/annual-performance-summary.md`。
- [x] 2.2 设计并落地快照/归属区间数据结构。Estimate: M. Depends on: 2.1. Acceptance: `rm_perf_summary_snapshot`、`rm_merchant_ownership_interval`、状态事件和收入事实映射可支持 Q-2/Q-3/Q-4；包含唯一键和幂等策略。物理模型由数据工程 `110a2c1` 落地；后端读侧 `JdbcPerformanceSnapshotRepository` / `JdbcAumSnapshotRepository` 接入 `rm_latest_perf_summary_snapshot` 视图与 `rm_aum_snapshot` 表，11 个 JDBC 适配器单测覆盖。
- [x] 2.3 实现领域模型和聚合器。Estimate: M. Depends on: 2.2. Acceptance: `PeriodType`、归属区间、状态去重、收入归属规则以纯领域单测覆盖，不依赖 Web/DB 框架。
- [x] 2.4 实现 T+1 快照生成或读取适配。Estimate: L. Depends on: 2.2, 2.3. Acceptance: 生成/读取 `current_year` 和 `all_time` 两类快照；返回批次完成时间；批次延迟时标记 `data_delay=true`。读侧由 `JdbcPerformanceSnapshotRepository` 覆盖（透传 `batch_finished_at` 与 `is_delayed`）；生成侧由 `SnapshotBuildJob` 驱动 canonical SQL（`resources/performance/snapshots/build_perf_summary_snapshot.sql`，由 `docs/data/annual-performance-summary.md` §Snapshot Build SQL 抽取），4 个单测覆盖参数绑定与 `data_delay` 计算。
- [x] 2.5 实现年度业绩汇总 API。Estimate: M. Depends on: 2.3, 2.4. Acceptance: `GET /api/v1/performance/annual-summary` 支持 `period_type`，返回 4 个 P1 指标、nullable `aum_total`、`updated_at`、`history_start_year`。落地：D1-D4 决策审批通过（Spring Boot 3.2.5 + Spring MVC + Maven + JDK 17 + MyBatis 3.0.3），`apps/backend/pom.xml`、`apps/backend/src/main/resources/application.yml` 与 `RmApplication` 入口完成；`AnnualPerformanceController` + `GlobalErrorHandler` + `ApiEnvelope` + `AnnualPerformanceResponse` 共 9 个 `@WebMvcTest` 用例覆盖 200/400/403/404 与 decimal 字符串序列化。原 JDBC 适配器已替换为 MyBatis 映射（`PerformanceSnapshotMapper`/`AumSnapshotMapper` + XML），10 个 `@MybatisTest` 用例对 H2 PostgreSQL 模式真实读视图/表。
- [x] 2.6 实现权限和审计。Estimate: S. Depends on: 2.5. Acceptance: 仅 `RELATIONSHIP_MANAGER` 可查本人；`employee_id` 不一致或非 RM 返回 403 `E_RM_PERF_FORBIDDEN` 并写审计日志。
- [x] 2.7 实现服务端缓存和失效策略。Estimate: M. Depends on: 2.5. Acceptance: 缓存 key 包含 `employee_id + period_type + snapshot_biz_date`；snapshot-ready 后可失效；禁止跨员工复用。落地：`CachedAnnualPerformanceQueryService` 用 Spring Cache 抽象（`@Cacheable` + `@CacheEvict`）包裹纯领域 `AnnualPerformanceQueryService`，key 为 `{employee_id}:{period_type}:{snapshot_biz_date}`；key 生成先通过 `PerformanceSnapshotRepository.findLatestBizDate()` 读取最新快照日期，因此新 `biz_date` 可自然进入新缓存 namespace；`condition` 排除非 RM 与跨员工请求避免污染缓存并保留审计。7 个缓存测试覆盖命中、employee/period 隔离、new biz_date 不复用旧值、`evict`、`evictAll`、跨员工拒绝不写缓存。控制器现注入 cached facade；snapshot-ready 的 `evict(employee, period)` 因 key 含旧 biz_date 采取保守 all-entries 清理，生产可切 Caffeine/Redis。
- [x] 2.8 后端测试。Estimate: M. Depends on: 2.3, 2.5, 2.6, 2.7. Acceptance: 单测/集成测试覆盖 Q-2、Q-3、Q-4、AUM null、金额 DECIMAL、T+1 延迟、403、非法 `period_type`。落地：`AnnualPerformanceIntegrationTest`（`@SpringBootTest` + `@AutoConfigureMockMvc` + H2 PostgreSQL 模式）10 个用例覆盖端到端 200、AUM null、history_start_year、非 RM 角色 → 403 `E_RM_PERF_FORBIDDEN` + 审计写入、跨员工请求 → 403 `E_RM_PERF_FORBIDDEN` + 审计写入、缺失快照 → 404、非法 period_type → 400、`data_delay` 透传、同 biz_date 缓存命中、新 biz_date 不被旧缓存遮蔽。后端整体 `mvn test` 77/77 通过。

## 3. Frontend

- [x] 3.1 新增 `/history-performance` 路由和入口。Estimate: M. Depends on: 1.2. Acceptance: 客户经理从「我的」进入页面；非客户经理看到不支持提示；未知 `type` 和 `type=new` 视为缺省。**进度**：用户 5/21 决策技术栈 = uni-app（.vue，兼容小程序构建，不引入 React/TSX）。`src/pages/history-performance/route.ts` + `HistoryPerformance.vue` 落地：RM 渲染汇总区 + 历史明细过滤容器；非 RM / 未登录渲染权限提示「您当前角色暂不支持查看历史业绩页」；`detail-filter` 通过 `data-filter` 暴露 qualified / active / default 三态，`type=new` 与未知值归默认。覆盖 7（route 解析） + 6（页面级 mount）= 13 个单测；菜单挂载在 5.x 全局导航任务里完成。
- [x] 3.2 实现 summary API client 和 DTO 类型。Estimate: S. Depends on: 2.5 API contract. Acceptance: 客户端只调用汇总接口，不从明细列表计算总收入；支持 `period_type=current_year|all_time`。`src/services/performance-summary.ts` 调用 `GET /api/v1/performance/annual-summary?period_type=...`，DTO 中 `income` / `aum_total` 保持 DECIMAL 字符串避免精度损失；`http.ts` 已扩展 `AbortSignal` 透传与非 2xx `body.code` 透传（覆盖 6 个单测，含 403 → `E_RM_PERF_FORBIDDEN`、stale guard）。
- [x] 3.3 实现年度业绩汇总区组件。Estimate: M. Depends on: 1.2, 1.3, 3.2. Acceptance: 渲染 Tab、4 张 P1 卡片、1 张 P2 卡片、`updated_at` 和 `data_delay` 提示。**进度**：框架无关控制器（idle/loading/success/error/forbidden 状态机，AUM null，`updated_at` / `data_delay` 透传）+ `AnnualPerformanceSummary.vue` 视图层落地，使用 `<view>` / `<text>` / `<button>` 兼容小程序构建，CSS 严格引用 `--aps-*` 变量（无硬编码颜色）。覆盖 controller 13 个 + 视图 15 个 = 28 个单测：Tab 切换/aria-selected、4 张 P1 卡片标签与数值（`12 / 8 / 6 / ¥1,234,567.89 元`）、AUM null → 「暂无数据」且绝不出现「万」、AUM 卡不可点击、卡片导航 emit 路由、`data_delay` 徽章显隐、`自 2026 年起` 仅 `all_time` 显示、骨架态、错误面板含重试、3 次失败折叠、403 forbidden、合法 0 值不当作空态。
- [x] 3.4 实现金额格式化工具。Estimate: S. Depends on: none. Acceptance: `0` 渲染为 `¥0.00 元`，`1234567.89` 渲染为 `¥1,234,567.89 元`，不使用「万」。`src/utils/money-format.ts` 全字符串运算，覆盖 8 个单测（零值、千分位、四舍五入、超长十进制、负数、null → 「暂无数据」、绝不输出「万」）。
- [x] 3.5 实现卡片跳转和不可点击规则。Estimate: S. Depends on: 3.1, 3.3. Acceptance: 入网跳 `/history-performance`，达标跳 `?type=qualified`，有效跳 `?type=active`，总收入跳 `/income-details`，AUM 不跳转并提示「开发中」。`src/components/annual-performance-summary/card-routes.ts` 的 `cardRoute()` 覆盖 6 个单测；视图侧只需消费该映射调用平台 navigator，不重复实现。
- [x] 3.6 实现加载、空态、错误态和重试。Estimate: M. Depends on: 3.3. Acceptance: 合法 0 值不隐藏；5xx/超时不展示旧数；连续 3 次失败折叠为稍后再试；AUM null 显示「暂无数据」。控制器内含 `consecutiveFailures` 计数与 3 次失败后 `canRetry=false` + 文案折叠；测试覆盖「合法 0 值」「5xx 不残值」「3 次折叠」「成功重试归零」。
- [x] 3.7 实现 Tab 请求竞态处理。Estimate: S. Depends on: 3.2, 3.3. Acceptance: 连续切换时取消旧请求或丢弃旧响应，只渲染最新 Tab 数据。控制器对每次 `load()` 创建新的 `AbortController` 并 abort 上一个；同时基于 `requestSeq` 序号在响应到达时丢弃旧 Tab 结果（双保险，覆盖原生小程序适配器无 abort 能力的场景）。
- [~] 3.8 前端测试和适配验证。Estimate: M. Depends on: 3.1-3.7. Acceptance: 单测覆盖格式化、路由参数、状态渲染；1440x900 首屏完整展示且无横向滚动。**已完成**：Vitest + Vue 3 + @vue/test-utils 测试基建（uni-app `<view>` / `<text>` / `<button>` 走 `isCustomElement`），66/66 单测通过 + `tsc --noEmit` 0 错误。覆盖 money-format / date-format / route 解析 / cardRoute / controller 状态机 / 视图渲染（汇总组件 + 历史业绩页）。**待真机/H5 构建**：1440x900 首屏视觉走查、跨端（H5 / 微信小程序 / 字节小程序）适配验证。

## 4. Contract, QA, Release

- [ ] 4.1 前后端契约联调。Estimate: M. Depends on: 2.5, 3.2. Acceptance: OpenAPI 示例、mock 数据和真实接口响应字段一致；decimal 字符串无精度损失。
- [ ] 4.2 数据口径验收脚本。Estimate: M. Depends on: 2.4. Acceptance: 使用在职、迁入、迁出、无业绩、AUM null 样本验证快照与源数据误差为 0。
- [ ] 4.3 E2E 场景测试。Estimate: M. Depends on: 2.8, 3.8, 4.1. Acceptance: 覆盖 spec.md 所有 Scenario，重点包括 Tab 切换、Q-4 迁移、403、T+1 延迟、卡片跳转、错误重试。
- [ ] 4.4 灰度发布准备。Estimate: S. Depends on: 4.3. Acceptance: 前端入口和后端 API 均有 feature flag；回滚方式为关闭入口/API；监控指标已配置。
- [ ] 4.5 运营与客服材料更新。Estimate: S. Depends on: 4.3. Acceptance: 培训材料解释 Q-4「计数随迁、收入不迁移」和 Q-5 T+1；FAQ 覆盖「为什么数字和报表对不上」。
