package com.example.info2ai.capture.client;

import com.example.info2ai.model.PlayerInfo;
import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.block.FluidBlock;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.*;

/** 只在客户端线程读拥有的字段，不触碰服务端玩家对象。 */
public final class ClientPlayerSection {
    public PlayerInfo collect(MinecraftClient client) {
        JsonObject result = new JsonObject();
        String screen = screen(client.currentScreen);
        result.add("screen", screen == null ? JsonNull.INSTANCE : new JsonPrimitive(screen));
        result.add("looking_at", lookingAt(client));
        JsonObject inputs = new JsonObject();
        inputs.addProperty("forward", client.options.forwardKey.isPressed());
        inputs.addProperty("jump", client.options.jumpKey.isPressed());
        inputs.addProperty("sneak", client.options.sneakKey.isPressed());
        inputs.addProperty("sprint", client.options.sprintKey.isPressed());
        inputs.addProperty("attack", client.options.attackKey.isPressed());
        inputs.addProperty("use", client.options.useKey.isPressed());
        Long idle=ClientObservations.idleTicks(client.world.getTime());
        if(idle!=null)inputs.addProperty("idle_ticks",idle);
        result.add("inputs", inputs);
        JsonObject view = new JsonObject();
        view.addProperty("fov", client.options.getFov().getValue());
        view.addProperty("render_distance", client.options.getViewDistance().getValue());
        view.addProperty("gui_scale", client.getWindow().getScaleFactor());
        JsonArray window = new JsonArray();
        window.add(client.getWindow().getFramebufferWidth()); window.add(client.getWindow().getFramebufferHeight());
        view.add("window", window); result.add("view", view);
        return PlayerInfo.fromJson(result);
    }
    private static String screen(Screen screen) {
        if (screen == null) return null;
        if (screen instanceof GenericContainerScreen) return "chest";
        if (screen instanceof InventoryScreen || screen instanceof CreativeInventoryScreen) return "inventory";
        if (screen instanceof CraftingScreen) return "crafting";
        if (screen instanceof MerchantScreen) return "merchant";
        if (screen instanceof FurnaceScreen) return "furnace";
        if (screen instanceof AnvilScreen) return "anvil";
        if (screen instanceof EnchantmentScreen) return "enchanting";
        if (screen instanceof ShulkerBoxScreen) return "shulker_box";
        if (screen instanceof GameMenuScreen) return "pause";
        if (screen instanceof ChatScreen) return "chat";
        return "other";
    }
    private static JsonObject lookingAt(MinecraftClient client) {
        JsonObject result = new JsonObject();
        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() == HitResult.Type.MISS) { result.addProperty("type", "miss"); return result; }
        result.addProperty("distance", client.player.getEyePos().distanceTo(hit.getPos()));
        JsonArray pos = new JsonArray();
        if (hit instanceof BlockHitResult block) {
            var at = block.getBlockPos(); var state = client.world.getBlockState(at);
            var fluid = client.world.getFluidState(at);
            boolean isFluid = state.getBlock() instanceof FluidBlock && !fluid.isEmpty();
            result.addProperty("type", isFluid ? "fluid" : "block");
            result.addProperty("id", isFluid ? Registries.FLUID.getId(fluid.getFluid()).toString() : Registries.BLOCK.getId(state.getBlock()).toString());
            pos.add(at.getX()); pos.add(at.getY()); pos.add(at.getZ());
        } else if (hit instanceof EntityHitResult entity) {
            result.addProperty("type", "entity");
            result.addProperty("id", Registries.ENTITY_TYPE.getId(entity.getEntity().getType()).toString());
            result.addProperty("entity_name", entity.getEntity().getName().getString());
            var at = entity.getEntity().getPos(); pos.add(at.x); pos.add(at.y); pos.add(at.z);
        }
        result.add("pos", pos); return result;
    }
}
