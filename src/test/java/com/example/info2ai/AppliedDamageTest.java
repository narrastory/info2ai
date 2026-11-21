package com.example.info2ai;

import com.example.info2ai.capture.server.AppliedDamage;
import org.junit.Test;
import static org.junit.Assert.*;

public class AppliedDamageTest {
    @Test public void finalDeltasRepresentUnarmoredAndArmoredHitsRatherThanIncomingAmount() {
        assertEquals(8, AppliedDamage.between(20, 0, 12, 0).amount(), 0);
        assertEquals(2, AppliedDamage.between(20, 0, 18, 0).amount(), 0);
    }

    @Test public void absorptionAndLethalHealthAreSeparateAndBoundedByActuallyConsumedPools() {
        var absorbed = AppliedDamage.between(20, 4, 18, 0);
        assertEquals(2, absorbed.health(), 0);
        assertEquals(4, absorbed.absorption(), 0);
        assertEquals(6, absorbed.amount(), 0);
        var lethal = AppliedDamage.between(3, 2, -100, -10);
        assertEquals(3, lethal.health(), 0);
        assertEquals(2, lethal.absorption(), 0);
        assertEquals(5, lethal.amount(), 0);
        assertEquals(2, AppliedDamage.between(20, 4, 20, 2).amount(), 0);
    }

    @Test public void blockedOrNoDamageNeverCreatesPositiveAmountAndHealingIsNotDamage() {
        assertEquals(0, AppliedDamage.between(20, 0, 20, 0).amount(), 0);
        assertEquals(0, AppliedDamage.between(10, 2, 20, 4).amount(), 0);
    }
}
