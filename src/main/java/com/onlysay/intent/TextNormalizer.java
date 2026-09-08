package com.onlysay.intent;

/**
 * 文本归一化：仅做三项轻量处理（容错设计约定，不做拼写纠错/口语转换/重复字压缩）：
 * 1. 全角标点/字母/数字转半角
 * 2. 去除零宽字符与控制字符（防注入）
 * 3. 连续空白符合并为单个空格
 *
 * 注意：归一化结果仅用于意图识别，传给下游（RAG 生成）的仍是原始输入。
 */
public final class TextNormalizer {

    private TextNormalizer() {}

    public static String normalize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length());
        boolean lastWasSpace = false;
        for (int i = 0; i < input.length(); ) {
            int codePoint = input.codePointAt(i);
            i += Character.charCount(codePoint);

            // 2. 去除零宽字符与控制字符（保留 \t \n \r，它们随后按空白合并处理）
            if (isZeroWidthOrControl(codePoint)) {
                continue;
            }

            // 1. 全角 → 半角
            int half = toHalfWidth(codePoint);
            char ch = (char) half;

            // 3. 连续空白合并为单个空格
            if (Character.isWhitespace(ch)) {
                if (!lastWasSpace) {
                    sb.append(' ');
                    lastWasSpace = true;
                }
            } else {
                sb.append(ch);
                lastWasSpace = false;
            }
        }
        // 去除首尾空格
        int start = 0;
        int end = sb.length();
        while (start < end && sb.charAt(start) == ' ') start++;
        while (end > start && sb.charAt(end - 1) == ' ') end--;
        return sb.substring(start, end);
    }

    /** 全角字符（FF01-FF5E 标点/字母/数字 + 全角空格 3000）转半角 */
    private static int toHalfWidth(int codePoint) {
        if (codePoint == 0x3000) {
            return ' ';
        }
        if (codePoint >= 0xFF01 && codePoint <= 0xFF5E) {
            return codePoint - 0xFEE0;
        }
        return codePoint;
    }

    private static boolean isZeroWidthOrControl(int codePoint) {
        // 控制字符（Cc 分类，含 U+0000-U+001F、U+007F-U+009F）
        if (Character.getType(codePoint) == Character.CONTROL) {
            // \t \n \r 不算注入字符，交给空白合并处理
            return codePoint != '\t' && codePoint != '\n' && codePoint != '\r';
        }
        // 零宽系列：U+200B-ZWSP, U+200C-ZWNJ, U+200D-ZWJ, U+FEFF-BOM, U+2060
        return codePoint == 0x200B || codePoint == 0x200C || codePoint == 0x200D
                || codePoint == 0xFEFF || codePoint == 0x2060;
    }
}
