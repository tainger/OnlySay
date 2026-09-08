package com.onlysay.intent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 意图注册表：静态注册全部意图定义（必需/可选槽位、规则关键词、示例）。
 * 校验与路由按注册表定义驱动；新增意图只需在此注册，无需修改漏斗编排逻辑。
 */
public final class IntentRegistry {

    private static final Map<IntentType, IntentDefinition> REGISTRY = new EnumMap<>(IntentType.class);

    static {
        register(new IntentDefinition(
                IntentType.CONTENT_GENERATION,
                List.of("topic"),
                List.of("style", "length", "platform", "tone"),
                List.of("写一篇", "帮我写", "生成文章", "出一篇", "创作", "写个", "帮我生成", "写条"),
                "写一篇800字幽默风格的公众号文章，讲年轻人为什么不愿意加班"));

        register(new IntentDefinition(
                IntentType.REWRITE,
                List.of("source_text"),
                List.of("style", "length", "platform"),
                List.of("改写", "重写", "润色", "换种说法", "改成", "改一下", "扩写", "缩写", "摘要", "换种风格"),
                "把这篇改成小红书风格"));

        register(new IntentDefinition(
                IntentType.HOT_SEARCH,
                List.of(),
                List.of("platform", "time", "topic"),
                List.of("热搜", "热点", "最近火", "爆款", "trending", "大瓜", "有什么大事", "有什么新闻"),
                "今天微博有什么大瓜"));

        register(new IntentDefinition(
                IntentType.SYSTEM_CONTROL,
                List.of("action"),
                List.of("target"),
                List.of("保存", "存到", "分享", "撤销", "撤回", "设置", "以后都", "记住偏好"),
                "把这篇存到我的草稿箱"));

        register(new IntentDefinition(
                IntentType.CLARIFICATION,
                List.of(),
                List.of(),
                List.of(),
                "没太理解你的意思，你是想写文章还是查资料？"));
    }

    private static void register(IntentDefinition definition) {
        REGISTRY.put(definition.getType(), definition);
    }

    public static Optional<IntentDefinition> definitionOf(IntentType type) {
        return Optional.ofNullable(REGISTRY.get(type));
    }

    public static List<IntentDefinition> allDefinitions() {
        return new ArrayList<>(REGISTRY.values());
    }

    public static boolean isValid(IntentType type) {
        return type != null && REGISTRY.containsKey(type);
    }

    /** 合法意图名称清单（供 LLM Prompt 使用，不含 CLARIFICATION——它由系统自身兜底产出） */
    public static List<String> recognizableIntentNames() {
        return REGISTRY.values().stream()
                .map(d -> d.getType().name())
                .filter(name -> !name.equals(IntentType.CLARIFICATION.name()))
                .toList();
    }

    /** 关键词反向匹配：返回第一个关键词命中输入的意图（按注册顺序，先具体后一般） */
    public static Optional<IntentType> findByKeyword(String normalizedText) {
        if (normalizedText == null || normalizedText.isEmpty()) {
            return Optional.empty();
        }
        for (IntentDefinition definition : REGISTRY.values()) {
            for (String keyword : definition.getKeywords()) {
                if (normalizedText.contains(keyword)) {
                    return Optional.of(definition.getType());
                }
            }
        }
        return Optional.empty();
    }

    private IntentRegistry() {}
}
