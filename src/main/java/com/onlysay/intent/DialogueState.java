package com.onlysay.intent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * 对话状态栈：按会话隔离，保存每轮意图与槽位（固定深度环形栈）。
 * 支持槽位跨轮继承与意图切换清空；同时记录上一轮是否为澄清轮，
 * 供修正事件配对（数据回流）判断使用。
 */
public class DialogueState {

    /** 单轮状态：意图 + 槽位 */
    public record Turn(IntentType intent, Map<String, Object> slots) {}

    /** 单会话状态 */
    private static class Session {
        final Deque<Turn> stack = new ArrayDeque<>();
        /** 上一轮的澄清输入（若上一轮为 CLARIFICATION），用于修正配对 */
        String lastClarificationInput;
    }

    private static final int MAX_DEPTH = 8;
    private final Map<String, Session> sessions = new HashMap<>();

    /**
     * 记录本轮结果；若本轮为 CLARIFICATION 则同时记录澄清输入。
     */
    public synchronized void push(String sessionId, String input, IntentResult result) {
        if (sessionId == null || sessionId.isBlank()) {
            return; // 无 sessionId 视为独立会话，不维护状态
        }
        Session session = sessions.computeIfAbsent(sessionId, k -> new Session());
        session.stack.addLast(new Turn(result.getIntent(), Map.copyOf(result.getSlots())));
        while (session.stack.size() > MAX_DEPTH) {
            session.stack.removeFirst();
        }
        session.lastClarificationInput =
                result.getIntent() == IntentType.CLARIFICATION ? input : null;
    }

    /**
     * 槽位继承：意图切换信号 + 新意图 → 清空旧状态不继承；
     * 否则从上一轮继承当前轮缺失的槽位。
     *
     * @return 继承合并后的槽位（可能包含上一轮的同名槽位）
     */
    public synchronized Map<String, Object> inheritSlots(String sessionId, IntentType currentIntent,
                                                         Map<String, Object> currentSlots,
                                                         boolean switchDetected) {
        if (sessionId == null || sessionId.isBlank()) {
            return currentSlots;
        }
        Session session = sessions.get(sessionId);
        if (session == null || session.stack.isEmpty()) {
            return currentSlots;
        }
        Turn previous = session.stack.peekLast();

        // 意图切换：清空旧状态，切换新意图，不继承与新意图无关的槽位
        if (switchDetected && previous != null && previous.intent() != currentIntent) {
            session.stack.clear();
            return currentSlots;
        }

        // 跨轮继承：当前轮缺失的槽位从上一轮补齐
        Map<String, Object> merged = new HashMap<>(currentSlots);
        if (previous != null) {
            previous.slots().forEach((key, value) -> {
                if (!merged.containsKey(key) && value != null) {
                    merged.put(key, value);
                }
            });
        }
        return merged;
    }

    /**
     * 判断是否构成"修正"：上一轮为 CLARIFICATION 且本轮为非 CLARIFICATION 结果。
     *
     * @return 上一轮的澄清输入；null 表示不构成修正
     */
    public synchronized String consumeClarificationInput(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        Session session = sessions.get(sessionId);
        return session == null ? null : session.lastClarificationInput;
    }

    public synchronized int depth(String sessionId) {
        Session session = sessions.get(sessionId);
        return session == null ? 0 : session.stack.size();
    }
}
