package com.onlysay.intent;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 第三级 LLM 兜底每日调用限流：AtomicInteger 按自然日重置。
 * 超限后的请求直接返回 CLARIFICATION，不再调用 LLM。
 */
public class DailyRateLimiter {

    private final int dailyLimit;
    private final AtomicInteger count = new AtomicInteger(0);
    private final AtomicReference<LocalDate> currentDay = new AtomicReference<>(LocalDate.now());

    public DailyRateLimiter(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    /** 尝试获取一次调用额度；false 表示当日已达上限 */
    public boolean tryAcquire() {
        rollDayIfNeeded();
        int used = count.incrementAndGet();
        return used <= dailyLimit;
    }

    /** 当日已消耗额度（跨天时先滚动，供监控读取） */
    public int usedToday() {
        rollDayIfNeeded();
        return count.get();
    }

    private void rollDayIfNeeded() {
        LocalDate today = LocalDate.now();
        LocalDate day = currentDay.get();
        if (!today.equals(day) && currentDay.compareAndSet(day, today)) {
            count.set(0);
        }
    }
}
