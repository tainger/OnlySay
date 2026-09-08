package com.onlysay;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 样本录入服务：读取博主样本 → 切分 → 向量化 → 存入向量库
 * 向量库后端由 embedding.store 配置决定（memory | pgvector）
 */
public class IngestService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public IngestService() {
        System.out.println("========================================");
        System.out.println("初始化本地 Embedding 模型...");
        System.out.println("首次运行会自动下载 ONNX 模型到 ~/.langchain4j/");
        System.out.println("========================================");
        this.embeddingModel = new BgeSmallZhV15EmbeddingModel();
        this.embeddingStore = EmbeddingStoreFactory.build();
        System.out.println("Embedding 模型就绪！向量库后端: " + Config.getEmbeddingStoreType() + "\n");
    }

    /**
     * 从 samples/blogger.md 读取样本，解析每条样本，向量化并存储
     */
    public void ingestSamples(String samplesPath) {
        System.out.println("开始录入博主风格样本...");
        System.out.println("样本文件: " + samplesPath);

        Path path = Paths.get(samplesPath);
        if (!Files.exists(path)) {
            throw new RuntimeException("样本文件不存在: " + samplesPath);
        }

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);

            // 按 "## 样本" 分割成独立帖子
            List<String> samples = parseSamples(content);
            System.out.println("解析到 " + samples.size() + " 条样本\n");

            List<TextSegment> segments = new ArrayList<>();
            for (int i = 0; i < samples.size(); i++) {
                String sample = samples.get(i);
                String metadata = "样本 " + (i + 1);
                TextSegment segment = TextSegment.from(sample,
                        Metadata.from("source", metadata));
                segments.add(segment);
                System.out.println("  [" + metadata + "] 长度: " + sample.length() + " 字");
            }

            // 批量向量化
            System.out.println("\n正在向量化 " + segments.size() + " 条样本...");
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            System.out.println("向量化完成！每条向量维度: " + embeddings.get(0).dimension());

            // 存入向量库
            System.out.println("清空旧向量数据（若存在）...");
            EmbeddingStoreFactory.clear(embeddingStore);
            for (int i = 0; i < embeddings.size(); i++) {
                embeddingStore.add(embeddings.get(i), segments.get(i));
            }
            System.out.println("\n✅ 录入完成！向量库中共 " + EmbeddingStoreFactory.count(embeddingStore) + " 条记录。");

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

    /** 当前向量库记录数（封装 EmbeddingStoreFactory.count，供 ApiServer 调用） */
    public int getRecordCount() {
        return EmbeddingStoreFactory.count(embeddingStore);
    }
}
