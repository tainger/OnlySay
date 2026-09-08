package com.onlysay.intent;

/**
 * 澄清反问话术：基于缺失槽位或场景生成面向用户的反问，而非静默失败
 */
public final class ClarificationText {

    /** 短输入无动词时的通用反问 */
    public static String shortInput() {
        return "你的输入有点简短，我没太理解意图。你是想：写文章 / 改写文案 / 查热点，还是聊聊别的？";
    }

    /** 全部层级失败时的最终兜底 */
    public static String fallback() {
        return "抱歉，我没太理解你的意思。你是想让我写一篇内容、改写现有文案，还是查查最近的热点？";
    }

    /** 必需槽位缺失时按槽位名生成追问 */
    public static String missingSlot(String slotName) {
        return switch (slotName) {
            case "topic" -> "好的，你想写一篇内容——具体想聊什么主题呢？告诉我话题就可以开始了。";
            case "source_text" -> "你想改写哪段内容呢？把原文发给我，并告诉我想要的风格或长度。";
            case "action" -> "你想让我做什么操作呢？（例如：保存、分享、撤销）";
            default -> "还差一个信息：" + slotName + "，可以补充一下吗？";
        };
    }

    private ClarificationText() {}
}
