package com.onlysay.intent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 识别结果精确匹配缓存：key = 归一化后文本，LRU 容量可配置。
 * 命中时 hitLayer 标注为 CACHE 并继承原结果，避免重复 LLM 调用。
 */
public class IntentCache {

    private final Map<String, IntentResult> cache;

    public IntentCache(int maxSize) {
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, IntentResult> eldest) {
                return size() > maxSize;
            }
        };
    }

    public synchronized IntentResult get(String normalizedText) {
        return cache.get(normalizedText);
    }

    public synchronized void put(String normalizedText, IntentResult result) {
        cache.put(normalizedText, result);
    }

    public synchronized int size() {
        return cache.size();
    }
}
