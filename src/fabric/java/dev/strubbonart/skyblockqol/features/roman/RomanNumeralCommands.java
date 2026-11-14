package dev.strubbonart.skyblockqol.features.roman;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

/**
 * Fabric client command for the Roman numeral conversion utility.
 *
 * Copyright (C) 2026 Hamlock and Contributors.
 * Licensed under the Aetheria Mod License (AML-1.0).
 */
public final class RomanNumeralCommands {
    private RomanNumeralCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("roman")
                        .then(ClientCommandManager.argument("numeral", StringArgumentType.word())
                                .executes(context -> {
                                    String input = StringArgumentType.getString(context, "numeral");
                                    if (!RomanNumeralParser.isValid(input)) {
                                        context.getSource().sendError(Text.literal(
                                                "Invalid Roman numeral. Use canonical numerals from I to MMMCMXCIX."
                                        ));
                                        return 0;
                                    }
                                    int value = RomanNumeralParser.parse(input);
                                    context.getSource().sendFeedback(Text.literal(input.toUpperCase()
                                            + " = " + value));
                                    return 1;
                                }))));
    }
}
