# 页面骨架占位

5.2-5.12 各页面需结合最终选定的小程序框架（uni-app / 原生 / Taro）补 `.vue` 或 `.wxml`+`.js`。

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
