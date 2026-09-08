package com.onlysay.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 意图识别业务表初始化（仅 pgvector 模式生效）。
 *
 * 不用 spring.sql.init / schema.sql 的原因：本项目 DataSource 始终装配（memory 模式也连 PG 配置），
 * schema.sql 会在 memory 模式下强制建连，PG 不可用时拖垮启动；
 * 这里与 EmbeddingStoreConfig 保持同一开关，只有持久化模式才建表。
 *
 * 表清单：
 *   intent_recognition_log  — 每次识别明细（输入/意图/命中层/置信度/耗时/trace）
 *   intent_correction       — 澄清-修正配对（数据回流，JSONL 的库内镜像，支持 SQL 统计）
 *   intent_metrics_snapshot — 指标定时快照（跨重启趋势）
 */
@Component
@ConditionalOnProperty(name = "onlysay.embedding.store", havingValue = "pgvector")
public class IntentSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IntentSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public IntentSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> ddls = List.of(
                // ① 识别明细：一次识别一行
                """
                CREATE TABLE IF NOT EXISTS intent_recognition_log (
                    id          BIGSERIAL PRIMARY KEY,
                    session_id  VARCHAR(64),
                    user_input  TEXT NOT NULL,
                    intent      VARCHAR(32) NOT NULL,
                    hit_layer   VARCHAR(16) NOT NULL,
                    confidence  DOUBLE PRECISION,
                    latency_ms  BIGINT,
                    trace       JSONB,
                    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """,
                "CREATE INDEX IF NOT EXISTS idx_intent_log_created ON intent_recognition_log(created_at)",
                "CREATE INDEX IF NOT EXISTS idx_intent_log_intent  ON intent_recognition_log(intent)",

                // ② 澄清-修正配对：一轮澄清 + 下一轮修正 = 一行
                """
                CREATE TABLE IF NOT EXISTS intent_correction (
                    id                   BIGSERIAL PRIMARY KEY,
                    session_id           VARCHAR(64),
                    clarification_input  TEXT NOT NULL,
                    correction_input     TEXT NOT NULL,
                    final_intent         VARCHAR(32) NOT NULL,
                    final_hit_layer      VARCHAR(16) NOT NULL,
                    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """,
                "CREATE INDEX IF NOT EXISTS idx_intent_correction_created ON intent_correction(created_at)",

                // ③ 指标快照：定时刷入，跨重启看趋势
                """
                CREATE TABLE IF NOT EXISTS intent_metrics_snapshot (
                    id                 BIGSERIAL PRIMARY KEY,
                    total_requests     BIGINT,
                    layer_hits         JSONB,
                    intent_counts      JSONB,
                    llm_fallback_rate  DOUBLE PRECISION,
                    clarification_rate DOUBLE PRECISION,
                    p95_ms             BIGINT,
                    p99_ms             BIGINT,
                    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """
        );

        for (String ddl : ddls) {
            jdbcTemplate.execute(ddl);
        }
        log.info("🗄️ 意图识别业务表已就绪（intent_recognition_log / intent_correction / intent_metrics_snapshot）");
    }
}
