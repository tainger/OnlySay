package com.onlysay.intent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第二级分类器输出解析（不依赖网络）
 */
class LlmIntentClassifierTest {

    private final LlmIntentClassifier classifier = new LlmIntentClassifier(FakeModelStub.model());

    @Test
    void 解析多标签置信度分布() {
        Map<String, Double> scores = classifier.parseScores(
                "{\"CONTENT_GENERATION\": 0.92, \"DATA_QUERY\": 0.78}");
        assertEquals(0.92, scores.get("CONTENT_GENERATION"));
        // 非法意图名被过滤（DATA_QUERY 不在 MVP 意图清单）
        assertTrue(!scores.containsKey("DATA_QUERY"));
    }

    @Test
    void 带代码块或前后缀的输出可提取() {
        Map<String, Double> scores = classifier.parseScores(
                "好的，结果如下：\n```json\n{\"REWRITE\": 0.9}\n```");
        assertEquals(0.9, scores.get("REWRITE"));
    }

    @Test
    void 非法JSON抛异常() {
        assertThrows(IllegalArgumentException.class,
                () -> classifier.parseScores("我觉得是写文章吧"));
    }

    @Test
    void 置信度越界被过滤导致失败() {
        assertThrows(IllegalArgumentException.class,
                () -> classifier.parseScores("{\"REWRITE\": 1.5}"));
    }
}

/** 无网络模型桩：任何调用都抛异常（本测试类只测解析逻辑） */
class FakeModelStub {
    static dev.langchain4j.model.chat.ChatModel model() {
        return new FakeChatModel().asModel();
    }
}
