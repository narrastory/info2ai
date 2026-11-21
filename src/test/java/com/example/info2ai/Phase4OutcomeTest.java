package com.example.info2ai;
import com.example.info2ai.events.ActionEvidence;
import org.junit.Test;
import static org.junit.Assert.*;
public class Phase4OutcomeTest {
    @Test public void acceptedEnchantmentClickAloneCannotProveSuccess() {
        assertFalse(ActionEvidence.enchanted(true,false,4,4));
        assertFalse(ActionEvidence.enchanted(true,true,4,4));
        assertFalse(ActionEvidence.enchanted(false,true,4,5));
        assertTrue(ActionEvidence.enchanted(true,true,4,5));
    }
    @Test public void wakeWithoutActualDaylightSkipCannotClaimSlept() {
        assertFalse(ActionEvidence.slept(false,13000,13000));
        assertFalse(ActionEvidence.slept(true,13000,13000));
        assertFalse(ActionEvidence.slept(false,13000,24000));
        assertTrue(ActionEvidence.slept(true,13000,24000));
    }
    @Test public void placementReturnNeedsMatchingObservedBlock() {
        assertFalse(ActionEvidence.placed(true,"minecraft:chest","minecraft:air"));
        assertFalse(ActionEvidence.placed(false,"minecraft:chest","minecraft:chest"));
        assertTrue(ActionEvidence.placed(true,"minecraft:chest","minecraft:chest"));
    }
}
