package com.onlysay.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Embedding 模型与向量库装配（替代旧 EmbeddingStoreFactory 静态工厂）。
 *
 * 设计：
 *   - EmbeddingModel 始终是 BgeSmallZhV15EmbeddingModel（中文优化，首次运行自动下载 ONNX）
 *   - EmbeddingStore 二选一：memory（默认，进程内，重启丢） / pgvector（PostgreSQL + pgvector，持久化）
 *   - 切换由 onlysay.embedding.store 配置项控制（一键回滚）
 *
 * count()/clear() 接口未暴露的辅助 SQL 由 VectorStatsMapper 接管，不在本类。
 */
@Configuration
public class EmbeddingStoreConfig {

    /**
     * 向量库（内存模式）：默认装配。仅当 onlysay.embedding.store=memory 或缺省时生效。
     */
    @Bean
    @ConditionalOnProperty(name = "onlysay.embedding.store", havingValue = "memory", matchIfMissing = true)
    public EmbeddingStore<TextSegment> inMemoryEmbeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    /**
     * 向量库（pgvector 模式）：仅当 onlysay.embedding.store=pgvector 时装配。
     * createTable=true 首次启动自动建表；dropTableFirst=false 重启不丢数据。
     */
    @Bean
    @ConditionalOnProperty(name = "onlysay.embedding.store", havingValue = "pgvector")
    public EmbeddingStore<TextSegment> pgVectorEmbeddingStore(OnlySayProperties props) {
        OnlySayProperties.Pg pg = props.getPg();
        return PgVectorEmbeddingStore.builder()
                .host(pg.getHost())
                .port(pg.getPort())
                .database(pg.getDatabase())
                .user(pg.getUser())
                .password(pg.getPassword())
                .table(pg.getVectorTable())
                .dimension(512)   // bge-small-zh-v1.5 固定 512 维
                .createTable(true)
                .dropTableFirst(false)
                .build();
    }

    /**
     * 本地 Embedding 模型（ONNX，首次运行自动下载到 ~/.langchain4j/）。
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new BgeSmallZhV15EmbeddingModel();
    }
}
