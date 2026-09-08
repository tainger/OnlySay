# 迁移 InMemoryEmbeddingStore → PostgreSQL + pgvector

## Context

当前 OnlySay 使用 `InMemoryEmbeddingStore` 存储向量，进程重启即丢失，每次启动都要重新录入样本。本机已安装 PostgreSQL 14 + pgvector 0.8.6，数据库 `onlysay` 和用户已就绪。本次迁移将向量库后端切换为 pgvector，实现样本持久化跨重启保留。

核心设计：**配置开关一键回滚**（`embedding.store=memory|pgvector`，默认 `memory`），与 `ChatModelFactory` 模式对齐，新增 `EmbeddingStoreFactory` 统一构建。

## 修改清单（5 个文件，1 个新文件）

### 1. pom.xml — 添加 2 个依赖

在 `langchain4j-embeddings-bge-small-zh-v15` 之后添加：

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.3</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-pgvector</artifactId>
    <version>${langchain4j.embeddings.version}</version>  <!-- 1.19.0-beta29 -->
</dependency>
```

### 2. application.properties — 添加开关

在 `embedding.model-name` 之后添加：
```properties
# 向量库后端：memory（默认，回滚用）| pgvector（持久化）
embedding.store=memory
```
在 `pg.*` 块中添加表名：
```properties
pg.vector.table=onlysay_embeddings
```

### 3. Config.java — 添加 8 个 getter

- `getEmbeddingStoreType()` → `embedding.store`（默认 `memory`）
- `getPgVectorTable()` → `pg.vector.table`（默认 `onlysay_embeddings`）
- `getEmbeddingDimension()` → 固定返回 `512`（bge-small-zh-v1.5）
- `getPgHost()` / `getPgPort()` / `getPgDatabase()` / `getPgUser()` / `getPgPassword()` → 读取已有 `pg.*` 配置

### 4. 新文件：EmbeddingStoreFactory.java

镜像 [ChatModelFactory](file:///Users/rocky/study/profile/Markdown-Resume/repo/OnlySay/src/main/java/com/onlysay/ChatModelFactory.java) 模式：

- `build()` → 按 `embedding.store` 配置返回 `EmbeddingStore<TextSegment>`（接口类型）
  - `pgvector`：`PgVectorEmbeddingStore.builder().host().port().database().user().password().table().dimension(512).createTable(true).dropTableFirst(false).build()`
  - `memory`：`new InMemoryEmbeddingStore<>()`
- `count(store)` → `instanceof` 分支：InMemory 调 `.size()`，pgvector 走 JDBC `SELECT COUNT(*)`
- `clear(store)` → InMemory 不操作（保持旧行为），pgvector 走 JDBC `TRUNCATE TABLE`

**解决的核心问题**：`EmbeddingStore` 接口没有 `size()` 方法，只有 `InMemoryEmbeddingStore` 有。`count()` 静态方法通过 `instanceof` 分支处理两种实现。

### 5. IngestService.java — 4 处修改

| 位置 | 修改 |
|------|------|
| 字段类型 | `InMemoryEmbeddingStore<TextSegment>` → `EmbeddingStore<TextSegment>` |
| 构造器 | `new InMemoryEmbeddingStore<>()` → `EmbeddingStoreFactory.build()` |
| `ingestSamples()` | 录入前调 `EmbeddingStoreFactory.clear(embeddingStore)`；`embeddingStore.size()` → `EmbeddingStoreFactory.count(embeddingStore)` |
| getter 返回类型 | `InMemoryEmbeddingStore<TextSegment>` → `EmbeddingStore<TextSegment>`；新增 `getRecordCount()` 方法 |

### 6. ApiServer.java — 3 处 `.size()` 替换

所有 `ingestService.getEmbeddingStore().size()` → `ingestService.getRecordCount()`

### 7. GenerateService.java — 无需修改

已使用 `EmbeddingStore<TextSegment>` 接口（[L22](file:///Users/rocky/study/profile/Markdown-Resume/repo/OnlySay/src/main/java/com/onlysay/GenerateService.java#L22)），构造器接收接口类型，无需改动。

## 验证步骤

1. **回滚安全（memory 模式）**：`embedding.store=memory`，`mvn exec:java`，行为与迁移前完全一致
2. **pgvector 模式**：`embedding.store=pgvector`，`mvn exec:java`
   - 首次启动自动建表 `onlysay_embeddings`（含 `embedding vector(512)` 列）
   - `psql -d onlysay -U onlysay -c "SELECT COUNT(*) FROM onlysay_embeddings"` 验证行数
   - `/api/ingest` 录入后 `/api/generate` 正常生成
3. **持久化测试**：重启后端，不调 `/api/ingest` 直接 `/api/generate`，应仍能正常检索生成
4. **重新录入**：再次 `/api/ingest`，`clear()` 先 TRUNCATE 再插入，不产生重复
5. **切回 memory**：改回 `embedding.store=memory` 重启，一切如旧

## 风险

- `dropTableFirst=false` 硬编码，防止重启丢数据
- pgvector 表名冲突：若 `onlysay_embeddings` 已存在不同 schema，首次启动可能失败，需手动 `DROP TABLE`
- `count()`/`clear()` 每次开短连接，MVP 低频录入场景可接受
