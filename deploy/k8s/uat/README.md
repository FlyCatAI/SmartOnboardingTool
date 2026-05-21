# UAT / production Kubernetes manifests (annual-performance-summary)

Source-of-truth manifests for promoting the `rm-backend` Spring Boot image to a
real cluster. Compose verification (`deploy/docker-compose.test.yml`) already
covers local E2E and metrics; these files cover everything the cluster needs
on top of that.

Manifests are intentionally plain YAML (no Helm, no Kustomize overlay) so a
cluster operator can `kubectl apply -f` after a small number of in-file
`REPLACE_*` substitutions. They map 1:1 to the role-spec deploy flow
(canary 10% → SRE monitor → full rollout / rollback).

## Files

| File | Purpose |
|---|---|
| `namespace.yaml` | `rm-annual-summary-uat` namespace |
| `configmap.yaml` | non-secret env (profile=prod, AUTH_TEST_PRINCIPAL_ENABLED=false) |
| `secret.example.yaml` | template for DB + SSO secrets — DO NOT commit populated copy |
| `deployment.yaml` | stable track Deployment + ServiceAccount (3 replicas, non-root, RO root FS) |
| `service.yaml` | ClusterIP Service, PodDisruptionBudget, HPA (3→8 @ 65% CPU) |
| `ingress.yaml` | Ingress + NetworkPolicy (locks ingress to ingress-nginx + monitoring ns) |
| `canary.yaml` | canary track Deployment (replicas=1, shares Service) |
| `servicemonitor.yaml` | Prometheus Operator ServiceMonitor (skip if cluster uses static scrape) |

## Required substitutions

Grep `REPLACE_` before applying. Each placeholder must be supplied by the
cluster operator / platform team:

| Placeholder | Where | Source |
|---|---|---|
| `REPLACE_IMAGE_TAG` | `deployment.yaml` | GHCR tag of the stable build (commit SHA) |
| `REPLACE_SOURCE_SHA` | `deployment.yaml` | git SHA of the stable rollout (audit annotation) |
| `REPLACE_CANARY_TAG` / `REPLACE_CANARY_SHA` | `canary.yaml` | GHCR tag + SHA of the canary build |
| `REPLACE_INGRESS_CLASS` | `ingress.yaml` | e.g. `nginx`, `higress`, `apisix` |
| `REPLACE_UAT_HOST` | `ingress.yaml` | UAT FQDN, e.g. `rm-uat.example.cn` |
| `REPLACE_TLS_SECRET` | `ingress.yaml` | name of the `kubernetes.io/tls` secret in this namespace |
| `REPLACE_DB_HOST` / `REPLACE_DB_USER` / `REPLACE_DB_PASSWORD` | `secret.example.yaml` | UAT Postgres credentials |
| `REPLACE_SSO_ISSUER` / `REPLACE_SSO_CLIENT_ID` / `REPLACE_SSO_CLIENT_SECRET` | `secret.example.yaml` | OIDC / 行内 SSO credentials |

## Hard blockers (cannot proceed without human input)

The compose track is complete; these items need humans because they touch real
infrastructure that no agent has credentials for:

| ID | Blocker | Owner / what to provide |
|---|---|---|
| **B-CLUSTER** | No UAT namespace / kubeconfig | Platform team: provision `rm-annual-summary-uat` namespace, hand back kubeconfig + RBAC (`get,list,patch deployments,services,ingress,configmaps,secrets` in that ns) |
| **B-INGRESS** | No UAT host / TLS cert / ingress class | Network team: assign FQDN (suggest `rm-uat.<corp>`), create TLS secret in the namespace, declare ingress class |
| **B-SSO** | Production `SsoClient` not implemented — only the SPI interface exists. `TestPrincipalFilter` MUST stay off (config already enforces `AUTH_TEST_PRINCIPAL_ENABLED=false`). Without a real implementation every UAT request hits 401. | Auth team: implement `SsoClient.exchange(code)` against 行内 OIDC; also need issuer URL + client id/secret for the secret manifest |
| **B-DB** | No managed Postgres endpoint for UAT (compose used the local container). Migrations `migrations/V202605201441__annual_performance_summary.sql` must be applied; UAT data load policy (anonymised prod snapshot vs. seed) unconfirmed. | DBA: provision Postgres, run `psql -f migrations/V*.sql`, decide on UAT data set |
| **B-CI** | CI workflows are still under `ci/workflows-pending/` (agent token lacks `workflow` OAuth scope, push rejected by GitHub). Until promoted, GHCR has no `rm-backend:<sha>` images — `REPLACE_IMAGE_TAG` has nothing to point at. | Repo admin: `git mv ci/workflows-pending/*.yml .github/workflows/ && git rm ci/workflows-pending/README.md && git commit -m "ci: enable workflows"` and enable Actions → Workflow permissions: "Read and write" |
| **B-ZAP** | No OWASP ZAP runner / production-grade load test runner is wired into CI. Compose `ab` numbers are single-node and not representative. | Security + perf team: confirm ZAP scan target (baseline vs. full) + load-gen environment, then add the workflow under `.github/workflows/` |

If a blocker is hit, post the blocker ID in the comment and stop. Do **not**
work around B-SSO by enabling `AUTH_TEST_PRINCIPAL_ENABLED` outside of
compose / local — the filter is gated for a reason and the prod `application.yml`
default keeps it off.

## Deploy flow (once blockers are cleared)

Follows the role-spec flow and existing audit pattern (git tags per stage).

### 1. Build & publish

CI (`backend-ci.yml`) produces `ghcr.io/flycatai/rm-backend:<sha>`. After
B-CI is cleared, every push to `change/**` re-tags `latest` and the sha.

For an ad-hoc build:

```bash
docker buildx build -t ghcr.io/flycatai/rm-backend:$(git rev-parse --short HEAD) \
  -f apps/backend/Dockerfile --push .
```

### 2. Stable apply (first UAT rollout)

```bash
NS=rm-annual-summary-uat
kubectl apply -f deploy/k8s/uat/namespace.yaml
kubectl -n "$NS" apply -f deploy/k8s/uat/configmap.yaml
kubectl -n "$NS" apply -f deploy/k8s/uat/secret.yaml      # operator-prepared copy of secret.example.yaml
kubectl -n "$NS" apply -f deploy/k8s/uat/deployment.yaml
kubectl -n "$NS" apply -f deploy/k8s/uat/service.yaml
kubectl -n "$NS" apply -f deploy/k8s/uat/ingress.yaml
kubectl -n "$NS" apply -f deploy/k8s/uat/servicemonitor.yaml   # optional

kubectl -n "$NS" rollout status deploy/rm-backend --timeout=180s
git tag "deployed/uat/$(git rev-parse --short HEAD)" && git push --tags
```

Smoke check (replace `RM001` once SSO is live — `AUTH_TEST_PRINCIPAL_ENABLED`
is OFF in UAT, so this curl will return 401 until B-SSO is closed; that
is the expected behaviour, NOT a regression):

```bash
curl --fail -sS "https://$REPLACE_UAT_HOST/actuator/health/readiness" | jq
curl -sS "https://$REPLACE_UAT_HOST/api/v1/performance/annual-summary?period_type=current_year" \
  -H "Cookie: ${REAL_SSO_COOKIE_OR_BEARER}" | jq
```

### 3. Canary (10% — replicas=1 alongside stable=3)

```bash
sed -i "s/REPLACE_CANARY_TAG/${CANARY_SHA}/; s/REPLACE_CANARY_SHA/${CANARY_SHA}/" \
  deploy/k8s/uat/canary.yaml
kubectl -n "$NS" apply -f deploy/k8s/uat/canary.yaml
kubectl -n "$NS" rollout status deploy/rm-backend-canary --timeout=180s
git tag "deployed/canary/${CANARY_SHA}" && git push --tags
```

Hand off to SRE for N-hour observation. Required gates (matches OpenSpec /
4.3 metric exposure):

- `annual_summary_api_latency_seconds` P95 < 500ms, P99 < 1s
- `annual_summary_forbidden_count_total` rate within baseline ± 3σ
- `annual_summary_snapshot_lag_hours` ≤ 27 (T+1 contract)
- `http_server_requests_seconds_count{status=~"5.."}` rate < 0.1 / s

### 4. Full rollout

```bash
kubectl -n "$NS" set image deploy/rm-backend backend=ghcr.io/flycatai/rm-backend:${CANARY_SHA}
kubectl -n "$NS" rollout status deploy/rm-backend --timeout=240s
kubectl -n "$NS" delete deploy rm-backend-canary
git tag "deployed/uat-full/${CANARY_SHA}" && git push --tags
```

### 5. Rollback (SRE-triggered)

```bash
# Drain canary first if still present
kubectl -n "$NS" scale deploy/rm-backend-canary --replicas=0 || true
# Roll stable Deployment back to the previous revision
kubectl -n "$NS" rollout undo deploy/rm-backend
kubectl -n "$NS" rollout status deploy/rm-backend --timeout=240s
git tag "rollback/${CANARY_SHA}/$(date -u +%Y%m%dT%H%M%SZ)" && git push --tags
```

If the stable Deployment itself is the regression target, supply the prior
stable image tag explicitly via `kubectl set image` rather than `rollout undo`.
