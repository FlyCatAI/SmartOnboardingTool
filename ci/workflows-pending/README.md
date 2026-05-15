# Pending CI workflows

由 agent 准备好但**未直接放到 `.github/workflows/`** 的 GitHub Actions 配置。原因：agent 的 GitHub Token 没有 `workflow` scope，无法直接推工作流文件。

请仓库管理员（具有 `workflow` 权限的人）手工执行：

```
mv ci/workflows-pending/*.yml .github/workflows/
git rm -rf ci/workflows-pending
git commit -m "ci: enable backend / miniprogram / openspec workflows"
```

三份工作流的设计意图见每个文件顶部注释。
