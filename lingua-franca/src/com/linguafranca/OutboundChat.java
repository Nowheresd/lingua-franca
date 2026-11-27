package com.linguafranca;

import com.linguafranca.core.Normalizer;
import com.linguafranca.core.TranslationEngine;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** 把中文聊天翻成英文再发出去。 */
public final class OutboundChat {
    private static final ThreadLocal<Boolean> SENDING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private OutboundChat() {
    }

    public static boolean handle(ClientPacketListener listener, String message) {
        if (Boolean.TRUE.equals(SENDING.get())) {
            return false;
        }
        LinguaConfig cfg = TextTranslator.configRef();
        TranslationEngine engine = TextTranslator.engineRef();
        if (cfg == null || engine == null || !cfg.enabled || !cfg.translateOutbound || message == null) {
            return false;
        }
        String text = message.trim();
        if (text.isEmpty() || text.startsWith("/") || !Normalizer.hasCjk(text)) {
            return false;
        }
        String quick = OutboundGlossary.lookup(text);
        if (quick != null) {
            send(listener, quick);
            return true;
        }
        if (!BUSY.compareAndSet(false, true)) {
            return false;
        }
        engine.translateOutbound(text)
                .orTimeout(8, TimeUnit.SECONDS)
                .whenComplete((en, err) -> {
                    String out = en != null && !en.isBlank() ? en : text;
                    Minecraft mc = Minecraft.getInstance();
                    Runnable task = () -> {
                        try {
                            send(listener, out);
                        } finally {
                            BUSY.set(false);
                        }
                    };
                    if (mc != null) {
                        mc.execute(task);
                    } else {
                        task.run();
                    }
                });
        return true;
    }

    private static void send(ClientPacketListener listener, String text) {
        SENDING.set(Boolean.TRUE);
        try {
            listener.sendChat(text);
        } finally {
            SENDING.set(Boolean.FALSE);
        }
    }
}
