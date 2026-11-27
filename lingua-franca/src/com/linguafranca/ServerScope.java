package com.linguafranca;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/** 当前在哪个服：用来分文件存译文，国服/外服互不干扰。 */
public final class ServerScope {
    private ServerScope() {
    }

    public static String current() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return "menu";
            }
            ServerData data = mc.getCurrentServer();
            if (data != null && data.ip != null && !data.ip.isBlank()) {
                return "server-" + sanitize(data.ip);
            }
            if (mc.hasSingleplayerServer()) {
                return "singleplayer";
            }
            return "menu";
        } catch (Throwable t) {
            return "menu";
        }
    }

    private static String sanitize(String s) {
        String out = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return out.length() > 48 ? out.substring(0, 48) : out;
    }
}
