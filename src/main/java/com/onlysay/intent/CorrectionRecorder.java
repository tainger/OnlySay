package com.onlysay.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 修正事件配对与数据回流：CLARIFICATION 轮 + 同会话下一轮非 CLARIFICATION 结果
 * 自动配对为一条修正记录，追加写入本地 JSONL（重启不丢失），
 * 供后续生成训练样本/扩充 few-shot 示例库（本类不做自动微调）。
 *
 * 改造自原 CorrectionRecorder：加 @Component 让 Spring 容器管理单例，
 * 保留测试用 Path 构造器（IntentRecognizerTest 直接 new）。
 */
@Component
public class CorrectionRecorder {

    private static final String DEFAULT_FILE = "data/intent-corrections.jsonl";

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();

    public CorrectionRecorder() {
        this(Path.of(DEFAULT_FILE));
    }

    /** 测试用：自定义输出文件 */
    public CorrectionRecorder(Path file) {
        this.file = file;
        try {
            Files.createDirectories(file.getParent() == null ? Path.of(".") : file.getParent());
        } catch (IOException e) {
            System.err.println("⚠️ 修正记录目录创建失败: " + e.getMessage());
        }
    }

    /**
     * 若构成修正（上一轮为澄清轮且本轮为非澄清结果）则落盘一条记录。
     *
     * @param sessionId          会话 ID
     * @param clarificationInput 上一轮澄清输入（null 表示不构成修正）
     * @param currentInput       本轮用户输入（即修正内容）
     * @param currentResult      本轮识别结果
     */
    public void maybeRecord(String sessionId, String clarificationInput,
                            String currentInput, IntentResult currentResult) {
        if (clarificationInput == null || clarificationInput.isBlank()) {
            return; // 非澄清轮不产生修正记录
        }
        if (currentResult.getIntent() == IntentType.CLARIFICATION) {
            return; // 连续澄清不配对
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("timestamp", Instant.now().toString());
        record.put("sessionId", sessionId == null ? "" : sessionId);
        record.put("clarificationInput", clarificationInput);
        record.put("clarificationResult", "CLARIFICATION");
        record.put("correctionInput", currentInput);
        record.put("finalIntent", currentResult.getIntent().name());
        record.put("finalHitLayer", currentResult.getHitLayer().name());
        append(record);
    }

    private synchronized void append(Map<String, Object> record) {
        try (BufferedWriter writer = Files.newBufferedWriter(file,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(mapper.writeValueAsString(record));
            writer.newLine();
        } catch (IOException e) {
            System.err.println("⚠️ 修正记录写入失败: " + e.getMessage());
        }
    }
}
