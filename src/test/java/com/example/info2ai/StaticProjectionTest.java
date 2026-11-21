package com.example.info2ai;
import com.example.info2ai.model.*;
import com.example.info2ai.staticworld.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class StaticProjectionTest {
    @Test public void absoluteCandidatesAreRebasedSortedFilteredAndVisitedAtCurrentPlayerPosition() {
        var absolute=new StaticWorldInfo("cubiomes","medium",null,null,List.of(
            new StructureInfo("minecraft:village","minecraft:village_savanna",100,0,null,999d,"S",180d,"medium",null,null),
            new StructureInfo("minecraft:igloo","minecraft:igloo",50,0,null,null,null,null,"medium",null,null),
            new StructureInfo("minecraft:igloo","minecraft:igloo",5000,0,null,null,null,null,"medium",null,null)),List.of());
        var projected=StaticProjection.project(absolute,"minecraft:overworld",90,0,200,key->key.endsWith("|100|0"));
        assertEquals(2,projected.structures().size());var village=projected.structures().getFirst();
        assertEquals(10d,village.distance(),0);assertEquals("E",village.direction());assertTrue(village.visited());assertNull(village.y());
        assertEquals(40d,projected.structures().get(1).distance(),0);assertEquals("W",projected.structures().get(1).direction());
        assertEquals(999d,absolute.structures().getFirst().distance(),0);
    }
}
