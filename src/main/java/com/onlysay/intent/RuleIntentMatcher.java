package com.onlysay.intent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 第一级规则匹配：关键词表 + 正则槽位提取 + 短文本兜底。
 * 纯 Java 实现，不调用任何外部模型或网络服务。
 */
public final class RuleIntentMatcher {

    /** 明确创作/操作动词（短文本含动词时不触发澄清，放行到后续层级） */
    private static final List<String> ACTION_VERBS = List.of(
            "写", "改", "查", "搜", "生成", "创作", "润色", "扩写", "缩写", "摘要",
            "分析", "存", "分享", "撤销", "设置", "讲", "看看", "浏览");

    private static final Pattern LENGTH_PATTERN = Pattern.compile("(\\d+)\\s*字");
    private static final Pattern STYLE_EXPLICIT_PATTERN = Pattern.compile(
            "(?:改成|换成|变成|变为)([\\u4e00-\\u9fa5A-Za-z0-9]{1,8})风格");
    private static final Pattern STYLE_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]{1,8})风格");
    private static final Pattern PLATFORM_PATTERN = Pattern.compile(
            "(公众号|小红书|微博|抖音|知乎|朋友圈|B站|bilibili|视频号|飞书)");
    private static final Pattern TOPIC_ABOUT_PATTERN = Pattern.compile(
            "(?:关于|讲讲|说说|围绕)(.+?)(?:的)?(?:文章|文案|帖子|内容|短文|推文)");
    private static final Pattern TOPIC_WRITE_PATTERN = Pattern.compile(
            "(?:写|帮我写|生成|出|创作)(?:一篇|一个|个|条|则)?(.+)(?:的)?(?:文章|文案|帖子)");
    private static final Pattern SOURCE_REWRITE_PATTERN = Pattern.compile("把(.+?)改成");
    private static final Pattern TARGET_PATTERN = Pattern.compile("(?:存到|分享到|分享给|发送到)\\s*(.+)$");
    /** 结构化创作模板（独立于关键词表）：写/生成/创作 + 产出物名词 */
    private static final Pattern CONTENT_TEMPLATE_PATTERN = Pattern.compile(
            "(?:帮我写|写|生成|出一篇|创作)(?:一篇|一个|个|条|则)?.*(?:文章|文案|帖子|短文)");

    /**
     * 匹配结果：empty 表示规则层未命中（放行到后续层级）
     */
    public Optional<IntentResult> match(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return Optional.empty();
        }

        // 1. 关键词表匹配（注册表驱动，按注册顺序先具体后一般）
        Optional<IntentType> keywordHit = IntentRegistry.findByKeyword(normalizedText);
        if (keywordHit.isPresent()) {
            Map<String, Object> slots = extractSlots(keywordHit.get(), normalizedText);
            return Optional.of(new IntentResult(keywordHit.get(), slots, 1.0, HitLayer.RULE));
        }

        // 2. 结构化正则模板：写/生成/创作 + 产出物（独立于关键词表）
        if (CONTENT_TEMPLATE_PATTERN.matcher(normalizedText).matches()) {
            Map<String, Object> slots = extractSlots(IntentType.CONTENT_GENERATION, normalizedText);
            return Optional.of(new IntentResult(
                    IntentType.CONTENT_GENERATION, slots, 1.0, HitLayer.RULE));
        }

        // 3. 短文本兜底：<10 字且无明确动词 → CLARIFICATION
        String compact = normalizedText.replace(" ", "");
        if (compact.length() < 10 && ACTION_VERBS.stream().noneMatch(compact::contains)) {
            return Optional.of(new IntentResult(
                    IntentType.CLARIFICATION,
                    Map.of(),
                    1.0,
                    HitLayer.RULE,
                    false,
                    List.of(),
                    ClarificationText.shortInput()));
        }

        return Optional.empty();
    }

    /** 按意图定义提取槽位（长度/风格/平台等通用槽 + 意图专属槽） */
    private Map<String, Object> extractSlots(IntentType intent, String text) {
        Map<String, Object> slots = new HashMap<>();

        Matcher length = LENGTH_PATTERN.matcher(text);
        if (length.find()) {
            slots.put("length", Integer.parseInt(length.group(1)));
        }
        Matcher style = STYLE_EXPLICIT_PATTERN.matcher(text);
        if (style.find()) {
            slots.put("style", style.group(1));
        } else {
            style = STYLE_PATTERN.matcher(text);
            if (style.find()) {
                // 剥离贪婪匹配带上的前缀动词/数字（如"写800字幽默"→"幽默"）
                String candidate = style.group(1)
                        .replaceFirst("^(写|创作|生成|出一篇|帮我写)?(\\d+)?(字)?", "");
                if (!candidate.isBlank()) {
                    slots.put("style", candidate);
                }
            }
        }
        Matcher platform = PLATFORM_PATTERN.matcher(text);
        if (platform.find()) {
            slots.put("platform", platform.group(1));
        }

        switch (intent) {
            case CONTENT_GENERATION -> extractTopic(slots, text);
            case REWRITE -> extractSource(slots, text);
            case HOT_SEARCH -> extractTime(slots, text);
            case SYSTEM_CONTROL -> extractAction(slots, text);
            default -> { }
        }
        return slots;
    }

    private void extractTopic(Map<String, Object> slots, String text) {
        Matcher about = TOPIC_ABOUT_PATTERN.matcher(text);
        if (about.find()) {
            slots.put("topic", about.group(1).trim());
            return;
        }
        Matcher write = TOPIC_WRITE_PATTERN.matcher(text);
        if (write.find()) {
            String candidate = cleanTopicCandidate(write.group(1));
            if (!candidate.isEmpty()) {
                slots.put("topic", candidate);
            }
        }
    }

    /** 从写作模式候选中剥离已识别的结构化槽位片段（800字 / X风格），得到干净话题 */
    private String cleanTopicCandidate(String candidate) {
        String cleaned = candidate;
        Matcher length = LENGTH_PATTERN.matcher(cleaned);
        if (length.find()) {
            cleaned = cleaned.replace(length.group(0), "");
        }
        Matcher style = STYLE_PATTERN.matcher(cleaned);
        if (style.find()) {
            cleaned = cleaned.replace(style.group(0), "");
        }
        cleaned = cleaned.replaceAll("^的+|的+$", "").trim();
        return cleaned;
    }

    private void extractSource(Map<String, Object> slots, String text) {
        Matcher source = SOURCE_REWRITE_PATTERN.matcher(text);
        if (source.find()) {
            slots.put("source_text", source.group(1).trim());
        }
    }

    private void extractTime(Map<String, Object> slots, String text) {
        Matcher time = Pattern.compile("(今天|昨天|明天|最近|本周|上周|这个月|上个月|实时)").matcher(text);
        if (time.find()) {
            slots.put("time", time.group(1));
        }
    }

    private void extractAction(Map<String, Object> slots, String text) {
        // action 取命中的控制类关键词本身
        for (String keyword : List.of("保存", "存到", "分享", "撤销", "撤回", "设置")) {
            if (text.contains(keyword)) {
                slots.put("action", keyword);
                break;
            }
        }
        Matcher target = TARGET_PATTERN.matcher(text);
        if (target.find()) {
            slots.put("target", target.group(1).trim());
        }
    }

    RuleIntentMatcher() {}
}
