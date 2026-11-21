package com.example.info2ai.input;

import com.example.info2ai.api.DirectorPort;
import com.example.info2ai.api.Info2AiApi;
import com.example.info2ai.http.ApiException;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class DirectorKey {
    private DirectorKey() { }

    public static void register() {
        var key = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.info2ai.director_toggle",
            GLFW.GLFW_KEY_F8, "key.categories.info2ai"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.wasPressed()) {
                var director = Info2AiApi.director();
                if (client.player == null || director == DirectorPort.NONE) continue;
                boolean on = !director.status().enabled();
                try {
                    director.enabled(on);
                    client.player.sendMessage(Text.literal(on ? "伙伴导演：已开启" : "伙伴导演：已关闭"), true);
                } catch (ApiException failure) {
                    client.player.sendMessage(Text.literal(failure.code().equals("guard_missing")
                        ? "伙伴导演：运行时护栏缺失" : "伙伴导演：无法保存开关"), true);
                }
            }
        });
    }
}
