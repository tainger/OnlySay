package com.onlysay.intent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据回流：对应规格 Scenario「澄清后修正自动配对」「非澄清轮不产生修正记录」
 */
class CorrectionRecorderTest {

    @TempDir
    Path tempDir;

    @Test
    void 澄清后修正自动配对落盘() throws Exception {
        Path file = tempDir.resolve("corrections.jsonl");
        CorrectionRecorder recorder = new CorrectionRecorder(file);

        recorder.maybeRecord("s1", "加班", "写一篇关于爬山的朋友圈文案",
                new IntentResult(IntentType.CONTENT_GENERATION,
                        java.util.Map.of("topic", "爬山"), 1.0, HitLayer.RULE));

        List<String> lines = Files.readAllLines(file);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"clarificationInput\":\"加班\""));
        assertTrue(lines.get(0).contains("\"correctionInput\":\"写一篇关于爬山的朋友圈文案\""));
        assertTrue(lines.get(0).contains("\"finalIntent\":\"CONTENT_GENERATION\""));
    }

    @Test
    void 非澄清轮不产生修正记录() throws Exception {
        Path file = tempDir.resolve("corrections.jsonl");
        CorrectionRecorder recorder = new CorrectionRecorder(file);

        recorder.maybeRecord("s1", null, "写一篇文章",
                new IntentResult(IntentType.CONTENT_GENERATION, java.util.Map.of(), 1.0, HitLayer.RULE));

        assertFalse(Files.exists(file));
    }

    @Test
    void 连续澄清不配对() throws Exception {
        Path file = tempDir.resolve("corrections.jsonl");
        CorrectionRecorder recorder = new CorrectionRecorder(file);

        recorder.maybeRecord("s1", "加班", "什么加班",
                new IntentResult(IntentType.CLARIFICATION, java.util.Map.of(), 0.5, HitLayer.CLASSIFIER));

        assertFalse(Files.exists(file));
    }

    @Test
    void 重启后文件保留() throws Exception {
        Path file = tempDir.resolve("corrections.jsonl");
        new CorrectionRecorder(file).maybeRecord("s1", "加班", "写爬山的文章",
                new IntentResult(IntentType.CONTENT_GENERATION, java.util.Map.of(), 1.0, HitLayer.RULE));

        // 模拟重启：新建 recorder 指向同一文件继续追加
        new CorrectionRecorder(file).maybeRecord("s2", "热点", "看看微博热搜",
                new IntentResult(IntentType.HOT_SEARCH, java.util.Map.of(), 1.0, HitLayer.RULE));

        assertEquals(2, Files.readAllLines(file).size());
    }
}
