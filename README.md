# OnlySay
一个把自媒体各种人的风格蒸馏成skill， 然后使用者可以发送自己的一些事情，调用这些skill，生成自媒体内容，引入智能体对生成内容进行打分， 使用者挑选好的发送到自媒体，构建影响力

---

## 🎯 当前阶段：最小 MVP RAG Demo

跑通 **"录入博主样本 → 语义检索风格 → LLM 生成同风格文案"** 的最小闭环。

### 技术栈

| 层 | 选型 |
|----|------|
| 语言 | Java 17+ |
| 构建 | Maven |
| RAG 框架 | LangChain4j 1.19.0 |
| 向量库 | InMemoryEmbeddingStore（内存，零配置） |
| Embedding | 本地 ONNX 模型 `bge-small-zh-v1.5`（中文优化，首次运行自动下载） |
| LLM | DeepSeek（OpenAI 兼容 API） |
| 交互 | CLI 命令行 |

### 项目结构

```
OnlySay/
├── pom.xml                              # Maven 依赖
├── samples/
│   └── blogger.md                       # 博主风格样本（占位示例）
├── src/main/java/com/onlysay/
│   ├── OnlySayApplication.java           # CLI 主入口
│   ├── Config.java                      # 配置读取
│   ├── IngestService.java               # 样本录入 + 向量化
│   └── GenerateService.java             # 检索 + LLM 生成
├── src/main/resources/
│   └── application.properties           # 配置文件
└── README.md
```

### 快速开始

#### 1. 配置 DeepSeek API Key

编辑 `src/main/resources/application.properties`，填入你的 DeepSeek API Key：

```properties
deepseek.api-key=sk-your-real-api-key
```

> API Key 获取地址：https://platform.deepseek.com/

#### 2. 编译运行

```bash
cd OnlySay
mvn compile exec:java
```

#### 3. 使用流程

首次运行会自动下载本地 Embedding 模型（ONNX，约 90MB），你会看到下载过程。

```
========================================
   OnlySay - RAG 风格生成 Demo
========================================

----------------------------------------
请选择操作:
  1) 录入博主风格样本
  2) 输入你的事情，生成同风格文案
  3) 退出
请输入选项 (1/2/3): 1
```

**步骤：**
1. 先选 `1` 录入样本（将 `samples/blogger.md` 中的 10 条帖子向量化存入内存）
2. 再选 `2`，输入你想分享的事情，系统会检索最相似的 3 条风格样本，调用 DeepSeek 生成同风格文案
3. 选 `3` 退出

### 替换真实博主样本

编辑 `samples/blogger.md`，按以下格式替换为真实博主的帖子：

```markdown
# 博主风格样本

## 样本 1
第一条帖子的完整内容...

## 样本 2
第二条帖子的完整内容...

## 样本 3
...
```

建议 10-20 条代表性帖子，效果最佳。

### 已知问题与解决方案

#### macOS 上 DJL Native Library 加载失败

**现象**：运行时出现 `Failed to load Huggingface native library` 或 `Unexpected flavor: cpu` 错误。

**原因**：LangChain4j 1.19.0 依赖的 DJL 0.36.0 在部分 macOS 环境（尤其是 x86_64）上 native library 加载有兼容性问题。

**解决方案**：pom.xml 中已通过 `dependencyManagement` 强制将 DJL 降级到 0.29.0，这是社区验证可用的版本。无需额外操作，已内置在依赖配置中。

#### 首次运行需要下载模型

首次运行时，LangChain4j 会自动下载：
1. ONNX Runtime native library
2. `bge-small-zh-v1.5` 模型文件（约 100MB）到 `~/.langchain4j/`

请确保首次运行时网络畅通。

### 后续迭代方向

- [ ] 多博主支持（按博主分 collection）
- [ ] 智能体打分模块
- [ ] 持久化向量库（Chroma/Milvus）
- [ ] Web UI
- [ ] RAG 重排序优化
