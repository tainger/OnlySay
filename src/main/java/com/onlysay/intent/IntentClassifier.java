package com.onlysay.intent;

import java.util.Map;

/**
 * 第二级轻量分类模型 SPI：输入归一化文本，输出各意图置信度分布（多标签）。
 * MVP 默认实现为 LlmIntentClassifier（deepseek-v4-flash，非思考模式）；
 * 未来可替换为微调小模型实现，只需实现本接口。
 */
public interface IntentClassifier {

    /**
     * @return 各合法意图的置信度分布（0.0-1.0）；空 Map 表示无法给出有效分布
     */
    ClassifierOutput classify(String normalizedText);

    /**
     * 分类器输出：scores 为意图名 → 置信度；
     * failed=true 表示调用超时/返回不可解析（编排层据此降级到第三级）。
     */
    record ClassifierOutput(Map<String, Double> scores, boolean failed, String error) {

        public static ClassifierOutput ok(Map<String, Double> scores) {
            return new ClassifierOutput(scores, false, null);
        }

        public static ClassifierOutput failure(String error) {
            return new ClassifierOutput(Map.of(), true, error);
        }
    }
}
