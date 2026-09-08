package com.onlysay.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.Map;

/**
 * ChatModel 装配（替代旧 ChatModelFactory 静态工厂）。
 * V4 系列默认开启思考模式，所有调用 MUST 显式关闭（thinking.type=disabled），
 * 否则延迟与 token 成本失控（设计决策 D4）。
 *
 * 三个 Bean：
 *   - generateChatModel（@Primary）：生成 RAG 文案用，deepseek.model，temperature 0.7，120s
 *   - classifierChatModel：意图第二级分类，deepseek.intent.classifier-model，temperature 0.1，20s
 *   - fallbackChatModel：意图第三级兜底，deepseek.intent.llm-model，temperature 0.1，40s
 */
@Configuration
public class ChatModelConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatModelConfig.class);

    /** application.yml 中的占位符：api-key 仍是它说明环境变量 DEEPSEEK_API_KEY 未注入 */
    private static final String PLACEHOLDER_KEY = "sk-your-deepseek-api-key-here";

    /** 思考模式关闭参数（OpenAI 格式 body 顶层字段，经 customParameters 透传） */
    private static final Map<String, Object> THINKING_DISABLED =
            Map.of("thinking", Map.of("type", "disabled"));

    private final OnlySayProperties props;

    public ChatModelConfig(OnlySayProperties props) {
        this.props = props;
    }

    /**
     * 启动期 fail-fast 检查：key 为空或仍是占位符时打醒目 WARN。
     * 不直接阻止启动（embedding/规则匹配/前端控制台仍可演示），但 LLM 调用必然 401，提前告知原因。
     */
    @PostConstruct
    void validateApiKey() {
        String key = props.getDeepseek().getApiKey();
        if (key == null || key.isBlank() || PLACEHOLDER_KEY.equals(key)) {
            log.warn("==========================================================");
            log.warn("⚠️  DeepSeek API Key 未配置（当前为占位符/空），LLM 调用将返回 401！");
            log.warn("    请设置环境变量后重启：export DEEPSEEK_API_KEY=sk-你的真实key");
            log.warn("    （zsh 用户写入 ~/.zshrc，IDEA 在 Run Configuration 环境变量中添加）");
            log.warn("==========================================================");
        } else {
            log.info("🔑 DeepSeek API Key 已配置：{}...{}",
                    key.substring(0, Math.min(6, key.length())),
                    key.substring(Math.max(0, key.length() - 4)));
        }
    }

    @Bean(name = "generateChatModel")
    @Primary
    public ChatModel generateChatModel(OnlySayProperties props) {
        return build(props.getDeepseek().getApiKey(),
                props.getDeepseek().getBaseUrl(),
                props.getDeepseek().getModel(),
                0.7, Duration.ofSeconds(120));
    }

    /**
     * 第二级轻量分类器 Bean：仅当 intent.classifier=llm 时装配。
     * （intent.enabled=false 时识别路径不被调用，Bean 即使存在也不会被 invoke，足够安全。）
     * 否则容器中不存在，IntentRecognizer 通过 @Autowired(required=false) 注入 null。
     */
    @Bean(name = "classifierChatModel")
    @ConditionalOnProperty(name = "onlysay.intent.classifier", havingValue = "llm", matchIfMissing = true)
    public ChatModel classifierChatModel(OnlySayProperties props) {
        return build(props.getDeepseek().getApiKey(),
                props.getDeepseek().getBaseUrl(),
                props.getIntent().getClassifierModel(),
                0.1, Duration.ofSeconds(20));
    }

    /**
     * 第三级 LLM 兜底 Bean：仅当 intent.enabled=true 时装配。
     */
    @Bean(name = "fallbackChatModel")
    @ConditionalOnProperty(name = "onlysay.intent.enabled", havingValue = "true", matchIfMissing = true)
    public ChatModel fallbackChatModel(OnlySayProperties props) {
        return build(props.getDeepseek().getApiKey(),
                props.getDeepseek().getBaseUrl(),
                props.getIntent().getLlmModel(),
                0.1, Duration.ofSeconds(40));
    }

    private ChatModel build(String apiKey, String baseUrl, String modelName,
                            double temperature, Duration timeout) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(timeout)
                .customParameters(THINKING_DISABLED)
                .build();
    }
}
