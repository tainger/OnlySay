package com.onlysay.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 第二级默认实现：调用 deepseek-v4-flash（非思考模式、temperature≈0.1、短 Prompt）
 * 输出各意图置信度 JSON 分布，支持主/副意图多标签。
 *
 * 改造自原 LlmIntentClassifier：删除依赖 Config 的无参构造器，
 * 改用 Spring DI 注入 classifierChatModel Bean（ChatModelConfig 装配，已显式关闭思考模式）。
 */
@Component
@ConditionalOnProperty(name = "onlysay.intent.classifier", havingValue = "llm", matchIfMissing = true)
public class LlmIntentClassifier implements IntentClassifier {

    private final ChatModel chatModel;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Spring DI 主构造器：注入 classifierChatModel Bean */
    public LlmIntentClassifier(@Qualifier("classifierChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public ClassifierOutput classify(String normalizedText) {
        String prompt = buildPrompt(normalizedText);
        try {
            String response = chatModel.chat(prompt);
            return ClassifierOutput.ok(parseScores(response));
        } catch (Exception e) {
            return ClassifierOutput.failure(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String buildPrompt(String normalizedText) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是意图分类器。从用户输入判断意图，输出各意图的置信度（0-1 的小数），允许多标签。\n");
        sb.append("意图清单：").append(IntentRegistry.recognizableIntentNames()).append("\n");
        sb.append("只输出 JSON 对象，格式如 {\"CONTENT_GENERATION\": 0.92, \"DATA_QUERY\": 0.41}，不要输出其他内容。\n\n");
        sb.append("用户输入：").append(normalizedText).append("\nJSON：");
        return sb.toString();
    }

    /** 解析 LLM 返回的置信度分布 JSON；非法输出抛异常由调用方重试/降级 */
    Map<String, Double> parseScores(String response) {
        String json = extractJson(response);
        try {
            JsonNode root = mapper.readTree(json);
            if (!root.isObject()) {
                throw new IllegalArgumentException("输出不是 JSON 对象");
            }
            Map<String, Double> scores = new LinkedHashMap<>();
            root.fieldNames().forEachRemaining(name -> {
                // 只保留注册表中的合法意图名
                if (IntentRegistry.recognizableIntentNames().contains(name)) {
                    JsonNode value = root.get(name);
                    if (value.isNumber()) {
                        double d = value.asDouble();
                        if (d >= 0.0 && d <= 1.0) {
                            scores.put(name, d);
                        }
                    }
                }
            });
            if (scores.isEmpty()) {
                throw new IllegalArgumentException("无合法意图置信度");
            }
            return scores;
        } catch (Exception e) {
            throw new IllegalArgumentException("分类器输出解析失败: " + e.getMessage());
        }
    }

    /** 从可能包含代码块/前缀文本的响应中提取 JSON 对象片段 */
    static String extractJson(String response) {
        if (response == null) {
            return "";
        }
        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
