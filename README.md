# OnlySay
一个把自媒体各种人的风格蒸馏成skill， 然后使用者可以发送自己的一些事情，调用这些skill，生成自媒体内容，引入智能体对生成内容进行打分， 使用者挑选好的发送到自媒体，构建影响力

---

## 🎯 当前阶段：最小 MVP RAG Demo

跑通 **"录入博主样本 → 语义检索风格 → LLM 生成同风格文案"** 的最小闭环。

> 📖 详细架构与对话流程分析见 [docs/architecture.md](docs/architecture.md)

### 技术栈

| 层 | 选型 |
|----|------|
| 后端语言 | Java 17+ |
| 后端框架 | Spring Boot 3.3.5（embedded Tomcat + Spring DI） |
| 持久层 | MyBatis Spring Boot Starter 3.0.4（@Mapper 接管辅助 SQL；向量读写仍由 PgVectorEmbeddingStore 负责） |
| 后端构建 | Maven（spring-boot-maven-plugin） |
| RAG 框架 | LangChain4j 1.19.0 |
| 向量库 | 双后端：`InMemoryEmbeddingStore`（默认，回滚用） / `PgVectorEmbeddingStore`（PostgreSQL 14 + pgvector 0.8.6，持久化），由 `onlysay.embedding.store` 配置一键切换 |
| Embedding | 本地 ONNX 模型 `bge-small-zh-v1.5`（中文优化，首次运行自动下载） |
| LLM | DeepSeek V4（`deepseek-v4-flash` 生成/分类，`deepseek-v4-pro` 兜底复核，思考模式显式关闭） |
| 配置 | `application.yml` + `@ConfigurationProperties(prefix="onlysay")`，环境变量优先 |
| 前端 | React 19 + Vite 8 + Tailwind CSS v4（`@tailwindcss/vite` 插件，CSS-first 配置） |
| 交互 | Web 界面 |

### 项目结构

```
OnlySay/
├── pom.xml                              # Maven 依赖
├── samples/
│   └── blogger.md                       # 博主风格样本（占位示例）
├── frontend/                            # React 前端项目（Console Dashboard）
│   ├── src/
│   │   ├── App.jsx                      # Shell：sidebar+topbar+4 个内容区
│   │   ├── App.css                     # 仅 keyframes（typing/dot-pulse/sparkline）
│   │   ├── index.css                   # Tailwind v4 入口 + @theme tokens
│   │   ├── api.js                      # API_BASE / fetch 封装 / sessionId
│   │   ├── hooks/
│   │   │   ├── useChat.js               # 聊天状态（迁移自原 App.jsx）
│   │   │   ├── useStats.js              # 15s 轮询 /api/intent/stats
│   │   │   └── useHealth.js             # 30s 健康探测
│   │   └── components/
│   │       ├── Sidebar.jsx              # 240px 侧边栏 + 6 nav + 录入 CTA
│   │       ├── Topbar.jsx               # 64px 顶栏 + 面包屑 + 搜索 + 健康点
│   │       ├── KpiCards.jsx             # 4 卡 grid + sparkline
│   │       ├── Sparkline.jsx            # KPI 底部迷你折线
│   │       ├── TrendChart.jsx           # 纯 SVG 7 天调用量折线
│   │       ├── IntentTable.jsx         # 意图分布表 5 行
│   │       ├── StatusDot.jsx            # 彩色圆点 + pulse 呼吸
│   │       └── ChatDebugger.jsx         # 调试会话面板（保留原 chat-bubble）
│   └── package.json
├── src/main/java/com/onlysay/
│   ├── OnlySayWebApplication.java       # Spring Boot 主入口（@SpringBootApplication + @MapperScan）
│   ├── config/
│   │   ├── OnlySayProperties.java       # @ConfigurationProperties(prefix="onlysay")
│   │   ├── ChatModelConfig.java         # 3 个 ChatModel @Bean（generate/classifier/fallback）
│   │   ├── EmbeddingStoreConfig.java    # 条件 @Bean：memory | pgvector + EmbeddingModel
│   │   └── WebConfig.java               # CORS 配置（替代 Javalin CORS）
│   ├── web/
│   │   ├── ApiController.java           # @RestController，5 个 /api/* 端点
│   │   └── GlobalExceptionHandler.java  # @RestControllerAdvice 统一异常
│   ├── mapper/
│   │   └── VectorStatsMapper.java       # @Mapper：count/truncate（接管旧 EmbeddingStoreFactory JDBC）
│   ├── service/
│   │   ├── IngestService.java           # @Service 样本录入 + 向量化
│   │   └── GenerateService.java         # @Service 检索 + LLM 生成
│   └── intent/                          # 三级漏斗意图识别模块（@Component + 构造器注入）
│       ├── IntentRecognizer.java        #   漏斗编排（规则→缓存→分类器→LLM兜底）
│       ├── IntentRegistry.java          #   意图注册表（意图/槽位/关键词）
│       ├── RuleIntentMatcher.java       #   第一级：关键词 + 正则槽位提取 + 短文本兜底
│       ├── IntentClassifier.java        #   第二级 SPI + LlmIntentClassifier（v4-flash）
│       ├── LlmFallbackRecognizer.java   #   第三级：LLM 兜底 + Few-shot JSON（v4-pro）
│       ├── IntentValidator.java         #   输出校验（注册表驱动）
│       ├── DialogueState.java           #   对话状态栈（槽位继承/意图切换）
│       ├── TextNormalizer.java          #   轻量归一化（全角/零宽/空白）
│       ├── IntentCache.java             #   精确匹配缓存（LRU）
│       ├── DailyRateLimiter.java        #   LLM 兜底每日限流
│       ├── IntentMetrics.java           #   @Component 指标聚合（命中率/兜底率/P95）
│       └── CorrectionRecorder.java      #   @Component 澄清-修正配对落盘（数据回流 JSONL）
├── src/main/resources/
│   └── application.yml                  # 配置文件（onlysay.* 前缀 + Spring DataSource + mybatis）
└── README.md
```

### 快速开始

#### 1. 配置 DeepSeek API Key

**推荐方式：环境变量**（更安全，不写入文件）

```bash
export DEEPSEEK_API_KEY=sk-your-real-api-key
```

**备选方式：编辑配置文件**

编辑 `src/main/resources/application.yml` 中 `onlysay.deepseek.api-key` 字段：

```yaml
onlysay:
  deepseek:
    api-key: sk-your-real-api-key
```

> API Key 获取地址：https://platform.deepseek.com/

#### 2. （可选）启用 pgvector 持久化后端

默认使用 `InMemoryEmbeddingStore`（进程内，重启丢数据）。如需样本跨重启保留，切换到 PostgreSQL + pgvector：

**前置：本机已安装 PostgreSQL 14 + pgvector 0.8.6**

```bash
# 创建数据库与用户（首次）
psql postgres <<'SQL'
CREATE ROLE onlysay WITH LOGIN PASSWORD 'onlysay_dev_2026' CREATEDB;
CREATE DATABASE onlysay OWNER onlysay;
\c onlysay
CREATE EXTENSION IF NOT EXISTS vector;
GRANT ALL PRIVILEGES ON DATABASE onlysay TO onlysay;
SQL
```

**切换配置**：编辑 `src/main/resources/application.yml`

```yaml
onlysay:
  embedding:
    store: pgvector    # memory | pgvector
```

切回内存模式只需改回 `onlysay.embedding.store: memory`，**一键回滚**。

#### 3. 启动后端 Web API（Spring Boot）

```bash
cd OnlySay
mvn spring-boot:run
```

首次运行会自动下载本地 Embedding 模型（ONNX，约 100MB），你会看到下载过程。
后端启动后监听 `http://localhost:8080`，提供以下 API：

| 方法 | 端点 | 说明 |
|------|------|------|
| POST | `/api/ingest` | 录入博主风格样本 |
| POST | `/api/generate` | 前置意图路由：创作/改写走 RAG 生成，澄清返回反问，其余意图返回占位 |
| POST | `/api/intent` | 纯意图识别调试（不触发下游执行），响应含 trace |
| GET | `/api/intent/stats` | 识别指标：各层命中率、兜底率、澄清率、P95/P99、per-intent 命中计数 |
| GET | `/api/health` | 健康检查 |

#### 4. 启动前端 Console Dashboard

```bash
cd OnlySay/frontend
npm install      # 首次运行需要安装依赖（含 Tailwind v4）
npm run dev
```

前端启动后访问终端显示的地址（通常是 `http://localhost:5173`，如果端口被占用会自动切换）。

**界面结构**（Sidebar + Topbar + 4 个内容区）：

| 区域 | 说明 |
|------|------|
| Sidebar 左侧栏 | 紫色「录入样本」CTA + 6 项导航 + 底部用户卡 |
| Topbar 顶栏 | 面包屑 + 搜索框 + 通知铃铛 + 后端健康状态点 |
| KPI 卡片行 | 4 张卡：意图识别总数 / P95 延迟 / 澄清触发率 / LLM 兜底率（每卡含 sparkline 迷你折线） |
| 调用量趋势图 | 近 7 日调用量折线（紫色实线「调用量」+ 灰色虚线「上周」），纯 SVG 绘制 |
| 意图分布表 | 5 行意图类别（内容创作/改写润色/热点搜索/系统控制/澄清反问），命中次数 + 占比 + 状态点呼吸动画 |
| 调试会话 | 聊天调试器：输入→意图识别→RAG 生成，回复下方可展开 trace 决策与检索样本 |

**使用流程：**
1. 点击左侧栏顶部「录入样本」按钮，加载博主风格样本（状态显示在按钮下方）
2. 在「调试会话」区底部输入框输入内容，回车或点击「发送」
3. AI 会先做意图识别：创作/改写请求检索最相似的 3 条风格样本（展示相似度）并生成同风格文案；模糊输入会收到澄清反问；热点/系统控制等意图返回"暂不支持"占位
4. 每条回复下方可展开查看意图识别决策（trace 各层耗时）与检索到的风格样本详情
5. KPI 卡片每 15s 自动刷新（也可点击顶栏健康状态点手动刷新）

#### 意图识别（三级漏斗）

`/api/generate` 在生成前先过"门神"：

```
用户输入 → 归一化 → ①规则匹配(<10ms) → ②v4-flash 分类器(~1s) → ③v4-pro LLM兜底复核
                         ↓命中              ↓≥0.85 采纳            ↓0.60-0.85 复核
                       直接路由             直接路由                校验后路由
```

- **置信度策略**：≥0.85 直接采纳；0.60-0.85 降级 pro 复核；<0.60 触发澄清
- **澄清兜底**：必需槽位缺失/全部层级失败 → 返回反问话术而非静默失败
- **槽位继承**：同 sessionId 下"写一篇…→改成小红书风格"自动继承 topic
- **成本控制**：精确匹配缓存（hitLayer=CACHE）+ LLM 兜底每日上限
- **数据回流**：澄清→修正自动配对落盘 `data/intent-corrections.jsonl`
- **一键回滚**：`onlysay.intent.enabled=false` 恢复旧版直通生成行为

```
========================================
   OnlySay - RAG 风格生成 Demo
========================================

----------------------------------------
请选择操作:
  1) 录入博主风格样本
  2) 输入你的事情，生成同风格文案
  3) 退出
请输入选项 (1/2/3): 1
```

**步骤：**
1. 先选 `1` 录入样本（将 `samples/blogger.md` 中的 10 条帖子向量化存入内存）
2. 再选 `2`，输入你想分享的事情，系统会检索最相似的 3 条风格样本，调用 DeepSeek 生成同风格文案
3. 选 `3` 退出

### 替换真实博主样本

编辑 `samples/blogger.md`，按以下格式替换为真实博主的帖子：

```markdown
# 博主风格样本

## 样本 1
第一条帖子的完整内容...

## 样本 2
第二条帖子的完整内容...

## 样本 3
...
```

建议 10-20 条代表性帖子，效果最佳。

### 已知问题与解决方案

#### macOS 上 DJL Native Library 加载失败

**现象**：运行时出现 `Failed to load Huggingface native library` 或 `Unexpected flavor: cpu` 错误。

**原因**：LangChain4j 1.19.0 依赖的 DJL 0.36.0 在部分 macOS 环境（尤其是 x86_64）上 native library 加载有兼容性问题。

**解决方案**：pom.xml 中已通过 `dependencyManagement` 强制将 DJL 降级到 0.29.0，这是社区验证可用的版本。无需额外操作，已内置在依赖配置中。

#### 首次运行需要下载模型

首次运行时，LangChain4j 会自动下载：
1. ONNX Runtime native library
2. `bge-small-zh-v1.5` 模型文件（约 100MB）到 `~/.langchain4j/`

请确保首次运行时网络畅通。

### 后续迭代方向

- [ ] 多博主支持（按博主分 collection）
- [ ] 智能体打分模块
- [ ] 持久化向量库（Chroma/Milvus）
- [ ] Web UI（已有调试台）
- [ ] RAG 重排序优化
- [ ] 意图识别：修正样本消费与 few-shot 示例库自动扩充（当前已落盘 `data/intent-corrections.jsonl`）
- [ ] 意图识别：HOT_SEARCH/SYSTEM_CONTROL 实际业务实现（当前返回占位）
