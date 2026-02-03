package com.andrews.archivedownloader.util;

import org.joml.Matrix3x2fStack;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class RenderUtil {
    private RenderUtil() {}

    public static void fillRect(GuiGraphics context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y2, color);
    }

    public static void enableScissor(GuiGraphics context, int x1, int y1, int x2, int y2) {
        context.enableScissor(x1, y1, x2, y2);
    }

    public static void disableScissor(GuiGraphics context) {
        context.disableScissor();
    }

    public static void drawString(GuiGraphics context, Font font, String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) return;
        context.drawString(font, text, x, y, color, false);
    }

    public static void drawString(GuiGraphics context, Font font, Component text, int x, int y, int color) {
        if (text == null) return;
        context.drawString(font, text, x, y, color, false);
    }

    public static void drawCenteredString(GuiGraphics context, Font font, String text, int centerX, int y, int color) {
        if (text == null || text.isEmpty()) return;
        context.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    public static void blit(GuiGraphics context, RenderPipeline pipeline, Identifier texture, int x, int y, int u, int v, int width, int height, int texWidth, int texHeight) {
        context.blit(pipeline, texture, x, y, u, v, width, height, texWidth, texHeight);
    }

    public static void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
      fillRect(context, x, y, x + width, y + 1, color);
      fillRect(context, x, y + height - 1, x + width, y + height, color);
      fillRect(context, x, y + 1, x + 1, y + height - 1, color);
      fillRect(context, x + width - 1, y + 1, x + width, y + height - 1, color);
   }

    public static void drawScaledString(GuiGraphics context, String text, int x, int y, int color, float scale) {
        if (text == null || text.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        Font font = client.font;
        Matrix3x2fStack matrix = context.pose().pushMatrix();
        context.pose().translate(x, y, matrix);
        context.pose().scale(scale, scale, matrix);
        context.drawString(font, text, 0, 0, color, false);
        context.pose().popMatrix();
    }

    public static void drawScaledString(GuiGraphics context, String text, int x, int y, int color, float scale, int maxWidth) {
        if (text == null || text.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        Font font = client.font;
        String clipped = text;
        if (maxWidth > 0) {
            float scaledWidth = font.width(text) * scale;
            if (scaledWidth > maxWidth) {
                while (!clipped.isEmpty() && font.width(clipped + "...") * scale > maxWidth) {
                    clipped = clipped.substring(0, clipped.length() - 1);
                }
                clipped = clipped + "...";
            }
        }
        drawScaledString(context, clipped, x, y, color, scale);
    }

    public static void drawWrappedText(GuiGraphics context, Font font, String text, int textX, int textY, int maxWidth, int color) {
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
                int testWidth = font.width(testLine);
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

    public static int getWrappedTextHeight(Font font, String text, int maxWidth) {
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
                int testWidth = font.width(testLine);
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
