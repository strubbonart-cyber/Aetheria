package dev.strubbonart.skyblockqol.features.timer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses concise durations such as 1h30m, 5m, and 45s.
 *
 * Derived from the original Aetheria timer utilities.
 * Copyright (C) 2026 Hamlock and Contributors.
 * Licensed under the Aetheria Mod License (AML-1.0).
 */
public final class TimeFormatter {
    private static final Pattern TOKEN = Pattern.compile(
            "(\\d+)\\s*(days?|d|hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)?",
            Pattern.CASE_INSENSITIVE
    );

    private TimeFormatter() {
    }

    /**
     * Returns milliseconds, or -1 when the full input is not a valid duration.
     * A unitless token means seconds.
     */
    public static long parseDurationMs(String raw) {
        if (raw == null || raw.isBlank()) return -1L;

        Matcher matcher = TOKEN.matcher(raw);
        int cursor = 0;
        long total = 0L;
        boolean found = false;

        while (cursor < raw.length()) {
            while (cursor < raw.length() && Character.isWhitespace(raw.charAt(cursor))) {
                cursor++;
            }
            if (cursor >= raw.length()) break;

            matcher.region(cursor, raw.length());
            if (!matcher.lookingAt()) return -1L;

            try {
                long value = Long.parseLong(matcher.group(1));
                String unit = matcher.group(2);
                long multiplier;
                if (unit == null || unit.isEmpty() || unit.toLowerCase(Locale.ROOT).startsWith("s")) {
                    multiplier = 1_000L;
                } else {
                    char first = Character.toLowerCase(unit.charAt(0));
                    multiplier = switch (first) {
                        case 'd' -> 86_400_000L;
                        case 'h' -> 3_600_000L;
                        case 'm' -> 60_000L;
                        default -> 1_000L;
                    };
                }
                total = Math.addExact(total, Math.multiplyExact(value, multiplier));
                found = true;
            } catch (ArithmeticException | NumberFormatException exception) {
                return -1L;
            }

            cursor = matcher.end();
        }

        return found && total > 0L ? total : -1L;
    }

    public static String formatCountdown(long millis) {
        if (millis <= 0) return "0s";

        long seconds = millis / 1_000L;
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        if (days > 0) return String.format(Locale.ROOT, "%dd %dh %dm", days, hours, minutes);
        if (hours > 0) return String.format(Locale.ROOT, "%dh %dm", hours, minutes);
        if (minutes > 0) return String.format(Locale.ROOT, "%dm %ds", minutes, seconds);
        return String.format(Locale.ROOT, "%d.%ds", seconds, (millis % 1_000L) / 100L);
    }
}
