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
        return props.getProperty("deepseek.model", "deepseek-chat");
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
}
