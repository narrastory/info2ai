package com.example.info2ai;

import com.example.info2ai.capture.server.ContainerKinds;
import com.example.info2ai.events.*;
import com.example.info2ai.events.Observation.*;
import net.minecraft.screen.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class Phase4ContainerAdapterTest {
    @Test public void chestAndFurnaceOpenEvenWhenRareContentsAreUnknown() {
        EventEngine events=new EventEngine();ActivityInference activity=new ActivityInference();
        var b=Phase4RulesTest.baseline(0);events.tick(b,0);activity.observe(b);
        List<String> kinds=new ArrayList<>();
        Class<?>[] handlers={GenericContainerScreenHandler.class,FurnaceScreenHandler.class};
        String[] expected={"chest","furnace"};
        for(int i=0;i<handlers.length;i++) {
            String id=ContainerKinds.opened(PlayerScreenHandler.class,handlers[i],true);
            assertEquals(expected[i],id);
            var open=new Action(ActionType.OPEN,id,1,0,null,"p",true,true,false,false,null,null);
            var frame=Phase4RulesTest.frame(i+1,b.vital(),b.env(),null,null,List.of(open));
            var emitted=events.tick(frame,2000L*(i+1));activity.observe(frame);
            assertEquals(1,emitted.stream().filter(e->e.kind().equals("chest_opened")).count());
            assertFalse(emitted.stream().anyMatch(e->e.kind().equals("chest_loot_found")));
            kinds.add(id);
        }
        assertEquals(List.of("chest","furnace"),kinds);
        assertEquals("looting",activity.current().kind());
    }
    @Test public void ordinaryInventoryClosingInitialAndUnchangedHandlersDoNotOpenContainers() {
        assertNull(ContainerKinds.opened(GenericContainerScreenHandler.class,PlayerScreenHandler.class,true));
        assertNull(ContainerKinds.opened(GenericContainerScreenHandler.class,null,true));
        assertNull(ContainerKinds.opened(null,GenericContainerScreenHandler.class,true));
        assertNull(ContainerKinds.opened(PlayerScreenHandler.class,FurnaceScreenHandler.class,false));
        for(Class<?> station:List.of(CraftingScreenHandler.class,EnchantmentScreenHandler.class,MerchantScreenHandler.class,
            AnvilScreenHandler.class,SmithingScreenHandler.class,BeaconScreenHandler.class,GrindstoneScreenHandler.class,
            CartographyTableScreenHandler.class,LoomScreenHandler.class,StonecutterScreenHandler.class)) {
            assertNull(station.getSimpleName(),ContainerKinds.opened(PlayerScreenHandler.class,station,true));
        }
    }
    @Test public void vanillaPersistentContainerHandlersHaveARecognizedKind() {
        for(Class<?> type:List.of(BlastFurnaceScreenHandler.class,SmokerScreenHandler.class,BrewingStandScreenHandler.class,
            Generic3x3ContainerScreenHandler.class,CrafterScreenHandler.class,HorseScreenHandler.class,
            ShulkerBoxScreenHandler.class,HopperScreenHandler.class,LecternScreenHandler.class)) {
            assertNotNull(type.getSimpleName(),ContainerKinds.kind(type));
        }
    }
}
