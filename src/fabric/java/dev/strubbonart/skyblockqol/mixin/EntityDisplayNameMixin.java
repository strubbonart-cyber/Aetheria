package dev.strubbonart.skyblockqol.mixin;

import dev.strubbonart.skyblockqol.features.qol.MiscQolFeatures;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compacts numeric floating armor-stand labels such as SkyBlock damage numbers. */
@Mixin(Entity.class)
public abstract class EntityDisplayNameMixin {
    private static final Pattern NUMBER = Pattern.compile("^\\s*([\\d,]+)(?:\\.(\\d+))?\\s*$");

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void skyblockqol$compactDamageLabel(CallbackInfoReturnable<Text> cir) {
        if (!MiscQolFeatures.isDamageFormatterEnabled() || !((Object) this instanceof ArmorStandEntity)) return;
        Text original = cir.getReturnValue();
        if (original == null) return;

        Matcher matcher = NUMBER.matcher(original.getString().replaceAll("§[0-9A-FK-ORa-fk-or]", ""));
        if (!matcher.matches()) return;

        try {
            long value = Long.parseLong(matcher.group(1).replace(",", ""));
            String formatted = compact(value);
            if (formatted.equals(matcher.group(1))) return;
            cir.setReturnValue(Text.literal(formatted).setStyle(original.getStyle()));
        } catch (NumberFormatException ignored) {
            // Leave unusually large or malformed labels unchanged.
        }
    }

    private static String compact(long value) {
        if (value >= 1_000_000_000L) return decimal(value / 1_000_000_000.0D) + "B";
        if (value >= 1_000_000L) return decimal(value / 1_000_000.0D) + "M";
        if (value >= 1_000L) return decimal(value / 1_000.0D) + "K";
        return Long.toString(value);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value).replaceAll("\\.0$", "");
    }
}
