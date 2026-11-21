package com.example.info2ai.capture.client;
import java.util.List;
/** 仅报告最近 250ms 进入 renderer 的实体 ID；不能据此声称无遮挡可见。 */
public final class ClientEntitySection {
    public List<Integer> rendered(long now) { return ClientObservations.rendered(now); }
}
