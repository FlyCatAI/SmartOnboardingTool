# Controller Blockers — `GET /api/v1/performance/annual-summary`

Task **2.5** in `openspec/changes/annual-performance-summary/tasks.md`. This
document is what the PM asked for in the **5/20 backend round-2** message:
"先明确最小待决项与建议选型，不要自行引入未批准框架".

The application layer (`AnnualPerformanceQueryService`) and the JDBC adapters
are framework-free and ready for any of the candidate stacks below. The only
thing blocking 2.5 is the **HTTP-binding** layer (the `@RestController` /
`@Path` / route declaration), which requires a Web framework decision.

## Status of the controller-adjacent surface

| Layer | Status | File |
|---|---|---|
| Domain (aggregator, intervals, period windows) | Done | `src/main/java/com/flycat/rm/performance/domain/*` |
| Application layer (RBAC + audit + AUM fallback + history_start_year) | Done | `src/main/java/com/flycat/rm/performance/api/AnnualPerformanceQueryService.java` |
| SPI / read adapters (`JdbcPerformanceSnapshotRepository`, `JdbcAumSnapshotRepository`) | Done | `src/main/java/com/flycat/rm/performance/adapter/jdbc/*` |
| T+1 batch driver (`SnapshotBuildJob`) | Done | `src/main/java/com/flycat/rm/performance/batch/SnapshotBuildJob.java` |
| HTTP controller binding `GET /api/v1/performance/annual-summary` | **Blocked** on Decisions 1–4 below | — |
| Cache (task 2.7) | Blocked on Decision 1 + 3 | — |
| Integration tests (task 2.8) | Blocked on Decision 1 + 2 | — |

## Minimum decisions needed (D1–D4)

### D1. Web framework

**Options** (from `BUILD_TOOL_TBD.md`, plus what `design.md` Decision 7 hints
at):

- **A. Spring Boot 3.x + Spring MVC (Servlet)** — *recommended*.
- B. Spring Boot 3.x + Spring WebFlux (reactive).
- C. Quarkus 3.x.

**Recommendation: A (Spring Boot 3.x + Spring MVC, JDK 21).**

Reasoning:

| Criterion | A: Spring MVC | B: WebFlux | C: Quarkus |
|---|---|---|---|
| Fits existing skeleton (`@RequiresRole` annotation, `Principal`, `AuditLogService` interfaces) | Direct — these read like Spring stereotypes | Forces every service method into `Mono<…>` | Drop-in but adds tech-stack risk inside a team that has more Spring experience |
| API workload | T+1 snapshot reads, P95 ≤ 500ms (`design.md` §SLA). One DB call. No streaming. | Reactive gives nothing here — load is non-streaming, low-concurrency RM count | Same |
| RBAC interception | `@RequiresRole` is already designed annotation-style; Spring AOP / `HandlerInterceptor` is the natural binding | Needs `WebFilter` rewriting | Needs CDI interceptor + Quarkus security |
| Cache (task 2.7) | `@Cacheable` / Spring Cache abstraction is well-trodden for `(employee_id, period_type, biz_date)` keys | Requires reactive cache wrappers | Manual Caffeine |
| Migration to MyBatis/JPA later | Both first-class in Spring Boot starters | Same | Panache, but another DSL to learn |
| Risk to scope | None — recommendation aligns with the de-facto stack the prior skeleton expected | Adds reactive learning curve to a non-streaming use case | Adds JVM/native build complexity that the team hasn't validated |

If the team has already standardized on Quarkus elsewhere, **C** is also
acceptable; the JDBC adapter and `AnnualPerformanceQueryService` would not
change — only the controller class and the DI bootstrap.

### D2. Build tool

**Options:**

- **A. Maven** — *recommended*.
- B. Gradle (Kotlin DSL).

**Recommendation: A (Maven).**

Reasoning:

- The other repo modules (`apps/miniprogram`) use `npm`; `apps/backend` has
  no committed build file yet. Maven's `pom.xml` is closer to the team's
  prior Java muscle memory in similar codebases, and a single `pom.xml` is
  easier to review in PR than a multi-file Gradle setup for a module this
  small.
- Both Gradle and Maven satisfy the test-runner + Spring Boot starter
  needs; this is a low-stakes choice. Decision can be reversed at low cost.
- If the team picks **B (Gradle)**, nothing in the source tree changes;
  only the root build file differs.

### D3. JDK target

**Options:** 17 LTS / 21 LTS.

**Recommendation: 21 LTS.**

- Existing code (records, text-block SQL, switch-pattern in error mapping
  later) already presumes ≥17. 21 LTS is the current LTS and matches the
  Spring Boot 3.x baseline best.
- Local environment is JDK 17.0.12; bumping to 21 is a one-line target
  change in the future Maven config and adds no migration cost.
- If the deploy environment is locked to 17, that is also fine — none of
  the committed code uses 18–21-exclusive APIs today.

### D4. Persistence layer

**Options:**

- **A. Spring `JdbcClient` (Spring 6.1+) / `NamedParameterJdbcTemplate`** —
  *recommended*.
- B. MyBatis 3.
- C. Spring Data JPA / Hibernate.

**Recommendation: A.**

- The summary read path is a single `SELECT` against a view, and the batch
  driver runs one canonical SQL statement. JPA/MyBatis carries mapping
  overhead with no benefit for one query each.
- The JDBC adapter classes already deal with `Connection` /
  `PreparedStatement` directly; `JdbcClient` is a thin wrapper that lets
  us delete the in-house boilerplate without changing the SPI shape.
- `SnapshotBuildJob.JobRunner` can be implemented in ~20 lines with
  `NamedParameterJdbcTemplate.update`. No additional library needed.
- If the wider product needs JPA elsewhere, MyBatis/JPA can coexist;
  this module only requires `spring-boot-starter-jdbc` (+ the PostgreSQL
  driver).

## What the controller PR will look like (preview, not yet committed)

Once D1–D4 are approved, the 2.5 PR adds:

```
apps/backend/pom.xml                                            (build tool: D2)
apps/backend/src/main/java/com/flycat/rm/RmApplication.java     (Spring boot entry)
apps/backend/src/main/java/com/flycat/rm/performance/web/
    AnnualPerformanceController.java                            (REST binding)
    AnnualPerformanceResponse.java                              (DTO -> JSON shape)
    GlobalErrorHandler.java                                     (E_RM_PERF_FORBIDDEN -> 403)
apps/backend/src/main/resources/application.yml                 (datasource + cache)
```

Approximate shape (Spring MVC):

```java
@RestController
@RequestMapping("/api/v1/performance")
@RequiresRole(Role.RELATIONSHIP_MANAGER)
class AnnualPerformanceController {
    private final AnnualPerformanceQueryService service;

    @GetMapping("/annual-summary")
    AnnualPerformanceResponse get(
            @RequestParam("period_type") String periodType,
            @RequestParam(value = "employee_id", required = false) String employeeId,
            @AuthenticationPrincipal Principal caller) {
        SummaryResult r = service.getSummary(
                caller,
                Optional.ofNullable(employeeId),
                PeriodType.ofWire(periodType));
        return AnnualPerformanceResponse.from(r);
    }
}
```

The controller is a 30-line shell because all business logic (RBAC, audit,
AUM fallback, history_start_year, error codes) is already in
`AnnualPerformanceQueryService`. Once D1–D4 ship, the implementation +
tests can land in a single small PR without rework of anything below.

## Out of scope of this document (deferred until D1–D4 land)

- Cache wiring (task 2.7) — needs the Spring/Quarkus cache abstraction in
  scope so the key namespace `(employee_id, period_type, biz_date)` can
  use the framework's idiomatic eviction.
- Feature flag (`annualPerformanceSummary.enabled`) — straightforward in
  Spring (`@ConditionalOnProperty`) but trivially adjustable in any of the
  candidates.
- Auth integration (`Principal` resolution from JWT/SSO) — already
  abstracted; controller just needs `@AuthenticationPrincipal` (Spring
  Security) or `@Context SecurityIdentity` (Quarkus).
- OpenAPI generation — `springdoc-openapi-starter-webmvc-ui` if D1=A;
  Quarkus has built-in if D1=C.

## Asks (what unblocks 2.5)

| ID | Decision | Owner | Default if no input by 2026-05-21 EOB |
|---|---|---|---|
| D1 | Web framework | Tech lead / 解决方案架构师 Agent | **A. Spring Boot 3.x + Spring MVC** (no PR yet; flag in tasks.md as awaiting confirmation) |
| D2 | Build tool | Tech lead | **A. Maven** |
| D3 | JDK target | Tech lead + DevOps | **21 LTS** if deploy infra supports, else 17 |
| D4 | Persistence | Tech lead | **A. Spring JdbcClient / NamedParameterJdbcTemplate** |

The "default" column reflects the recommendation, but per the PM message
we will **not** scaffold the framework until D1–D4 are explicitly
approved.
