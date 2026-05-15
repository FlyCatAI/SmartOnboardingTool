# 构建工具待选

design.md Decision 7 明确技术栈由开发团队评审决定。本目录预留 Java 源码包结构，但**不锁定**：

- 构建工具（Maven / Gradle）
- Web 框架（Spring Boot 3.x / Spring WebFlux / Quarkus）
- 持久层（JPA / MyBatis-Plus）
- JDK 版本（17 / 21）

待开发团队第一周技术评审产出后，由架构师补 `pom.xml` 或 `build.gradle.kts`、`application.yml`、入口类 `RmApplication`，并把 SPI 占位接口接到具体实现 bean。

## 当前可用文件

- `src/main/java/com/flycat/rm/common/error/` — 错误码与业务异常
- `src/main/java/com/flycat/rm/common/rbac/` — 角色 / 数据范围 / 主体 / `@RequiresRole`
- `src/main/java/com/flycat/rm/common/audit/` — 操作日志契约与 `@Audited`
- `src/main/java/com/flycat/rm/common/statemachine/` — 通用状态机
- `src/main/java/com/flycat/rm/common/otp/` — OTP 通道 SPI + 明文窗口策略
- `src/main/java/com/flycat/rm/common/clock/` — 时钟抽象（测试可注入）
- `src/main/java/com/flycat/rm/common/FileStorage.java` — 文件存储 SPI
- `src/main/java/com/flycat/rm/auth/SsoClient.java` — SSO 客户端 SPI
- `src/main/java/com/flycat/rm/merchant/MerchantDataClient.java` — 商户主数据 SPI
- `src/main/java/com/flycat/rm/merchant/AcquirerClient.java` — 收单交易系统 SPI
- `src/main/java/com/flycat/rm/merchant/FollowupPolicy.java` — 24h 编辑窗口 + 9 张图片上限
- `src/main/java/com/flycat/rm/task/` — 七态状态机 + 事件枚举 + Factory
- `src/main/java/com/flycat/rm/notification/PushChannel.java` — 推送通道 SPI
- `src/main/java/com/flycat/rm/notification/NotificationKind.java` — 14 类通知 + 通道策略
- `src/main/java/com/flycat/rm/notification/QuietHours.java` — 免打扰时段策略

## 单测

- `src/test/java/com/flycat/rm/task/TaskStateMachineTest.java` — 验证 5 个 spec Scenario
- `src/test/java/com/flycat/rm/notification/QuietHoursTest.java` — 默认 22-08 跨午夜
- `src/test/java/com/flycat/rm/merchant/FollowupPolicyTest.java` — 编辑窗口 + 图片数

测试依赖 JUnit 5（`org.junit.jupiter.api.*`），构建工具就绪后即可运行。
