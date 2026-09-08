package com.onlysay.intent;

/**
 * 意图切换信号词检测：用户表达"算了/还是/换/不是"等否定或更换意愿时，
 * 若随后出现新意图则清空旧状态切换新流水线。
 * 信号词仅对 <30 字短输入生效（长输入中出现在正文里的信号词不算切换信号）。
 */
public final class SwitchDetector {

    private static final int MAX_SIGNAL_LENGTH = 30;
    private static final String[] SIGNALS = {"算了", "还是", "换", "不是"};

    public static boolean isSwitchSignal(String normalizedInput) {
        if (normalizedInput == null) {
            return false;
        }
        String compact = normalizedInput.replace(" ", "");
        if (compact.length() >= MAX_SIGNAL_LENGTH) {
            return false;
        }
        for (String signal : SIGNALS) {
            if (compact.contains(signal)) {
                return true;
            }
        }
        return false;
    }

    private SwitchDetector() {}
}
