package com.onlysay.intent;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册表驱动校验：对应规格 Scenario「注册表驱动校验」
 */
class IntentValidatorTest {

    @Test
    void 注册表定义必需槽位() {
        assertEquals("topic", IntentRegistry.definitionOf(IntentType.CONTENT_GENERATION)
                .orElseThrow().getRequiredSlots().get(0));
        assertEquals("source_text", IntentRegistry.definitionOf(IntentType.REWRITE)
                .orElseThrow().getRequiredSlots().get(0));
    }

    @Test
    void 非法意图校验失败() {
        assertFalse(IntentValidator.validate(null, Map.of()).intentValid());
    }

    @Test
    void 必需槽位缺失被检出() {
        IntentValidator.ValidationResult result =
                IntentValidator.validate(IntentType.REWRITE, Map.of("style", "小红书"));
        assertTrue(result.hasMissingRequiredSlots());
        assertTrue(result.missingSlots().contains("source_text"));
    }

    @Test
    void 类型非法槽位置空() {
        Map<String, Object> slots = new HashMap<>();
        slots.put("topic", "加班");
        slots.put("length", "abc"); // 类型非法 → 移除
        IntentValidator.ValidationResult result =
                IntentValidator.validate(IntentType.CONTENT_GENERATION, slots);
        assertFalse(result.cleanedSlots().containsKey("length"));
        assertEquals("加班", result.cleanedSlots().get("topic"));
    }

    @Test
    void 数字字符串length转为整数() {
        Map<String, Object> slots = new HashMap<>();
        slots.put("topic", "猫");
        slots.put("length", "800");
        IntentValidator.ValidationResult result =
                IntentValidator.validate(IntentType.CONTENT_GENERATION, slots);
        assertEquals(800, result.cleanedSlots().get("length"));
    }

    @Test
    void 未定义槽位被丢弃() {
        Map<String, Object> slots = new HashMap<>();
        slots.put("topic", "猫");
        slots.put("unknown_slot", "x");
        IntentValidator.ValidationResult result =
                IntentValidator.validate(IntentType.CONTENT_GENERATION, slots);
        assertFalse(result.cleanedSlots().containsKey("unknown_slot"));
    }
}
