package com.onlysay.intent;

/**
 * 意图类型（MVP 集合，注册表驱动，新增意图无需修改漏斗编排逻辑）
 */
public enum IntentType {
    /** 内容创作：写一篇关于 X 的文章/文案 */
    CONTENT_GENERATION,
    /** 改写：把已有内容改成某风格/长度 */
    REWRITE,
    /** 热点追踪：今天有什么热点/大瓜 */
    HOT_SEARCH,
    /** 系统控制：保存/分享/设置偏好/撤销 */
    SYSTEM_CONTROL,
    /** 兜底澄清：无法给出可信意图或必需槽位缺失时反问 */
    CLARIFICATION
}
