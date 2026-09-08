package com.onlysay.intent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 输出校验器：全部按 IntentRegistry 定义驱动（而非硬编码规则）。
 * 校验顺序：意图合法性 → 槽位类型 → 必需槽位缺失。
 */
public final class IntentValidator {

    /**
     * 校验结果：cleanedSlots 为清洗后的合法槽位；missingSlots 为缺失的必需槽位
     */
    public record ValidationResult(
            boolean intentValid,
            Map<String, Object> cleanedSlots,
            List<String> missingSlots) {

        public boolean hasMissingRequiredSlots() {
            return !missingSlots.isEmpty();
        }
    }

    public static ValidationResult validate(IntentType intent, Map<String, Object> slots) {
        if (!IntentRegistry.isValid(intent)) {
            return new ValidationResult(false, Map.of(), List.of());
        }
        IntentDefinition definition = IntentRegistry.definitionOf(intent).orElseThrow();
        Map<String, Object> cleaned = cleanSlots(definition, slots == null ? Map.of() : slots);
        List<String> missing = definition.getRequiredSlots().stream()
                .filter(required -> {
                    Object value = cleaned.get(required);
                    return value == null || (value instanceof String s && s.isBlank());
                })
                .toList();
        return new ValidationResult(true, cleaned, missing);
    }

    /** 槽位清洗：只保留注册表定义的槽位；类型非法的槽位置空（移除） */
    static Map<String, Object> cleanSlots(IntentDefinition definition, Map<String, Object> slots) {
        Map<String, Object> cleaned = new HashMap<>();
        for (Map.Entry<String, Object> entry : slots.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            boolean defined = definition.getRequiredSlots().contains(key)
                    || definition.getOptionalSlots().contains(key);
            if (!defined || value == null) {
                continue; // 未定义槽位直接丢弃；空值视为缺失
            }
            if ("length".equals(key)) {
                Integer length = asPositiveInt(value);
                if (length == null) {
                    continue; // 类型非法 → 置空（移除，视为缺失）
                }
                cleaned.put(key, length);
                continue;
            }
            if (value instanceof String s) {
                if (!s.isBlank()) {
                    cleaned.put(key, s.trim());
                }
            } else {
                cleaned.put(key, value);
            }
        }
        return cleaned;
    }

    private static Integer asPositiveInt(Object value) {
        try {
            int i = value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString().trim());
            return i >= 0 ? i : null;
        } catch (Exception e) {
            return null;
        }
    }

    private IntentValidator() {}
}
