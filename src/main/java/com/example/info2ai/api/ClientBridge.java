package com.example.info2ai.api;
import com.example.info2ai.http.ApiException;
/** 纯 Java 边界；HTTP 不知道游戏类和线程。 */
@FunctionalInterface
public interface ClientBridge {
    ScreenshotResult captureScreen(int maxWidth, boolean hideHud, int quality, String format);
    default boolean gamePaused() { return false; }
    ClientBridge NONE = (width, hide, quality, format) -> { throw new ApiException(503, "no_client"); };
}
