package com.example.info2ai.util;

public final class VecUtil {
    private static final String[] CARDINALS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private VecUtil() { }
    public static String facing(double yaw) { return direction(yaw + 180); }
    public static double bearing(double dx, double dz) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz)) throw new IllegalArgumentException("坐标差必须有限");
        if (dx == 0 && dz == 0) return 0;
        return normalize(Math.toDegrees(Math.atan2(dx, -dz)));
    }
    public static String directionFromDelta(double dx, double dz) { return direction(bearing(dx, dz)); }
    public static double horizontalDistance(double x1, double z1, double x2, double z2) { return Math.hypot(x2-x1, z2-z1); }
    private static String direction(double degrees) { return CARDINALS[(int) Math.floor((normalize(degrees) + 22.5) / 45) % 8]; }
    private static double normalize(double degrees) {
        if (!Double.isFinite(degrees)) throw new IllegalArgumentException("角度必须有限");
        return ((degrees % 360) + 360) % 360;
    }
}
