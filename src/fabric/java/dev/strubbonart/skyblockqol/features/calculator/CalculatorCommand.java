package dev.strubbonart.skyblockqol.features.calculator;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

/**
 * Local client-side calculator command.
 * Ported from the original Aetheria calculator feature.
 * Copyright (C) 2026 Hamlock and Contributors.
 * Licensed under the Aetheria Mod License (AML-1.0).
 */
public final class CalculatorCommand {
    private CalculatorCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = dispatcher.register(ClientCommandManager.literal("calc")
                    .then(ClientCommandManager.argument("expression", StringArgumentType.greedyString())
                            .executes(context -> {
                                String expression = StringArgumentType.getString(context, "expression");
                                String result = CalculatorUtils.calculateAndFormat(expression);
                                if (result == null) {
                                    context.getSource().sendError(Text.literal(
                                            "Could not calculate that expression. Check the syntax and try again."
                                    ));
                                    return 0;
                                }
                                context.getSource().sendFeedback(Text.literal(expression + " = " + result));
                                return 1;
                            })));

            dispatcher.register(ClientCommandManager.literal("athrcalc").redirect(root));
        });
    }
}
