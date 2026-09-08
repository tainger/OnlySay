package com.onlysay.intent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 文本归一化：对应规格 Scenario「全角标点输入」「含零宽字符的输入」
 */
class TextNormalizerTest {

    @Test
    void 全角标点字母数字转半角() {
        String normalized = TextNormalizer.normalize("写一篇关于ＡＩ的文章！");
        assertEquals("写一篇关于AI的文章!", normalized);
    }

    @Test
    void 零宽字符与控制字符被移除() {
        String withZeroWidth = "写\u200B一\u200C篇\u200D文\uFEFF章\u2060";
        assertEquals("写一篇文章", TextNormalizer.normalize(withZeroWidth));
    }

    @Test
    void 连续空白合并为单个空格() {
        assertEquals("写 一 篇", TextNormalizer.normalize("写  \t 一 \n 篇"));
    }

    @Test
    void 全角数字转半角() {
        assertEquals("写800字文章", TextNormalizer.normalize("写８００字文章"));
        assertFalse(TextNormalizer.normalize("写８００字文章").contains("８"));
    }

    @Test
    void 空输入安全() {
        assertEquals("", TextNormalizer.normalize(null));
        assertEquals("", TextNormalizer.normalize("   "));
    }
}
