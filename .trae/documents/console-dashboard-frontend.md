# 前端改造：OnlySay Console Dashboard

## Context

当前前端是单页 chat-bubble 调试器（React 19 + Vite，无 Tailwind，纯 App.css 自写 270 行 CSS）。用户给的「AI 控制台总览」设计模板要求：固定 240px sidebar + 64px topbar + 滚动主区 + 5 个内容区域，浅色白底紫强调配色，KPI + 趋势图 + 状态表 + 会话列表。

按用户决策：5 个区域按 OnlySay 真实功能适配（不 mock 平台数据），真集成 Tailwind v4，录入按钮放 sidebar 顶部 CTA。

**关键发现**：IntentMetrics.snapshot() 当前不输出 per-intent 命中次数，需扩展后端才能支撑「意图分布表」。这是唯一后端改动。

## 改动清单

### 后端：1 个文件微调（IntentMetrics.java）

[IntentMetrics.java](file:///Users/rocky/study/profile/Markdown-Resume/repo/OnlySay/src/main/java/com/onlysay/intent/IntentMetrics.java) 加 `intentCounts`：

- 新增字段 `Map<IntentType, LongAdder> intentCounts = new EnumMap<>(IntentType.class)`，构造器初始化所有 IntentType
- `record()` 中：`intentCounts.get(result.getIntent()).increment()`
- `snapshot()` 末尾加：`snapshot.put("intentCounts", intentTypeHits)`（Map<String,Long>）
- 顺带补 `p95LatencyMs` 别名指向 `p95Ms`，匹配 spec 命名

这是约 10 行改动，无侵入。无既有 IntentMetricsTest 覆盖，新增 smoke test 即可。

### 前端：文件结构

```
frontend/
├── index.html               # 改：+Google Fonts + Font Awesome CDN + title
├── vite.config.js           # 改：加 @tailwindcss/vite 插件
├── package.json              # 改：+tailwindcss +@tailwindcss/vite
└── src/
    ├── main.jsx             # 不动（保留 import './index.css'）
    ├── index.css            # 改：Tailwind 入口 + @theme tokens（替换原 10 行）
    ├── App.css              # 缩减到 ~30 行：仅保留 typing-bounce/dot-pulse/sparkline-draw 3 个 keyframes
    ├── App.jsx              # 重写：Shell + state wiring
    ├── api.js               # 新：API_BASE / SESSION_KEY / getSessionId / fetch 封装
    ├── hooks/
    │   ├── useStats.js      # 新：轮询 /api/intent/stats（15s + visibilitychange）
    │   ├── useHealth.js     # 新：30s 健康探测 /api/health
    │   └── useChat.js       # 新：复用当前 App.jsx 的 handleSend/handleIngest/messages
    └── components/
        ├── Sidebar.jsx      # <aside> 240px / 56px 响应式
        ├── Topbar.jsx       # <header> 64px 面包屑+搜索+铃铛+头像
        ├── KpiCards.jsx     # 4 卡 grid（调用数/P95延迟/澄清率/LLM兜底率）+ sparkline SVG
        ├── TrendChart.jsx   # 纯 SVG 7 天折线 + 浅紫填充 + 灰网格
        ├── IntentTable.jsx  # 5 行意图分布表 + 状态点 pulse 动画
        ├── ChatDebugger.jsx # 保留当前 chat-bubble UI（重写样式）
        ├── Sparkline.jsx    # KPI 底部迷你折线生成器
        └── StatusDot.jsx    # 彩色圆点 + pulse 呼吸
```

### Tailwind v4 集成步骤

1. `cd frontend && npm install tailwindcss @tailwindcss/vite`
2. `vite.config.js` 加 `import tailwindcss from '@tailwindcss/vite'` 和 `plugins: [react(), tailwindcss()]`
3. `index.css` 顶部 `@import "tailwindcss";` + `@theme { --color-accent: #7C3AED; ... }` 定义设计 tokens
4. 无需 `tailwind.config.js`、无需 `postcss.config.js`（v4 CSS-first）

### index.html 改动

`<head>` 内加：
- Google Fonts preconnect + Inter(400-800) + JetBrains Mono(400-600)
- Font Awesome 6.5.2 CDN
- `<title>OnlySay Console</title>`

### 视觉规范（嵌入 @theme tokens）

| Token | 值 | 用途 |
|-------|----|----|
| `--color-bg` | `#F8FAFC` | 主区背景 |
| `--color-panel` | `#FFFFFF` | 卡片/sidebar/topbar 底色 |
| `--color-accent` | `#7C3AED` | 主强调色 |
| `--color-accent-soft` | `#F5F3FF` | hover/active 浅紫底 |
| `--color-border` | `#E2E8F0` | 1px 边框 |
| `--color-success` | `#10B981` | 运行中状态点 |
| `--color-warning` | `#F59E0B` | 暂停状态 |
| `--color-danger` | `#EF4444` | 异常状态 |
| `--font-sans` | `'Inter'` | UI 字体 |
| `--font-mono` | `'JetBrains Mono'` | KPI 大数字/数据列 |

### 布局（硬约束三段式）

```jsx
<div className="flex h-screen bg-[#F8FAFC]">
  <Sidebar/>                    {/* 240px fixed, 56px on mobile */}
  <div className="flex-1 flex flex-col">
    <Topbar/>                   {/* 64px sticky */}
    <main className="flex-1 overflow-y-auto p-6 space-y-5">
      <KpiCards/>
      <TrendChart/>
      <IntentTable/>
      <ChatDebugger/>
    </main>
  </div>
</div>
```

### 5 个区域内容映射

| 区域 | 模板原内容 | OnlySay 适配 |
|------|----------|------------|
| Sidebar | NEXUS Console logo + 6 nav | OnlySay + Console，nav: 概览/意图识别/样本库/会话/指标/设置（点击只切 active 状态，主区不变） |
| Sidebar 顶部 CTA | 无 | 「录入样本」紫色按钮 + 状态行（✅ 录入完成，共 N 条样本） |
| Topbar | 面包屑+搜索+铃铛+头像 | 完全照模板，铃铛 badge 显示「3」(静态)，搜索框 placeholder「搜索意图、样本…」 |
| KPI 4 卡 | 智能体总数/今日调用/平均延迟/成功率 | 意图识别总数(totalRequests) / P95延迟(p95Ms) / 澄清率(clarificationRate×100%) / LLM兜底率(llmFallbackRate×100%) |
| 趋势图 | 近7日调用量 | 标题照搬，mock 7 天数据（以当前 totalRequests 为最新值，前6天按 0.65-0.95 衰减） |
| 意图分布表 | 智能体状态表 | 5 行：CONTENT_GENERATION / REWRITE / HOT_SEARCH / SYSTEM_CONTROL / CLARIFICATION，列：意图名/命中次数/占比/最近活动(mock 相对时间) |
| 聊天调试 | 最近会话列表 | 保留当前 chat-bubble UI，重写样式：用户气泡紫底白字，AI 气泡白底带边框，trace `<details>`+检索样本卡保留 |

### 状态管理策略

| 关注点 | 策略 |
|--------|------|
| 拉 stats | `useStats` hook：mount 拉 + `setInterval(15s)` + `visibilitychange` 可见时刷新 |
| stats 为空（total=0） | KPI 显示 `0` + 「暂无数据」副标；意图表行命中=0/占比=0%/最近活动=「—」 |
| 健康探测 | `useHealth` 30s 探 `/api/health`；失败时顶部 banner「后端未连接」+ textarea placeholder→「等待后端启动」 |
| 7 天趋势数据 | 模块级 `let cachedTrend` 缓存，避免重渲染时重新生成；seed 用 `useRef` 锁定 |
| 聊天状态 | `useChat` 完整迁移当前 App.jsx 的 `handleSend`/`handleIngest`/`messages`/`input`/`loading`/`ingestStatus` |
| nav active | `useState('overview')`，nav 点击只切状态（不引入路由库，所有区域都在 main 区） |

### 复用现有逻辑

- `getSessionId()`/`SESSION_KEY`/`API_BASE` 移到 `api.js`
- `handleSend` / `handleIngest` / `messages` state 全部移到 `hooks/useChat.js`，**逻辑不变**
- `<details>` trace 折叠结构、retrievedSamples 卡片结构、typing 指示器原样搬到 `ChatDebugger.jsx`，仅替换 className
- 后端响应契约（clarification/success+generatedText/intent≠CONTENT_GENERATION）三个分支处理保留

### 响应式（硬约束 #8）

- `<768px`：sidebar `w-14`（仅图标，隐藏文字与用户卡详情），KPI 1 列，搜索框隐藏
- `≥768px`：sidebar `w-60`（240px），KPI 2 列
- `≥1024px`：KPI 4 列
- 语义标签：`<aside>`/`<header>`/`<nav>`/`<main>`/`<section>`

### 微交互（硬约束 #5）

- 表格行 hover：`hover:bg-[#F5F3FF] transition-colors duration-180`
- nav 激活项：`bg-[#7C3AED] text-white` + 左 3px 高亮条
- 状态点呼吸：`@keyframes dot-pulse { 0%{box-shadow:0 0 0 0 rgba(16,185,129,.5)} 70%{box-shadow:0 0 0 6px rgba(16,185,129,0)} 100%{box-shadow:0 0 0 0 rgba(16,185,129,0)} }`
- KPI 卡 hover：边框 `#E2E8F0`→`#7C3AED` `transition-colors duration-200`
- sparkline 描边动画：`stroke-dasharray:1000; stroke-dashoffset:1000 → 0; animation:1.2s ease-out`

## 验证步骤

1. `cd frontend && npm install` 成功，`npm run dev` 启动无 Tailwind 编译错误
2. 不启动后端访问页面：KPI 显示「暂无数据」banner，textarea placeholder 显示「等待后端启动」，无 fetch 异常
3. 启动后端：`DEEPSEEK_API_KEY=... mvn exec:java`
4. 点击 sidebar「录入样本」按钮 → 状态显示「✅ 录入完成，共 10 条样本」
5. 输入「今天去爬山了，风景很美」回车 → AI 气泡 + 展开的 trace + 3 个检索样本卡（同当前体验）
6. 等 15s 或点「刷新」→ KPI 卡显示 totalRequests、p95Ms 等真实数据，意图分布表 5 行填充
7. 浏览器宽度拖到 <768px → sidebar 缩到 56px 图标条，KPI 变 1 列，搜索框消失
8. `npm run build` 成功无警告

## 风险

- **IntentMetrics 扩展是前置依赖**：意图分布表必须有 intentCounts 字段，否则 5 行都显示 0
- **Tailwind v4 vs v3**：v4 是 2024 年新版，CSS-first 配置，需用 `@tailwindcss/vite` 插件而非传统 PostCSS
- **Font Awesome CDN 依赖**：离线环境会丢图标，可后续换本地化方案
- **趋势图是 mock 数据**：因后端 stats 仅进程内累计，无历史时序。如果将来要真实趋势，需后端记录时序数据（独立 change）
