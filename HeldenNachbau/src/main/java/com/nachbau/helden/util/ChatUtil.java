package com.nachbau.helden.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Baut den Chat-Prefix "[Helden] " (weiss/[ , aqu Helden, weiss ]). */
public final class ChatUtil {

    private ChatUtil() {
    }

    public static Component getPrefix() {
        return Component.text("[").color(NamedTextColor.WHITE)
                .append(Component.text("Helden").color(NamedTextColor.AQUA))
                .append(Component.text("] ").color(NamedTextColor.WHITE));
    }
}
