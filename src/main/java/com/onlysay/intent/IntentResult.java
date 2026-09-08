package com.onlysay.intent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图识别结果：意图 + 槽位 + 置信度 + 命中层级（+ 副意图、澄清话术、退化标记）
 */
public class IntentResult {

    private final IntentType intent;
    private final Map<String, Object> slots;
    private final double confidence;
    private final HitLayer hitLayer;
    /** 第三级自报置信度缺失等场景下的退化标记 */
    private final boolean degraded;
    /** 副意图（多标签场景，仅记录供上层并行调度，不进入当前流水线） */
    private final List<String> secondaryIntents;
    /** CLARIFICATION 意图附带的反问话术 */
    private final String clarificationText;

    public IntentResult(IntentType intent,
                        Map<String, Object> slots,
                        double confidence,
                        HitLayer hitLayer) {
        this(intent, slots, confidence, hitLayer, false, List.of(), null);
    }

    public IntentResult(IntentType intent,
                        Map<String, Object> slots,
                        double confidence,
                        HitLayer hitLayer,
                        boolean degraded,
                        List<String> secondaryIntents,
                        String clarificationText) {
        this.intent = intent;
        this.slots = slots == null ? new HashMap<>() : new HashMap<>(slots);
        this.confidence = confidence;
        this.hitLayer = hitLayer;
        this.degraded = degraded;
        this.secondaryIntents = secondaryIntents == null ? List.of() : List.copyOf(secondaryIntents);
        this.clarificationText = clarificationText;
    }

    /** 派生副本：替换命中层级（缓存命中时使用，其余字段继承原结果） */
    public IntentResult withHitLayer(HitLayer newLayer) {
        return new IntentResult(intent, slots, confidence, newLayer, degraded, secondaryIntents, clarificationText);
    }

    public IntentType getIntent() { return intent; }
    public Map<String, Object> getSlots() { return slots; }
    public double getConfidence() { return confidence; }
    public HitLayer getHitLayer() { return hitLayer; }
    public boolean isDegraded() { return degraded; }
    public List<String> getSecondaryIntents() { return secondaryIntents; }
    public String getClarificationText() { return clarificationText; }
}
