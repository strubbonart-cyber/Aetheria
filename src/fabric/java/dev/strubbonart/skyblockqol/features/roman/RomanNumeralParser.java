package dev.strubbonart.skyblockqol.features.roman;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Strict parser for canonical Roman numerals from I through MMMCMXCIX.
 *
 * Derived from the original Aetheria Roman numeral utility.
 * Copyright (C) 2026 Hamlock and Contributors.
 * Licensed under the Aetheria Mod License (AML-1.0).
 */
public final class RomanNumeralParser {
    private static final Pattern VALID = Pattern.compile(
            "^(?=.+)M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$"
    );
    private static final Map<Character, Integer> VALUES = Map.of(
            'I', 1, 'V', 5, 'X', 10, 'L', 50, 'C', 100, 'D', 500, 'M', 1_000
    );

    private RomanNumeralParser() {
    }

    public static boolean isValid(String input) {
        return input != null && !input.isEmpty()
                && VALID.matcher(input.toUpperCase(java.util.Locale.ROOT)).matches();
    }

    public static int parse(String input) {
        if (!isValid(input)) {
            throw new IllegalArgumentException("Not a canonical Roman numeral: " + input);
        }

        String numeral = input.toUpperCase(java.util.Locale.ROOT);
        int total = 0;
        for (int index = 0; index < numeral.length(); index++) {
            int current = VALUES.get(numeral.charAt(index));
            if (index + 1 < numeral.length()
                    && VALUES.get(numeral.charAt(index + 1)) > current) {
                total -= current;
            } else {
                total += current;
            }
        }
        return total;
    }
}
