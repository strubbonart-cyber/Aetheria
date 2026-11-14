package dev.strubbonart.skyblockqol.features.timer;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Fabric implementation of the original countdown timer feature.
 *
 * Copyright (C) 2026 Hamlock and Contributors.
 * Licensed under the Aetheria Mod License (AML-1.0).
 */
public final class CountdownTimer {
    private static final TimerManager TIMER = TimerManager.getInstance();
    private static final String PREFIX = "[Timer] ";

    private CountdownTimer() {
    }

    public static void initialize() {
        registerCommands();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (TIMER.pollExpired() && client.player != null) {
                client.player.sendMessage(
                        Text.literal(PREFIX + "Countdown finished!").formatted(Formatting.GOLD, Formatting.BOLD),
                        false
                );
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden || !TIMER.isActive()) {
                return;
            }

            String label = TIMER.isPaused() ? "Timer (paused): " : "Timer: ";
            Text text = Text.literal(label).formatted(Formatting.AQUA, Formatting.BOLD)
                    .append(Text.literal(TimeFormatter.formatCountdown(TIMER.getRemainingMs()))
                            .formatted(remainingColor()));

            drawContext.drawTextWithShadow(client.textRenderer, text, 8, 52, 0xFFFFFF);
        });
    }

    private static Formatting remainingColor() {
        long remaining = TIMER.getRemainingMs();
        long total = TIMER.getTotalDurationMs();
        if (total <= 0) return Formatting.WHITE;
        double ratio = (double) remaining / (double) total;
        if (ratio > 0.5) return Formatting.GREEN;
        if (ratio > 0.25) return Formatting.YELLOW;
        return Formatting.RED;
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var timerRoot = ClientCommandManager.literal("timer")
                    .executes(context -> show(context.getSource()))
                    .then(ClientCommandManager.literal("show")
                            .executes(context -> show(context.getSource())))
                    .then(ClientCommandManager.literal("cancel")
                            .executes(context -> cancel(context.getSource())))
                    .then(ClientCommandManager.literal("stop")
                            .executes(context -> cancel(context.getSource())))
                    .then(ClientCommandManager.literal("pause")
                            .executes(context -> pause(context.getSource())))
                    .then(ClientCommandManager.literal("resume")
                            .executes(context -> resume(context.getSource())))
                    .then(ClientCommandManager.literal("add")
                            .then(ClientCommandManager.argument("duration", StringArgumentType.greedyString())
                                    .executes(context -> add(context.getSource(),
                                            StringArgumentType.getString(context, "duration")))))
                    .then(ClientCommandManager.argument("duration", StringArgumentType.greedyString())
                            .executes(context -> start(context.getSource(),
                                    StringArgumentType.getString(context, "duration"))));

            var registered = dispatcher.register(timerRoot);
            dispatcher.register(ClientCommandManager.literal("asmtimer").redirect(registered));
        });
    }

    private static int show(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        if (!TIMER.isActive()) {
            source.sendFeedback(Text.literal(PREFIX + "No timer is running. Try /timer 5m."));
            return 1;
        }
        String state = TIMER.isPaused() ? " (paused)" : "";
        source.sendFeedback(Text.literal(PREFIX + "Remaining: "
                + TimeFormatter.formatCountdown(TIMER.getRemainingMs()) + state));
        return 1;
    }

    private static int start(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source,
                             String input) {
        long duration = TimeFormatter.parseDurationMs(input);
        if (duration <= 0) {
            source.sendError(Text.literal("Invalid duration. Examples: 30s, 5m, 1h30m."));
            return 0;
        }
        TIMER.start(duration);
        source.sendFeedback(Text.literal(PREFIX + "Started a "
                + TimeFormatter.formatCountdown(duration) + " countdown."));
        return 1;
    }

    private static int add(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source,
                           String input) {
        long duration = TimeFormatter.parseDurationMs(input);
        if (duration <= 0) {
            source.sendError(Text.literal("Invalid duration. Examples: 30s, 5m, 1h30m."));
            return 0;
        }
        if (!TIMER.isActive()) {
            return start(source, input);
        }
        TIMER.addTime(duration);
        source.sendFeedback(Text.literal(PREFIX + "Added "
                + TimeFormatter.formatCountdown(duration) + ". Remaining: "
                + TimeFormatter.formatCountdown(TIMER.getRemainingMs())));
        return 1;
    }

    private static int pause(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        if (!TIMER.isRunning()) {
            source.sendError(Text.literal("No running timer to pause."));
            return 0;
        }
        TIMER.pause();
        source.sendFeedback(Text.literal(PREFIX + "Paused with "
                + TimeFormatter.formatCountdown(TIMER.getRemainingMs()) + " remaining."));
        return 1;
    }

    private static int resume(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        if (!TIMER.isPaused()) {
            source.sendError(Text.literal("The timer is not paused."));
            return 0;
        }
        TIMER.resume();
        source.sendFeedback(Text.literal(PREFIX + "Timer resumed."));
        return 1;
    }

    private static int cancel(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        if (!TIMER.isActive()) {
            source.sendError(Text.literal("No timer is running."));
            return 0;
        }
        TIMER.cancel();
        source.sendFeedback(Text.literal(PREFIX + "Timer cancelled."));
        return 1;
    }
}
