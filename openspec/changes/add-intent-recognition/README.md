# add-intent-recognition

为 OnlySay 增加三级漏斗意图识别模块：规则匹配 → 轻量分类 → LLM 兜底，输出意图+槽位+置信度并路由到现有 RAG 生成链路
