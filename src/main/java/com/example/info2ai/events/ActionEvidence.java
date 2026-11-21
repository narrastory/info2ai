package com.example.info2ai.events;
/** 原版成功回调的最小证据组合；仅作观察，绝不更改回调结果。 */
public final class ActionEvidence {
    private ActionEvidence() {
    }
    public static boolean enchanted(boolean accepted,boolean changed,int before,int after) {
        return accepted&&changed&&after>before;
    }
    public static boolean slept(boolean daylight,long before,long after) {
        return daylight&&after>before&&after/24000>before/24000&&Math.floorMod(after,24000)<1000;
    }
    public static boolean placed(boolean accepted,String expected,String actual) {
        return accepted&&expected!=null&&expected.equals(actual);
    }
}
