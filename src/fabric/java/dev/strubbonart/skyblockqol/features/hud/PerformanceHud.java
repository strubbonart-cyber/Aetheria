package dev.strubbonart.skyblockqol.features.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PerformanceHud {
    private static boolean enabled = true;

    private PerformanceHud() {
    }

    public static void initialize() {
        HudRenderCallback.EVENT.register(PerformanceHud::render);
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!enabled) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) {
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add("Skyblock QoL  |  Performance HUD");

        PlayerListEntry playerEntry = client.getNetworkHandler() == null
                ? null
                : client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());

        String ping = playerEntry == null ? "--" : playerEntry.getLatency() + " ms";
        lines.add("FPS: " + client.getCurrentFps() + "   Ping: " + ping);
        lines.add(String.format(
                Locale.ROOT,
                "XYZ: %.1f  %.1f  %.1f",
                client.player.getX(),
                client.player.getY(),
                client.player.getZ()
        ));
        lines.add(String.format(
                Locale.ROOT,
                "Yaw: %.1f   Pitch: %.1f",
                client.player.getYaw(),
                client.player.getPitch()
        ));

        int y = 8;
        for (String line : lines) {
            context.drawTextWithShadow(client.textRenderer, line, 8, y, 0xFFFFFF);
            y += 10;
        }
    }
}
