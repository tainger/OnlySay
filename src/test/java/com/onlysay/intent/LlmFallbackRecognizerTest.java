package com.onlysay.intent;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第三级 LLM 兜底：对应规格 Scenario「LLM 正常识别」「LLM 输出非法意图」「必需槽位缺失」
 */
class LlmFallbackRecognizerTest {

    @Test
    void LLM正常识别() {
        FakeChatModel fake = new FakeChatModel(
                "{\"intent\":\"HOT_SEARCH\",\"slots\":{\"platform\":\"微博\",\"time\":\"今天\"},\"confidence\":0.94}");
        LlmFallbackRecognizer recognizer = new LlmFallbackRecognizer(fake.asModel());

        Optional<IntentResult> result = recognizer.recognize("今天微博有什么大瓜");
        assertTrue(result.isPresent());
        assertEquals(IntentType.HOT_SEARCH, result.get().getIntent());
        assertEquals("微博", result.get().getSlots().get("platform"));
        assertEquals(HitLayer.LLM, result.get().getHitLayer());
        assertEquals(0.94, result.get().getConfidence());
    }

    @Test
    void LLM输出非法意图_重试后回退FALLBACK() {
        // 两次响应都返回非法意图
        FakeChatModel fake = new FakeChatModel(
                "{\"intent\":\"MAKE_COFFEE\",\"slots\":{},\"confidence\":0.9}",
                "{\"intent\":\"MAKE_COFFEE\",\"slots\":{},\"confidence\":0.9}");
        LlmFallbackRecognizer recognizer = new LlmFallbackRecognizer(fake.asModel());

        Optional<IntentResult> result = recognizer.recognize("给我来杯咖啡");
        assertTrue(result.isEmpty());
        assertEquals(2, fake.callCount()); // 解析失败重试 1 次
    }

    @Test
    void 必需槽位缺失触发澄清() {
        FakeChatModel fake = new FakeChatModel(
                "{\"intent\":\"CONTENT_GENERATION\",\"slots\":{},\"confidence\":0.9}");
        LlmFallbackRecognizer recognizer = new LlmFallbackRecognizer(fake.asModel());

        Optional<IntentResult> result = recognizer.recognize("写一篇文章");
        assertTrue(result.isPresent());
        assertEquals(IntentType.CLARIFICATION, result.get().getIntent());
        // 反问话术包含对 topic 的追问
        assertTrue(result.get().getClarificationText().contains("主题"));
    }

    @Test
    void 置信度缺失标记degraded并取默认值() {
        FakeChatModel fake = new FakeChatModel(
                "{\"intent\":\"HOT_SEARCH\",\"slots\":{\"platform\":\"微博\"}}");
        LlmFallbackRecognizer recognizer = new LlmFallbackRecognizer(fake.asModel());

        Optional<IntentResult> result = recognizer.recognize("微博大瓜");
        assertTrue(result.isPresent());
        assertEquals(0.75, result.get().getConfidence());
        assertTrue(result.get().isDegraded());
    }

    @Test
    void 非法JSON重试一次成功() {
        FakeChatModel fake = new FakeChatModel(
                "这不是JSON",
                "{\"intent\":\"SYSTEM_CONTROL\",\"slots\":{\"action\":\"保存\"},\"confidence\":0.9}");
        LlmFallbackRecognizer recognizer = new LlmFallbackRecognizer(fake.asModel());

        Optional<IntentResult> result = recognizer.recognize("把这篇保存一下");
        assertTrue(result.isPresent());
        assertEquals(IntentType.SYSTEM_CONTROL, result.get().getIntent());
        assertEquals(2, fake.callCount());
    }
}
