# UX ADR - annual-performance-summary

## ADR-001: 使用页面顶部固定汇总区承载年度业绩

Status: Accepted

Context:

客户经理进入历史业绩页的核心目标是先获得可汇报的年度 / 历史累计数字，再决定是否查看明细。OpenSpec 要求页面顶部固定渲染年度业绩汇总区，并要求 1440x900 首屏内完整展示。

Decision:

在 `/history-performance` 顶部放置「年度业绩汇总」区，汇总区先于明细区展示。结构固定为：标题与数据更新时间、`period_type` Tab、4 张 P1 指标卡、1 张 P2 资产总计卡。

Alternatives:

- 将汇总区放在明细列表下方：不采用。用户需要先滚动才能获得核心结论，违背快速汇报目标。
- 将年度与历史做成两个独立页面：不采用。会增加路由与心智成本，也不利于同一批指标对比。

Consequences:

- 首屏必须控制汇总区高度，避免压缩明细入口。
- UI 阶段需要保证移动端 2 列、宽屏 4 列的稳定布局。

## ADR-002: Tab 控制汇总周期，type 参数只控制明细口径

Status: Accepted

Context:

OpenSpec 同时定义 `period_type` 和 `type`：前者用于本年度 / 历史汇总切换，后者用于明细列表的入网 / 达标 / 有效过滤。若二者混用，会导致用户误以为点击达标商户会改变汇总周期。

Decision:

`period_type` 只由「本年度 / 历史汇总」Tab 控制，并触发整个汇总区重新拉取；`type` 只影响明细区口径，不改变汇总区当前周期。

Alternatives:

- 点击指标卡同时改变 Tab 与明细过滤：不采用。跨维度联动过多，容易造成数据错位。
- 将 `type` 也做成汇总区 Tab：不采用。会把指标卡变成筛选器，削弱 4 项 P1 指标并列比较。

Consequences:

- 前端实现需拆清两个状态：`period_type` 与 `type`。
- 路由带 `type` 打开页面时，汇总区仍按默认本年度加载，明细区按 `type` 过滤。

## ADR-003: 入网商户卡不使用 type=new

Status: Accepted

Context:

业务 Q-7 已确认取消 `type=new` 跳转。OpenSpec 要求 `type=new` 与未知值都视为缺省，默认明细口径为入网商户。

Decision:

入网商户卡点击跳转 `/history-performance`，不携带 `type` 参数。若用户通过旧链接访问 `?type=new`，系统忽略参数并按默认入网口径展示。

Alternatives:

- 保留 `type=new` 作为兼容参数：不采用。会与审批口径冲突，并增加后续路由维护成本。
- 入网商户卡不可点击：不采用。OpenSpec 要求点击后滚动到明细区。

Consequences:

- 前端路由解析应显式把 `new` 视为缺省，避免报错。
- 文档和测试用例需避免再出现 `type=new` 作为合法行为。

## ADR-004: AUM 资产总计作为 P2 占位卡保留

Status: Accepted

Context:

Q-1 采用方案 B：前端预留可选接口，后端可返回 `aum_total=null`。OpenSpec 要求卡片保留，不隐藏，不触发额外明细查询。

Decision:

资产总计始终作为第二行全宽卡片渲染。返回有效金额时展示金额和「上一日时点值（非日均）」；返回 null 或缺省时展示「暂无数据」。卡片不可点击，hover 提示「开发中」。

Alternatives:

- 后端未实现前隐藏卡片：不采用。会造成布局跳变，也不符合 P2 占位策略。
- 点击资产总计进入建设中页面：不采用。没有可用明细页，增加无效跳转。

Consequences:

- UI 阶段需设计不可点击态和 tooltip / 触控端等效提示。
- 测试需覆盖 `aum_total` 有值、null、缺省三类响应。

## ADR-005: 错误态不展示历史缓存数值

Status: Accepted

Context:

OpenSpec 明确接口超时或 5xx 时不展示历史缓存数值，避免用户把过期数字用于汇报。

Decision:

汇总接口失败时，P1 四卡整体替换为错误态「加载失败，点击重试」。不保留上次成功数值。连续 3 次失败后折叠为「请稍后再来查看」。

Alternatives:

- 展示旧数据并加过期标记：不采用。银行业绩汇报场景对时点敏感，过期数字仍可能被误用。
- 单卡独立错误：不采用。P1 四项来自同一汇总接口，拆散错误会误导为部分数据可信。

Consequences:

- 成功数据缓存可用于性能优化，但失败态不得展示缓存值。
- 重试动作必须绑定当前 `period_type`。

## ADR-006: 非客户经理采用页面级权限态

Status: Accepted

Context:

本提案仅支持客户经理本人视角。主管 / 支行行长已有各自工作台聚合能力，但不在本期查看个人历史业绩汇总。

Decision:

非客户经理访问 `/history-performance` 时不渲染汇总区，展示页面级权限态与「返回工作台」按钮。后端聚合接口仍必须返回 403 和 `E_RM_PERF_FORBIDDEN`。

Alternatives:

- 展示空汇总区：不采用。会让用户误以为自己暂无业绩。
- 直接静默跳回工作台：不采用。用户无法理解跳转失败原因。

Consequences:

- 菜单入口应对非客户经理隐藏，但直接 URL 仍需兜底。
- 权限态文本不得暴露其他角色数据存在性。

