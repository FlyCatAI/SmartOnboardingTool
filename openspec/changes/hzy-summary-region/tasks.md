## 1. 后端：performance capability 骨架

- [x] 1.1 新增 `com.flycat.rm.performance` 包，沉淀 DTO `PerformanceSummary`（9 个字段，与 spec `Requirement: 汇总接口字段契约` 一一对应）
- [x] 1.2 沉淀 `PerformanceSummaryRepository` SPI（按 `Principal` 解析数据范围 → 返回 `PerformanceSummary`），实现层待技术栈评审后接入
- [x] 1.3 沉淀 `PerformanceSummaryService`（编排 SPI 调用 + 时区处理 + 金额 2 位小数兜底）
- [x] 1.4 沉淀 `PerformanceMoneyFormatter`，统一服务端金额标准化：从 BigDecimal / 分单位转换为对外 2 位小数
- [x] 1.5 在 `ErrorCode` 注册 `PERFORMANCE_SUMMARY_UNAVAILABLE`（接口失败兜底）
- [x] 1.6 单元测试覆盖：本年度边界、零值、scope 越权拒绝、金额精度归一、SPI 异常降级

## 2. 小程序前端：历史业绩页顶部汇总区

- [x] 2.1 新增 `pages/performance/historical/` 历史业绩页骨架（汇总区 + 列表占位），明确顶部插槽 = 汇总区
- [x] 2.2 新增 `components/performance-summary/` Vue SFC：两组 × 4 指标 + 数据截止时间 + 加载态 + 失败态
- [x] 2.3 新增 `services/performance.ts`：`fetchPerformanceSummary()` 强类型客户端，错误统一转 `BizError`
- [x] 2.4 新增 `utils/format-currency.ts`：人民币金额 → 千分位 + 2 位小数 + 单位「元」；非数字回退 `'--'`
- [x] 2.5 新增 `utils/format-count.ts`：非负整数 → 整数字符串 + 单位「户」；非数字回退 `'--'`
- [x] 2.6 新增 `utils/format-statistics-as-of.ts`：ISO 8601 → `YYYY-MM-DD HH:mm`，时区取行内默认
- [x] 2.7 单元测试覆盖：千分位、零值、负数、大数、空值、非数字、ISO 8601 解析

## 3. 联调与测试映射

- [ ] 3.1 待后端选型评审通过、补 `pom.xml` / `build.gradle.kts` 后接入 JUnit 5 运行（脚本 `mvn test` / `./gradlew test`）
- [ ] 3.2 待前端选型评审通过、补 `package.json` 与测试运行器（Vitest 候选）后接入单元测试运行
- [ ] 3.3 全链路联调：小程序进入历史业绩页 → 汇总接口返回 9 个字段 → 前端按格式渲染 → 失败场景独立降级
- [ ] 3.4 GRA-32 测试案例 1:1 回归（HZY-SUM-001 ~ HZY-SUM-042）

## 4. 归档

- [ ] 4.1 业务方 / 项目经理 / 测试三方确认后，将本次 spec_delta 合入 `openspec/specs/performance/spec.md`
