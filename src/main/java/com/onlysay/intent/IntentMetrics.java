package com.onlysay.intent;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * 识别指标聚合（进程内，重启清零）：
 * 各层命中计数、LLM 兜底率、澄清触发率、识别耗时 P95/P99。
 */
public class IntentMetrics {

    private static final int MAX_LATENCY_SAMPLES = 20000;

    private final Map<HitLayer, LongAdder> layerHits = new EnumMap<>(HitLayer.class);
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder clarifications = new LongAdder();
    private final ArrayDeque<Long> latencySamples = new ArrayDeque<>();

    public IntentMetrics() {
        for (HitLayer layer : HitLayer.values()) {
            layerHits.put(layer, new LongAdder());
        }
    }

    public synchronized void record(IntentResult result, long latencyMs) {
        totalRequests.increment();
        layerHits.get(result.getHitLayer()).increment();
        if (result.getIntent() == IntentType.CLARIFICATION) {
            clarifications.increment();
        }
        latencySamples.addLast(latencyMs);
        while (latencySamples.size() > MAX_LATENCY_SAMPLES) {
            latencySamples.removeFirst();
        }
    }

    public synchronized Map<String, Object> snapshot() {
        long total = totalRequests.sum();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("totalRequests", total);

        Map<String, Long> hits = new LinkedHashMap<>();
        for (Map.Entry<HitLayer, LongAdder> entry : layerHits.entrySet()) {
            hits.put(entry.getKey().name(), entry.getValue().sum());
        }
        snapshot.put("layerHits", hits);

        snapshot.put("llmFallbackRate",
                total == 0 ? 0.0 : (double) layerHits.get(HitLayer.LLM).sum() / total);
        snapshot.put("clarificationRate",
                total == 0 ? 0.0 : (double) clarifications.sum() / total);
        snapshot.put("p95Ms", percentile(0.95));
        snapshot.put("p99Ms", percentile(0.99));
        return snapshot;
    }

    private synchronized long percentile(double p) {
        if (latencySamples.isEmpty()) {
            return 0;
        }
        long[] sorted = latencySamples.stream().mapToLong(Long::longValue).sorted().toArray();
        int index = (int) Math.ceil(p * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }
}
