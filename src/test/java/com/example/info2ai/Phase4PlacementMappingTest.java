package com.example.info2ai;

import com.example.info2ai.events.*;
import com.example.info2ai.events.Observation.*;
import com.google.gson.JsonParser;
import org.junit.Test;
import org.objectweb.asm.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** 读取实际映射字节码与已编译Mixin元数据，不启动MC/初始化注册表。 */
public class Phase4PlacementMappingTest {
    private static final String PLACE="place(Lnet/minecraft/item/ItemPlacementContext;Lnet/minecraft/block/BlockState;)Z";
    private record Method(String owner,String signature) { }
    private static ClassReader bytes(String owner) throws IOException {
        try(var stream=Phase4PlacementMappingTest.class.getResourceAsStream("/"+owner+".class")) {
            assertNotNull("缺少实际映射/已编译类："+owner,stream);
            return new ClassReader(stream);
        }
    }
    private static List<Method> registeredHooks() throws IOException {
        List<Method> hooks=new ArrayList<>();
        try(var stream=Phase4PlacementMappingTest.class.getResourceAsStream("/info2ai.mixins.json")) {
            var config=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var entry:config.getAsJsonArray("mixins")) {
                List<String> targets=new ArrayList<>(),methods=new ArrayList<>();
                String owner=config.get("package").getAsString().replace('.','/')+"/"+entry.getAsString();
                bytes(owner).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String desc,boolean visible) {
                        if(!desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))return null;
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override public AnnotationVisitor visitArray(String name) {
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override public void visit(String name,Object value) {
                                        if(value instanceof Type type)targets.add(type.getInternalName());
                                    }
                                };
                            }
                        };
                    }
                    @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public AnnotationVisitor visitAnnotation(String desc,boolean visible) {
                                if(!desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))return null;
                                List<String> injected=new ArrayList<>();boolean[] returned={false};
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override public AnnotationVisitor visitArray(String name) {
                                        if(name.equals("method"))return new AnnotationVisitor(Opcodes.ASM9) {
                                            @Override public void visit(String name,Object value) {injected.add((String)value);}
                                        };
                                        if(!name.equals("at"))return null;
                                        return new AnnotationVisitor(Opcodes.ASM9) {
                                            @Override public AnnotationVisitor visitAnnotation(String name,String descriptor) {
                                                return new AnnotationVisitor(Opcodes.ASM9) {
                                                    @Override public void visit(String key,Object value) {
                                                        if(key.equals("value")&&value.equals("RETURN"))returned[0]=true;
                                                    }
                                                };
                                            }
                                        };
                                    }
                                    @Override public void visitEnd() {if(returned[0])methods.addAll(injected);}
                                };
                            }
                        };
                    }
                },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                for(String target:targets)for(String method:methods)hooks.add(new Method(target,method));
            }
        }
        return hooks;
    }
    private static List<Method> actualCalls(String itemOwner) throws IOException {
        ClassReader reader=bytes(itemOwner);List<Method> calls=new ArrayList<>();boolean[] declared={false};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
                if(!(name+desc).equals(PLACE))return null;
                declared[0]=true;calls.add(new Method(itemOwner,PLACE));
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode,String owner,String name,String desc,boolean isInterface) {
                        if(opcode==Opcodes.INVOKESPECIAL&&(name+desc).equals(PLACE))calls.add(new Method(owner,PLACE));
                    }
                };
            }
        },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        return declared[0]?calls:actualCalls(reader.getSuperName());
    }
    private static List<EventDraft> place(String owner,String id,boolean success) throws IOException {
        List<Method> hooks=registeredHooks();List<Action> facts=new ArrayList<>();
        for(Method call:actualCalls(owner))for(Method hook:hooks)if(hook.equals(call)&&ActionEvidence.placed(success,id,success?id:"minecraft:air")) {
            facts.add(new Action(ActionType.PLACE,id,1,0,null,"p",true,true,false,false,List.of(0d,64d,0d),null));
        }
        assertEquals("实际放置调用路径应恰好记录一次成功/零次失败",success?1:0,facts.size());
        EventEngine engine=new EventEngine();var b=Phase4RulesTest.baseline(0);engine.tick(b,0);
        return engine.tick(Phase4RulesTest.frame(1,b.vital(),b.env(),b.inv(),null,facts),1);
    }
    @Test public void successfulBedOverrideHasExactlyOnePlacementObservation() throws Exception {
        assertEquals(List.of(new Method("net/minecraft/item/BedItem",PLACE)),actualCalls("net/minecraft/item/BedItem"));
        assertEquals(1,place("net/minecraft/item/BedItem","minecraft:red_bed",true).stream().filter(e->e.kind().equals("placed_significant")).count());
    }
    @Test public void failedBedHasNoPlacementObservation() throws Exception {
        assertTrue(place("net/minecraft/item/BedItem","minecraft:red_bed",false).isEmpty());
    }
    @Test public void ordinaryCraftingTableStillHasExactlyOnePlacementObservation() throws Exception {
        assertEquals(1,place("net/minecraft/item/BlockItem","minecraft:crafting_table",true).stream().filter(e->e.kind().equals("placed_significant")).count());
    }
}
