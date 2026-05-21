# 页面骨架占位

> **5/21 用户决策**：技术栈 = **uni-app**（Vue 3），新增页面优先落 `.vue` 实现，
> 兼容微信 / 字节小程序构建。不再引入独立 React/TSX 路线。

5.2-5.12 各页面在 uni-app 工程下补 `.vue` SFC（参考 `history-performance/HistoryPerformance.vue`）。

| 目录 | 对应 spec | tasks.md 编号 |
|---|---|---|
| `login/` | `auth-and-identity/spec.md` | 4.1 + 5.1 |
| `workstation/` | `workstation/spec.md` | 5.2 |
| `merchant/list` | `merchant-management/spec.md` Requirement 1 | 5.3 |
| `merchant/detail` | `merchant-management/spec.md` Requirement 2-5 | 5.4 - 5.6 |
| `task/list` | `task-management/spec.md` Requirement 2 | 5.7 |
| `task/detail` | `task-management/spec.md` Requirement 3 + 5-7 | 5.8 |
| `task/pool` | `task-management/spec.md` Requirement 4 | 5.9 |
| `notification/` | `notification/spec.md` 通知中心 | 5.10 |
| `settings/` | `auth-and-identity/spec.md` 订阅消息授权 + 免打扰 | 5.12 |

每个页面在落地时务必：

1. 在顶部留 `// SPEC: <capability>/spec.md - Requirement <名> - Scenario <名>` 注释，方便回溯
2. 引用 `utils/task-status.ts`（不要再硬编码 7 个状态字符串）
3. 引用 `utils/remaining-time.ts` 输出剩余时间标签（不要重复实现颜色阈值）
4. 业务错误码 toast 走统一 i18n 表（待补 `services/error-messages.ts`）
