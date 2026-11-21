package com.example.info2ai;
import com.example.info2ai.capture.server.EnclosureSampler;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
public class EnclosureSamplerTest {
 @Test public void enclosureUsesAll48StepsAndCachesUntilFifthIntervalOrChunkChange() {
   var sampler=new EnclosureSampler(); var calls=new AtomicInteger();
   assertEquals(.5,sampler.sample("a",0,0,i->{calls.incrementAndGet();return i<24?1:0;}),0);
   for(int n=0;n<4;n++)assertEquals(.5,sampler.sample("a",0,0,i->{fail("cached");return 0;}),0);
   assertEquals(48,calls.get());
   assertEquals(1,sampler.sample("a",0,0,i->1),0);
   assertEquals(0,sampler.sample("a",1,0,i->0),0);
   assertNull(sampler.sample("b",1,0,i->-1));
 }
}
