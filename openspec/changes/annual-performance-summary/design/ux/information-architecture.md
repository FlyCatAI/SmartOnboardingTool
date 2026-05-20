# Information Architecture - annual-performance-summary

## 站点结构

```text
工作台 /
├─ 顶部个人 / 角色信息
├─ 数据卡片区
├─ 快捷入口区
│  ├─ 我的商户
│  ├─ 我的任务
│  ├─ 扫一扫
│  └─ 我的 / 更多
│     └─ 我的历史业绩  ->  /history-performance
└─ 待办聚合区

/history-performance
├─ 页面标题：历史业绩
├─ 年度业绩汇总区
│  ├─ 区域标题：年度业绩汇总
│  ├─ 数据更新时间：数据更新于 YYYY-MM-DD HH:mm
│  ├─ 延迟提示：数据延迟更新中（仅延迟时）
│  ├─ period_type Tab
│  │  ├─ 本年度      current_year
│  │  └─ 历史汇总    all_time
│  │     └─ 标注：自 2026 年起（仅选中历史汇总时）
│  ├─ P1 指标卡行
│  │  ├─ 入网商户    -> /history-performance
│  │  ├─ 达标商户    -> /history-performance?type=qualified
│  │  ├─ 有效商户    -> /history-performance?type=active
│  │  └─ 总收入      -> /income-details
│  └─ P2 资产总计
│     └─ 不可点击；null 时显示暂无数据；hover 提示开发中
└─ 历史业绩明细区（本提案仅定义入口与 type 参数语义）
   ├─ 默认入网口径      type 缺省 / new / 未知值
   ├─ 达标商户口径      type=qualified
   └─ 有效商户口径      type=active
```

## 页面层级与优先级

1. 「年度业绩汇总区」固定在 `/history-performance` 顶部，优先于明细区。
2. Tab 是汇总区全局周期控制，影响 4 张 P1 卡片和 P2 资产总计。
3. `type` 是明细区口径控制，不改变当前 Tab，也不改变汇总区数值。
4. 「历史汇总」起始年标注属于 Tab 语境信息，不能放在单张卡片内，以免被误解为只作用于某个指标。
5. 总收入只展示后端聚合结果，前端不得从明细区求和反推。

## 内容模型

| 模块 | 字段 / 内容 | 说明 |
| --- | --- | --- |
| 汇总区标题 | 年度业绩汇总 | 与业务命名保持一致。 |
| 更新时间 | `updated_at` | Asia/Shanghai，精确到分钟；来源为 T+1 批次完成时间。 |
| 延迟提示 | 数据延迟更新中 | 当 T+1 未完成且沿用最近批次时展示。 |
| Tab | 本年度 / 历史汇总 | 绑定 `period_type=current_year/all_time`。 |
| 历史起始标注 | 自 2026 年起 | 仅历史汇总选中时展示。 |
| 入网商户 | 整数 | 按入网时间归集，默认明细口径。 |
| 达标商户 | 整数 | 周期内曾达到已达标，去重。 |
| 有效商户 | 整数 | 周期内曾达到已有效，去重。 |
| 总收入 | `¥1,234.00 元` | 后端聚合，前端不二次求和。 |
| 资产总计 | 金额或暂无数据 | Q-1 方案 B，P2 占位能力。 |

## 导航与参数

| 来源 | 动作 | 目标 | 参数处理 |
| --- | --- | --- | --- |
| 工作台「我的历史业绩」 | 点击 | `/history-performance` | 默认 `period_type=current_year`；明细 `type` 缺省。 |
| 入网商户卡 | 点击 | `/history-performance` | 清除 `type`，滚动到明细区默认入网口径。 |
| 达标商户卡 | 点击 | `/history-performance?type=qualified` | 明细过滤达标商户。 |
| 有效商户卡 | 点击 | `/history-performance?type=active` | 明细过滤有效商户。 |
| 总收入卡 | 点击 | `/income-details` | 不传 `period_type`，目标页自行定义。 |
| 资产总计卡 | hover / 点击 | 当前页 | 无跳转；hover 仅提示「开发中」。 |

## API 需求清单

| 编号 | 场景 | API 需求 | 传给架构师的约束 |
| --- | --- | --- | --- |
| API-UX-01 | 进入历史业绩页 | 会话 / 角色校验 | 仅客户经理可见；非客户经理不渲染汇总区。 |
| API-UX-02 | 汇总区加载 | 汇总聚合接口 | 请求参数只需 `period_type`；员工号从会话取，不信任前端传入。 |
| API-UX-03 | 汇总响应 | P1 / P2 / 时间戳 | 返回 `new_merchants`、`qualified`、`active`、`income`、`aum_total`、`updated_at`、延迟标记。 |
| API-UX-04 | AUM P2 占位 | 可选 AUM 字段 | `aum_total=null` 或缺省必须是合法状态，不触发错误。 |
| API-UX-05 | Tab 竞态 | 请求取消或响应版本号 | 前端需要能取消旧请求，或以 request token 丢弃旧响应。 |
| API-UX-06 | 明细区跳转 | 明细列表口径 | `type=qualified/active` 必须被明细列表识别；缺省 / new / 未知值按入网口径。 |
| API-UX-07 | 越权保护 | 403 + 错误码 | 员工号不一致返回 `E_RM_PERF_FORBIDDEN`，并写审计日志。 |

