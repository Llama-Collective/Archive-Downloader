package com.andrews.st2downloader.util;

import org.joml.Matrix3x2fStack;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class RenderUtil {
    private RenderUtil() {}

    public static void fillRect(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y2, color);
    }

    public static void enableScissor(DrawContext context, int x1, int y1, int x2, int y2) {
        context.enableScissor(x1, y1, x2, y2);
    }

    public static void disableScissor(DrawContext context) {
        context.disableScissor();
    }

    public static void drawString(DrawContext context, TextRenderer font, String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) return;
        context.drawText(font, text, x, y, color, false);
    }

    public static void drawString(DrawContext context, TextRenderer font, Text text, int x, int y, int color) {
        if (text == null) return;
        context.drawText(font, text, x, y, color, false);
    }

    public static void drawCenteredString(DrawContext context, TextRenderer font, String text, int centerX, int y, int color) {
        if (text == null || text.isEmpty()) return;
        context.drawText(font, text, centerX - font.getWidth(text) / 2, y, color, false);
    }

    public static void blit(DrawContext context, RenderPipeline pipeline, Identifier texture, int x, int y, int u, int v, int width, int height, int texWidth, int texHeight) {
        context.drawTexture(pipeline, texture, x, y, u, v, width, height, texWidth, texHeight);
    }

    public static void drawScaledString(DrawContext context, String text, int x, int y, int color, float scale) {
        if (text == null || text.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer font = client.textRenderer;
        Matrix3x2fStack matrix = context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y, matrix);
        context.getMatrices().scale(scale, scale, matrix);
        context.drawText(font, text, 0, 0, color, false);
        context.getMatrices().popMatrix();
    }

    public static void drawScaledString(DrawContext context, String text, int x, int y, int color, float scale, int maxWidth) {
        if (text == null || text.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer font = client.textRenderer;
        String clipped = text;
        if (maxWidth > 0) {
            float scaledWidth = font.getWidth(text) * scale;
            if (scaledWidth > maxWidth) {
                while (!clipped.isEmpty() && font.getWidth(clipped + "...") * scale > maxWidth) {
                    clipped = clipped.substring(0, clipped.length() - 1);
                }
                clipped = clipped + "...";
            }
        }
        drawScaledString(context, clipped, x, y, color, scale);
    }

    public static void drawWrappedText(DrawContext context, TextRenderer font, String text, int textX, int textY, int maxWidth, int color) {
        if (text == null || text.isEmpty()) return;
        int lineY = textY;
        String[] paragraphs = text.split("\\r?\\n");
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lineY += 10;
                continue;
            }
            String[] words = paragraph.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String testLine = !line.isEmpty() ? line + " " + word : word;
                int testWidth = font.getWidth(testLine);
                if (testWidth > maxWidth && !line.isEmpty()) {
                    drawString(context, font, line.toString(), textX, lineY, color);
                    line = new StringBuilder(word);
                    lineY += 10;
                } else {
                    line = new StringBuilder(testLine);
                }
            }
            if (!line.isEmpty()) {
                drawString(context, font, line.toString(), textX, lineY, color);
                lineY += 10;
            }
        }
    }

    public static int getWrappedTextHeight(TextRenderer font, String text, int maxWidth) {
        if (text == null || text.isEmpty()) return 10;
        int lines = 0;
        String[] paragraphs = text.split("\\r?\\n");
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines++;
                continue;
            }
            String[] words = paragraph.split(" ");
            StringBuilder line = new StringBuilder();
            int paragraphLines = 1;
            for (String word : words) {
                String testLine = !line.isEmpty() ? line + " " + word : word;
                int testWidth = font.getWidth(testLine);
                if (testWidth > maxWidth && !line.isEmpty()) {
                    line = new StringBuilder(word);
                    paragraphLines++;
                } else {
                    line = new StringBuilder(testLine);
                }
            }
            lines += paragraphLines;
        }
        return Math.max(lines, 1) * 10;
    }
}
