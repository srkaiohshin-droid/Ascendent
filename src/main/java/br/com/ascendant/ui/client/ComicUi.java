package br.com.ascendant.ui.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** Shared comic-print drawing helpers for the Ascendant catalog screens. */
@OnlyIn(Dist.CLIENT)
public final class ComicUi {
    public static final int BACKGROUND_WIDTH = 1024;
    public static final int BACKGROUND_HEIGHT = 576;
    private static final ResourceLocation HALFTONE =
            new ResourceLocation("ascendant", "textures/gui/halftone.png");

    private ComicUi() {
    }

    /** Paints the themed cover art, a readability veil and a subtle halftone screen. */
    public static void backdrop(GuiGraphics graphics, CatalogTheme theme, int width, int height) {
        graphics.fill(0, 0, width, height, 0xFF070A10);
        ResourceLocation background = theme.background();
        if (background != null) {
            float scale = Math.max(width / (float) BACKGROUND_WIDTH, height / (float) BACKGROUND_HEIGHT);
            int drawWidth = Math.round(BACKGROUND_WIDTH * scale);
            int drawHeight = Math.round(BACKGROUND_HEIGHT * scale);
            int drawX = (width - drawWidth) / 2;
            int drawY = (height - drawHeight) / 2;
            graphics.setColor(0.92F, 0.96F, 1.0F, 0.44F);
            graphics.blit(background, drawX, drawY, drawWidth, drawHeight,
                    0.0F, 0.0F, BACKGROUND_WIDTH, BACKGROUND_HEIGHT,
                    BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        graphics.fill(0, 0, width, height, 0x9E070A10);
        graphics.fillGradient(0, 0, width, 92, withAlpha(theme.accent(), 0x55), 0x00000000);
        graphics.fillGradient(0, height - 64, width, height, 0x00000000, withAlpha(theme.accent(), 0x3A));

        graphics.setColor(1.0F, 1.0F, 1.0F, 0.07F);
        for (int y = 0; y < height; y += 128) {
            for (int x = 0; x < width; x += 128) {
                graphics.blit(HALFTONE, x, y, 128, 128, 0.0F, 0.0F, 64, 64, 64, 64);
            }
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.fill(0, 0, width, 2, theme.accent());
        graphics.fill(0, height - 2, width, height, withAlpha(theme.accent(), 0xAA));
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height,
                             CatalogTheme theme, boolean strong) {
        graphics.fill(x, y, x + width, y + height, strong ? theme.panelStrong() : theme.panel());
        graphics.renderOutline(x, y, width, height, withAlpha(theme.accent(), 0x88));
        graphics.fill(x, y, x + 3, y + height, theme.accent());
        graphics.fill(x + 3, y, x + width, y + 1, withAlpha(theme.accentBright(), 0x55));
    }

    public static void headerBand(GuiGraphics graphics, int x, int y, int width, int height,
                                  CatalogTheme theme) {
        graphics.fill(x, y, x + width, y + height, withAlpha(theme.accent(), 0x2E));
        graphics.fill(x, y + height - 1, x + width, y + height, withAlpha(theme.accent(), 0xCC));
        graphics.fill(x, y, x + 4, y + height, theme.accent());
    }

    public static void badge(GuiGraphics graphics, Font font, String text, int x, int y,
                             int color, int textColor) {
        int width = font.width(text) + 8;
        graphics.fill(x, y, x + width, y + 11, color);
        graphics.renderOutline(x, y, width, 11, withAlpha(textColor, 0x66));
        graphics.drawString(font, text, x + 4, y + 2, textColor, false);
    }

    public static void stateDot(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 5, y + 5, color);
        graphics.fill(x + 1, y + 1, x + 4, y + 4, withAlpha(color, 0xFF));
    }

    public static void wrapped(GuiGraphics graphics, Font font, String text, int x, int y,
                               int maxWidth, int maxLines, int lineHeight, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), maxWidth);
        int limit = Math.min(lines.size(), Math.max(1, maxLines));
        for (int index = 0; index < limit; index++) {
            graphics.drawString(font, lines.get(index), x, y + index * lineHeight, color, false);
        }
    }

    public static int wrappedHeight(Font font, String text, int maxWidth, int lineHeight) {
        return Math.max(1, font.split(Component.literal(text), maxWidth).size()) * lineHeight;
    }

    public static String fit(Font font, String text, int maxWidth) {
        return font.plainSubstrByWidth(text == null ? "" : text, Math.max(8, maxWidth));
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int blend(int from, int to, float delta) {
        float clamped = Math.max(0.0F, Math.min(1.0F, delta));
        int a = Math.round(((from >> 24) & 0xFF) + (((to >> 24) & 0xFF) - ((from >> 24) & 0xFF)) * clamped);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * clamped);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * clamped);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
