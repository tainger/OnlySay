package com.onlysay.intent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 漏斗编排：置信度策略各路径 + 缓存 + 限流 + 修正配对 + trace
 */
class IntentRecognizerTest {

    @TempDir
    Path tempDir;

    private IntentRecognizer recognizer(IntentClassifier classifier,
                                        FakeChatModel fallbackFake,
                                        CorrectionRecorder recorder) {
        return new IntentRecognizer(classifier,
                new LlmFallbackRecognizer(fallbackFake == null ? new FakeChatModel().asModel() : fallbackFake.asModel()),
                new IntentCache(100),
                new DailyRateLimiter(5000),
                recorder);
    }

    @Test
    void 规则命中_不调用LLM() {
        FakeChatModel fallbackFake = new FakeChatModel();
        IntentRecognizer recognizer = recognizer(null, fallbackFake, new CorrectionRecorder(tempDir.resolve("c1.jsonl")));

        IntentRecognizer.Recognition recognition = recognizer.recognize("帮我写一篇关于年轻人加班的文章", null);

        assertEquals(IntentType.CONTENT_GENERATION, recognition.result().getIntent());
        assertEquals(HitLayer.RULE, recognition.result().getHitLayer());
        assertEquals(0, fallbackFake.callCount());
        // trace 含 RULE hit
        assertTrue(recognition.trace().getLayers().stream()
                .anyMatch(l -> l.getLayer().equals("RULE") && l.getStatus().equals("hit")));
    }

    @Test
    void 分类器高置信度直接采纳_副意图记录() {
        FakeChatModel fallbackFake = new FakeChatModel();
        FakeChatModel classifierFake = new FakeChatModel(
                "{\"CONTENT_GENERATION\": 0.92, \"REWRITE\": 0.78}");
        IntentRecognizer recognizer = recognizer(
                new LlmIntentClassifier(classifierFake.asModel()), fallbackFake,
                new CorrectionRecorder(tempDir.resolve("c2.jsonl")));

        IntentRecognizer.Recognition recognition = recognizer.recognize(
                "随便聊聊年轻人加班这个话题怎么样啊", "s1");

        assertEquals(IntentType.CONTENT_GENERATION, recognition.result().getIntent());
        assertEquals(HitLayer.CLASSIFIER, recognition.result().getHitLayer());
        assertEquals(0.92, recognition.result().getConfidence());
        // 副意图仅记录
        assertEquals(java.util.List.of("REWRITE"), recognition.result().getSecondaryIntents());
        // 不调用 LLM
        assertEquals(0, fallbackFake.callCount());
    }

    @Test
    void 分类器中置信度降级LLM复核() {
        FakeChatModel fallbackFake = new FakeChatModel(
                "{\"intent\":\"REWRITE\",\"slots\":{\"source_text\":\"这篇\",\"style\":\"小红书\"},\"confidence\":0.8}");
        FakeChatModel classifierFake = new FakeChatModel("{\"REWRITE\": 0.72}");
        IntentRecognizer recognizer = recognizer(
                new LlmIntentClassifier(classifierFake.asModel()), fallbackFake,
                new CorrectionRecorder(tempDir.resolve("c3.jsonl")));

        // 无关键词、长度≥10，规则未命中
        IntentRecognizer.Recognition recognition = recognizer.recognize("按小红书那种感觉重新写一遍行不行", "s1");

        assertEquals(IntentType.REWRITE, recognition.result().getIntent());
        assertEquals(HitLayer.LLM, recognition.result().getHitLayer());
        assertEquals(1, fallbackFake.callCount());
        // trace：RULE miss + CLASSIFIER miss + LLM hit
        assertTrue(recognition.trace().getLayers().stream()
                .anyMatch(l -> l.getLayer().equals("CLASSIFIER") && l.getStatus().equals("miss")));
        assertTrue(recognition.trace().getLayers().stream()
                .anyMatch(l -> l.getLayer().equals("LLM") && l.getStatus().equals("hit")));
    }

    @Test
    void 分类器低置信度触发澄清() {
        FakeChatModel fallbackFake = new FakeChatModel();
        FakeChatModel classifierFake = new FakeChatModel("{\"HOT_SEARCH\": 0.45}");
        IntentRecognizer recognizer = recognizer(
                new LlmIntentClassifier(classifierFake.asModel()), fallbackFake,
                new CorrectionRecorder(tempDir.resolve("c4.jsonl")));

        IntentRecognizer.Recognition recognition = recognizer.recognize(
                "随便说点什么关于加班的吧好吧", "s1");

        assertEquals(IntentType.CLARIFICATION, recognition.result().getIntent());
        assertEquals(HitLayer.CLASSIFIER, recognition.result().getHitLayer());
        assertEquals(0, fallbackFake.callCount());
    }

    @Test
    void 分类器调用失败降级第三级() {
        // 分类器模型返回非法 JSON → classify 内部解析失败 → failed → 降级
        FakeChatModel classifierFake = new FakeChatModel("乱七八糟的输出");
        FakeChatModel fallbackFake = new FakeChatModel(
                "{\"intent\":\"HOT_SEARCH\",\"slots\":{\"platform\":\"微博\"},\"confidence\":0.9}");
        IntentRecognizer recognizer = recognizer(
                new LlmIntentClassifier(classifierFake.asModel()), fallbackFake,
                new CorrectionRecorder(tempDir.resolve("c5.jsonl")));

        IntentRecognizer.Recognition recognition = recognizer.recognize(
                "随便说点什么关于加班的吧好吧", "s1");

        assertEquals(IntentType.HOT_SEARCH, recognition.result().getIntent());
        assertEquals(HitLayer.LLM, recognition.result().getHitLayer());
        assertTrue(recognition.trace().getLayers().stream()
                .anyMatch(l -> l.getLayer().equals("CLASSIFIER") && l.getStatus().equals("failed")));
    }

    @Test
    void 分类器关闭_直接进第三级() {
        FakeChatModel fallbackFake = new FakeChatModel(
                "{\"intent\":\"HOT_SEARCH\",\"slots\":{\"platform\":\"微博\"},\"confidence\":0.9}");
        IntentRecognizer recognizer = recognizer(null, fallbackFake,
                new CorrectionRecorder(tempDir.resolve("c6.jsonl")));

        IntentRecognizer.Recognition recognition = recognizer.recognize(
                "随便说点什么关于加班的吧好吧", "s1");

        assertEquals(HitLayer.LLM, recognition.result().getHitLayer());
        assertTrue(recognition.trace().getLayers().stream()
                .anyMatch(l -> l.getLayer().equals("CLASSIFIER") && l.getStatus().equals("disabled")));
    }

    @Test
    void 全部层级失败_最终兜底澄清() {
        FakeChatModel classifierFake = new FakeChatModel("坏输出", "坏输出");
        FakeChatModel fallbackFake = new FakeChatModel("还是坏", "还是坏");
        IntentRecognizer recognizer = recognizer(
                new LlmIntentClassifier(classifierFake.asModel()), fallbackFake,
                new CorrectionRecorder(tempDir.resolve("c7.jsonl")));

        IntentRecognizer.Recognition recognition = recognizer.recognize(
                "随便说点什么关于加班的吧好吧", "s1");

        assertEquals(IntentType.CLARIFICATION, recognition.result().getIntent());
        assertEquals(HitLayer.FALLBACK, recognition.result().getHitLayer());
        assertTrue(recognition.result().getClarificationText() != null);
    }

    @Test
    void 相同输入第二次命中缓存不再调用LLM() {
        FakeChatModel fallbackFake = new FakeChatModel(
                "{\"intent\":\"HOT_SEARCH\",\"slots\":{\"platform\":\"微博\"},\"confidence\":0.9}");
        IntentRecognizer recognizer = recognizer(null, fallbackFake,
                new CorrectionRecorder(tempDir.resolve("c8.jsonl")));

        String input = "随便说点什么关于加班的吧好吧";
        recognizer.recognize(input, null);
        IntentRecognizer.Recognition second = recognizer.recognize(input, null);

        assertEquals(HitLayer.CACHE, second.result().getHitLayer());
        assertEquals(IntentType.HOT_SEARCH, second.result().getIntent());
        assertEquals(1, fallbackFake.callCount()); // 第二次未调用 LLM
    }

    @Test
    void 超出每日上限直接澄清() {
        FakeChatModel fallbackFake = new FakeChatModel(
                "{\"intent\":\"HOT_SEARCH\",\"slots\":{},\"confidence\":0.9}");
        IntentRecognizer recognizer = new IntentRecognizer(null,
                new LlmFallbackRecognizer(fallbackFake.asModel()),
                new IntentCache(100),
                new DailyRateLimiter(0), // 上限 0 → 全部限流
                new CorrectionRecorder(tempDir.resolve("c9.jsonl")));

        IntentRecognizer.Recognition recognition = recognizer.recognize(
                "随便说点什么关于加班的吧好吧", "s1");

        assertEquals(IntentType.CLARIFICATION, recognition.result().getIntent());
        assertEquals(HitLayer.FALLBACK, recognition.result().getHitLayer());
        assertEquals(0, fallbackFake.callCount());
    }

    @Test
    void 澄清后修正自动配对落盘() throws Exception {
        Path file = tempDir.resolve("c10.jsonl");
        FakeChatModel fallbackFake = new FakeChatModel();
        FakeChatModel classifierFake = new FakeChatModel("{\"CONTENT_GENERATION\": 0.93}");
        IntentRecognizer recognizer = recognizer(
                new LlmIntentClassifier(classifierFake.asModel()), fallbackFake,
                new CorrectionRecorder(file));

        // 第一轮：短输入澄清
        recognizer.recognize("加班", "session-x");
        // 第二轮：用户修正 → 应配对落盘
        recognizer.recognize("写一篇关于爬山的朋友圈文案", "session-x");

        assertTrue(Files.exists(file));
        String line = Files.readAllLines(file).get(0);
        assertTrue(line.contains("\"clarificationInput\":\"加班\""));
        assertTrue(line.contains("\"finalIntent\":\"CONTENT_GENERATION\""));
    }

    @Test
    void 跨轮槽位继承生效() {
        FakeChatModel fallbackFake = new FakeChatModel(
                "{\"intent\":\"REWRITE\",\"slots\":{\"source_text\":\"这篇\",\"style\":\"小红书\"},\"confidence\":0.9}");
        IntentRecognizer recognizer = recognizer(null, fallbackFake,
                new CorrectionRecorder(tempDir.resolve("c11.jsonl")));

        // 第一轮：创作 topic=年轻人加班（规则命中）
        recognizer.recognize("帮我写一篇关于年轻人加班的文章", "s-iter");
        // 第二轮：改写 → topic 从上一轮继承
        IntentRecognizer.Recognition second = recognizer.recognize("改成小红书风格", "s-iter");

        assertEquals(IntentType.REWRITE, second.result().getIntent());
        assertEquals("年轻人加班", second.result().getSlots().get("topic"));
        assertEquals("小红书", second.result().getSlots().get("style"));
    }

    @Test
    void 意图切换清空旧槽位() {
        FakeChatModel fallbackFake = new FakeChatModel(
                "{\"intent\":\"HOT_SEARCH\",\"slots\":{\"platform\":\"微博\",\"time\":\"今天\"},\"confidence\":0.9}");
        IntentRecognizer recognizer = recognizer(null, fallbackFake,
                new CorrectionRecorder(tempDir.resolve("c12.jsonl")));

        recognizer.recognize("帮我写一篇关于年轻人加班的文章", "s-switch");
        IntentRecognizer.Recognition second = recognizer.recognize("算了，看看今天热点吧", "s-switch");

        assertEquals(IntentType.HOT_SEARCH, second.result().getIntent());
        assertTrue(!second.result().getSlots().containsKey("topic")); // 旧创作槽位被清空
    }

    @Test
    void 指标聚合正确() {
        FakeChatModel fallbackFake = new FakeChatModel(
                "{\"intent\":\"HOT_SEARCH\",\"slots\":{},\"confidence\":0.9}");
        IntentRecognizer recognizer = recognizer(null, fallbackFake,
                new CorrectionRecorder(tempDir.resolve("c13.jsonl")));

        recognizer.recognize("帮我写一篇关于年轻人加班的文章", null); // RULE
        recognizer.recognize("随便说点什么关于加班的吧好吧", null); // LLM

        Map<String, Object> stats = recognizer.getMetrics().snapshot();
        assertEquals(2L, stats.get("totalRequests"));
        @SuppressWarnings("unchecked")
        Map<String, Long> hits = (Map<String, Long>) stats.get("layerHits");
        assertEquals(1L, hits.get("RULE"));
        assertEquals(1L, hits.get("LLM"));
        assertEquals(0.5, (double) stats.get("llmFallbackRate"));
    }
}
