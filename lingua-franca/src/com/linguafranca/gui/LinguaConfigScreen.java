package com.linguafranca.gui;

import com.linguafranca.LinguaConfig;
import com.linguafranca.LinguaFrancaTranslate;
import com.linguafranca.TextTranslator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 翻译设置：逐项开关 + 词库导入导出（固定文件夹，不弹系统窗口）。
 * 导出：config/lingua-franca/export/
 * 导入：config/lingua-franca/import/ 下所有 .tsv
 */
public class LinguaConfigScreen extends Screen {
    private static final int W = 152;
    private static final int H = 20;

    private String status = "";

    public LinguaConfigScreen() {
        super(Component.literal("Lingua Franca 翻译设置"));
    }

    private static Path root() {
        return LinguaFrancaTranslate.configPath().getParent().resolve("lingua-franca");
    }

    public static Path importFolder() {
        return root().resolve("import");
    }

    public static Path exportFolder() {
        return root().resolve("export");
    }

    @Override
    protected void init() {
        LinguaConfig cfg = LinguaConfig.get();
        if (cfg == null) {
            return;
        }
        int left = this.width / 2 - W - 2;
        int right = this.width / 2 + 2;
        int top = 36;
        int gap = 24;

        toggle(left, top, "总开关", () -> cfg.enabled, v -> cfg.enabled = v);
        toggle(right, top, "聊天", () -> cfg.translateChat, v -> cfg.translateChat = v);
        toggle(left, top + gap, "物品提示", () -> cfg.translateTooltip, v -> cfg.translateTooltip = v);
        toggle(right, top + gap, "物品名", () -> cfg.translateItemName, v -> cfg.translateItemName = v);
        toggle(left, top + gap * 2, "物品 Lore", () -> cfg.translateItemLore, v -> cfg.translateItemLore = v);
        toggle(right, top + gap * 2, "木牌", () -> cfg.translateSigns, v -> cfg.translateSigns = v);
        toggle(left, top + gap * 3, "浮空字", () -> cfg.translateTextDisplay, v -> cfg.translateTextDisplay = v);
        toggle(right, top + gap * 3, "成就", () -> cfg.translateAdvancements, v -> cfg.translateAdvancements = v);
        toggle(left, top + gap * 4, "出站聊天", () -> cfg.translateOutbound, v -> cfg.translateOutbound = v);
        toggle(right, top + gap * 4, "中文字体", () -> cfg.useDefaultFontForChinese, v -> cfg.useDefaultFontForChinese = v);

        int y = top + gap * 5 + 8;
        addRenderableWidget(Button.builder(Component.literal("导出词库"), b -> exportDictionary())
                .bounds(left, y, W, H).build());
        addRenderableWidget(Button.builder(Component.literal("导入词库"), b -> importDictionary())
                .bounds(right, y, W, H).build());

        int y2 = y + gap;
        addRenderableWidget(Button.builder(Component.literal("重新加载词表"), b -> {
            TextTranslator.invalidateCaches();
            status = "已重新加载词表";
        }).bounds(left, y2, W, H).build());
        addRenderableWidget(Button.builder(Component.literal("完成"), b -> this.onClose())
                .bounds(right, y2, W, H).build());
    }

    private void toggle(int x, int y, String label, Supplier<Boolean> get, Consumer<Boolean> set) {
        Button b = Button.builder(text(label, get.get()), btn -> {
            set.accept(!get.get());
            btn.setMessage(text(label, get.get()));
            LinguaConfig.save(LinguaFrancaTranslate.configPath(), LinguaConfig.get());
        }).bounds(x, y, W, H).build();
        addRenderableWidget(b);
    }

    private static Component text(String label, boolean on) {
        return Component.literal(label + (on ? "：开" : "：关"));
    }

    private void exportDictionary() {
        try {
            Path dir = exportFolder();
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path file = dir.resolve("dict-" + LinguaFrancaTranslate.cache().scope() + "-" + stamp + ".tsv");
            int n = LinguaFrancaTranslate.cache().exportTo(file);
            status = n >= 0 ? "已导出 " + n + " 条 → " + file : "导出失败";
        } catch (Throwable t) {
            status = "导出失败：" + t.getMessage();
        }
    }

    private void importDictionary() {
        try {
            Path dir = importFolder();
            Files.createDirectories(dir);
            int n = LinguaFrancaTranslate.cache().importFolder(dir);
            TextTranslator.invalidateCaches();
            status = n > 0 ? "已导入 " + n + " 条" : "import 文件夹里没有 .tsv";
        } catch (Throwable t) {
            status = "导入失败：" + t.getMessage();
        }
    }

    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor gfx,
                                   int mouseX, int mouseY, float partial) {
        super.extractRenderState(gfx, mouseX, mouseY, partial);
        try {
            gfx.text(this.font, this.title,
                    this.width / 2 - this.font.width(this.title) / 2, 12, 0xFFFFFF);
            var hint = Component.literal("导出 → " + exportFolder() + "    导入 ← " + importFolder());
            gfx.text(this.font, hint, 8, this.height - 26, 0xAAAAAA);
            if (!status.isEmpty()) {
                gfx.text(this.font, Component.literal(status), 8, this.height - 12, 0x7CFC00);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
