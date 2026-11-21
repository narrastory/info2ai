package com.example.info2ai.capture.server;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.model.Facts;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Optional hook: availability is proven only by a valid HEAD/RETURN pair in this session. */
public final class DamageHook {
    public enum State { INACTIVE, UNCONFIRMED, AVAILABLE, FAILED }
    private record Pools(double health, double absorption) { }
    private final SnapshotStore store;
    private final Deque<Pools> pending = new ArrayDeque<>();
    private State state = State.INACTIVE;

    public DamageHook(SnapshotStore store) {
        this.store = Objects.requireNonNull(store);
        start();
    }

    public State state() {
        return state;
    }

    public boolean available() {
        return state == State.AVAILABLE;
    }

    public Facts knownDamage(Facts lastRecorded) {
        return available() ? lastRecorded : null;
    }

    public int pendingCount() {
        return pending.size();
    }

    public void begin(double health, double absorption) {
        if (state == State.INACTIVE) return;
        if (pending.size() >= 8) {
            fail();
            return;
        }
        pending.push(new Pools(health, absorption));
    }

    public AppliedDamage finish(double health, double absorption) {
        if (state == State.INACTIVE) return null;
        if (pending.isEmpty()) {
            fail();
            return null;
        }
        Pools before = pending.pop();
        try {
            AppliedDamage damage = AppliedDamage.between(before.health(), before.absorption(), health, absorption);
            setState(State.AVAILABLE);
            return damage;
        } catch (IllegalArgumentException invalidPools) {
            fail();
            return null;
        }
    }

    /** Synchronous applyDamage must return before END_SERVER_TICK; no probe can outlive that tick. */
    public void finishTick() {
        if (!pending.isEmpty()) fail();
    }

    public void fail() {
        pending.clear();
        if (state != State.INACTIVE) setState(State.FAILED);
    }

    public void stop() {
        pending.clear();
        setState(State.INACTIVE);
    }

    public void start() {
        pending.clear();
        setState(State.UNCONFIRMED);
    }

    private void setState(State next) {
        boolean changed = state != next;
        state = next;
        store.clearWarning("capture_damage_hook_unconfirmed");
        store.clearWarning("capture_damage_hook_failed");
        if (state == State.UNCONFIRMED) {
            store.warning("capture_damage_hook_unconfirmed");
            if (changed) CaptureLog.debug("实际伤害 hook 尚未收到完整回调，last_damage 保持未知");
        } else if (state == State.FAILED) {
            store.warning("capture_damage_hook_failed");
            if (changed) CaptureLog.error("实际伤害 hook 回调不配对或数据无效，last_damage 保持未知", null);
        }
    }
}
