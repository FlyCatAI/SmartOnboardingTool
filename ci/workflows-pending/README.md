# Pending CI workflows

GitHub Actions YAML produced by agents but **not** placed directly into
`.github/workflows/` because the agent's GitHub Token lacks the `workflow`
OAuth scope (push is rejected by the API).

A repository admin with `workflow` permission must run, once per branch:

```bash
mkdir -p .github/workflows
git mv ci/workflows-pending/*.yml .github/workflows/
git rm ci/workflows-pending/README.md
git commit -m "ci: enable backend / miniprogram / openspec / e2e workflows"
```

## Files

| File | Triggers | Purpose |
|---|---|---|
| `backend-ci.yml` | push/PR on `apps/backend/**`, `deploy/**` | mvn test → package → docker buildx + GHCR push (sha + branch tag) |
| `miniprogram-ci.yml` | push/PR on `apps/miniprogram/**` | guards → vitest → typecheck → npm audit → H5 build → WeChat mini-program build (artifacts) |
| `openspec-validate.yml` | push/PR on `openspec/**` | `npx openspec validate` for every change folder |
| `e2e.yml` | push/PR on backend/miniprogram/deploy/migrations + manual dispatch | `docker compose -f deploy/docker-compose.test.yml up -d` → wait for `/actuator/health/readiness` → asserts Prometheus exposition includes `annual_summary` metric family |

## Why not push directly?

Earlier agents in this issue hit the same limitation
(see comment `1c5572ab` and the previous round in
`ci/workflows-pending/README.md` history). Until a human with `workflow`
scope runs the `git mv`, these files are **dormant** — they do not run.

The `backend-ci.yml` and `e2e.yml` workflows assume `secrets.GITHUB_TOKEN`
has `packages: write` so the docker buildx job can push to GHCR. That is
the default for `GITHUB_TOKEN` once enabled in repo settings → Actions →
General → Workflow permissions: "Read and write permissions".
