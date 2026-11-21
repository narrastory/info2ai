package com.example.info2ai.director;

import com.example.info2ai.director.guard.GuardMixinPlugin;
import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

public class DirectorGuardTest {
    @Test
    public void guardRequiresEveryMandatoryMixin() {
        var missing = GuardMixinPlugin.missing(Set.of("DirectorWorldMixin", "DirectorEntityMixin",
            "DirectorServerWorldMixin", "DirectorLivingEntityMixin", "DirectorArmorStandMixin", "DirectorCloneMixin",
            "DirectorFillMixin", "DirectorBlockDataMixin"));
        assertEquals(java.util.List.of("DirectorSetBlockMixin"), missing);
    }
}
