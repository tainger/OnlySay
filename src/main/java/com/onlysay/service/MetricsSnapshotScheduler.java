package com.onlysay.service;

import com.onlysay.intent.IntentRecognizer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 意图识别指标快照定时任务（仅 pgvector 持久化模式装配）。
 *
 * 实时指标仍由 IntentMetrics 内存计数（高频写、低开销）；
 * 本任务每 5 分钟把当前聚合快照刷入 intent_metrics_snapshot，
 * 并在应用关闭前补刷一次，用于跨重启看趋势。
 *
 * 间隔可配：onlysay.intent.metrics-snapshot-interval-ms（默认 300000 = 5 分钟）
 */
@Component
@ConditionalOnProperty(name = "onlysay.embedding.store", havingValue = "pgvector")
public class MetricsSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(MetricsSnapshotScheduler.class);

    private final IntentRecognizer intentRecognizer;
    private final IntentPersistenceService persistence;

    public MetricsSnapshotScheduler(IntentRecognizer intentRecognizer,
                                    IntentPersistenceService persistence) {
        this.intentRecognizer = intentRecognizer;
        this.persistence = persistence;
    }

    /** 定时快照：首次延迟一个间隔执行，避免启动时全零快照无意义 */
    @Scheduled(fixedRateString = "${onlysay.intent.metrics-snapshot-interval-ms:300000}",
               initialDelayString = "${onlysay.intent.metrics-snapshot-interval-ms:300000}")
    public void snapshot() {
        persistence.snapshotMetrics(intentRecognizer.getMetrics().snapshot());
        log.debug("意图指标快照已刷入 intent_metrics_snapshot");
    }

    /** 关闭前补刷最后一版（kill 正常退出时生效） */
    @PreDestroy
    public void flushOnShutdown() {
        persistence.snapshotMetrics(intentRecognizer.getMetrics().snapshot());
    }
}
