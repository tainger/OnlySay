package com.onlysay.service;

import com.onlysay.config.OnlySayProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成服务：用户输入 → 语义检索相似风格样本 → 组装 Prompt → LLM 生成同风格文案。
 *
 * 改造自原 GenerateService：构造器注入 EmbeddingModel/EmbeddingStore/ChatModel Bean，
 * 替代旧版 ChatModelFactory.build(...) 静态调用。
 */
@Service
public class GenerateService {

    private static final Logger log = LoggerFactory.getLogger(GenerateService.class);

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final OnlySayProperties props;

    public GenerateService(@Qualifier("generateChatModel") ChatModel chatModel,
                           EmbeddingModel embeddingModel,
                           EmbeddingStore<TextSegment> embeddingStore,
                           OnlySayProperties props) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.props = props;
        log.info("DeepSeek 对话模型已就绪: {}（思考模式已关闭）", props.getDeepseek().getModel());
    }

    /**
     * 根据用户输入检索风格样本并生成文案（CLI 兼容方法，只返回文本）
     */
    public String generate(String userInput) {
        return generateWithDetails(userInput).getGeneratedText();
    }

    /**
     * 根据用户输入检索风格样本并生成文案，返回完整详情（含检索结果）
     */
    public GenerateResult generateWithDetails(String userInput) {
        log.info("正在检索相关风格样本...");

        // 1. 将用户输入向量化
        Embedding queryEmbedding = embeddingModel.embed(userInput).content();

        // 2. 从向量库检索 Top-K 相似样本
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(props.getRetrieval().getMaxResults())
                .minScore(props.getRetrieval().getMinScore())
                .build();
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

        List<RetrievalDetail> retrievalDetails = new ArrayList<>();
        if (matches.isEmpty()) {
            log.warn("未检索到相似样本，将使用通用风格生成。");
        } else {
            log.info("检索到 {} 条相关风格样本", matches.size());
            for (int i = 0; i < matches.size(); i++) {
                EmbeddingMatch<TextSegment> match = matches.get(i);
                log.info("  {}. 相似度: {}", i + 1, String.format("%.4f", match.score()));
                retrievalDetails.add(new RetrievalDetail(
                        i + 1,
                        match.score(),
                        match.embedded().text()
                ));
            }
        }

        // 3. 组装 Prompt
        String prompt = buildPrompt(userInput, matches);

        // 4. 调用 LLM 生成
        log.info("正在调用 DeepSeek 生成文案...");
        String result = chatModel.chat(prompt);

        log.info("生成完成！");
        return new GenerateResult(result, retrievalDetails);
    }

    /**
     * 构建 Prompt：将检索到的风格样本作为 few-shot 示例
     */
    private String buildPrompt(String userInput, List<EmbeddingMatch<TextSegment>> matches) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位擅长模仿特定博主风格的内容创作助手。\n\n");

        if (!matches.isEmpty()) {
            sb.append("以下是该博主的风格样本，请仔细学习其语气、句式、用词和节奏：\n\n");
            for (int i = 0; i < matches.size(); i++) {
                TextSegment segment = matches.get(i).embedded();
                sb.append("【风格样本 ").append(i + 1).append("】\n");
                sb.append(segment.text()).append("\n\n");
            }
        }

        sb.append("现在，请以该博主的风格，围绕以下主题创作一篇自媒体帖子：\n\n");
        sb.append("【我的事情】\n").append(userInput).append("\n\n");
        sb.append("要求：\n");
        sb.append("1. 保持博主的语气和风格\n");
        sb.append("2. 内容真实自然，有个人视角\n");
        sb.append("3. 长度 100-300 字\n");
        sb.append("4. 直接输出帖子内容，不要加任何说明\n");

        return sb.toString();
    }

    /**
     * 生成结果（含检索详情，供 API 返回）
     */
    public static class GenerateResult {
        private final String generatedText;
        private final List<RetrievalDetail> retrievedSamples;

        public GenerateResult(String generatedText, List<RetrievalDetail> retrievedSamples) {
            this.generatedText = generatedText;
            this.retrievedSamples = retrievedSamples;
        }

        public String getGeneratedText() { return generatedText; }
        public List<RetrievalDetail> getRetrievedSamples() { return retrievedSamples; }
    }

    /**
     * 检索详情（供前端展示）
     */
    public static class RetrievalDetail {
        private final int index;
        private final double score;
        private final String text;

        public RetrievalDetail(int index, double score, String text) {
            this.index = index;
            this.score = score;
            this.text = text;
        }

        public int getIndex() { return index; }
        public double getScore() { return score; }
        public String getText() { return text; }
    }
}
