## Why

OnlySay 当前 `/api/generate` 把所有用户输入一律当作"生成同风格文案"处理：用户输入"今天有什么热点"也会触发 RAG 检索 + DeepSeek 生成，产出驴唇不对马嘴的结果，且每次请求都消耗 LLM 成本。在引入多意图（创作/改写/热点/系统控制）之前，需要一个"门神"模块在请求进入业务逻辑前完成意图分类与槽位提取。

## What Changes

- 新增三级漏斗意图识别模块（FunnelIntentRecognizer）：
  - 第一级：规则匹配（关键词表 + 正则槽位提取 + 短文本兜底），纯 Java，<10ms；
  - 第二级：轻量 LLM 分类器（`IntentClassifier` SPI + 默认实现 `LlmIntentClassifier`，调用 `deepseek-v4-flash` 非思考模式），配置开关 `intent.classifier=llm|none`，置信度策略照常生效；
  - 第三级：LLM 兜底（复用 DeepSeek，Few-shot + JSON 输出 + 输出校验）。
- 新增意图体系：意图注册表（intent registry）、必需/可选槽位定义、置信度分级策略（≥0.85 直接采纳 / 0.60-0.85 降级复核 / <0.60 澄清）。
- 新增对话状态栈（DialogueState）：槽位跨轮继承 + 意图切换检测（"算了/还是/换"等信号词）。
- 新增文本归一化：全角转半角、去零宽字符、空白合并（仅此三项）。
- 新增 REST 端点 `POST /api/intent`：独立调试意图识别结果（意图 + 槽位 + 命中层级 + 耗时）。
- 修改 `/api/generate`：前置意图路由，仅 `CONTENT_GENERATION`/`REWRITE` 走现有 RAG 链路；`CLARIFICATION` 返回反问话术；其他已识别意图返回"暂不支持"占位响应。
- 可观测：每层命中情况、置信度、耗时计入响应 `trace` 字段并打印日志。
- 数据回流与指标：澄清-修正自动配对落盘 JSONL（供后续训练样本/示例库使用，本 change 不做自动微调）；进程内指标聚合（各层命中率、LLM 兜底率、澄清触发率、耗时 P95/P99），新增 `GET /api/intent/stats` 查询。

## Capabilities

### New Capabilities
- `intent-recognition`: 三级漏斗意图识别的核心行为——归一化、规则匹配、分类模型 SPI 与置信度策略、LLM 兜底与输出校验、槽位继承与意图切换、澄清兜底、成本控制与可观测性。

### Modified Capabilities

（项目尚无既有 spec，无既有能力的需求变更。`/api/generate` 前置路由作为 `intent-recognition` 能力的路由需求一并描述。）

## Impact

- **代码**：新增 `IntentRecognizer`（漏斗编排）、`IntentRegistry`（意图/槽位注册表）、`DialogueState`（状态栈）、`NormalizedText`（归一化）、`IntentClassifier`（SPI 接口）、修正记录落盘与指标聚合组件于 `com.onlysay.intent` 包；修改 `ApiServer.java`（新端点 + generate 前置路由）、`Config.java`（置信度阈值等配置）。
- **API**：新增 `POST /api/intent` 与 `GET /api/intent/stats`；`POST /api/generate` 响应新增 `intent`/`trace` 字段，非创作意图不再返回 generatedText（对旧前端是行为变化，前端 `App.jsx` 需同步适配）。
- **依赖**：无新增第三方依赖；LLM 兜底复用现有 DeepSeek `ChatModel`。附带迁移：`deepseek.model` 默认值从已停用的 `deepseek-chat` 改为 `deepseek-v4-flash`，且识别/生成调用显式关闭 V4 思考模式。
- **成本**：非创作意图不再消耗 RAG 检索与长 Prompt 生成调用；LLM 兜底层新增少量识别调用（短 Prompt，成本远低于生成调用）。
