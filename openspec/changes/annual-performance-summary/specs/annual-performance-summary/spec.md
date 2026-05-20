## ADDED Requirements

### Requirement: 历史业绩页入口与路由

系统 SHALL 为客户经理新增「历史业绩」二级页面，挂载在工作台「我的」入口下，前端路由为 `/history-performance`，仅对当前登录的客户经理本人开放。

#### Scenario: 客户经理进入历史业绩页

- **GIVEN** 当前用户角色为「客户经理」且已通过身份认证
- **WHEN** 用户从工作台「我的」菜单点击「我的历史业绩」入口
- **THEN** 系统 SHALL 在 2 秒内（P95）打开 `/history-performance` 路由
- **AND** 页面 SHALL 在顶部固定渲染年度业绩汇总区

#### Scenario: 非客户经理角色访问历史业绩页

- **GIVEN** 当前用户角色为「团队主管」「支行行长」或其他非客户经理角色
- **WHEN** 用户通过菜单或直接 URL 访问 `/history-performance`
- **THEN** 系统 SHALL 不渲染年度业绩汇总区
- **AND** 系统 SHALL 展示「您当前角色暂不支持查看历史业绩页」并提供返回工作台按钮

#### Scenario: 路由查询参数解析

- **GIVEN** 用户访问 `/history-performance?type=<value>`
- **WHEN** 路由解析器读取 `type` 查询参数
- **THEN** 系统 SHALL 仅识别 `type=qualified` 与 `type=active` 两个合法取值，用于过滤明细列表的口径维度
- **AND** 当 `type` 缺省时 SHALL 按「入网商户」默认口径渲染明细
- **AND** 当 `type` 取其他值（包括 `new` 及未来未定义值）时 SHALL 视为缺省并忽略该参数（`type=new` 不再作为合法路由参数支持）

### Requirement: 年度业绩汇总区布局

系统 SHALL 在 `/history-performance` 路由的页面顶部铺设固定的「年度业绩汇总区」，由 Tab 切换、第一行 4 列指标卡（P1）与第二行 1 张全宽资产总计卡片（P2）组成。

#### Scenario: 默认渲染年度汇总区

- **GIVEN** 已登录的客户经理首次打开历史业绩页
- **WHEN** 页面加载完成
- **THEN** 系统 SHALL 在顶部展示「年度业绩汇总」区域标题与「数据更新于 YYYY-MM-DD HH:mm」时间戳（Asia/Shanghai，精确到分钟）
- **AND** 标题下方 SHALL 渲染 Tab 组件，包含「本年度」与「历史汇总」两个 Tab，默认高亮「本年度」（`period_type=current_year`）
- **AND** Tab 下方 SHALL 渲染第一行 4 列卡片，顺序固定为：入网商户 / 达标商户 / 有效商户 / 总收入
- **AND** 第一行下方 SHALL 渲染第二行全宽卡片「资产总计」
- **AND** 桌面端 1440×900 首屏可视区内 SHALL 完整展示整个汇总区，不出现横向滚动

#### Scenario: 「历史汇总」Tab 起始年标注

- **GIVEN** 已登录的客户经理切换到「历史汇总」Tab
- **WHEN** 该 Tab 渲染完成
- **THEN** 系统 SHALL 在该 Tab 下方或区域副标题处标注「自 2026 年起」
- **AND** 该标注 SHALL 仅在「历史汇总」Tab 选中时展示；「本年度」Tab 选中时不展示

### Requirement: period_type Tab 切换

系统 SHALL 通过 `period_type` 字段在「本年度」与「历史汇总」两个统计维度之间切换，切换 SHALL 触发整个汇总区的数据重新拉取，但不刷新页面路由。

#### Scenario: 切换到「历史汇总」

- **GIVEN** 当前 Tab 为「本年度」且 4 张 P1 指标卡已加载完成
- **WHEN** 用户点击「历史汇总」Tab
- **THEN** Tab 高亮 SHALL 立即切换到「历史汇总」
- **AND** 4 张 P1 卡片 SHALL 立即显示骨架加载态
- **AND** 系统 SHALL 携带 `period_type=all_time` 重新请求汇总接口
- **AND** 接口返回后 SHALL 将 4 张卡片替换为对应历史累计值
- **AND** P2 资产总计卡片 SHALL 同步切换

#### Scenario: 切换 Tab 期间用户连续点击

- **GIVEN** 用户已点击「历史汇总」且请求处于 pending 状态
- **WHEN** 用户在请求返回前再次点击「本年度」
- **THEN** 系统 SHALL 取消上一次请求或丢弃其响应
- **AND** 仅渲染与最新选中 Tab 对应的请求结果，避免数据错位

### Requirement: P1 指标 - 入网商户

系统 SHALL 在当前 `period_type` 周期内，统计归属当前登录客户经理（员工号 = 当前会话员工号）名下、按「入网时间」归集的入网商户数（去重计数），以整数格式展示。

#### Scenario: 本年度入网商户统计

- **GIVEN** 当前 Tab 为「本年度」（`period_type=current_year`）且当前登录客户经理为员工号 X
- **WHEN** 系统计算「入网商户」
- **THEN** 系统 SHALL 统计「入网时间」位于当前自然年 1 月 1 日 00:00（Asia/Shanghai）至「数据截止时点」之间、入网时归属员工号 X 的商户去重计数
- **AND** 卡片 SHALL 以整数 `{n}` 格式展示

#### Scenario: 历史汇总入网商户统计

- **GIVEN** 当前 Tab 为「历史汇总」（`period_type=all_time`）且当前登录客户经理为员工号 X
- **WHEN** 系统计算「入网商户」
- **THEN** 系统 SHALL 统计 X 在职期间名下、按「入网时间」归集的入网商户去重计数
- **AND** 计数 SHALL 包含从其他客户经理迁入员工号 X 名下、且在 X 名下完成入网或已处于入网状态的商户（Q-4 迁移规则）
- **AND** 离职 / 转岗后被迁出员工号 X 名下的商户 SHALL 不再计入 X 的历史汇总

#### Scenario: 点击「入网商户」卡片

- **GIVEN** 「入网商户」卡片已加载完成且数值非空态
- **WHEN** 用户点击卡片
- **THEN** 系统 SHALL 路由跳转到 `/history-performance`（不携带 `type` 参数）
- **AND** 跳转后 SHALL 滚动到明细区域并按默认「入网」口径展示明细列表

### Requirement: P1 指标 - 达标商户

系统 SHALL 在当前 `period_type` 周期内，统计归属当前登录客户经理名下、状态曾达到「已达标」的商户数（去重；同一商户多次达标只计一次），以整数格式展示。

#### Scenario: 周期内曾达标即计入（Q-2 口径）

- **GIVEN** 当前 Tab 为「本年度」且当前登录客户经理为员工号 X
- **WHEN** 系统计算「达标商户」
- **THEN** 系统 SHALL 统计本年度周期内任一时点曾达到 `status='已达标'` 的、归属员工号 X 名下的商户去重计数
- **AND** 商户在周期内多次进出「已达标」状态 SHALL 仅计 1 次
- **AND** 商户在周期外（去年或更早）已达标但本周期内未再次达标 SHALL 不计入本年度

#### Scenario: 历史汇总达标商户统计（含迁移规则）

- **GIVEN** 当前 Tab 为「历史汇总」且当前登录客户经理为员工号 X
- **WHEN** 系统计算「达标商户」
- **THEN** 系统 SHALL 统计 X 在职期间归属其名下、且名下期间内曾达到 `status='已达标'` 的商户去重计数
- **AND** 由其他客户经理迁入 X 名下后才达标的商户 SHALL 计入 X 的历史汇总（Q-4 迁移规则）
- **AND** 在 X 离职 / 转岗后被迁出的商户 SHALL 不再计入 X 的历史汇总

#### Scenario: 点击「达标商户」卡片

- **GIVEN** 「达标商户」卡片已加载完成且数值非空态
- **WHEN** 用户点击卡片
- **THEN** 系统 SHALL 路由跳转到 `/history-performance?type=qualified`
- **AND** 目标路由 SHALL 按 `type=qualified` 过滤明细列表（本提案同步落地该路由查询参数支持，见「历史业绩页入口与路由」Requirement）

### Requirement: P1 指标 - 有效商户

系统 SHALL 在当前 `period_type` 周期内，统计归属当前登录客户经理名下、状态曾达到「已有效」的商户数（去重），以整数格式展示。

#### Scenario: 周期内曾有效即计入（Q-2 口径）

- **GIVEN** 当前 Tab 为「本年度」且当前登录客户经理为员工号 X
- **WHEN** 系统计算「有效商户」
- **THEN** 系统 SHALL 统计本年度周期内任一时点曾达到 `status='已有效'` 的、归属员工号 X 名下的商户去重计数
- **AND** 同一商户在周期内多次进出「已有效」状态 SHALL 仅计 1 次

#### Scenario: 历史汇总有效商户统计（含迁移规则）

- **GIVEN** 当前 Tab 为「历史汇总」且当前登录客户经理为员工号 X
- **WHEN** 系统计算「有效商户」
- **THEN** 系统 SHALL 统计 X 在职期间归属其名下、且名下期间内曾达到 `status='已有效'` 的商户去重计数
- **AND** Q-4 迁移规则同样适用：迁入后才达到「已有效」的商户计入接收方；迁出后不再计入原归属人

#### Scenario: 点击「有效商户」卡片

- **GIVEN** 「有效商户」卡片已加载完成且数值非空态
- **WHEN** 用户点击卡片
- **THEN** 系统 SHALL 路由跳转到 `/history-performance?type=active`
- **AND** 目标路由 SHALL 按 `type=active` 过滤明细列表（本提案同步落地该路由查询参数支持）

### Requirement: P1 指标 - 总收入

系统 SHALL 在当前 `period_type` 周期内，由后端聚合接口直接返回归属当前登录客户经理的累计收单收入；前端 SHALL **不**对明细做二次求和。

#### Scenario: 总收入由后端聚合返回

- **GIVEN** 当前 Tab 为「本年度」或「历史汇总」
- **WHEN** 前端拉取年度业绩汇总接口
- **THEN** 接口响应 SHALL 包含字段 `income`，类型为 `DECIMAL`，单位为「元」
- **AND** 前端 SHALL 直接展示该字段数值，不读取明细列表进行求和

#### Scenario: 总收入金额展示格式

- **GIVEN** 后端返回的 `income = 1234567.89` 元
- **WHEN** 前端渲染「总收入」卡片
- **THEN** 卡片 SHALL 展示 `¥1,234,567.89 元`
- **AND** 金额 SHALL 保留 2 位小数
- **AND** 整数部分 SHALL 在 ≥ 1000 时启用千分位分隔
- **AND** 单位 SHALL 显示为「元」（不再使用「万」单位，Q-8 已订正）

#### Scenario: 历史汇总总收入不随商户迁移并入（Q-4）

- **GIVEN** 商户 M 在某年度从员工号 Y 迁移到员工号 X
- **WHEN** 系统计算 X 的「历史汇总 → 总收入」
- **THEN** M 在迁移前归属 Y 期间产生的收单收入 SHALL 仅计入 Y 的（历史）总收入，**不**并入 X 的历史汇总
- **AND** M 在迁移后归属 X 期间产生的收单收入 SHALL 计入 X 的总收入
- **AND** 该规则适用于本年度和历史汇总两个维度

#### Scenario: 点击「总收入」卡片

- **GIVEN** 「总收入」卡片已加载完成且数值非空态
- **WHEN** 用户点击卡片
- **THEN** 系统 SHALL 路由跳转到 `/income-details`（现存页面，本期不新建）
- **AND** 若目标页因故 404 / 403 SHALL 由路由层兜底，汇总区本身不处理

### Requirement: P2 指标 - 资产总计（方案 B）

系统 SHALL 在年度业绩汇总区第二行渲染全宽「资产总计」卡片，前端按 Q-1 方案 B 实现：预留可选接口；后端返回 `null` 时降级为「暂无数据」占位文案。

#### Scenario: 后端返回有效 AUM 值

- **GIVEN** 后端 AUM 聚合上线后，接口返回 `aum_total = 8560000.00` 元
- **WHEN** 前端渲染「资产总计」卡片
- **THEN** 卡片 SHALL 展示 `¥8,560,000.00 元`，沿用与「总收入」一致的「元 + 千分位 + 2 位小数」格式
- **AND** 卡片注脚 SHALL 标注「上一日时点值（非日均）」

#### Scenario: 后端尚未实现或返回 null

- **GIVEN** 后端 AUM 聚合尚未上线或本次接口返回 `aum_total = null` / 字段缺省
- **WHEN** 前端渲染「资产总计」卡片
- **THEN** 卡片 SHALL 展示占位文案「暂无数据」并保留卡片结构（不隐藏）
- **AND** 卡片 SHALL **不**触发任何额外的明细查询或错误提示

#### Scenario: 资产总计卡片不可点击

- **GIVEN** 「资产总计」卡片已渲染（无论方案 B 是否返回数值）
- **WHEN** 用户在卡片上 hover 或点击
- **THEN** 卡片 SHALL **不**触发任何路由跳转
- **AND** 鼠标 hover 时 SHALL 展示 tooltip「开发中」
- **AND** 触控端长按 SHALL 不触发任何上下文菜单

### Requirement: 边界与异常态

系统 SHALL 对年度业绩汇总区的「空态 / 加载态 / 错误态 / 权限态」分别提供明确、可预期的 UI 反馈。

#### Scenario: 当前 Tab 完全无业绩

- **GIVEN** 当前 `period_type` 下 4 项 P1 指标的后端返回均为 0（合法零值，非缺数）
- **WHEN** 前端渲染卡片
- **THEN** 4 张 P1 卡片 SHALL 分别显示 `0`（商户类）与 `¥0.00 元`（总收入），不隐藏卡片
- **AND** 资产总计卡片按其方案 B 状态独立处理

#### Scenario: 汇总接口超时或返回 5xx

- **GIVEN** 前端拉取年度业绩汇总接口
- **WHEN** 接口超时或返回 5xx 错误
- **THEN** 4 张 P1 卡片 SHALL 整体替换为「加载失败，点击重试」错误态
- **AND** 系统 SHALL **不**展示任何历史缓存数值（避免错觉）
- **AND** 「重试」点击 SHALL 重新拉取；连续 3 次失败后 SHALL 折叠为「请稍后再来查看」

#### Scenario: 加载中骨架态

- **GIVEN** 用户进入页面或切换 Tab，请求尚未返回
- **WHEN** 卡片处于 pending 状态
- **THEN** 4 张 P1 卡片 SHALL 显示骨架占位（灰色形状块）
- **AND** Tab 高亮 SHALL 立即切换、不等待请求返回
- **AND** 资产总计卡片骨架态独立展示，不阻塞 P1 渲染

#### Scenario: 非客户经理角色误入

- **GIVEN** 当前会话角色非客户经理（如团队主管 / 支行行长 / 管理员）
- **WHEN** 用户访问 `/history-performance`
- **THEN** 年度业绩汇总区 SHALL **不**渲染
- **AND** 后端聚合接口 SHALL 拒绝该会话的汇总请求，返回 403 与错误码 `E_RM_PERF_FORBIDDEN`
- **AND** 前端 SHALL 展示「您当前角色暂不支持查看历史业绩页」并引导返回工作台

### Requirement: 数据权限与刷新基线

系统 SHALL 强制校验汇总接口的「请求员工号 = 当前会话员工号」一致性，并按 T+1 全量回算作为数据刷新基线（与 AUM 一致）。

#### Scenario: 越权请求他人业绩

- **GIVEN** 客户经理 A 持自身会话调用年度业绩汇总接口，但请求参数中显式传入员工号 = B（B ≠ A）
- **WHEN** 后端接收请求
- **THEN** 接口 SHALL 返回 HTTP 403 与错误码 `E_RM_PERF_FORBIDDEN`
- **AND** 后端 SHALL 在审计日志中记录该越权尝试，载荷包含会话员工号、目标员工号、时间戳
- **AND** 前端 SHALL 展示「无权查看其他客户经理业绩」并自动回退至工作台

#### Scenario: 数据更新于 时间戳展示

- **GIVEN** 行内数据仓库已完成 T-1 全量回算（Q-5 基线）
- **WHEN** 客户经理打开历史业绩页
- **THEN** 年度业绩汇总区顶部 SHALL 展示「数据更新于 YYYY-MM-DD HH:mm」（Asia/Shanghai）
- **AND** 时间戳来源 SHALL 为该批次 T+1 任务完成时间，而非前端请求时间

#### Scenario: T+1 任务延迟

- **GIVEN** 当日 T+1 任务延迟未完成、最近可用批次仍为 T-2
- **WHEN** 客户经理打开历史业绩页
- **THEN** 系统 SHALL 沿用最近一批可用结果展示
- **AND** 时间戳右侧 SHALL 追加黄色「数据延迟更新中」提示
