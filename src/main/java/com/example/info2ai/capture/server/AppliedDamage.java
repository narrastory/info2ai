package com.example.info2ai.capture.server;

/** Damage consumes health and absorption; overkill and later healing are excluded. */
public record AppliedDamage(double health, double absorption) {
    public static AppliedDamage between(double beforeHealth, double beforeAbsorption, double afterHealth, double afterAbsorption) {
        return new AppliedDamage(consumed(beforeHealth, afterHealth), consumed(beforeAbsorption, afterAbsorption));
    }

    private static double consumed(double before, double after) {
        if (!Double.isFinite(before) || !Double.isFinite(after)) {
            throw new IllegalArgumentException("伤害前后生命值必须有限");
        }
        return Math.min(Math.max(0, before), Math.max(0, before - Math.max(0, after)));
    }

    public double amount() {
        return health + absorption;
    }
}
