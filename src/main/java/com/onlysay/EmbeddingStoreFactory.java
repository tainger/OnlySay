package com.onlysay;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * EmbeddingStore 工厂：按配置项 embedding.store 切换后端。
 * - memory：InMemoryEmbeddingStore（默认，进程内，重启丢失，用于回滚）
 * - pgvector：PgVectorEmbeddingStore（PostgreSQL + pgvector，持久化）
 *
 * 设计与 ChatModelFactory 对齐：final 类 + private 构造器 + 静态 build()。
 * count() / clear() 用于解决 EmbeddingStore 接口无 size()/clear() 的问题。
 */
public final class EmbeddingStoreFactory {

    /**
     * 按配置构建向量库实例。
     * pgvector 模式下 createTable=true、dropTableFirst=false：
     *   - 首次启动自动建表（若用户有 CREATE 权限还会自动 CREATE EXTENSION vector）
     *   - 重启不丢数据（持久化的核心价值）
     */
    public static EmbeddingStore<TextSegment> build() {
        String type = Config.getEmbeddingStoreType();
        switch (type) {
            case "pgvector":
                return PgVectorEmbeddingStore.builder()
                        .host(Config.getPgHost())
                        .port(Config.getPgPort())
                        .database(Config.getPgDatabase())
                        .user(Config.getPgUser())
                        .password(Config.getPgPassword())
                        .table(Config.getPgVectorTable())
                        .dimension(Config.getEmbeddingDimension()) // bge-small-zh-v1.5 = 512
                        .createTable(true)     // 首次自动建表
                        .dropTableFirst(false)  // 重启不丢数据
                        .build();
            case "memory":
            default:
                return new InMemoryEmbeddingStore<>();
        }
    }

    /**
     * 返回向量库当前记录数。
     * EmbeddingStore 接口无 size()，需按具体实现分支处理：
     *   - InMemoryEmbeddingStore：直接调用其 size()
     *   - PgVectorEmbeddingStore：通过 JDBC 执行 SELECT COUNT(*)
     */
    public static int count(EmbeddingStore<TextSegment> store) {
        if (store instanceof InMemoryEmbeddingStore) {
            return ((InMemoryEmbeddingStore<TextSegment>) store).size();
        }
        // pgvector：直接查表行数
        return countPgVector();
    }

    /**
     * 清空向量库（用于 /api/ingest 重新录入前避免重复）。
     * - InMemoryEmbeddingStore：保持现有行为（append，不清空），与旧版一致
     * - PgVectorEmbeddingStore：TRUNCATE 表，确保重新录入不产生重复向量
     */
    public static void clear(EmbeddingStore<TextSegment> store) {
        if (store instanceof InMemoryEmbeddingStore) {
            // 旧版行为：InMemory 重启即清空，进程内重复录入会追加。
            // 保持不变，避免破坏 GenerateService 持有的引用。
            return;
        }
        // pgvector：TRUNCATE 清空表
        truncatePgVector();
    }

    // ===== 内部 JDBC 工具方法 =====

    private static int countPgVector() {
        String sql = "SELECT COUNT(*) FROM " + Config.getPgVectorTable();
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl(), Config.getPgUser(), Config.getPgPassword());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            throw new RuntimeException("查询 pgvector 记录数失败: " + e.getMessage(), e);
        }
    }

    private static void truncatePgVector() {
        String sql = "TRUNCATE TABLE " + Config.getPgVectorTable();
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl(), Config.getPgUser(), Config.getPgPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        } catch (Exception e) {
            throw new RuntimeException("清空 pgvector 表失败: " + e.getMessage(), e);
        }
    }

    private static String jdbcUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s",
                Config.getPgHost(), Config.getPgPort(), Config.getPgDatabase());
    }

    private EmbeddingStoreFactory() {}
}
