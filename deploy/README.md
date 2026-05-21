# deploy/

Test/E2E environment artifacts for the annual-performance-summary stack.

## Files

| Path | Purpose |
|---|---|
| `docker-compose.test.yml` | Postgres + backend + Prometheus stack for the test cluster |
| `postgres/01-schema.sql` | Applies `migrations/V202605201441__annual_performance_summary.sql` on first start |
| `postgres/02-seed-q1-snapshot.sql` | Q-1 sample snapshot rows (4 RMs covering E5/E9/E11/E12) |
| `prometheus/prometheus.yml` | Scrape config for `/actuator/prometheus` on the backend |

The backend image is built from `apps/backend/Dockerfile`. CI publishes
`ghcr.io/flycatai/rm-backend:<sha>`; local builds tag it `rm-backend:test`.

## Quick start (local)

```bash
# from repo root
docker compose -f deploy/docker-compose.test.yml up --build -d

# wait for readiness
curl --fail --silent http://localhost:8080/actuator/health/readiness | jq

# hit the annual summary endpoint
curl --silent 'http://localhost:8080/api/v1/performance/annual-summary?period_type=current_year' \
  -H 'X-Test-Employee-Id: RM001' | jq

# Prometheus + scrape target (optional profile)
docker compose -f deploy/docker-compose.test.yml --profile observability up -d
open http://localhost:9090/targets
```

## Seed coverage

| Employee | Scenario | E2E cases |
|---|---|---|
| RM001 张三 | Happy path, non-zero, AUM present, history_start_year=2026 | E1, E5, E10, E16 |
| RM002 李四 | Zero-value snapshot, AUM null | E11 (zero/empty) |
| RM003 王五 | `data_delay=true` T+1 delayed | E9 |
| RM004 赵六 | Two biz_dates (2026-05-19, 2026-05-20) to exercise cache key | E12 |

## Tear down

```bash
docker compose -f deploy/docker-compose.test.yml down -v
```

`-v` drops the `postgres-data` volume so init scripts re-run on next start.

## Reset DB only

```bash
docker compose -f deploy/docker-compose.test.yml exec -T postgres \
  psql -U rm -d rm <<'SQL'
TRUNCATE rm_perf_summary_snapshot, rm_aum_snapshot, rm_relationship_manager_snapshot RESTART IDENTITY CASCADE;
SQL
docker compose -f deploy/docker-compose.test.yml exec -T postgres \
  psql -U rm -d rm -f /docker-entrypoint-initdb.d/02-seed-q1-snapshot.sql
```

## Metrics exposed

Backend exposes the OpenSpec-required series on `/actuator/prometheus`:

- `annual_summary_api_latency` (Timer, histogram, tags: `period_type`, `outcome`)
- `annual_summary_forbidden_count` (Counter, tag: `reason`)
- `annual_summary_snapshot_lag_hours` (Distribution summary, tag: `period_type`)

Existing Spring/JVM metrics (`http_server_requests_seconds`, `jvm_memory_used_bytes`, etc.) are also exported under `http_server_requests` / `jvm_*` namespaces.
