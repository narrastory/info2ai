package com.example.info2ai.director;

import com.example.info2ai.api.Info2AiApi;
import com.example.info2ai.director.guard.ActionWindow;
import com.example.info2ai.director.guard.EntityJournal;
import com.example.info2ai.events.EventData;
import com.example.info2ai.events.EventDraft;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public final class DirectorEvents {
    private DirectorEvents() { }

    public static void emit(ServerWorld world, String kind, String severity, String text, Object... fields) {
        var store = Info2AiApi.store();
        if (store != null) store.emit(store.worldEpoch(), new EventDraft(world.getTime(), kind, severity,
            text, EventData.of(fields), null, 0));
    }

    public static Long action(Entity entity) {
        if (!entity.getCommandTags().contains("info2ai")) return null;
        for (String tag : entity.getCommandTags()) {
            if (!tag.startsWith("info2ai_a")) continue;
            try { return Long.parseLong(tag.substring("info2ai_a".length())); }
            catch (NumberFormatException invalid) { /* 游戏标签可由玩家修改。 */ }
        }
        return null;
    }

    public static Long action(ItemStack stack) {
        var data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null) return null;
        var mark = data.copyNbt().getCompound("info2ai");
        return mark.contains("action", NbtElement.NUMBER_TYPE) ? mark.getLong("action") : null;
    }

    public static void mark(ItemStack stack, long id) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
            var mark = nbt.getCompound("info2ai");
            mark.putLong("action", id);
            nbt.put("info2ai", mark);
        });
    }

    public static void death(Entity entity, DamageSource source) {
        if (!(entity.getWorld() instanceof ServerWorld world) || !EntityJournal.counted(entity)) return;
        Long id = action(entity);
        if (id == null) return;
        String name = entity.getName().getString();
        if (source.getAttacker() instanceof ServerPlayerEntity) {
            emit(world, "director_entity_killed", "info", "玩家击杀了伙伴生成的" + name,
                "action_id", id, "type", EntityJournal.type(entity), "name", name);
            if (Info2AiApi.director() instanceof DirectorService service) service.forgetEntity(entity);
        } else {
            gone(entity, "死亡");
        }
    }

    public static void gone(Entity entity, String reason) {
        if (!(entity.getWorld() instanceof ServerWorld world) || !EntityJournal.counted(entity)) return;
        Long id = action(entity);
        if (id == null) return;
        emit(world, "director_entity_gone", "info", "伙伴生成的" + entity.getName().getString() + "消失了（" + reason + "）",
            "action_id", id, "type", EntityJournal.type(entity), "reason", reason);
        if (Info2AiApi.director() instanceof DirectorService service) service.forgetEntity(entity);
    }

    public static void damage(ServerPlayerEntity player, Entity attacker, double amount) {
        if (attacker == null || !(Info2AiApi.director() instanceof DirectorService service)) return;
        Long id = action(attacker);
        if (id == null || !service.cooldown("hurt", id, 100)) return;
        emit(player.getServerWorld(), "director_entity_hurt_player", "warn",
            "伙伴生成的" + attacker.getName().getString() + "打了玩家 " + amount + " 点",
            "action_id", id, "amount", amount);
    }

    public static void pickedUp(ServerPlayerEntity player, long id, String item, int amount) {
        if (amount <= 0 || !(Info2AiApi.director() instanceof DirectorService service)
            || !service.cooldown("pickup", id, 40)) return;
        emit(player.getServerWorld(), "director_item_picked_up", "info", "玩家捡起了伙伴放下的" + item,
            "action_id", id, "item", item, "count", amount);
    }

    public static void opened(ServerPlayerEntity player, BlockPos pos) {
        if (Info2AiApi.director() instanceof DirectorService service) service.opened(player.getServerWorld(), pos);
    }

    public static void broken(ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (!(Info2AiApi.director() instanceof DirectorService service)) return;
        Long id = service.changedAt(player.getServerWorld(), pos, state);
        if (id == null || !service.cooldown("broken", id, 200)) return;
        String block = Registries.BLOCK.getId(state.getBlock()).toString();
        emit(player.getServerWorld(), "director_block_broken", "info", "玩家破坏了伙伴放置的" + block,
            "action_id", id, "block", block, "count", 1);
    }

    public static void used(ServerPlayerEntity player, ItemStack stack) {
        if (ActionWindow.active() != null || !(Info2AiApi.director() instanceof DirectorService service)) return;
        Long id = action(stack);
        if (id == null || !service.cooldown("used", id, 600)) return;
        String item = Registries.ITEM.getId(stack.getItem()).toString();
        emit(player.getServerWorld(), "director_gift_used", "info", "玩家用掉了伙伴送的" + item,
            "action_id", id, "item", item);
    }
}
