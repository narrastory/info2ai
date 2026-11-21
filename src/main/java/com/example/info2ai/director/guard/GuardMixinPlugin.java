package com.example.info2ai.director.guard;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GuardMixinPlugin implements IMixinConfigPlugin {
    private static final Map<String, Integer> REQUIRED = Map.of(
        "DirectorWorldMixin", 1, "DirectorServerWorldMixin", 1, "DirectorEntityMixin", 1,
        "DirectorLivingEntityMixin", 1, "DirectorArmorStandMixin", 1, "DirectorCloneMixin", 1,
        "DirectorFillMixin", 1, "DirectorSetBlockMixin", 1, "DirectorBlockDataMixin", 1);
    private static final Map<String, Set<String>> APPLIED = new ConcurrentHashMap<>();

    public static List<String> missing() {
        Set<String> applied = ConcurrentHashMap.newKeySet();
        REQUIRED.forEach((name, count) -> {
            if (APPLIED.getOrDefault(name, Set.of()).size() == count) applied.add(name);
        });
        return missing(applied);
    }

    public static List<String> missing(Set<String> applied) {
        return REQUIRED.keySet().stream().filter(name -> !applied.contains(name)).sorted().toList();
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> own, Set<String> other) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        APPLIED.computeIfAbsent(mixin.substring(mixin.lastIndexOf('.') + 1), key -> ConcurrentHashMap.newKeySet()).add(target);
    }
}
