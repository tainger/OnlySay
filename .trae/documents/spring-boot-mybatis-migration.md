# 迁移方案：Javalin → Spring Boot + MyBatis + PgVectorEmbeddingStore

## Context（背景）

当前 OnlySay 是一个 Javalin + 静态工厂 + 原生 JDBC 的轻量栈，配置由静态 `Config` 类管理，服务在 `ApiServer.main()` 中手动 `new` 装配。随着业务表（修正样本回流、博主/用户/打分等）即将增加，静态装配与原生 JDBC 已到瓶颈：DI 缺失让单元测试困难，原生 JDBC 写多表 CRUD 易出错。

本次迁移目标：**用 Spring Boot 3.x 替换 Javalin + 手工装配，用 MyBatis 接管非向量 SQL（count/truncate 及后续业务表），PgVectorEmbeddingStore 仍负责向量读写**。改造遵循 project memory 中"最小复杂度、易回滚"原则——`embedding.store=memory` 仍可一键回滚到内存模式，`intent.enabled=false` 仍可旁路意图识别。

## 架构对比

| 维度 | 现状 | 迁移后 |
|------|------|--------|
| Web 框架 | Javalin 6.3.0 | Spring Boot 3.3.x（embedded Tomcat） |
| 装配 | 静态 main + new | Spring DI（@Component/@Service/@Configuration） |
| 配置 | 静态 Config + Properties | @ConfigurationProperties Bean |
| JDBC | DriverManager + 原生 PreparedStatement | MyBatis-Spring-Boot-Starter（@Mapper） |
| 向量库 | PgVectorEmbeddingStore | **不变**（仅改为 @Bean 装配） |
| JSON | Jackson 手工 mapper | Spring Boot 自带 Jackson |
| 日志 | slf4j-simple | Spring Boot Logback |
| 启动 | exec-maven-plugin | spring-boot-maven-plugin |
| 前端 | Vite 8（无 proxy） | **不变**（仍直连 :8080） |

## 1. pom.xml 依赖变更

**移除**：
- `io.javalin:javalin`（被 spring-boot-starter-web 取代）
- `com.fasterxml.jackson.core:jackson-databind`（Spring Boot 内置）
- `org.slf4j:slf4j-simple`（Spring Boot 内置 Logback）
- `org.codehaus.mojo:exec-maven-plugin`（被 spring-boot-maven-plugin 取代）

**保留**：
- `langchain4j` / `langchain4j-open-ai` / `langchain4j-embeddings-bge-small-zh-v15` / `langchain4j-pgvector`（核心 RAG 与向量库）
- `org.postgresql:postgresql`（Spring Boot DataSource 与 PgVectorEmbeddingStore 共用）
- `org.junit.jupiter:junit-jupiter`（测试）

**新增**：
- `spring-boot-starter-parent`（BOM，统一版本管理）
- `spring-boot-starter-web`
- `spring-boot-starter-test`
- `mybatis-spring-boot-starter` 3.0.3+（兼容 Spring Boot 3.x）
- `spring-boot-starter-jdbc`（DataSource 自动配置，PgVectorEmbeddingStore 不直接用但 Mapper 用）

**保留 DJL 版本覆盖**：`dependencyManagement` 中 `ai.djl:api` 与 `ai.djl.huggingface:tokenizers` 强制 0.29.0 不变（macOS native library 兼容性补丁，见 README 已知问题）。

**构建插件**：`spring-boot-maven-plugin` 替换 exec-maven-plugin。

## 2. 配置文件：application.properties → application.yml

为可读性改用 YAML，配置 key 加 `onlysay.` 前缀（Spring Boot 命名规范）。

```yaml
spring:
  application:
    name: onlysay
  datasource:
    url: jdbc:postgresql://localhost:5432/onlysay
    username: onlysay
    password: onlysay_dev_2026
    driver-class-name: org.postgresql.Driver
  profiles:
    active: web  # 默认 Web 模式；CLI 用 --spring.profiles.active=cli

mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true

onlysay:
  deepseek:
    api-key: ${DEEPSEEK_API_KEY:sk-your-key-here}
    base-url: https://api.deepseek.com/v1
    model: deepseek-v4-flash
  embedding:
    model-name: bge-small-zh-v1.5
    store: memory                # memory | pgvector
  retrieval:
    max-results: 3
    min-score: 0.5
  pg:
    host: localhost
    port: 5432
    database: onlysay
    user: onlysay
    password: onlysay_dev_2026
    vector-table: onlysay_embeddings
  intent:
    enabled: true
    classifier: llm
    classifier-model: deepseek-v4-flash
    llm-model: deepseek-v4-pro
    llm-daily-limit: 5000
    cache-size: 1000
```

## 3. 新增/修改文件清单

```
src/main/java/com/onlysay/
├── OnlySayWebApplication.java           [新增] @SpringBootApplication 主入口
├── OnlySayCliApplication.java           [重命名自 OnlySayApplication.java] @Profile("cli") CommandLineRunner
├── config/
│   └── OnlySayProperties.java           [新增] @ConfigurationProperties(prefix="onlysay")
├── web/
│   └── ApiController.java               [新增] @RestController，迁移 ApiServer 全部 handler
├── config/
│   ├── ChatModelConfig.java             [重命名自 ChatModelFactory.java] @Configuration，3 个 ChatModel @Bean
│   ├── EmbeddingStoreConfig.java        [重命名自 EmbeddingStoreFactory.java] @Configuration，条件 @Bean
│   └── WebConfig.java                   [新增] @Configuration，CORS 与静态资源配置
├── mapper/
│   └── VectorStatsMapper.java           [新增] @Mapper，count/truncate
├── service/
│   ├── IngestService.java               [改为 @Service，构造器注入]
│   └── GenerateService.java             [改为 @Service，构造器注入]
└── intent/                              [全部改为 @Component + 构造器注入 OnlySayProperties]
    ├── IntentRecognizer.java           【改造模式见第 6 节】
    ├── IntentRegistry.java
    ├── RuleIntentMatcher.java
    ├── IntentClassifier.java            (接口/枚举保持)
    ├── LlmIntentClassifier.java        @Component
    ├── LlmFallbackRecognizer.java       @Component
    ├── IntentValidator.java            静态工具不变
    ├── DialogueState.java              非单例（每会话），保留 new()
    ├── TextNormalizer.java              静态工具不变
    ├── IntentCache.java                非单例（per-instance），由 IntentRecognizer 构造
    ├── DailyRateLimiter.java            同上
    ├── IntentMetrics.java              @Component（单例）
    └── CorrectionRecorder.java         @Component
```

**删除**：
- `src/main/java/com/onlysay/ApiServer.java`（逻辑迁入 `web/ApiController.java`）
- `src/main/java/com/onlysay/Config.java`（职责迁入 `config/OnlySayProperties.java`）

## 4. 入口与启动模式

### 4.1 Web 主入口 `OnlySayWebApplication.java`

```java
@SpringBootApplication
@MapperScan("com.onlysay.mapper")
public class OnlySayWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlySayWebApplication.class, args);
    }
}
```

启动命令：`mvn spring-boot:run` 或 `mvn compile && java -jar target/onlysay-rag-0.1.0.jar`

### 4.2 CLI 模式 `OnlySayCliApplication.java`

```java
@Component
@Profile("cli")
public class OnlySayCliApplication implements CommandLineRunner {
    private final IngestService ingestService;
    private final GenerateService generateService;
    // 构造器注入，run() 方法保留原 Scanner 菜单逻辑
}
```

启动命令：`mvn spring-boot:run -Dspring-boot.run.profiles=cli`

### 4.3 ApiServer.java 删除

原 `ApiServer.main()` 中所有 Javalin 路由迁移到 `ApiController`，见下。

## 5. 各层迁移细则

### 5.1 配置层：`OnlySayProperties`

```java
@Component
@ConfigurationProperties(prefix = "onlysay")
public class OnlySayProperties {
    private DeepSeek deepseek = new DeepSeek();
    private Embedding embedding = new Embedding();
    private Retrieval retrieval = new Retrieval();
    private Pg pg = new Pg();
    private Intent intent = new Intent();
    // 嵌套静态类 + getter/setter，对应 YAML 结构
    // DeepSeek.apiKey 等
    // Intent.enabled / classifier / classifierModel / llmModel / llmDailyLimit / cacheSize
}
```

环境变量优先：`${DEEPSEEK_API_KEY:默认值}` 在 YAML 中已处理，无需 Java 代码特殊逻辑。

### 5.2 Web 层：`ApiController`

将 `ApiServer` 的 5 个 static handler 改为 `@RestController` 方法，保留 URL 路径不变（前端零改动）：

```java
@RestController
@RequestMapping("/api")
public class ApiController {
    private final IngestService ingestService;
    private final GenerateService generateService;
    private final IntentRecognizer intentRecognizer;
    private final OnlySayProperties props;
    private final ObjectMapper mapper;  // Spring Boot 注入

    @GetMapping("/health")
    public Map<String, Object> health() { ... }

    @PostMapping("/ingest")
    public Map<String, Object> ingest() { ... }

    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody Map<String, String> body) { ... }

    @PostMapping("/intent")
    public Map<String, Object> intent(@RequestBody Map<String, String> body) { ... }

    @GetMapping("/intent/stats")
    public Map<String, Object> intentStats() { ... }

    private Map<String, Object> baseResponse(IntentResult r, IntentTrace t) { ... }
}
```

错误处理用 `@RestControllerAdvice` + `@ExceptionHandler(Exception.class)` 统一兜底，替代每条 handler 内的 try-catch 套路（保持原响应结构 `{success:false, message:...}`）。

CORS：在 `WebConfig.java` 用 `WebMvcConfigurer.addCorsMappings` 注册 `/api/**`，等价 Javalin 现有 CORS 配置。

### 5.3 服务层：`IngestService` / `GenerateService`

```java
@Service
public class IngestService {
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final OnlySayProperties props;

    public IngestService(EmbeddingModel embeddingModel,
                         EmbeddingStore<TextSegment> embeddingStore,
                         OnlySayProperties props) { ... }
    // ingestSamples / getRecordCount / getEmbeddingStore / getEmbeddingModel 不变
}

@Service
public class GenerateService {
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatModel chatModel;          // generateModel bean
    private final OnlySayProperties props;
    // generateWithDetails / generate 不变
}
```

### 5.4 工厂层 → @Configuration

#### `ChatModelConfig`（原 ChatModelFactory）

```java
@Configuration
public class ChatModelConfig {
    @Bean(name = "generateChatModel")
    @ConditionalOnMissingBean
    public ChatModel generateChatModel(OnlySayProperties p) {
        return OpenAiChatModel.builder()
            .apiKey(p.getDeepseek().getApiKey())
            .baseUrl(p.getDeepseek().getBaseUrl())
            .modelName(p.getDeepseek().getModel())
            .build();
    }

    @Bean(name = "classifierChatModel")
    @ConditionalOnProperty(name = "onlysay.intent.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(name = "onlysay.intent.classifier", havingValue = "llm")
    public ChatModel classifierChatModel(OnlySayProperties p) { /* v4-flash */ }

    @Bean(name = "fallbackChatModel")
    @ConditionalOnProperty(name = "onlysay.intent.enabled", havingValue = "true", matchIfMissing = true)
    public ChatModel fallbackChatModel(OnlySayProperties p) { /* v4-pro */ }
}
```

> ⚠️ Spring Boot 不允许多个 @ConditionalOnProperty 同方法叠加，需用 @Conditional 拼合或拆为多个 @Bean 方法。实施时按需调整。

#### `EmbeddingStoreConfig`（原 EmbeddingStoreFactory）

```java
@Configuration
public class EmbeddingStoreConfig {
    @Bean
    @ConditionalOnProperty(name = "onlysay.embedding.store", havingValue = "memory", matchIfMissing = true)
    public EmbeddingStore<TextSegment> inMemoryStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    @ConditionalOnProperty(name = "onlysay.embedding.store", havingValue = "pgvector")
    public EmbeddingStore<TextSegment> pgVectorStore(OnlySayProperties p) {
        Pg p2 = p.getPg();
        return PgVectorEmbeddingStore.builder()
            .host(p2.getHost()).port(p2.getPort())
            .database(p2.getDatabase()).user(p2.getUser()).password(p2.getPassword())
            .table(p2.getVectorTable())
            .dimension(512)
            .createTable(true).dropTableFirst(false)
            .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(OnlySayProperties p) {
        return new BgeSmallZhV15EmbeddingModel();
    }
}
```

#### `count()` / `clear()` 改用 Mapper

原 `EmbeddingStoreFactory` 中的 `countPgVector()` / `truncatePgVector()` JDBC 代码删除，由 `VectorStatsMapper` 接管：

```java
@Mapper
public interface VectorStatsMapper {
    @Select("SELECT COUNT(*) FROM ${table}")
    int countByTable(@Param("table") String table);

    @Update("TRUNCATE TABLE ${table}")
    void truncateByTable(@Param("table") String table);
}
```

> `${table}` 用于标识符注入（#{} 不支持表名参数）。值来自配置项，可信；如担心可加白名单校验。

服务层调用方改为：

```java
private final VectorStatsMapper vectorStatsMapper;
private final String vectorTable;  // 从 props.pg.vector-table 注入

public int getRecordCount() {
    if (embeddingStore instanceof InMemoryEmbeddingStore) {
        return ((InMemoryEmbeddingStore<TextSegment>) embeddingStore).size();
    }
    return vectorStatsMapper.countByTable(vectorTable);
}
```

### 5.5 intent 包：DI 改造模式（适用 12 个类）

`IntentRecognizer` 现状：构造器内部 `new` 6 个依赖，并直接静态调用 `Config.getXxx()`。

**改造模式**：

```java
@Component
public class IntentRecognizer {
    private final RuleIntentMatcher ruleMatcher;
    private final IntentClassifier classifier;        // 可为 null
    private final LlmFallbackRecognizer fallbackRecognizer;
    private final IntentCache cache;
    private final DailyRateLimiter rateLimiter;
    private final DialogueState dialogueState = new DialogueState();
    private final CorrectionRecorder correctionRecorder;
    private final IntentMetrics metrics;

    // Spring 主构造器：注入 OnlySayProperties 决定是否注入 classifier
    public IntentRecognizer(
            @Autowired(required = false) IntentClassifier classifier,
            LlmFallbackRecognizer fallbackRecognizer,
            IntentMetrics metrics,
            CorrectionRecorder correctionRecorder,
            OnlySayProperties props) {
        this.classifier = classifier;
        this.fallbackRecognizer = fallbackRecognizer;
        this.metrics = metrics;
        this.correctionRecorder = correctionRecorder;
        this.ruleMatcher = new RuleIntentMatcher();
        this.cache = new IntentCache(props.getIntent().getCacheSize());
        this.rateLimiter = new DailyRateLimiter(props.getIntent().getLlmDailyLimit());
    }

    // 保留测试全参构造器（包级可见，不加 @Autowired）
    IntentRecognizer(IntentClassifier classifier, LlmFallbackRecognizer f,
                     IntentCache c, DailyRateLimiter r, CorrectionRecorder cr) { ... }
}
```

**其他类改造**（统一模式）：
- `LlmIntentClassifier` → `@Component`，构造器注入 `classifierChatModel` Bean（用 `@Qualifier`）
- `LlmFallbackRecognizer` → `@Component`，构造器注入 `fallbackChatModel` Bean
- `IntentRegistry` → `@Component`（注册表数据静态初始化，无外部依赖）
- `RuleIntentMatcher` → 可保持为 `new`，或转 `@Component`
- `IntentMetrics` → `@Component`（单例，指标聚合）
- `CorrectionRecorder` → `@Component`，构造器注入 props 拿文件路径
- `IntentValidator` / `TextNormalizer` / `DialogueState` / `IntentCache` / `DailyRateLimiter`：保持现状（静态工具或 per-instance），由 `IntentRecognizer` 内部 new

> 关键点：`IntentClassifier` 是 SPI 接口。`LlmIntentClassifier` 加 `@ConditionalOnProperty(name="onlysay.intent.classifier", havingValue="llm")`，关闭时容器中不存在，`@Autowired(required=false)` 注入 null，等价旧版 `classifier == null` 行为。

### 5.6 CLI 模式

原 `OnlySayApplication.java` 重命名为 `OnlySayCliApplication.java`，转 `@Component @Profile("cli") CommandLineRunner`，`run()` 方法保留 Scanner 菜单逻辑，依赖通过构造器注入。

`application.yml` 默认 `profiles.active=web`，CLI 通过命令行覆盖。

## 6. 前端改动

**零改动**。理由：
- vite.config.js 未配 proxy（直连后端），前端通过 `http://localhost:8080/api/*` 调用，端口不变
- API 路径与响应结构保持不变
- CORS 在后端 `WebConfig` 中重配

## 7. 验证步骤

### 7.1 编译与启动
```bash
mvn clean compile          # 编译通过
mvn spring-boot:run        # 启动 Web 模式
mvn spring-boot:run -Dspring-boot.run.profiles=cli  # CLI 模式
```

### 7.2 端到端 API 验证
```bash
curl http://localhost:8080/api/health
curl -X POST http://localhost:8080/api/ingest
curl -X POST http://localhost:8080/api/intent -H "Content-Type: application/json" -d '{"userInput":"写一篇小红书"}'
curl -X POST http://localhost:8080/api/generate -H "Content-Type: application/json" -d '{"userInput":"今天去爬山"}'
curl http://localhost:8080/api/intent/stats
```

### 7.3 切换向量库
修改 `application.yml`：`onlysay.embedding.store: pgvector`，重启，重跑上述 curl。验证：
- 表自动建出
- ingest 后 `SELECT COUNT(*) FROM onlysay_embeddings` 返回 10
- VectorStatsMapper.countByTable() 返回正确数

### 7.4 前端验证
```bash
cd frontend && npm run dev
```
访问 http://localhost:5173，KPI 卡、趋势图、调试会话均正常。

### 7.5 回滚验证
- `onlysay.embedding.store: memory` → 回到内存向量库
- `onlysay.intent.enabled: false` → /api/generate 旁路意图识别
两者均不动业务代码，仅配置切换。

### 7.6 测试
现有单测保留；新增 `VectorStatsMapperTest`（@MybatisTest 切片测试，验证 count/truncate SQL 正确）。`IntentRecognizer` 的测试全参构造器仍可用，DI 不破坏测试。

## 8. 风险与注意事项

| 风险 | 缓解 |
|------|------|
| Spring Boot 3.x 要求 Java 17+ | 项目已是 Java 17，✅ |
| DJL 0.29.0 与 Spring Boot 依赖冲突 | dependencyManagement 强制版本，验证 mvn dependency:tree |
| PgVectorEmbeddingStore 内部自管 Connection，不共用 Spring DataSource | 不影响——它只是 Spring 容器中的一个 Bean，DataSource 用于 Mapper |
| ${table} SQL 注入 | 表名来自配置文件，可信；如担心加白名单校验 |
| intent 包 12 个类全改 DI 量大 | 分批改造：先 IntentRecognizer 主类，子组件按模式批量套用 |
| ChatModel Bean 命名冲突 | 用 @Qualifier 显式区分 generateChatModel/classifierChatModel/fallbackChatModel |
