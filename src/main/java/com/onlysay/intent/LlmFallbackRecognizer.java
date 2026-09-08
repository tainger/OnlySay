package com.onlysay.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlysay.ChatModelFactory;
import com.onlysay.Config;
import dev.langchain4j.model.chat.ChatModel;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 第三级 LLM 兜底识别（deepseek-v4-pro）：Few-shot + JSON 输出 + 注册表驱动校验。
 * 返回 empty 表示两次尝试均失败（编排层据此产出 hitLayer=FALLBACK 的澄清兜底）。
 */
public class LlmFallbackRecognizer {

    private final ChatModel chatModel;
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmFallbackRecognizer() {
        this(ChatModelFactory.build(
                Config.getIntentLlmModel(), 0.1, Duration.ofSeconds(40)));
    }

    /** 测试用：注入自定义 ChatModel */
    LlmFallbackRecognizer(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public Optional<IntentResult> recognize(String normalizedText) {
        String prompt = buildPrompt(normalizedText);
        // 解析/校验失败重试 1 次，再失败回退 FALLBACK
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String response = chatModel.chat(prompt);
                RawOutput raw = parseOutput(response);
                return Optional.of(toResult(raw));
            } catch (Exception ignored) {
                // 重试
            }
        }
        return Optional.empty();
    }

    private String buildPrompt(String normalizedText) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是意图识别专家。从用户输入中提取意图和槽位，输出 JSON。\n");
        sb.append("意图列表：").append(IntentRegistry.recognizableIntentNames()).append("\n");
        sb.append("槽位说明：topic=主题，style=风格，length=数字字数，platform=发布平台，")
          .append("source_text=被改写的原文，time=时间词\n\n");
        sb.append("【Few-shot 示例】\n");
        sb.append("用户：\"用搞笑风格写一篇300字的猫主子日常\"\n");
        sb.append("输出：{\"intent\":\"CONTENT_GENERATION\",\"slots\":{\"topic\":\"猫主子日常\",\"style\":\"搞笑\",\"length\":300},\"confidence\":0.96}\n\n");
        sb.append("用户：\"今天微博有什么大瓜\"\n");
        sb.append("输出：{\"intent\":\"HOT_SEARCH\",\"slots\":{\"platform\":\"微博\",\"time\":\"今天\"},\"confidence\":0.94}\n\n");
        sb.append("只输出一个 JSON 对象，不要输出其他内容。\n\n");
        sb.append("【当前输入】\n").append(normalizedText).append("\nJSON：");
        return sb.toString();
    }

    private RawOutput parseOutput(String response) {
        String json = LlmIntentClassifier.extractJson(response);
        try {
            JsonNode root = mapper.readTree(json);
            if (!root.isObject() || !root.hasNonNull("intent")) {
                throw new IllegalArgumentException("缺少 intent 字段");
            }
            String intentName = root.get("intent").asText();
            Map<String, Object> slots = new HashMap<>();
            JsonNode slotsNode = root.get("slots");
            if (slotsNode != null && slotsNode.isObject()) {
                slotsNode.fieldNames().forEachRemaining(name -> {
                    JsonNode value = slotsNode.get(name);
                    if (value.isNumber()) {
                        slots.put(name, value.numberValue());
                    } else if (value.isTextual()) {
                        slots.put(name, value.asText());
                    }
                });
            }
            double confidence = 0.75;
            boolean degraded = true;
            JsonNode confNode = root.get("confidence");
            if (confNode != null && confNode.isNumber()) {
                double c = confNode.asDouble();
                if (c >= 0.0 && c <= 1.0) {
                    confidence = c;
                    degraded = false;
                }
            }
            return new RawOutput(intentName, slots, confidence, degraded, response);
        } catch (Exception e) {
            throw new IllegalArgumentException("兜底输出解析失败: " + e.getMessage());
        }
    }

    private IntentResult toResult(RawOutput raw) {
        IntentType intent;
        try {
            intent = IntentType.valueOf(raw.intentName());
        } catch (IllegalArgumentException e) {
            // 意图不在合法清单 → 视为失败，交由重试/回退逻辑
            throw new IllegalArgumentException("非法意图: " + raw.intentName());
        }

        IntentValidator.ValidationResult validation = IntentValidator.validate(intent, raw.slots());
        if (!validation.intentValid()) {
            throw new IllegalArgumentException("意图校验失败");
        }

        // 必需槽位缺失 → 触发澄清反问，而非直接执行
        if (validation.hasMissingRequiredSlots()) {
            String question = ClarificationText.missingSlot(validation.missingSlots().get(0));
            return new IntentResult(
                    IntentType.CLARIFICATION,
                    validation.cleanedSlots(),
                    raw.confidence(),
                    HitLayer.LLM,
                    raw.degraded(),
                    List.of(),
                    question);
        }

        return new IntentResult(
                intent,
                validation.cleanedSlots(),
                raw.confidence(),
                HitLayer.LLM,
                raw.degraded(),
                List.of(),
                null);
    }

    private record RawOutput(String intentName, Map<String, Object> slots,
                             double confidence, boolean degraded, String rawResponse) {}
}
