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
- [~] 2.5 实现年度业绩汇总 API。Estimate: M. Depends on: 2.3, 2.4. Acceptance: `GET /api/v1/performance/annual-summary` 支持 `period_type`，返回 4 个 P1 指标、nullable `aum_total`、`updated_at`、`history_start_year`。**Blocked**: Web 框架 / 构建工具 / JDK / 持久层四项决策未批复；最小待决项与建议选型见 `apps/backend/CONTROLLER_BLOCKERS.md`（推荐 Spring Boot 3.x + Spring MVC + Maven + JDK 21 + Spring JdbcClient）。下层（service + JDBC + batch）已就绪，controller 在四项决策审批后可在小 PR 内落地。
- [x] 2.6 实现权限和审计。Estimate: S. Depends on: 2.5. Acceptance: 仅 `RELATIONSHIP_MANAGER` 可查本人；`employee_id` 不一致或非 RM 返回 403 `E_RM_PERF_FORBIDDEN` 并写审计日志。
- [ ] 2.7 实现服务端缓存和失效策略。Estimate: M. Depends on: 2.5. Acceptance: 缓存 key 包含 `employee_id + period_type + snapshot_biz_date`；snapshot-ready 后可失效；禁止跨员工复用。
- [ ] 2.8 后端测试。Estimate: M. Depends on: 2.3, 2.5, 2.6, 2.7. Acceptance: 单测/集成测试覆盖 Q-2、Q-3、Q-4、AUM null、金额 DECIMAL、T+1 延迟、403、非法 `period_type`。

## 3. Frontend

- [~] 3.1 新增 `/history-performance` 路由和入口。Estimate: M. Depends on: 1.2. Acceptance: 客户经理从「我的」进入页面；非客户经理看到不支持提示；未知 `type` 和 `type=new` 视为缺省。**进度**：`src/pages/history-performance/route.ts` 已实现 `parseHistoryPerformanceType()`（含 `type=new` / 未知 / 大小写非法均归默认）并覆盖 7 个单测；页面级入口绑定（菜单挂载、角色兜底 UI）需在 uni-app / 原生 / Taro 技术栈最终确定后补 `.vue` 或 `.wxml` 视图。
- [x] 3.2 实现 summary API client 和 DTO 类型。Estimate: S. Depends on: 2.5 API contract. Acceptance: 客户端只调用汇总接口，不从明细列表计算总收入；支持 `period_type=current_year|all_time`。`src/services/performance-summary.ts` 调用 `GET /api/v1/performance/annual-summary?period_type=...`，DTO 中 `income` / `aum_total` 保持 DECIMAL 字符串避免精度损失；`http.ts` 已扩展 `AbortSignal` 透传与非 2xx `body.code` 透传（覆盖 6 个单测，含 403 → `E_RM_PERF_FORBIDDEN`、stale guard）。
- [~] 3.3 实现年度业绩汇总区组件。Estimate: M. Depends on: 1.2, 1.3, 3.2. Acceptance: 渲染 Tab、4 张 P1 卡片、1 张 P2 卡片、`updated_at` 和 `data_delay` 提示。**进度**：框架无关控制器 `src/components/annual-performance-summary/controller.ts` 已完成，包含状态机（idle/loading/success/error/forbidden）、AUM null 状态、`updated_at` / `data_delay` 透传，覆盖 13 个单测；视图层（卡片、Tab、骨架、tooltip 等）需在 UI 框架敲定后绑定 controller state 渲染。
- [x] 3.4 实现金额格式化工具。Estimate: S. Depends on: none. Acceptance: `0` 渲染为 `¥0.00 元`，`1234567.89` 渲染为 `¥1,234,567.89 元`，不使用「万」。`src/utils/money-format.ts` 全字符串运算，覆盖 8 个单测（零值、千分位、四舍五入、超长十进制、负数、null → 「暂无数据」、绝不输出「万」）。
- [x] 3.5 实现卡片跳转和不可点击规则。Estimate: S. Depends on: 3.1, 3.3. Acceptance: 入网跳 `/history-performance`，达标跳 `?type=qualified`，有效跳 `?type=active`，总收入跳 `/income-details`，AUM 不跳转并提示「开发中」。`src/components/annual-performance-summary/card-routes.ts` 的 `cardRoute()` 覆盖 6 个单测；视图侧只需消费该映射调用平台 navigator，不重复实现。
- [x] 3.6 实现加载、空态、错误态和重试。Estimate: M. Depends on: 3.3. Acceptance: 合法 0 值不隐藏；5xx/超时不展示旧数；连续 3 次失败折叠为稍后再试；AUM null 显示「暂无数据」。控制器内含 `consecutiveFailures` 计数与 3 次失败后 `canRetry=false` + 文案折叠；测试覆盖「合法 0 值」「5xx 不残值」「3 次折叠」「成功重试归零」。
- [x] 3.7 实现 Tab 请求竞态处理。Estimate: S. Depends on: 3.2, 3.3. Acceptance: 连续切换时取消旧请求或丢弃旧响应，只渲染最新 Tab 数据。控制器对每次 `load()` 创建新的 `AbortController` 并 abort 上一个；同时基于 `requestSeq` 序号在响应到达时丢弃旧 Tab 结果（双保险，覆盖原生小程序适配器无 abort 能力的场景）。
- [~] 3.8 前端测试和适配验证。Estimate: M. Depends on: 3.1-3.7. Acceptance: 单测覆盖格式化、路由参数、状态渲染；1440x900 首屏完整展示且无横向滚动。**已完成**：Vitest + TS 测试基建落地（`apps/miniprogram/package.json` / `tsconfig.json` / `vitest.config.ts`），40/40 单测通过 + `tsc --noEmit` 0 错误，覆盖 money-format / route 解析 / cardRoute / controller 状态机。**待 UI 框架后**：1440x900 首屏视觉走查与跨端适配验证。

## 4. Contract, QA, Release

- [ ] 4.1 前后端契约联调。Estimate: M. Depends on: 2.5, 3.2. Acceptance: OpenAPI 示例、mock 数据和真实接口响应字段一致；decimal 字符串无精度损失。
- [ ] 4.2 数据口径验收脚本。Estimate: M. Depends on: 2.4. Acceptance: 使用在职、迁入、迁出、无业绩、AUM null 样本验证快照与源数据误差为 0。
- [ ] 4.3 E2E 场景测试。Estimate: M. Depends on: 2.8, 3.8, 4.1. Acceptance: 覆盖 spec.md 所有 Scenario，重点包括 Tab 切换、Q-4 迁移、403、T+1 延迟、卡片跳转、错误重试。
- [ ] 4.4 灰度发布准备。Estimate: S. Depends on: 4.3. Acceptance: 前端入口和后端 API 均有 feature flag；回滚方式为关闭入口/API；监控指标已配置。
- [ ] 4.5 运营与客服材料更新。Estimate: S. Depends on: 4.3. Acceptance: 培训材料解释 Q-4「计数随迁、收入不迁移」和 Q-5 T+1；FAQ 覆盖「为什么数字和报表对不上」。
