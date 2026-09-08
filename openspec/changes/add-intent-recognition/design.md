# 设计：三级漏斗意图识别（OnlySay 适配版）

## Context

现有代码（见 proposal.md - Why）：`ApiServer` 暴露 `/api/generate`，直接调 `GenerateService.generateWithDetails()`，无任何意图判断。技术栈为 Java 17 + Javalin 6 + LangChain4j 1.19 + DeepSeek（OpenAI 兼容）+ React 前端。向量库为内存实现，无会话概念。前端 `App.jsx` 直接渲染 `generatedText`。

约束：
- 单体 MVP，不引入微服务或消息队列；
- 无训练数据、无 GPU，不具备训练 DistilBERT 的条件；
- 用户偏好"配置开关而非复杂模式"，便于一键回滚。

## Goals / Non-Goals

**Goals**
- 在 Java 单体内实现漏斗编排：规则 → 分类器 SPI → LLM 兜底，每层可独立禁用；
- 输出统一结构 `IntentResult{intent, slots, confidence, hitLayer, trace}`；
- `/api/generate` 前置路由 + 新增 `/api/intent` 调试端点；
- 会话级槽位继承与意图切换（内存实现）。

**Non-Goals**
- 不训练/不部署自研分类模型（第二级直接使用云端轻量 LLM）；
- 不做自动微调与示例库自动更新（仅落盘修正记录，消费属后续 change）；
- 不做拼写纠错、口语转换等重清洗；
- 不做持久化会话存储、不接入多博主；
- 不实现 HOT_SEARCH/SYSTEM_CONTROL 等意图的实际业务执行（返回占位响应）。

## Decisions

### D1：第二级落地为 SPI + 默认 LLM 轻量实现（deepseek-v4-flash）
`IntentClassifier` 为单方法接口（`归一化后文本 → Map<intent, confidence>`）。MVP 默认实现 `LlmIntentClassifier`：调用 `deepseek-v4-flash`（显式非思考模式、temperature≈0.1、短 Prompt 约 300 token）输出意图置信度 JSON 分布，模型名可配置。
- **备选**：训练 DistilBERT——被否：无训练数据与 GPU，运维成本高；`NoopClassifier`（跳过二级）——保留为 `intent.classifier=none` 可选项，但默认 `llm`：flash 输入 $0.14/M tokens、13B 激活参数且非思考模式延迟可接受，能吸收大部分规则未命中流量，与"漏斗过滤"目标一致。
- 未来切换更快模型或接入本地小模型只改配置，不动编排逻辑。

### D2：意图注册表用静态代码定义（enum + record）
`IntentType` 枚举 + `IntentDefinition(intent, requiredSlots, optionalSlots, keywords, examples)`，以静态 Map 注册。
- **备选**：YAML 配置热加载——被否：MVP 意图集合小且稳定，静态定义有编译期检查；"新增意图不改漏斗逻辑"的要求通过"注册表驱动校验与路由"满足，而非配置化。

### D3：第三级用更强模型复核，JSON 校验手写
Prompt 为"意图清单 + 2 条 Few-shot + 当前输入 → JSON"，Jackson `readTree` 解析；校验顺序：意图合法性 → 槽位类型 → 必需槽位缺失。解析失败重试 1 次，再失败回退 CLARIFICATION。第三级默认模型 `deepseek-v4-pro`（可配置）：第二级 flash 已用便宜模型吸收流量，能走到第三级的是 0.60–0.85 的模糊输入，用强模型复核才花得值。
- **备选**：LangChain4j structured output / AiServices——被否：当前版本 JSON mode 支持依赖模型端能力不稳定，手写校验 + 重试更可控且零新依赖。
- 兜底专用短 Prompt（约 300 token），与生成调用的长 Prompt 成本拉开数量级差异。

### D4：DeepSeek 模型名迁移（附带修复）
`deepseek.model` 默认值 `deepseek-chat` 已被官方于 2026-07-24 停用，本期将默认值迁移为 `deepseek-v4-flash`；V4 系列**默认开启思考模式**，识别与生成的所有调用 MUST 显式关闭思考模式（关闭方式以官方 thinking_mode 文档为准），否则延迟与 token 成本失控。

### D5：会话状态为内存 ConcurrentHashMap
`DialogueState` 按 sessionId 保存固定深度（8 轮）的环形栈；无 sessionId 的请求视为独立会话。
- **备选**：Redis/DB 持久化——被否：与内存向量库同一取舍，MVP 重启即失效可接受。

### D6：成本控制用进程内计数器 + 缓存
每日上限：`AtomicInteger` 按自然日重置（作用于第三级 pro 复核调用）；缓存：`ConcurrentHashMap<归一化文本, IntentResult>`，上限 1000 条 LRU 淘汰。
- **备选**：向量化相似缓存（相似度>0.95 复用）——被否：需调用 embedding 模型（本地 ONNX 约 10-30ms），与"相同输入零成本"目标不符且引入复杂度；精确匹配缓存已覆盖调试场景的大部分收益。

### D7：一键回滚开关
新增配置 `intent.enabled`（默认 true）。关闭时 `/api/generate` 行为与现状完全一致（跳过识别直接生成），`/api/intent` 返回 503。出问题无需回滚代码。

### D8：置信度语义
规则命中固定 1.0；第二级 flash 分类器输出模型自报的置信度分布（受置信度策略约束，0.60–0.85 升级第三级）；第三级 pro 复核结果直接采纳，自报置信度缺失时记 0.75 并标记 `degraded=true`；缓存命中继承原结果。

### D9：回流落盘 JSONL + 进程内指标
修正记录以追加写 JSONL（`data/intent-corrections.jsonl`，启动时确保目录存在）持久化，天然可被后续脚本消费生成训练样本/扩充 few-shot 示例库；指标用进程内 `LongAdder` 计数 + 简单耗时样本集，`GET /api/intent/stats` 只读聚合，重启清零（与 D5/D6 同一内存取舍）。
- **备选**：SQLite/时序指标库——被否：MVP 数据量小，JSONL + 内存零依赖够用；自动微调/示例库自动更新——留独立 change，需样本量与评测集支撑。

## Risks / Trade-offs

- [LLM JSON 输出格式不稳定] → 手写校验 + 重试 1 次 + 非法回退 CLARIFICATION，trace 记录原始输出片段
- [V4 默认思考模式开启导致识别延迟飙升] → 所有识别/生成调用显式关闭思考模式，trace 记录各层耗时便于发现回归
- [flash 分类器对模糊输入误判且自报置信度偏高] → 0.60–0.85 区间强制 pro 复核；每日上限超限时回退 CLARIFICATION 而非硬猜
- [意图切换信号词误判（如用户文章内容里含"算了"）] → 信号词仅对 <30 字短输入生效；长输入必须依赖层级识别结果
- [内存缓存/计数器重启丢失] → 可接受：上限类数据重置无副作用，缓存冷启动只增加少量 LLM 调用
- [/api/generate 响应结构变化破坏旧前端] → 前端 `App.jsx` 同步适配；`intent.enabled=false` 提供行为回滚
- [归一化把全角数字转半角影响正文语义] → 归一化结果仅用于识别，传给 RAG 生成的仍是原始输入

## Migration Plan

1. 迁移 `deepseek.model` 默认值为 `deepseek-v4-flash` 并显式关闭思考模式（独立可验证，先做——旧模型名已停用）；
2. 新增 `com.onlysay.intent` 包全部类 + `Config` 新配置项 + `/api/intent`（纯新增，无行为变化）；
3. `/api/generate` 接入前置路由（受 `intent.enabled` 开关保护），前端 `App.jsx` 适配 `intent`/`trace`/澄清/占位四类响应；
4. 回滚策略：配置 `intent.enabled=false` 即恢复旧行为（生成链路仍走新模型名）；代码回滚仅涉及 `ApiServer.generate()` 一处调用点。

## Open Questions

无（第二级模型选型已定为 `deepseek-v4-flash`；若后续有训练数据与 GPU，可训练小模型作为新的 `IntentClassifier` 实现替换，属独立 change）。
