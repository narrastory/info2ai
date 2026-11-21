package com.example.info2ai.api;
import com.example.info2ai.model.Facts;
import java.util.Objects;
/** 帧字节和捕获时的不可变上下文一起交付，禁止 HTTP 事后拼接。 */
public record ScreenshotResult(byte[] bytes, long seq, long capturedAt, Facts context, boolean hideHudSupported) {
    public ScreenshotResult { bytes = Objects.requireNonNull(bytes).clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
}
