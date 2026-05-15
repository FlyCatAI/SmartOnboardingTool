# RM 展业小程序 — 前端脚手架

对应 OpenSpec change `rm-mvp-prd` 的任务组 5（小程序前端开发）。

## 状态

**这是脚手架，不是可上线代码。** 本目录交付：

- 工程结构（pages / components / services / store / utils / styles / config）
- 设计 token（颜色 / 字号 / 间距）— 行内 UI 规范到位前为占位值
- 基础请求层与全局错误处理 / 未授权拦截占位
- 路由与登录拦截骨架（不接 SSO，仅校验本地 session token 是否存在）
- 任务七态在前端的展示常量（与后端 `TaskStatus` 一致）

## 技术栈候选

候选项之一：**uni-app + Vue 3 + TypeScript**。理由：
- 行内多数小程序团队的熟悉度较高
- 一套代码可同时跑微信小程序 / H5（OQ 3.7 决定入口归属时不至于推翻工程结构）
- TypeScript 静态类型，对接后端 DTO 减少返工

最终由开发团队评审决定（design.md Decision 7）。若选原生小程序 / Taro，本目录结构按等价规则迁移即可（`pages/` 与 `components/` 概念通用）。

## 目录

```
src/
├── config/           # 环境变量与开关
├── pages/            # 页面（按 spec 五大模块分目录）
│   ├── login/
│   ├── workstation/
│   ├── merchant/
│   ├── task/
│   ├── notification/
│   └── settings/
├── components/       # 通用组件（行内设计语言基础组件接入处）
├── services/         # 后端 API 客户端
├── store/            # 全局状态（pinia 候选）
├── styles/           # 设计 token + 全局样式
└── utils/            # 工具函数（请求、时间、脱敏、剩余时间标签等）
```

## 如何启动

待技术栈评审通过、补 `package.json` 与 `vite.config.ts` / `manifest.json` 后由开发团队补充。
