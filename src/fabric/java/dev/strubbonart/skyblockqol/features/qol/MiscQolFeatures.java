package dev.strubbonart.skyblockqol.features.qol;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small client-only compatibility QoL features. */
public final class MiscQolFeatures {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("skyblockqol-misc-qol.properties");
    private static final Pattern SKYBLOCK_XP = Pattern.compile("\\+([\\d,]+) SkyBlock XP");
    private static boolean initialized;
    private static boolean skyblockXpInChat;
    private static boolean disableEntityFire;
    private static boolean noSwapAnimation;
    private static boolean itemStackTips = true;
    private static boolean damageFormatter = true;
    private static String lastSkyblockXpAmount;

    private MiscQolFeatures() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        load();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) return;
            String plain = message.getString().replaceAll("§[0-9A-FK-ORa-fk-or]", "");
            Matcher matcher = SKYBLOCK_XP.matcher(plain);
            if (!matcher.find()) {
                lastSkyblockXpAmount = null;
                return;
            }
            String amount = matcher.group(1);
            if (!skyblockXpInChat || amount.equals(lastSkyblockXpAmount)) return;
            lastSkyblockXpAmount = amount;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("+" + amount + " SkyBlock XP"), false);
            }
        });
    }

    public static boolean isSkyblockXpInChatEnabled() { return skyblockXpInChat; }
    public static void setSkyblockXpInChatEnabled(boolean value) {
        skyblockXpInChat = value;
        save();
    }
    public static boolean isDisableEntityFireEnabled() { return disableEntityFire; }
    public static boolean isNoSwapAnimationEnabled() { return noSwapAnimation; }
    public static boolean isItemStackTipsEnabled() { return itemStackTips; }
    public static boolean isDamageFormatterEnabled() { return damageFormatter; }
    public static void setDamageFormatterEnabled(boolean value) { damageFormatter = value; save(); }
    public static void setItemStackTipsEnabled(boolean value) { itemStackTips = value; save(); }
    public static void setNoSwapAnimationEnabled(boolean value) { noSwapAnimation = value; save(); }
    public static void setDisableEntityFireEnabled(boolean value) {
        disableEntityFire = value;
        save();
    }

    private static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            skyblockXpInChat = Boolean.parseBoolean(properties.getProperty("skyblockXpInChat", "false"));
            disableEntityFire = Boolean.parseBoolean(properties.getProperty("disableEntityFire", "false"));
            noSwapAnimation = Boolean.parseBoolean(properties.getProperty("noSwapAnimation", "false"));
            itemStackTips = Boolean.parseBoolean(properties.getProperty("itemStackTips", "true"));
            damageFormatter = Boolean.parseBoolean(properties.getProperty("damageFormatter", "true"));
        } catch (IOException ignored) {
            skyblockXpInChat = false;
            disableEntityFire = false;
            noSwapAnimation = false;
            itemStackTips = true;
            damageFormatter = true;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("skyblockXpInChat", Boolean.toString(skyblockXpInChat));
        properties.setProperty("disableEntityFire", Boolean.toString(disableEntityFire));
        properties.setProperty("noSwapAnimation", Boolean.toString(noSwapAnimation));
        properties.setProperty("itemStackTips", Boolean.toString(itemStackTips));
        properties.setProperty("damageFormatter", Boolean.toString(damageFormatter));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "Skyblock QoL Misc Settings");
            }
        } catch (IOException ignored) {
            // Keep in-memory settings active for the current session.
        }
    }
}
