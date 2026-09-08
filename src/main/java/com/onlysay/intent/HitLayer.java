package com.onlysay.intent;

/**
 * 命中层级：标识意图识别结果由漏斗哪一层产出
 */
public enum HitLayer {
    /** 第一级：规则匹配（关键词/正则/短文本兜底） */
    RULE,
    /** 第二级：轻量 LLM 分类器（deepseek-v4-flash） */
    CLASSIFIER,
    /** 第三级：LLM 兜底复核（deepseek-v4-pro） */
    LLM,
    /** 缓存命中（继承原结果） */
    CACHE,
    /** 全部层级失败后的最终兜底 */
    FALLBACK
}
