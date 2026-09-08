package com.onlysay.intent;

import dev.langchain4j.model.chat.ChatModel;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 测试用假 ChatModel：返回预置响应队列（可注入多个响应模拟重试），
 * 并记录收到的 Prompt 以便断言调用次数。
 */
public final class FakeChatModel implements ChatModel {

    private final Deque<String> responses = new ArrayDeque<>();
    private final Deque<String> prompts = new ArrayDeque<>();

    public FakeChatModel(String... responses) {
        for (String response : responses) {
            this.responses.add(response);
        }
    }

    @Override
    public String chat(String userMessage) {
        prompts.add(userMessage);
        String response = responses.isEmpty() ? "{}" : responses.poll();
        return response;
    }

    public int callCount() {
        return prompts.size();
    }

    public String lastPrompt() {
        return prompts.peekLast();
    }

    /** 构建 ChatModel 代理（绕过接口的 default 方法绑定） */
    public ChatModel asModel() {
        FakeChatModel self = this;
        return (ChatModel) Proxy.newProxyInstance(
                ChatModel.class.getClassLoader(),
                new Class<?>[]{ChatModel.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("chat")
                            && args != null && args.length == 1 && args[0] instanceof String) {
                        return self.chat((String) args[0]);
                    }
                    if (method.getName().equals("toString")) {
                        return "FakeChatModel";
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    return null;
                });
    }
}
