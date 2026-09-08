package com.onlysay.intent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话状态：对应规格 Scenario「跨轮槽位继承」「意图切换清空状态」
 */
class DialogueStateTest {

    @Test
    void 跨轮槽位继承() {
        DialogueState state = new DialogueState();
        // 上一轮：CONTENT_GENERATION topic=年轻人加班
        state.push("s1", "写一篇关于年轻人加班的文章",
                new IntentResult(IntentType.CONTENT_GENERATION,
                        Map.of("topic", "年轻人加班"), 1.0, HitLayer.RULE));

        // 当前轮：REWRITE style=小红书，缺 topic → 从上一轮继承
        Map<String, Object> merged = state.inheritSlots("s1", IntentType.REWRITE,
                Map.of("source_text", "这篇", "style", "小红书"), false);

        assertEquals("年轻人加班", merged.get("topic"));
        assertEquals("小红书", merged.get("style"));
        assertEquals("这篇", merged.get("source_text"));
    }

    @Test
    void 意图切换清空状态() {
        DialogueState state = new DialogueState();
        state.push("s1", "写一篇关于年轻人加班的文章",
                new IntentResult(IntentType.CONTENT_GENERATION,
                        Map.of("topic", "年轻人加班"), 1.0, HitLayer.RULE));

        // "算了，看看今天热点吧" 命中信号词 + 新意图 → 清空旧状态不继承
        assertTrue(SwitchDetector.isSwitchSignal("算了，看看今天热点吧"));
        Map<String, Object> merged = state.inheritSlots("s1", IntentType.HOT_SEARCH,
                Map.of("time", "今天"), true);

        assertTrue(!merged.containsKey("topic"));
        assertEquals("今天", merged.get("time"));
    }

    @Test
    void 长输入信号词不算切换() {
        assertTrue(!SwitchDetector.isSwitchSignal(
                "这篇文章讨论了很多关于加班还是不加班的社会议题，请帮我润色全文"));
    }

    @Test
    void 澄清轮标记与消费() {
        DialogueState state = new DialogueState();
        state.push("s1", "加班",
                new IntentResult(IntentType.CLARIFICATION, Map.of(), 1.0, HitLayer.RULE));
        assertEquals("加班", state.consumeClarificationInput("s1"));
    }

    @Test
    void 非澄清轮无修正标记() {
        DialogueState state = new DialogueState();
        state.push("s1", "写一篇文章",
                new IntentResult(IntentType.CONTENT_GENERATION, Map.of("topic", "x"), 1.0, HitLayer.RULE));
        assertEquals(null, state.consumeClarificationInput("s1"));
    }

    @Test
    void 无sessionId不维护状态() {
        DialogueState state = new DialogueState();
        state.push(null, "写一篇文章",
                new IntentResult(IntentType.CONTENT_GENERATION, Map.of("topic", "x"), 1.0, HitLayer.RULE));
        assertEquals(0, state.depth(null));
    }

    @Test
    void 状态栈深度上限8轮() {
        DialogueState state = new DialogueState();
        for (int i = 0; i < 12; i++) {
            state.push("s1", "输入" + i,
                    new IntentResult(IntentType.CONTENT_GENERATION, Map.of("topic", String.valueOf(i)), 1.0, HitLayer.RULE));
        }
        assertEquals(8, state.depth("s1"));
    }
}
