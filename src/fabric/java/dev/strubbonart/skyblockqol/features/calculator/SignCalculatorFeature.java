package dev.strubbonart.skyblockqol.features.calculator;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Expression calculator for signs. Put ^^^^^^ or 15 carets on line two. */
public final class SignCalculatorFeature {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-sign-calculator.properties");
    private static final String SHORT_TRIGGER = "^^^^^^";
    private static final String LONG_TRIGGER = "^^^^^^^^^^^^^^^";
    private static boolean enabled = true;
    private static boolean initialized;

    private SignCalculatorFeature() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
    }

    public static boolean isEnabled() { return enabled; }

    public static void setEnabled(boolean value) {
        enabled = value;
        save();
    }

    public static String preview(String[] lines) {
        if (!enabled || !hasTrigger(lines)) return null;
        String expression = lines[0] == null ? "" : lines[0].trim();
        if (expression.isEmpty() || CalculatorUtils.isPlainNumber(expression)) return null;
        try {
            BigDecimal result = CalculatorUtils.calculate(expression);
            String formatted = CalculatorUtils.FORMAT.format(result);
            if (formatted.length() > 16) return expression + " = Result too long";
            return expression + " = " + formatted;
        } catch (CalculatorUtils.CalculatorException error) {
            return "Error: " + error.getMessage();
        }
    }

    public static void apply(String[] lines) {
        if (!enabled || !hasTrigger(lines)) return;
        String expression = lines[0] == null ? "" : lines[0].trim();
        if (expression.isEmpty() || CalculatorUtils.isPlainNumber(expression)) return;
        try {
            BigDecimal result = CalculatorUtils.calculate(expression);
            String plain = result.stripTrailingZeros().toPlainString();
            if (!plain.isBlank()) lines[0] = plain;
        } catch (CalculatorUtils.CalculatorException ignored) {
            // Keep the user's expression if it cannot be evaluated.
        }
    }

    private static boolean hasTrigger(String[] lines) {
        if (lines == null || lines.length < 2) return false;
        String trigger = lines[1] == null ? "" : lines[1].trim();
        return SHORT_TRIGGER.equals(trigger) || LONG_TRIGGER.equals(trigger);
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
        } catch (IOException ignored) {
            enabled = true;
        }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Skyblock QoL Sign Calculator");
            }
        } catch (IOException ignored) {
            // Keep the current preference for this session.
        }
    }
}
