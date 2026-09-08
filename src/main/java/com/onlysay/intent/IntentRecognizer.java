package com.onlysay.intent;

import com.onlysay.config.OnlySayProperties;
import com.onlysay.service.IntentPersistenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 三级漏斗编排：归一化 → 规则 → 缓存 → 分类器（可关闭）→ LLM 兜底 → 校验 → 继承合并。
 * 产出 IntentResult + IntentTrace，并负责修正配对与指标记录。
 *
 * 改造自原 IntentRecognizer：删除依赖 Config 的无参构造器，
 * 改用 Spring DI 注入 LlmFallbackRecognizer / IntentMetrics / CorrectionRecorder / OnlySayProperties。
 * LlmIntentClassifier 是 @ConditionalOnProperty Bean，关闭时容器中不存在，通过 @Autowired(required=false) 注入 null。
 */
@Component
public class IntentRecognizer {

    /** 识别输出：结果 + 全程 trace */
    public record Recognition(IntentResult result, IntentTrace trace) {}

    /** 置信度策略阈值 */
    static final double ADOPT_THRESHOLD = 0.85;
    static final double RECHECK_THRESHOLD = 0.60;
    /** 副意图记录阈值（多标签） */
    static final double SECONDARY_THRESHOLD = 0.60;

    private final RuleIntentMatcher ruleMatcher = new RuleIntentMatcher();
    private final IntentClassifier classifier;
    private final LlmFallbackRecognizer fallbackRecognizer;
    private final IntentCache cache;
    private final DailyRateLimiter rateLimiter;
    private final DialogueState dialogueState = new DialogueState();
    private final CorrectionRecorder correctionRecorder;
    private final IntentMetrics metrics;
    private final OnlySayProperties props;
    /** 识别数据落库（仅 pgvector 模式装配，memory 模式/测试为 null） */
    private final IntentPersistenceService persistence;

    /**
     * Spring DI 主构造器：
     *   - classifier 通过 @Autowired(required=false) 注入，intent.classifier=none 时为 null
     *   - persistence 通过 @Autowired(required=false) 注入，embedding.store=memory 时为 null
     *   - fallbackRecognizer / metrics / correctionRecorder 由容器装配
     *   - props 提供 cacheSize 与 llmDailyLimit（构造期 new IntentCache/DailyRateLimiter）
     */
    @Autowired
    public IntentRecognizer(@Autowired(required = false) IntentClassifier classifier,
                            LlmFallbackRecognizer fallbackRecognizer,
                            IntentMetrics metrics,
                            CorrectionRecorder correctionRecorder,
                            OnlySayProperties props,
                            @Autowired(required = false) IntentPersistenceService persistence) {
        this.classifier = classifier;
        this.fallbackRecognizer = fallbackRecognizer;
        this.metrics = metrics;
        this.correctionRecorder = correctionRecorder;
        this.props = props;
        this.persistence = persistence;
        this.cache = new IntentCache(props.getIntent().getCacheSize());
        this.rateLimiter = new DailyRateLimiter(props.getIntent().getLlmDailyLimit());
    }

    /** 测试用全参构造：注入分类器/兜底/缓存/限流/记录器，模拟各层行为 */
    IntentRecognizer(IntentClassifier classifier,
                     LlmFallbackRecognizer fallbackRecognizer,
                     IntentCache cache,
                     DailyRateLimiter rateLimiter,
                     CorrectionRecorder correctionRecorder) {
        this.classifier = classifier;
        this.fallbackRecognizer = fallbackRecognizer;
        this.cache = cache;
        this.rateLimiter = rateLimiter;
        this.correctionRecorder = correctionRecorder;
        this.metrics = new IntentMetrics();
        this.props = null;
        this.persistence = null;
    }

    public Recognition recognize(String rawInput, String sessionId) {
        long start = System.currentTimeMillis();
        String normalized = TextNormalizer.normalize(rawInput);
        IntentTrace trace = new IntentTrace(normalized);

        // ===== 第一级：规则匹配 =====
        long t = System.currentTimeMillis();
        Optional<IntentResult> ruleHit = ruleMatcher.match(normalized);
        trace.addLayer("RULE", ruleHit.isPresent() ? "hit" : "miss",
                ruleHit.map(IntentResult::getConfidence).orElse(0.0),
                System.currentTimeMillis() - t, null);
        if (ruleHit.isPresent()) {
            return finalize(ruleHit.get(), normalized, rawInput, sessionId, trace, start);
        }

        // ===== 缓存：归一化文本精确匹配 =====
        t = System.currentTimeMillis();
        IntentResult cached = cache.get(normalized);
        if (cached != null) {
            trace.addLayer("CACHE", "hit", cached.getConfidence(),
                    System.currentTimeMillis() - t, null);
            return finalize(cached.withHitLayer(HitLayer.CACHE), normalized, rawInput, sessionId, trace, start);
        }
        trace.addLayer("CACHE", "miss", 0.0, System.currentTimeMillis() - t, null);

        // ===== 第二级：轻量分类器（可关闭） =====
        if (classifier != null) {
            t = System.currentTimeMillis();
            IntentClassifier.ClassifierOutput output = classifier.classify(normalized);
            long classifierMs = System.currentTimeMillis() - t;

            if (output.failed()) {
                // 分类器调用失败 → 降级第三级
                trace.addLayer("CLASSIFIER", "failed", 0.0, classifierMs,
                        truncate(output.error()));
            } else if (output.scores().isEmpty()) {
                trace.addLayer("CLASSIFIER", "miss", 0.0, classifierMs, null);
            } else {
                Map.Entry<String, Double> top = topScore(output.scores());
                List<String> secondary = secondaryOf(output.scores(), top.getKey());
                trace.addLayer("CLASSIFIER", top.getValue() >= ADOPT_THRESHOLD ? "hit" : "miss",
                        top.getValue(), classifierMs, "scores=" + new TreeMap<>(output.scores()));

                if (top.getValue() >= ADOPT_THRESHOLD) {
                    // ≥0.85 直接采纳，不调用 LLM
                    IntentType adopted = IntentType.valueOf(top.getKey());
                    Map<String, Object> slots = adoptSlots(adopted, normalized);
                    IntentValidator.ValidationResult validation =
                            IntentValidator.validate(adopted, slots);
                    if (!validation.hasMissingRequiredSlots()) {
                        IntentResult result = new IntentResult(adopted, validation.cleanedSlots(),
                                top.getValue(), HitLayer.CLASSIFIER, false, secondary, null);
                        return finalize(result, normalized, rawInput, sessionId, trace, start);
                    }
                    // 采纳意图但必需槽位缺失 → 交给第三级提取槽位
                } else if (top.getValue() < RECHECK_THRESHOLD) {
                    // <0.60 → 触发澄清
                    IntentResult result = new IntentResult(IntentType.CLARIFICATION, Map.of(),
                            top.getValue(), HitLayer.CLASSIFIER, false, secondary,
                            ClarificationText.fallback());
                    return finalize(result, normalized, rawInput, sessionId, trace, start);
                }
                // 0.60-0.85 或缺槽位 → 落入第三级复核
            }
        } else {
            trace.addLayer("CLASSIFIER", "disabled", 0.0, 0, null);
        }

        // ===== 第三级：LLM 兜底复核（每日限额） =====
        t = System.currentTimeMillis();
        if (!rateLimiter.tryAcquire()) {
            System.out.println("⛔ 意图识别 LLM 兜底已达每日上限，本次直接澄清");
            trace.addLayer("LLM", "rate-limited", 0.0, System.currentTimeMillis() - t, null);
            IntentResult result = new IntentResult(IntentType.CLARIFICATION, Map.of(), 0.0,
                    HitLayer.FALLBACK, false, List.of(), ClarificationText.fallback());
            return finalize(result, normalized, rawInput, sessionId, trace, start);
        }

        Optional<IntentResult> fallback = fallbackRecognizer.recognize(normalized);
        if (fallback.isPresent()) {
            trace.addLayer("LLM", "hit", fallback.get().getConfidence(),
                    System.currentTimeMillis() - t,
                    fallback.get().getIntent() == IntentType.CLARIFICATION
                            ? "必需槽位缺失或输出校验触发澄清" : null);
            return finalize(fallback.get(), normalized, rawInput, sessionId, trace, start);
        }

        // 全部层级失败 → 最终兜底澄清
        trace.addLayer("LLM", "failed", 0.0, System.currentTimeMillis() - t, "两次尝试均无法解析");
        IntentResult result = new IntentResult(IntentType.CLARIFICATION, Map.of(), 0.0,
                HitLayer.FALLBACK, false, List.of(), ClarificationText.fallback());
        return finalize(result, normalized, rawInput, sessionId, trace, start);
    }

    /** 收尾：缓存写入 → 修正配对 → 槽位继承/切换 → 状态入栈 → 指标记录 */
    private Recognition finalize(IntentResult result, String normalized, String rawInput,
                                 String sessionId, IntentTrace trace, long start) {
        long elapsed = System.currentTimeMillis() - start;
        trace.setTotalMs(elapsed);

        // 缓存写入（缓存命中的结果不重复写入）
        if (result.getHitLayer() != HitLayer.CACHE) {
            cache.put(normalized, result);
        }

        // 修正配对：上一轮为澄清轮且本轮非澄清 → 落盘修正记录（数据回流）
        String clarificationInput = dialogueState.consumeClarificationInput(sessionId);
        boolean correctionPaired =
                correctionRecorder.maybeRecord(sessionId, clarificationInput, rawInput, result);

        // 意图切换检测 + 槽位继承
        boolean switchDetected = SwitchDetector.isSwitchSignal(normalized);
        Map<String, Object> mergedSlots = dialogueState.inheritSlots(
                sessionId, result.getIntent(), result.getSlots(), switchDetected);
        IntentResult merged = new IntentResult(result.getIntent(), mergedSlots,
                result.getConfidence(), result.getHitLayer(), result.isDegraded(),
                result.getSecondaryIntents(), result.getClarificationText());

        dialogueState.push(sessionId, rawInput, merged);
        metrics.record(merged, elapsed);

        // 落库（仅 pgvector 模式 persistence 非 null；失败已在服务内吞掉，不阻断主链路）
        if (persistence != null) {
            persistence.logRecognition(sessionId, rawInput, merged, elapsed, trace);
            if (correctionPaired) {
                persistence.logCorrection(sessionId, clarificationInput, rawInput, merged);
            }
        }
        return new Recognition(merged, trace);
    }

    /** 分类器只输出置信度分布无槽位：创作意图以整句输入作为话题，其余意图走 LLM 提槽位 */
    private Map<String, Object> adoptSlots(IntentType intent, String normalized) {
        if (intent == IntentType.CONTENT_GENERATION) {
            return Map.of("topic", normalized);
        }
        return Map.of();
    }

    private Map.Entry<String, Double> topScore(Map<String, Double> scores) {
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();
    }

    private List<String> secondaryOf(Map<String, Double> scores, String primaryName) {
        List<String> secondary = new ArrayList<>();
        scores.forEach((name, score) -> {
            if (!name.equals(primaryName) && score >= SECONDARY_THRESHOLD) {
                secondary.add(name);
            }
        });
        return secondary;
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 120 ? text : text.substring(0, 120) + "...";
    }

    public IntentMetrics getMetrics() {
        return metrics;
    }
}
