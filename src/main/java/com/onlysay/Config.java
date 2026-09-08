package com.onlysay;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 配置类：从 application.properties 读取配置
 */
public class Config {

    private static final Properties props = new Properties();

    static {
        try (InputStream is = Config.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (is == null) {
                throw new RuntimeException("找不到 application.properties 配置文件");
            }
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("加载配置文件失败", e);
        }
    }

    public static String getDeepSeekApiKey() {
        // 优先从环境变量读取（更安全），其次从配置文件读取
        String envKey = System.getenv("DEEPSEEK_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey;
        }
        return props.getProperty("deepseek.api-key");
    }

    public static String getDeepSeekBaseUrl() {
        return props.getProperty("deepseek.base-url", "https://api.deepseek.com/v1");
    }

    public static String getDeepSeekModel() {
        // deepseek-chat 已于 2026-07-24 被 DeepSeek 停用，默认迁移到 V4 系列
        return props.getProperty("deepseek.model", "deepseek-v4-flash");
    }

    public static String getEmbeddingModelName() {
        return props.getProperty("embedding.model-name", "bge-small-zh-v1.5");
    }

    public static int getRetrievalMaxResults() {
        return Integer.parseInt(props.getProperty("retrieval.max-results", "3"));
    }

    public static double getRetrievalMinScore() {
        return Double.parseDouble(props.getProperty("retrieval.min-score", "0.5"));
    }

    // ===== 向量库配置 =====

    /** 向量库后端：memory | pgvector（默认 memory，保证回滚） */
    public static String getEmbeddingStoreType() {
        return props.getProperty("embedding.store", "memory");
    }

    /** pgvector 表名 */
    public static String getPgVectorTable() {
        return props.getProperty("pg.vector.table", "onlysay_embeddings");
    }

    /** bge-small-zh-v1.5 输出维度固定 512 */
    public static int getEmbeddingDimension() {
        return 512;
    }

    // ===== PostgreSQL 连接参数（pgvector 用） =====

    public static String getPgHost() {
        return props.getProperty("pg.host", "localhost");
    }

    public static int getPgPort() {
        return Integer.parseInt(props.getProperty("pg.port", "5432"));
    }

    public static String getPgDatabase() {
        return props.getProperty("pg.database", "onlysay");
    }

    public static String getPgUser() {
        return props.getProperty("pg.user", "onlysay");
    }

    public static String getPgPassword() {
        return props.getProperty("pg.password", "");
    }

    // ===== 意图识别配置 =====

    /** 意图识别总开关：false 时 /api/generate 行为与旧版一致（跳过识别直接生成） */
    public static boolean isIntentEnabled() {
        return Boolean.parseBoolean(props.getProperty("intent.enabled", "true"));
    }

    /** 第二级分类器：llm（deepseek-v4-flash 轻量分类）| none（跳过该层） */
    public static String getIntentClassifier() {
        return props.getProperty("intent.classifier", "llm");
    }

    /** 第二级轻量分类模型（非思考模式，快速便宜） */
    public static String getIntentClassifierModel() {
        return props.getProperty("intent.classifier.model", "deepseek-v4-flash");
    }

    /** 第三级 LLM 兜底复核模型（更强，处理 0.60-0.85 模糊区间） */
    public static String getIntentLlmModel() {
        return props.getProperty("intent.llm.model", "deepseek-v4-pro");
    }

    /** 第三级 LLM 兜底每日调用上限（自然日重置） */
    public static int getIntentLlmDailyLimit() {
        return Integer.parseInt(props.getProperty("intent.llm.daily-limit", "5000"));
    }

    /** 识别结果精确匹配缓存容量（LRU） */
    public static int getIntentCacheSize() {
        return Integer.parseInt(props.getProperty("intent.cache.size", "1000"));
    }
}
