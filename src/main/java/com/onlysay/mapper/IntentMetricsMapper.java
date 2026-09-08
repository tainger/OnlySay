package com.onlysay.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 意图识别指标快照落库 Mapper（跨重启趋势用，进程内实时指标仍走 IntentMetrics 内存计数）。
 */
public interface IntentMetricsMapper {

    /** 写入一版指标快照（layer_hits / intent_counts 为 JSONB） */
    @Insert("""
            INSERT INTO intent_metrics_snapshot
                (total_requests, layer_hits, intent_counts,
                 llm_fallback_rate, clarification_rate, p95_ms, p99_ms)
            VALUES
                (#{totalRequests}, #{layerHitsJson}::jsonb, #{intentCountsJson}::jsonb,
                 #{llmFallbackRate}, #{clarificationRate}, #{p95Ms}, #{p99Ms})
            """)
    void insertSnapshot(@Param("totalRequests") long totalRequests,
                        @Param("layerHitsJson") String layerHitsJson,
                        @Param("intentCountsJson") String intentCountsJson,
                        @Param("llmFallbackRate") double llmFallbackRate,
                        @Param("clarificationRate") double clarificationRate,
                        @Param("p95Ms") long p95Ms,
                        @Param("p99Ms") long p99Ms);
}
