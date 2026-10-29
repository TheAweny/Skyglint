package dev.skyglint.smoke;

import dev.skyglint.ui.text.SettingsText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Dev-only Inter proof sheet. Capture at 1920×1080 with GUI scales 2 and 3. */
public final class SettingsFontProofScreen extends Screen {
    private static final int INK = 0xFFF1F2F3;
    private static final int MUTED = 0xFFA9ADB4;
    private static final String LATIN = "Hamburgefontsiv / Minimum 0123456789";
    private static final String CYRILLIC = "Настройки · Обновления · Сохранить · Ёж";
    private long extractionNanos;
    private int frames;
    private int measuredFrames;

    public SettingsFontProofScreen() {
        super(Component.literal("Settings Inter font proof"));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        long began = System.nanoTime();
        float previousOpacity = SettingsText.INSTANCE.getOpacity();
        SettingsText.INSTANCE.setOpacity(1f);
        try {
            graphics.fill(0, 0, width, height, 0xFF17191D);
            SettingsText.INSTANCE.drawSized(graphics, "Inter / settings font proof", 24f, 22f, 17f, INK);
            var window = Minecraft.getInstance().getWindow();
            small(graphics, "GUI " + window.getGuiScale() + " · " + width + " × " + height
                + " GUI units · " + window.getWidth() + " × " + window.getHeight() + " pixels", 24f, 48f, MUTED);
            specimen(graphics, "8.1 / secondary", 8.1f, 73f, 87f, 100f);
            specimen(graphics, "9.5 / body", 9.5f, 122f, 137f, 152f);
            specimen(graphics, "17 / heading", 17f, 176f, 191f, 215f);

            small(graphics, "Fractional x origins / unchanged advance and cap height", 24f, 250f, MUTED);
            float column = (width - 48f) / 4f;
            for (int i = 0; i < 4; i++) {
                float x = 24f + i * column;
                small(graphics, "+" + (i * .25f), x, 265f, MUTED);
                SettingsText.INSTANCE.drawSized(graphics, "Hh Щщ Яя 012", x + i * .25f, 278f, 9.5f, INK);
            }

            int[] alphas = {255, 128, 26, 8};
            for (int i = 0; i < alphas.length; i++) {
                float x = 24f + i * column;
                small(graphics, "Alpha " + alphas[i] + "/255", x, 301f, MUTED);
                SettingsText.INSTANCE.drawSized(graphics, "Inter · Кириллица", x, 314f, 9.5f,
                    (alphas[i] << 24) | 0x00F1F2F3);
            }
            String timing = measuredFrames == 0 ? "CPU extraction: warming font cache"
                : String.format(Locale.ROOT, "CPU extraction %.3f ms / frame · GPU time not measured",
                    extractionNanos / (double) measuredFrames / 1_000_000.0);
            small(graphics, timing, 24f, height - 16f, MUTED);
        } finally {
            SettingsText.INSTANCE.setOpacity(previousOpacity);
            if (++frames > 30) {
                extractionNanos += System.nanoTime() - began;
                measuredFrames++;
            }
        }
    }

    private void specimen(GuiGraphicsExtractor graphics, String label, float size,
                          float labelY, float latinY, float cyrillicY) {
        small(graphics, label, 24f, labelY, MUTED);
        measuredLine(graphics, LATIN, size, latinY);
        measuredLine(graphics, CYRILLIC, size, cyrillicY);
    }

    private void measuredLine(GuiGraphicsExtractor graphics, String text, float size, float y) {
        String fitted = SettingsText.INSTANCE.fit(text, width - 60, size);
        int measured = SettingsText.INSTANCE.width(fitted, size);
        // An advance-width ruler, drawn underneath the sample rather than through its ink.
        int lineY = (int) Math.ceil(y + size + 2);
        graphics.fill(24, lineY, 24 + measured, lineY + 1, 0xFF30343B);
        graphics.fill(24 + measured, lineY - 2, 25 + measured, lineY + 2, 0xFF6E8297);
        SettingsText.INSTANCE.drawSized(graphics, fitted, 24f, y, size, INK);
    }

    private void small(GuiGraphicsExtractor graphics, String text, float x, float y, int color) {
        String fitted = SettingsText.INSTANCE.fit(text, Math.max(0, width - (int) x - 24), 8.1f);
        SettingsText.INSTANCE.drawSized(graphics, fitted, x, y, 8.1f, color);
    }
}
