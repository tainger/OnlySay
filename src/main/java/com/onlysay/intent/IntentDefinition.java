package com.onlysay.intent;

import java.util.List;

/**
 * 意图定义：意图标识 + 必需/可选槽位 + 规则关键词 + 示例。
 * 由 IntentRegistry 静态注册，校验与路由全部按此定义驱动（而非硬编码）。
 */
public class IntentDefinition {

    private final IntentType type;
    private final List<String> requiredSlots;
    private final List<String> optionalSlots;
    private final List<String> keywords;
    private final String example;

    public IntentDefinition(IntentType type,
                            List<String> requiredSlots,
                            List<String> optionalSlots,
                            List<String> keywords,
                            String example) {
        this.type = type;
        this.requiredSlots = List.copyOf(requiredSlots);
        this.optionalSlots = List.copyOf(optionalSlots);
        this.keywords = List.copyOf(keywords);
        this.example = example;
    }

    public IntentType getType() { return type; }
    public List<String> getRequiredSlots() { return requiredSlots; }
    public List<String> getOptionalSlots() { return optionalSlots; }
    public List<String> getKeywords() { return keywords; }
    public String getExample() { return example; }
}
