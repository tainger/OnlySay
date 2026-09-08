package com.onlysay.intent;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第一级规则匹配：对应规格 Scenario「关键词命中创作意图」「正则提取槽位」「短文本触发澄清」
 */
class RuleIntentMatcherTest {

    private final RuleIntentMatcher matcher = new RuleIntentMatcher();

    @Test
    void 关键词命中创作意图_置信度1_0_不调用LLM() {
        Optional<IntentResult> result = matcher.match("帮我写一篇关于年轻人加班的文章");
        assertTrue(result.isPresent());
        assertEquals(IntentType.CONTENT_GENERATION, result.get().getIntent());
        assertEquals(1.0, result.get().getConfidence());
        assertEquals(HitLayer.RULE, result.get().getHitLayer());
        // 关于XX 的文章 模式提取话题
        assertEquals("年轻人加班", result.get().getSlots().get("topic"));
    }

    @Test
    void 正则提取槽位_长度风格话题() {
        Optional<IntentResult> result = matcher.match("写800字幽默风格的加班文章");
        assertTrue(result.isPresent());
        assertEquals(IntentType.CONTENT_GENERATION, result.get().getIntent());
        Map<String, Object> slots = result.get().getSlots();
        assertEquals(800, slots.get("length"));
        assertEquals("幽默", slots.get("style"));
        // 话题剥离 800字/幽默风格 后包含"加班"
        assertTrue(String.valueOf(slots.get("topic")).contains("加班"),
                "topic 实际为: " + slots.get("topic"));
    }

    @Test
    void 短文本触发澄清() {
        Optional<IntentResult> result = matcher.match("加班");
        assertTrue(result.isPresent());
        assertEquals(IntentType.CLARIFICATION, result.get().getIntent());
        assertEquals(HitLayer.RULE, result.get().getHitLayer());
        assertTrue(result.get().getClarificationText() != null);
    }

    @Test
    void 短文本含动词不澄清_放行后续层级() {
        assertTrue(matcher.match("写爬山").isEmpty());
    }

    @Test
    void 长文本无关键词_未命中() {
        assertTrue(matcher.match("最近总是在思考工作的意义以及生活的平衡点在哪里").isEmpty());
    }

    @Test
    void 改写关键词与原文提取() {
        Optional<IntentResult> result = matcher.match("把这篇改成小红书风格");
        assertTrue(result.isPresent());
        assertEquals(IntentType.REWRITE, result.get().getIntent());
        assertEquals("小红书", result.get().getSlots().get("style"));
        assertEquals("这篇", result.get().getSlots().get("source_text"));
    }
}
