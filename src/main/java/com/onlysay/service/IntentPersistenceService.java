package com.onlysay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlysay.intent.IntentResult;
import com.onlysay.intent.IntentTrace;
import com.onlysay.mapper.IntentLogMapper;
import com.onlysay.mapper.IntentMetricsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 意图识别数据落库服务（仅 pgvector 持久化模式装配）。
 *
 * 三类写入：
 *   ① 识别明细 intent_recognition_log —— 每次识别一行（IntentRecognizer.finalize 收口）
 *   ② 修正配对 intent_correction      —— 澄清轮 + 下一轮非澄清配对成功时写一行
 *   ③ 指标快照 intent_metrics_snapshot —— 定时任务每 5 分钟 + 应用关闭前各刷一次
 *
 * 容错原则：落库失败只记 warn 日志、绝不向上抛，DB 故障不阻断识别主链路。
 * JSONL 数据回流（CorrectionRecorder）保留不动，DB 是便于统计查询的镜像。
 */
@Service
@ConditionalOnProperty(name = "onlysay.embedding.store", havingValue = "pgvector")
public class IntentPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(IntentPersistenceService.class);

    private final IntentLogMapper logMapper;
    private final IntentMetricsMapper metricsMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IntentPersistenceService(IntentLogMapper logMapper, IntentMetricsMapper metricsMapper) {
        this.logMapper = logMapper;
        this.metricsMapper = metricsMapper;
    }

    /** ① 识别明细落库 */
    public void logRecognition(String sessionId, String rawInput,
                               IntentResult result, long latencyMs, IntentTrace trace) {
        try {
            String traceJson = objectMapper.writeValueAsString(trace);
            logMapper.insertRecognitionLog(
                    sessionId, rawInput,
                    result.getIntent().name(),
                    result.getHitLayer().name(),
                    result.getConfidence(),
                    latencyMs,
                    traceJson);
        } catch (Exception e) {
            log.warn("识别明细落库失败（不影响主流程）: {}", e.getMessage());
        }
    }

    /** ② 澄清-修正配对落库 */
    public void logCorrection(String sessionId, String clarificationInput,
                              String correctionInput, IntentResult result) {
        try {
            logMapper.insertCorrection(
                    sessionId, clarificationInput, correctionInput,
                    result.getIntent().name(),
                    result.getHitLayer().name());
        } catch (Exception e) {
            log.warn("修正配对落库失败（不影响主流程）: {}", e.getMessage());
        }
    }

    /**
     * ③ 指标快照落库
     * @param snapshot IntentMetrics.snapshot() 产出的聚合 Map
     */
    @SuppressWarnings("unchecked")
    public void snapshotMetrics(Map<String, Object> snapshot) {
        try {
            metricsMapper.insertSnapshot(
                    ((Number) snapshot.getOrDefault("totalRequests", 0)).longValue(),
                    objectMapper.writeValueAsString(snapshot.get("layerHits")),
                    objectMapper.writeValueAsString(snapshot.get("intentCounts")),
                    ((Number) snapshot.getOrDefault("llmFallbackRate", 0.0)).doubleValue(),
                    ((Number) snapshot.getOrDefault("clarificationRate", 0.0)).doubleValue(),
                    ((Number) snapshot.getOrDefault("p95Ms", 0)).longValue(),
                    ((Number) snapshot.getOrDefault("p99Ms", 0)).longValue());
        } catch (Exception e) {
            log.warn("指标快照落库失败（不影响主流程）: {}", e.getMessage());
        }
    }
}
