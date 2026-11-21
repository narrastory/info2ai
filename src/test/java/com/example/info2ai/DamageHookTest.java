package com.example.info2ai;

import com.example.info2ai.capture.merge.SnapshotStore;
import com.example.info2ai.capture.server.DamageHook;
import org.junit.Test;
import static org.junit.Assert.*;

public class DamageHookTest {
    private com.example.info2ai.model.Facts previousDamage() {
        var record = new com.google.gson.JsonObject();
        record.addProperty("amount", 3);
        return com.example.info2ai.model.Facts.fromJson(record);
    }

    @Test public void missingHookRemainsUnconfirmedWithoutInventingAvailability() {
        var store = new SnapshotStore();
        var hook = new DamageHook(store);
        for (int tick = 0; tick < 100; tick++) hook.finishTick();
        assertEquals(DamageHook.State.UNCONFIRMED, hook.state());
        assertFalse(hook.available());
        assertNull(hook.knownDamage(previousDamage()));
        assertTrue(store.warnings().contains("capture_damage_hook_unconfirmed"));
        assertEquals(0, hook.pendingCount());
    }

    @Test public void pairedCallbacksConfirmRealPoolDeltaAndClearUnconfirmedWarning() {
        var store = new SnapshotStore();
        var hook = new DamageHook(store);
        hook.begin(20, 4);
        assertFalse(hook.available()); // HEAD alone does not prove a working RETURN
        var amount = hook.finish(18, 1);
        assertEquals(5, amount.amount(), 0);
        assertEquals(DamageHook.State.AVAILABLE, hook.state());
        assertTrue(store.warnings().isEmpty());
        assertEquals(previousDamage(), hook.knownDamage(previousDamage()));
        hook.begin(18, 1);
        assertEquals(0, hook.finish(18, 1).amount(), 0);
        assertTrue(hook.available());
    }

    @Test public void missingReturnExpiresAtTickEndAndReturnWithoutHeadFailsHonestly() {
        var store = new SnapshotStore();
        var hook = new DamageHook(store);
        hook.begin(20, 0);
        hook.finish(18, 0);
        hook.begin(18, 0);
        hook.finishTick();
        assertFalse(hook.available());
        assertEquals(DamageHook.State.FAILED, hook.state());
        assertNull(hook.knownDamage(previousDamage()));
        assertTrue(store.warnings().contains("capture_damage_hook_failed"));
        assertEquals(0, hook.pendingCount());
        assertNull(hook.finish(10, 0));
        hook.begin(10, 0);
        assertEquals(1, hook.finish(9, 0).amount(), 0);
        assertTrue(hook.available());
        assertTrue(store.warnings().isEmpty());
    }

    @Test public void unmatchedHeadsHaveBoundedStorageAndDisconnectReentryRequireFreshConfirmation() {
        var store = new SnapshotStore();
        var hook = new DamageHook(store);
        for (int i = 0; i < 1000; i++) {
            hook.begin(20, 0);
            assertTrue(hook.pendingCount() <= 8);
        }
        hook.finishTick();
        assertFalse(hook.available());
        hook.stop();
        assertEquals(DamageHook.State.INACTIVE, hook.state());
        assertEquals(0, hook.pendingCount());
        assertNull(hook.finish(1, 0));
        assertNull(hook.knownDamage(previousDamage()));
        assertTrue(store.warnings().isEmpty());
        hook.start();
        assertEquals(DamageHook.State.UNCONFIRMED, hook.state());
        assertFalse(hook.available());
        assertNull(hook.finish(1, 0)); // old world's RETURN cannot use an old pool snapshot
        assertEquals(DamageHook.State.FAILED, hook.state());
        hook.begin(20, 0);
        hook.finish(19, 0);
        assertTrue(hook.available());
    }
}
