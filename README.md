# SmartOnboardingTool

联合收单场景下，**客户经理展业小程序**的产品需求与规范驱动开发仓库。

本仓库以 [OpenSpec](https://github.com/openspec-dev/openspec) 作为规范驱动框架，沉淀产品能力（capability）与变更提案（change proposal）。

## 目录结构

```
openspec/
├── changes/            # 变更提案（proposal / spec_delta / design / tasks）
│   └── rm-mvp-prd/     # 客户经理展业小程序 MVP 需求提案（审查中）
└── specs/              # 已归档的稳定能力规格（capability）
```

## 当前提案

- **rm-mvp-prd** — 客户经理展业小程序 MVP 需求文档
  - 覆盖 5 个 capability：工作台、商户管理、任务管理、认证与身份、消息通知
  - 包含 25 条 Requirement，全部带 Given/When/Then 验收场景
  - 详见 [`openspec/changes/rm-mvp-prd/proposal.md`](openspec/changes/rm-mvp-prd/proposal.md)

## 工作流

1. **新建提案**：`openspec new change <slug>`
2. **撰写 proposal.md / spec_delta**：补充 Why / What / Capabilities / Impact
3. **本地校验**：`openspec validate <slug>`
4. **提交评审**：推送 `change/<slug>` 分支并发起 PR
5. **归档**：评审通过后将 delta 合入 `openspec/specs/`

## 当前状态

提案 `rm-mvp-prd` 已通过本地校验（`25 deltas, 0 issues`），进入**审查状态**，等待评审后再进入 design / tasks 阶段。
