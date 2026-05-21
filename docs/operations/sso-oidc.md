# 行内 SSO（OIDC）后端配置说明

> 作用：B-SSO 阻塞项的代码侧落地说明。仓库不保留任何真实凭据，所有敏感值通过环境变量或 K8s `Secret` 注入。
> 关联：`apps/backend/src/main/java/com/flycat/rm/auth/oidc/`、`docs/research/sso-research.md`。

## 1. 实现概览

- SPI：`com.flycat.rm.auth.SsoClient`（`exchange(code)` / `refresh(sessionToken)`）。
- 生产实现：`com.flycat.rm.auth.oidc.HttpSsoClient`，按 OIDC `authorization_code` 流程实现：
  1. POST `${SSO_OIDC_TOKEN_ENDPOINT}` 用 `code` 换 `access_token`；
  2. GET `${SSO_OIDC_USERINFO_ENDPOINT}` 用 `Bearer access_token` 拉 userinfo claims；
  3. 把 `employee_id / name / branch_id / team_id / role` 映射成 `Principal`。
- 失败映射：exchange 任一环节失败 → `BusinessException(ErrorCode.SSO_LOGIN_FAILED)`；refresh 失败 → `BusinessException(ErrorCode.SESSION_REFRESH_FAILED)`。底层 `RestClientException` 与凭据不会外泄到日志/响应。

## 2. 配置 key 与环境变量

| 配置 key                       | 环境变量                       | 必填  | 默认             | 说明                                                |
|--------------------------------|--------------------------------|-------|------------------|-----------------------------------------------------|
| `sso.oidc.enabled`             | `SSO_OIDC_ENABLED`             | 否    | `false`（prod 强制 `true`）| 关闭时不注册 `HttpSsoClient` bean                   |
| `sso.oidc.token-endpoint`      | `SSO_OIDC_TOKEN_ENDPOINT`      | 是    | —                | OIDC token endpoint，必须 https                      |
| `sso.oidc.userinfo-endpoint`   | `SSO_OIDC_USERINFO_ENDPOINT`   | 是    | —                | OIDC userinfo endpoint                              |
| `sso.oidc.client-id`           | `SSO_OIDC_CLIENT_ID`           | 是    | —                | 行内 SSO 颁发的 client_id                            |
| `sso.oidc.client-secret`       | `SSO_OIDC_CLIENT_SECRET`       | 是    | —                | client_secret，仅来自 K8s Secret / vault             |
| `sso.oidc.redirect-uri`        | `SSO_OIDC_REDIRECT_URI`        | 是    | —                | 与 SSO 后台登记的 redirect 完全一致                  |
| `sso.oidc.connect-timeout`     | `SSO_OIDC_CONNECT_TIMEOUT`     | 否    | `2s`             | 连接超时                                            |
| `sso.oidc.read-timeout`        | `SSO_OIDC_READ_TIMEOUT`        | 否    | `5s`             | 读超时                                              |
| `sso.oidc.role-claim`          | `SSO_OIDC_ROLE_CLAIM`          | 否    | `role`           | userinfo 中角色 claim 字段名                         |
| `auth.test-principal.enabled`  | `AUTH_TEST_PRINCIPAL_ENABLED`  | —     | `false`（prod 强制 `false`）| 仅 dev/compose 注入 `X-Test-Employee-Id`，prod 关闭 |

`application-prod.yml` 显式强制：

```
auth.test-principal.enabled: false
sso.oidc.enabled: true
sso.oidc.{token-endpoint,userinfo-endpoint,client-id,client-secret,redirect-uri}: ${ENV} （无默认值，缺失启动即失败）
```

任一必填 endpoint / 凭据缺失时，`OidcProperties.validate()` 抛 `IllegalStateException`，Spring 上下文不会启动 → fail-closed。

## 3. userinfo claims 口径

`HttpSsoClient` 读取以下字段（缺失或为 `null` 时按下表降级）：

| claim         | 必填  | 缺失行为                                            |
|---------------|-------|----------------------------------------------------|
| `employee_id` | 是    | 抛 `SSO_LOGIN_FAILED`                              |
| `name`        | 否    | 降级使用 `employee_id`                              |
| `branch_id`   | 否    | `Principal.branchId = null`                        |
| `team_id`     | 否    | `Principal.teamId = null`                          |
| `role`        | 否    | 默认 `RELATIONSHIP_MANAGER`；无法识别的值同样降级    |

`role` 解析规则：大小写不敏感，`-` 视为 `_`；当前仅识别 `RELATIONSHIP_MANAGER` / `TEAM_LEADER`（design.md Decision 2）。

> SSO 实际 claim 名最终以 PM《下游接口对齐纪要》为准；如有差异，调整 `OidcProperties.role-claim` 或在 SSO 侧做映射，无需改后端实现。

## 4. 凭据与禁项

- 仓库内严禁出现真实 `client_secret`、`redirect_uri`、`token_endpoint`、`userinfo_endpoint` 真实值。
- `application.yml` / `application-prod.yml` 仅保留 `${SSO_OIDC_*}` 占位。
- 测试桩使用 `secret-not-real` 字面量；CI 上 `mvn test` 不调用任何外部地址（Spring `MockRestServiceServer` 全栈拦截）。
- K8s 端凭据应通过 `Secret`（参见 DevOps 在 `deploy/k8s/uat/secret.example.yaml` 的模板）。

## 5. 测试覆盖

- `HttpSsoClientTest`（12 case）：正向 → role 映射 / 缺省 role；负向 → 空 code、token 4xx/5xx、access_token 缺失、userinfo 4xx、缺 `employee_id`；refresh → 正向 + 空 token + invalid_grant + 缺 access_token。
- `OidcPropertiesTest`（6 case）：5 个必填字段缺失分别抛错，全部填好 `validate()` 通过。
- 全量回归：`mvn -B -ntp test` 97/97 通过。

## 6. 下一步（不属于本 commit 范围）

- 真正调用 `HttpSsoClient` 的 servlet filter（小程序 `/auth/sso/exchange` 入口）尚未实现，需 1.6 OTP 与会话续期一并完成。
- 行内 SSO sandbox 账号、`redirect_uri` 登记、`client_secret` 由 PM/DBA 提供后即可 `SSO_OIDC_*` 环境变量到 UAT。
