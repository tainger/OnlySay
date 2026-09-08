package com.onlysay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 业务配置（prefix = onlysay）。对应 application.yml 中 onlysay.* 节点。
 * 环境变量优先（YAML 中通过 ${VAR:default} 语法）。
 */
@Component
@ConfigurationProperties(prefix = "onlysay")
public class OnlySayProperties {

    private DeepSeek deepseek = new DeepSeek();
    private Embedding embedding = new Embedding();
    private Retrieval retrieval = new Retrieval();
    private Pg pg = new Pg();
    private Intent intent = new Intent();

    public DeepSeek getDeepseek() { return deepseek; }
    public void setDeepseek(DeepSeek deepseek) { this.deepseek = deepseek; }

    public Embedding getEmbedding() { return embedding; }
    public void setEmbedding(Embedding embedding) { this.embedding = embedding; }

    public Retrieval getRetrieval() { return retrieval; }
    public void setRetrieval(Retrieval retrieval) { this.retrieval = retrieval; }

    public Pg getPg() { return pg; }
    public void setPg(Pg pg) { this.pg = pg; }

    public Intent getIntent() { return intent; }
    public void setIntent(Intent intent) { this.intent = intent; }

    public static class DeepSeek {
        private String apiKey = "sk-your-deepseek-api-key-here";
        private String baseUrl = "https://api.deepseek.com/v1";
        private String model = "deepseek-v4-flash";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class Embedding {
        private String modelName = "bge-small-zh-v1.5";
        /** memory | pgvector */
        private String store = "memory";

        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        public String getStore() { return store; }
        public void setStore(String store) { this.store = store; }
    }

    public static class Retrieval {
        private int maxResults = 3;
        private double minScore = 0.5;

        public int getMaxResults() { return maxResults; }
        public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
        public double getMinScore() { return minScore; }
        public void setMinScore(double minScore) { this.minScore = minScore; }
    }

    public static class Pg {
        private String host = "localhost";
        private int port = 5432;
        private String database = "onlysay";
        private String user = "onlysay";
        private String password = "";
        private String vectorTable = "onlysay_embeddings";

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getVectorTable() { return vectorTable; }
        public void setVectorTable(String vectorTable) { this.vectorTable = vectorTable; }
    }

    public static class Intent {
        /** 总开关：false 时 /api/generate 跳过识别直接生成（旧行为，回滚用） */
        private boolean enabled = true;
        /** 第二级分类器：llm | none */
        private String classifier = "llm";
        private String classifierModel = "deepseek-v4-flash";
        private String llmModel = "deepseek-v4-pro";
        private int llmDailyLimit = 5000;
        private int cacheSize = 1000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getClassifier() { return classifier; }
        public void setClassifier(String classifier) { this.classifier = classifier; }
        public String getClassifierModel() { return classifierModel; }
        public void setClassifierModel(String classifierModel) { this.classifierModel = classifierModel; }
        public String getLlmModel() { return llmModel; }
        public void setLlmModel(String llmModel) { this.llmModel = llmModel; }
        public int getLlmDailyLimit() { return llmDailyLimit; }
        public void setLlmDailyLimit(int llmDailyLimit) { this.llmDailyLimit = llmDailyLimit; }
        public int getCacheSize() { return cacheSize; }
        public void setCacheSize(int cacheSize) { this.cacheSize = cacheSize; }
    }
}
