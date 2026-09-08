package com.onlysay.service;

import com.onlysay.config.OnlySayProperties;
import com.onlysay.mapper.VectorStatsMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 样本录入服务：读取博主样本 → 切分 → 向量化 → 存入向量库。
 * 向量库后端由 onlysay.embedding.store 配置决定（memory | pgvector）。
 *
 * 改造自原 IngestService：构造器注入 EmbeddingModel/EmbeddingStore/Mapper/Properties，
 * 替代旧版 new BgeSmallZhV15EmbeddingModel() + EmbeddingStoreFactory.build() 静态调用。
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final VectorStatsMapper vectorStatsMapper;
    private final OnlySayProperties props;

    public IngestService(EmbeddingModel embeddingModel,
                         EmbeddingStore<TextSegment> embeddingStore,
                         VectorStatsMapper vectorStatsMapper,
                         OnlySayProperties props) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.vectorStatsMapper = vectorStatsMapper;
        this.props = props;
        log.info("Embedding 模型就绪！向量库后端: {}", props.getEmbedding().getStore());
    }

    /**
     * 从 samples/blogger.md 读取样本，解析每条样本，向量化并存储
     */
    public void ingestSamples(String samplesPath) {
        log.info("开始录入博主风格样本... 样本文件: {}", samplesPath);

        Path path = Paths.get(samplesPath);
        if (!Files.exists(path)) {
            throw new RuntimeException("样本文件不存在: " + samplesPath);
        }

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);

            List<String> samples = parseSamples(content);
            log.info("解析到 {} 条样本", samples.size());

            List<TextSegment> segments = new ArrayList<>();
            for (int i = 0; i < samples.size(); i++) {
                String sample = samples.get(i);
                String metadata = "样本 " + (i + 1);
                TextSegment segment = TextSegment.from(sample, Metadata.from("source", metadata));
                segments.add(segment);
                log.info("  [{}] 长度: {} 字", metadata, sample.length());
            }

            log.info("正在向量化 {} 条样本...", segments.size());
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            log.info("向量化完成！每条向量维度: {}", embeddings.get(0).dimension());

            log.info("清空旧向量数据（若存在）...");
            clearEmbeddingStore();
            for (int i = 0; i < embeddings.size(); i++) {
                embeddingStore.add(embeddings.get(i), segments.get(i));
            }
            log.info("录入完成！向量库中共 {} 条记录。", getRecordCount());

        } catch (IOException e) {
            throw new RuntimeException("读取样本文件失败", e);
        }
    }

    /**
     * 按 "## 样本" 分割文本，提取每条样本内容
     */
    private List<String> parseSamples(String content) {
        List<String> samples = new ArrayList<>();
        String[] lines = content.split("\n");
        StringBuilder current = new StringBuilder();
        boolean inSample = false;

        for (String line : lines) {
            if (line.trim().startsWith("## 样本")) {
                if (inSample && current.length() > 0) {
                    samples.add(current.toString().trim());
                }
                current = new StringBuilder();
                inSample = true;
            } else if (inSample) {
                // 跳过注释行
                if (line.trim().startsWith("# ") && !line.trim().startsWith("##")) {
                    continue;
                }
                current.append(line).append("\n");
            }
        }
        if (inSample && current.length() > 0) {
            samples.add(current.toString().trim());
        }
        return samples;
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    public EmbeddingStore<TextSegment> getEmbeddingStore() {
        return embeddingStore;
    }

    /**
     * 当前向量库记录数：
     *   - InMemoryEmbeddingStore：直接 size()
     *   - pgvector：通过 VectorStatsMapper SELECT COUNT(*)
     */
    public int getRecordCount() {
        if (embeddingStore instanceof InMemoryEmbeddingStore) {
            return ((InMemoryEmbeddingStore<TextSegment>) embeddingStore).size();
        }
        return vectorStatsMapper.countByTable(props.getPg().getVectorTable());
    }

    /**
     * 清空向量库（重新录入前调用）：
     *   - InMemory：保持旧版行为（append，进程重启即清）
     *   - pgvector：TRUNCATE 表
     */
    private void clearEmbeddingStore() {
        if (embeddingStore instanceof InMemoryEmbeddingStore) {
            // 与旧版一致：InMemory 不主动清空
            return;
        }
        vectorStatsMapper.truncateByTable(props.getPg().getVectorTable());
    }
}
