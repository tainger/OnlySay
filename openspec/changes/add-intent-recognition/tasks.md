## 1. 基础设施：模型迁移与配置

- [x] 1.1 DeepSeek 模型名迁移：`deepseek.model` 默认值 `deepseek-chat`（已于 2026-07-24 停用）改为 `deepseek-v4-flash`，并确认 V4 思考模式的显式关闭方式（官方 thinking_mode 文档），识别与生成调用全部默认关闭思考模式；验证：`mvn compile exec:java` 启动后 `POST /api/generate` 正常返回文案，无 400/模型不存在错误
- [x] 1.2 `Config.java` 新增配置项 `intent.enabled`（默认 true）、`intent.classifier`（`llm|none`，默认 llm）、`intent.classifier.model`（默认 deepseek-v4-flash）、`intent.llm.model`（默认 deepseek-v4-pro）、`intent.llm.daily-limit`（默认 5000）、`intent.cache.size`（默认 1000）；验证：`mvn compile` 通过且默认值在无配置文件时生效
- [x] 1.3 新增 `com.onlysay.intent` 包与核心数据模型：`IntentType`（枚举：CONTENT_GENERATION/REWRITE/HOT_SEARCH/SYSTEM_CONTROL/CLARIFICATION）、`IntentDefinition`（required/optional 槽位、关键词、示例）、`IntentResult`（intent/slots/confidence/hitLayer/degraded）、`IntentTrace`（归一化结果、各层命中与耗时）；验证：`mvn compile` 通过
- [x] 1.4 实现文本归一化工具（全角转半角、去零宽/控制字符、空白合并），确认不修改传给下游的原始输入；验证：单元测试覆盖规格中"全角标点输入""零宽字符"两个 Scenario

## 2. 意图注册表与规则层

- [x] 2.1 实现 `IntentRegistry`：静态注册 5 个意图的必需/可选槽位与关键词表，提供按意图查询定义、关键词反向匹配方法；验证：单元测试断言 CONTENT_GENERATION 必需槽位为 topic、REWRITE 必需槽位为 source_text
- [x] 2.2 实现规则匹配层：关键词表匹配（命中即 confidence=1.0）、正则槽位提取模板（长度/风格/平台/话题）、短文本兜底（<10 字且无创作动词 → CLARIFICATION）；验证：单元测试覆盖规格中"关键词命中""正则提取槽位""短文本触发澄清"三个 Scenario
- [x] 2.3 实现意图切换信号词检测（"算了/还是/换/不是"，仅对 <30 字短输入生效）；验证：单元测试覆盖"意图切换清空状态"Scenario

## 3. 第二级 LLM 轻量分类器（deepseek-v4-flash）

- [x] 3.1 实现 `IntentClassifier` SPI 接口与 `LlmIntentClassifier` 默认实现：调用 `intent.classifier.model`（非思考模式、temperature≈0.1、短 Prompt）输出各意图置信度 JSON 分布（含主/副意图多标签）；验证：单元测试用模拟 JSON 断言多标签解析正确
- [x] 3.2 实现分类器容错：调用超时/返回不可解析时返回空结果并由编排层降级第三级，trace 标注 failed；`intent.classifier=none` 时跳过该层；验证：单元测试覆盖"分类器调用失败降级第三级"Scenario
- [x] 3.3 接入真实 DeepSeek flash 调用联调；验证：启动后端 `POST /api/intent` 输入"写一篇关于年轻人加班的文章"（不命中关键词的变体表述）返回 CONTENT_GENERATION 且 hitLayer=CLASSIFIER

## 4. 第三级 LLM 兜底（deepseek-v4-pro 复核）

- [x] 4.1 实现兜底 Prompt 构建（意图清单 + 2 条 Few-shot + 当前输入）与 JSON 解析（Jackson readTree，失败重试 1 次），模型用 `intent.llm.model`；验证：用模拟返回的 JSON 字符串跑通解析，非法 JSON 重试后回退
- [x] 4.2 实现输出校验器：意图合法性 → 槽位类型 → 必需槽位缺失触发澄清，全部基于 `IntentRegistry` 定义；验证：单元测试覆盖"LLM 输出非法意图""必需槽位缺失"两个 Scenario
- [x] 4.3 接入 DeepSeek pro 调用联调；验证：启动后端，`POST /api/intent` 输入"今天微博有什么大瓜"返回 HOT_SEARCH 且 hitLayer=LLM

## 5. 成本控制与缓存

- [x] 5.1 实现精确匹配缓存（归一化文本为 key，LRU 上限 1000，命中时 hitLayer 标注 CACHE 并继承原结果）；验证：同一输入连续两次识别，第二次日志无 LLM 调用且 hitLayer=CACHE
- [x] 5.2 实现每日调用计数器（AtomicInteger 按自然日重置，作用于第三级复核调用），超限直接返回 CLARIFICATION 并记录限流日志；验证：将 `intent.llm.daily-limit` 临时设为 1，第二次低置信请求返回 CLARIFICATION

## 6. 会话状态与槽位继承

- [x] 6.1 实现 `DialogueState`：sessionId → 环形栈（深度 8），提供 push/inheritSlots/clearForSwitch；无 sessionId 视为独立会话；验证：单元测试覆盖"跨轮槽位继承"Scenario
- [x] 6.2 将切换检测与继承接入漏斗编排：切换信号 + 新意图时清空旧状态，否则继承缺失槽位；验证：`POST /api/intent` 带 sessionId 顺序发"写一篇年轻人加班的文章"→"改成小红书风格"，第二轮结果含继承的 topic

## 7. 漏斗编排

- [x] 7.1 实现 `IntentRecognizer` 漏斗编排：归一化 → 规则 → 分类器（可跳过）→ 缓存/限流判断 → LLM 兜底 → 校验 → 继承合并，产出 `IntentResult` + `IntentTrace`；验证：单元测试按置信度策略表驱动多条路径（规则命中/分类器高置信采纳/中置信 pro 复核/全部失败回退 CLARIFICATION）

## 8. API 集成与路由

- [x] 8.1 `ApiServer` 新增 `POST /api/intent`（请求体 userInput + 可选 sessionId，返回意图/槽位/置信度/hitLayer/trace，不触发下游执行）；验证：curl 调用后向量库 size 不变、无生成调用日志
- [x] 8.2 `/api/generate` 接入前置路由：CONTENT_GENERATION/REWRITE 走现有 RAG，CLARIFICATION 返回反问话术，其余意图返回 success=false 的"暂不支持"占位；响应新增 `intent`/`trace` 字段；受 `intent.enabled` 开关保护，关闭时行为与现状一致；验证：curl 分别验证创作意图走 RAG、热点意图返回占位、开关关闭后回到旧行为
- [x] 8.3 trace 打印服务端日志（一层一行，含耗时）；验证：`mvn compile exec:java` 启动后发送请求，终端可见各层决策日志

## 9. 数据回流与指标

- [x] 9.1 实现修正事件配对与 JSONL 落盘：CLARIFICATION 轮 + 同会话下一轮非 CLARIFICATION 结果自动配对，追加写 `data/intent-corrections.jsonl`（澄清输入/澄清结果/修正输入/最终意图/hitLayer/时间戳），启动时确保目录存在，不做自动微调；验证：单元测试覆盖"澄清后修正自动配对""非澄清轮不产生记录"两个 Scenario，进程重启后文件保留
- [x] 9.2 实现进程内指标聚合：各层命中计数（RULE/CLASSIFIER/LLM/CACHE/FALLBACK）、LLM 兜底率、澄清触发率、识别耗时 P95/P99；验证：单元测试灌入模拟请求后断言计数与比率正确
- [x] 9.3 `ApiServer` 新增 `GET /api/intent/stats` 返回聚合指标；验证：curl 调用返回各层命中计数、兜底率、澄清触发率与耗时分位数

## 10. 前端适配

- [x] 10.1 `App.jsx` 适配四类响应：正常生成（含 trace 折叠展示）、澄清反问（渲染为 AI 消息）、占位不支持（提示条）、`intent.enabled=false` 旧行为；验证：`npm run dev` 后手动走一遍四类场景
- [x] 10.2 会话支持：前端生成/复用 sessionId 随 `/api/generate` 与 `/api/intent` 请求发送，验证"写一篇…→改成小红书风格"跨轮继承生效

## 11. 集成验证

- [x] 11.1 全链路手工回归：录入样本 → 4 类输入（创作/改写/热点/短文本）分别验证路由正确性；验证：每类输入的响应字段与 spec Scenario 一致
- [x] 11.2 `openspec validate add-intent-recognition --strict` 通过；更新 README 的 API 表与后端迭代方向清单
