## Purpose

作为 OnlySay Agent 系统的"门神"，在用户请求进入业务逻辑前完成意图分类与槽位提取：以三级漏斗（规则匹配 → 轻量分类 → LLM 兜底）在准确率、延迟与成本之间取得平衡，并为下游路由输出统一的结构化结果（意图 + 槽位 + 置信度 + 命中层级）。

## ADDED Requirements

### Requirement: 文本归一化
意图识别入口 SHALL 先对用户输入执行轻量归一化，且仅包含以下三项：全角标点/字母/数字转半角、去除零宽字符与控制字符、连续空白符合并为单个空格。系统 MUST NOT 在归一化阶段做拼写纠错、口语转换或重复字压缩。

#### Scenario: 全角标点输入
- **WHEN** 用户输入"写一篇关于ＡＩ的文章！"
- **THEN** 归一化结果为"写一篇关于AI的文章!"，后续层级基于该结果处理

#### Scenario: 含零宽字符的输入
- **WHEN** 用户输入中夹带零宽空格（U+200B）或控制字符
- **THEN** 归一化阶段将其移除，不进入意图匹配逻辑

### Requirement: 第一级规则匹配
系统 SHALL 在归一化后的输入上首先执行规则匹配，包含：意图关键词表匹配、结构化正则模板（提取长度/风格/平台等槽位）、短文本兜底（归一化后长度小于 10 字且不含明确创作动词时归类为 CLARIFICATION）。规则匹配 MUST NOT 调用任何外部模型或网络服务。

#### Scenario: 关键词命中创作意图
- **WHEN** 用户输入"帮我写一篇关于年轻人加班的文章"
- **THEN** 识别为 CONTENT_GENERATION，confidence 为 1.0，hitLayer 为 RULE，且不调用 LLM

#### Scenario: 正则提取槽位
- **WHEN** 用户输入"写800字幽默风格的加班文章"
- **THEN** 识别为 CONTENT_GENERATION，槽位提取出 length=800、style=幽默、topic 含"加班"

#### Scenario: 短文本触发澄清
- **WHEN** 用户输入"加班"（<10 字且无创作动词）
- **THEN** 识别为 CLARIFICATION，hitLayer 为 RULE

### Requirement: 第二级分类模型与置信度策略
系统 SHALL 定义轻量分类模型 SPI（输入归一化文本，输出各意图置信度分布），并 SHALL 提供基于轻量 LLM（deepseek-v4-flash 或同级快速模型，显式非思考模式）的默认实现，通过配置可整体关闭。分类器关闭或调用失败时，规则未命中的输入 SHALL 直接进入第三级。当分类器启用时：最高置信度 ≥ 0.85 SHALL 直接采纳；0.60–0.85 SHALL 降级到第三级复核；< 0.60 SHALL 进入第三级或触发澄清。系统 SHALL 支持按阈值输出主意图与副意图（多标签），主意图进入当前流水线，副意图仅记录于结果供上层并行调度。

#### Scenario: 分类器高置信度直接采纳
- **WHEN** 分类器启用且输出 CONTENT_GENERATION=0.92、DATA_QUERY=0.78
- **THEN** 主意图为 CONTENT_GENERATION，副意图为 DATA_QUERY，不调用 LLM

#### Scenario: 分类器中置信度降级复核
- **WHEN** 分类器启用且最高置信度为 0.72
- **THEN** 该输入降级到 LLM 兜底复核，最终结果标注 hitLayer 为 LLM

#### Scenario: 分类器调用失败降级第三级
- **WHEN** 分类器启用但 LLM 调用超时或返回不可解析内容
- **THEN** 该输入进入第三级复核，trace 中分类器层标注 failed

### Requirement: 第三级 LLM 兜底与输出校验
当规则未命中且分类器不可用或置信度不足时，系统 SHALL 调用 LLM 兜底识别：Prompt 包含意图清单与至少 2 条 Few-shot 示例，并要求 JSON 输出。LLM 返回结果 MUST 经过校验：意图不在合法清单内 SHALL 回退为 CLARIFICATION；槽位类型非法 SHALL 置空该槽位；必需槽位缺失 SHALL 触发澄清反问而非直接执行。

#### Scenario: LLM 正常识别
- **WHEN** 用户输入"今天微博有什么大瓜"且规则未命中
- **THEN** LLM 兜底识别为 HOT_SEARCH，槽位含 platform=微博，hitLayer 为 LLM

#### Scenario: LLM 输出非法意图
- **WHEN** LLM 返回的 intent 不在意图清单内
- **THEN** 系统忽略该输出，回退为 CLARIFICATION 并附反问话术

#### Scenario: 必需槽位缺失
- **WHEN** LLM 识别为 CONTENT_GENERATION 但 topic 槽位为空
- **THEN** 系统返回 CLARIFICATION，反问内容包含对 topic 的追问

### Requirement: 意图注册表与槽位绑定
系统 SHALL 维护意图注册表，每个意图 MUST 定义：意图标识、必需槽位清单、可选槽位清单、示例。MVP 意图集合为 CONTENT_GENERATION、REWRITE、HOT_SEARCH、SYSTEM_CONTROL、CLARIFICATION，注册表 SHALL 支持新增意图而无需修改漏斗编排逻辑。

#### Scenario: 注册表驱动校验
- **WHEN** 识别结果为 REWRITE 且其必需槽位在注册表中定义为 source_text
- **THEN** 校验逻辑按注册表定义检查 source_text，而非硬编码规则

### Requirement: 澄清兜底
当所有层级均无法给出可信意图，或必需槽位缺失时，系统 SHALL 返回 CLARIFICATION 意图，并 SHALL 提供面向用户的反问话术（基于当前候选意图或缺失槽位生成），而非静默失败。

#### Scenario: 全部层级未命中
- **WHEN** 规则未命中、分类器关闭、LLM 返回非法 JSON 且重试一次仍失败
- **THEN** 最终结果为 CLARIFICATION，包含反问话术，hitLayer 为 FALLBACK

### Requirement: 槽位继承与意图切换
系统 SHALL 维护按会话隔离的对话状态栈，保存每轮意图与槽位。当前轮意图缺失的可选槽位 SHALL 从上一轮同类型槽位继承；检测到意图切换信号词（如"算了""还是""换""不是"）且出现新意图时，系统 MUST 清空旧状态并切换到新意图，不继承与新意图无关的槽位。

#### Scenario: 跨轮槽位继承
- **WHEN** 上一轮识别为 CONTENT_GENERATION（topic=年轻人加班），当前轮输入"改成小红书风格"
- **THEN** 当前轮识别为 REWRITE，topic 从上一轮继承，style=小红书

#### Scenario: 意图切换清空状态
- **WHEN** 上一轮为 CONTENT_GENERATION，当前轮输入"算了，看看今天热点吧"
- **THEN** 识别为 HOT_SEARCH，旧创作槽位被清空不继承

### Requirement: 生成链路前置路由
`/api/generate` SHALL 在执行 RAG 生成前先做意图识别：CONTENT_GENERATION 与 REWRITE 走现有 RAG 链路；CLARIFICATION 返回反问话术；其余意图 SHALL 返回"暂不支持"占位响应并附识别到的意图与槽位。响应 MUST 包含 `intent` 与 `trace` 字段。

#### Scenario: 创作意图走 RAG 链路
- **WHEN** 用户输入"写一篇关于爬山的朋友圈文案"且样本已录入
- **THEN** 请求进入 RAG 生成，响应含 generatedText、intent=CONTENT_GENERATION、trace

#### Scenario: 非创作意图返回占位
- **WHEN** 用户输入"今天微博有什么大瓜"
- **THEN** 不调用 RAG 生成，响应 intent=HOT_SEARCH、success=false、message 说明该意图暂不支持

#### Scenario: 未录入样本时创作意图
- **WHEN** 向量库为空且输入为创作意图
- **THEN** 返回与现状一致的"请先录入样本"错误，intent 字段仍正常返回

### Requirement: 意图识别调试端点
系统 SHALL 提供 `POST /api/intent` 端点，请求体为 `{ "userInput": "...", "sessionId": "..." }`，响应返回意图、槽位、置信度、命中层级与各层耗时，且 MUST NOT 触发任何下游业务执行（不调 RAG、不生成内容）。

#### Scenario: 纯识别不执行
- **WHEN** 调用 `POST /api/intent` 输入"写一篇爬山文案"
- **THEN** 响应含 intent=CONTENT_GENERATION、slots、confidence、hitLayer、elapsed 信息，且向量库状态不受影响

### Requirement: 识别过程可观测
每次识别 SHALL 输出结构化 trace：归一化结果、各层是否启用、各层命中结果与置信度、总耗时。trace MUST 随 API 响应返回并打印到服务端日志，用于定位每一层的决策过程。

#### Scenario: trace 完整记录漏斗决策
- **WHEN** 一次输入依次经过归一化、规则未命中、LLM 兜底命中
- **THEN** trace 中可见 normalization、rule（miss）、llm（hit）各节点及耗时

### Requirement: LLM 兜底成本控制
系统 SHALL 对第三级 LLM 兜底设置每日调用上限（默认可配置），超限后的请求 MUST 直接返回 CLARIFICATION 而不调用 LLM；相同输入（归一化后完全一致）SHALL 命中本地缓存复用历史识别结果。

#### Scenario: 相同输入命中缓存
- **WHEN** 同一输入在缓存有效期内被识别两次
- **THEN** 第二次不再调用 LLM，hitLayer 标注为 CACHE，结果与第一次一致

#### Scenario: 超出每日上限
- **WHEN** 当日 LLM 兜底调用次数达到配置上限
- **THEN** 后续低置信请求直接返回 CLARIFICATION，日志记录限流事件

### Requirement: 修正事件配对与数据回流
当一轮识别返回 CLARIFICATION 且同一会话的下一轮识别产生非 CLARIFICATION 的最终意图时，系统 SHALL 将两轮自动配对为一条修正记录，包含：澄清输入、澄清结果、修正输入、最终意图、命中层级与时间戳。修正记录 SHALL 以 JSONL 追加写入本地文件，重启不丢失，供后续生成训练样本或扩充 few-shot 示例库使用。系统 MUST NOT 在本能力范围内自动触发模型微调或示例库更新。

#### Scenario: 澄清后修正自动配对
- **WHEN** 同一 sessionId 上一轮返回 CLARIFICATION，本轮识别为 CONTENT_GENERATION
- **THEN** 生成一条含澄清输入、澄清结果、修正输入与最终意图的修正记录，追加写入 JSONL 文件

#### Scenario: 非澄清轮不产生修正记录
- **WHEN** 上一轮为规则层直接命中的正常识别，本轮为任意识别结果
- **THEN** 不生成修正记录

### Requirement: 识别指标聚合
系统 SHALL 在进程内聚合识别指标：各层命中率（RULE/CLASSIFIER/LLM/CACHE/FALLBACK）、LLM 兜底率、澄清触发率、识别耗时 P95/P99。指标 SHALL 通过 `GET /api/intent/stats` 暴露。指标为进程内存实现，重启清零。

#### Scenario: 指标端点返回聚合数据
- **WHEN** 服务运行期间已处理若干识别请求后调用 `GET /api/intent/stats`
- **THEN** 响应含各层命中计数、LLM 兜底率、澄清触发率与耗时分位数
