# RM 展业小程序 — 后端服务脚手架

对应 OpenSpec change `rm-mvp-prd`，覆盖 6 个 capability（spec_delta 后从 5 → 6，新增 ops-console）：

- `auth-and-identity` — SSO 会话、RBAC（三类角色含支行行长）、OTP
- `workstation` — 工作台聚合（含支行行长只读聚合）
- `merchant-management` — 商户档案与跟进
- `task-management` — 任务七态状态机 + 任务池可见范围（同团队 / 自定义，认领后锁定）
- `notification` — 双通道通知与未读计数
- `ops-console` — 运营后台配置中心（敏感字段明文窗口可配置等）

## 状态

**这是脚手架，不是可上线代码。** 下游 6 个系统（SSO / 商户主数据 / 收单系统 / 推送通道 / 文件存储 / 安合）的接口契约尚未对齐（详见 [`docs/research/sso-research.md`](../../docs/research/sso-research.md) 与 PM 任务 GRA-11）。本目录交付：

- 多模块包结构，按 capability 拆分
- 通用横切关注点骨架：`@RequiresRole` 注解、`AuditLogAspect`、统一错误码 `ErrorCode`、状态机框架 `StateMachine<S, E>`
- 外部依赖的 SPI 接口（`SsoClient` / `OtpChannel` / `MerchantDataClient` / `AcquirerClient` / `PushChannel` / `FileStorage`），仅有占位实现（throws `NotImplementedException`），具备接口契约后由实现类填充
- 任务状态机的合法流转表（来自 design.md Decision 1）以单元测试形式锚定

## 技术栈候选

- JDK 17 + Spring Boot 3.x（候选项之一，最终由开发团队评审）
- 持久层未确定（JPA / MyBatis-Plus 二选一）
- 缓存 / 消息队列 / 数据库均待行内技术规范输入

## 如何构建

脚手架阶段不绑定构建工具。在最终评审通过技术栈后，再补 `pom.xml` / `build.gradle.kts`。

## 与 OpenSpec spec 的对应关系

| 模块 | 对应 spec | 主要 Requirement |
|---|---|---|
| `auth` | `auth-and-identity/spec.md` | SSO 登录、会话续期、RBAC（三类角色）、OTP |
| `workstation` | `workstation/spec.md` | 工作台聚合首屏 ≤ 2s（含支行行长只读聚合） |
| `merchant` | `merchant-management/spec.md` | 列表/详情/跟进/敏感字段脱敏 |
| `task` | `task-management/spec.md` | 七态状态机 / 任务池可见范围 / 认领锁定 / 转派 / 完成回报 |
| `notification` | `notification/spec.md` | 订阅消息 + 站内双通道 |
| `common.audit` | 全 spec 横切 | 操作日志 ≥ 6 个月 |
| `common.otp` | `auth-and-identity` + `merchant-management` + `ops-console` | 敏感字段 OTP 解密窗口（默认 30s，运营后台可配 10-120s） |
| `common.rbac` | `auth-and-identity` | 三类角色（客户经理 / 团队主管 / 支行行长）；支行行长 = 只读聚合 + 团队下钻 |
