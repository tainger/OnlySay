package com.onlysay.intent;

import java.util.ArrayList;
import java.util.List;

/**
 * 识别过程 trace：归一化结果 + 各层决策记录 + 总耗时。
 * 随 API 响应返回并打印日志，用于定位每一层的决策过程。
 */
public class IntentTrace {

    /** 单层决策记录 */
    public static class LayerRecord {
        private final String layer;    // RULE / CLASSIFIER / LLM / CACHE / INHERIT ...
        private final String status;   // hit / miss / disabled / failed / applied
        private final double confidence;
        private final long elapsedMs;
        private final String detail;   // 补充信息（如失败原因、原始输出片段）

        public LayerRecord(String layer, String status, double confidence, long elapsedMs, String detail) {
            this.layer = layer;
            this.status = status;
            this.confidence = confidence;
            this.elapsedMs = elapsedMs;
            this.detail = detail;
        }

        public String getLayer() { return layer; }
        public String getStatus() { return status; }
        public double getConfidence() { return confidence; }
        public long getElapsedMs() { return elapsedMs; }
        public String getDetail() { return detail; }
    }

    private final String normalizedText;
    private final List<LayerRecord> layers = new ArrayList<>();
    private long totalMs;

    public IntentTrace(String normalizedText) {
        this.normalizedText = normalizedText;
    }

    public void addLayer(String layer, String status, double confidence, long elapsedMs, String detail) {
        layers.add(new LayerRecord(layer, status, confidence, elapsedMs, detail));
    }

    public void setTotalMs(long totalMs) { this.totalMs = totalMs; }

    public String getNormalizedText() { return normalizedText; }
    public List<LayerRecord> getLayers() { return layers; }
    public long getTotalMs() { return totalMs; }
}
