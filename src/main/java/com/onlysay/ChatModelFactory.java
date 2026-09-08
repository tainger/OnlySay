package com.onlysay;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.Map;

/**
 * ChatModel 工厂：统一构建 DeepSeek（OpenAI 兼容）模型实例。
 * V4 系列默认开启思考模式，所有调用 MUST 显式关闭（thinking.type=disabled），
 * 否则延迟与 token 成本失控（设计决策 D4）。
 */
public final class ChatModelFactory {

    /** 思考模式关闭参数（OpenAI 格式 body 顶层字段，经 customParameters 透传） */
    private static final Map<String, Object> THINKING_DISABLED =
            Map.of("thinking", Map.of("type", "disabled"));

    public static ChatModel build(String modelName, double temperature, Duration timeout) {
        return OpenAiChatModel.builder()
                .apiKey(Config.getDeepSeekApiKey())
                .baseUrl(Config.getDeepSeekBaseUrl())
                .modelName(modelName)
                .temperature(temperature)
                .timeout(timeout)
                .customParameters(THINKING_DISABLED)
                .build();
    }

    private ChatModelFactory() {}
}
