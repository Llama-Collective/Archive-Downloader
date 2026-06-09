package com.andrews.archivedownloader.util;

//? >=1.21.6
 import org.joml.Matrix3x2fStack;

import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.gui.UiFont;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import com.andrews.archivedownloader.wrapper.render.UiRenderPipeline;
import com.andrews.archivedownloader.wrapper.render.UiTextureId;
import com.andrews.archivedownloader.wrapper.text.UiText;

public final class RenderUtil {
    private RenderUtil() {}

    public static void fillRect(UiRenderContext context, int x1, int y1, int x2, int y2, int color) {
        context.graphics().fill(x1, y1, x2, y2, color);
    }

    public static void enableScissor(UiRenderContext context, int x1, int y1, int x2, int y2) {
        context.graphics().enableScissor(x1, y1, x2, y2);
    }

    public static void disableScissor(UiRenderContext context) {
        context.graphics().disableScissor();
    }

    public static void drawString(UiRenderContext context, UiFont font, String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        //? >=26.1 {
        context.graphics().text(font.nativeFont(), text, x, y, color, false);
        //? } else {
         /*context.graphics().drawString(font.nativeFont(), text, x, y, color, false);
        *///? }
    }

    public static void drawString(UiRenderContext context, UiFont font, UiText text, int x, int y, int color) {
        if (text == null) {
            return;
        }
        //? >=26.1 {
        context.graphics().text(font.nativeFont(), text.nativeComponent(), x, y, color, false);
        //? } else {
         /*context.graphics().drawString(font.nativeFont(), text.nativeComponent(), x, y, color, false);
        *///? }
    }

    public static void drawCenteredString(UiRenderContext context, UiFont font, String text, int centerX, int y, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        //? >=26.1 {
        context.graphics().text(font.nativeFont(), text, centerX - font.width(text) / 2, y, color, false);
        //? } else {
         /*context.graphics().drawString(font.nativeFont(), text, centerX - font.width(text) / 2, y, color, false);
        *///? }
    }

    public static void blit(UiRenderContext context, UiRenderPipeline pipeline, UiTextureId texture, int x, int y, int u, int v, int width, int height, int texWidth, int texHeight) {
        //? >=1.21.2 {
         context.graphics().blit(pipeline.nativePipeline(), texture.nativeId(), x, y, u, v, width, height, texWidth, texHeight);
        //? } else {
        /*context.graphics().blit(texture.nativeId(), x, y, u, v, width, height, texWidth, texHeight);
        *///? }
    }

    public static void drawBorder(UiRenderContext context, int x, int y, int width, int height, int color) {
        fillRect(context, x, y, x + width, y + 1, color);
        fillRect(context, x, y + height - 1, x + width, y + height, color);
        fillRect(context, x, y + 1, x + 1, y + height - 1, color);
        fillRect(context, x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void drawScaledString(UiRenderContext context, String text, int x, int y, int color, float scale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        UiFont font = UiMinecraftClient.getInstance().uiFont();

        //? >=21.6 {
        Matrix3x2fStack matrix = context.graphics().pose().pushMatrix();
        context.graphics().pose().translate(x, y, matrix);
        context.graphics().pose().scale(scale, scale, matrix);
        context.graphics().text(font.nativeFont(), text, 0, 0, color, false);
        context.graphics().pose().popMatrix();
        //? } else if >=1.21.6 {
        // Matrix3x2fStack matrix = context.graphics().pose().pushMatrix();
        // context.graphics().pose().translate(x, y, matrix);
        // context.graphics().pose().scale(scale, scale, matrix);
        // context.graphics().drawString(font.nativeFont(), text, 0, 0, color, false);
        // context.graphics().pose().popMatrix();
        //? } else {
         /*context.graphics().pose().pushPose();
         context.graphics().pose().translate(x, y, 0);
         context.graphics().pose().scale(scale, scale, 1);
         context.graphics().drawString(font.nativeFont(), text, 0, 0, color, false);
         context.graphics().pose().popPose();
        *///? }


    }

    public static void drawScaledString(UiRenderContext context, String text, int x, int y, int color, float scale, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return;
        }
        UiFont font = UiMinecraftClient.getInstance().uiFont();
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

    public static void drawWrappedText(UiRenderContext context, UiFont font, String text, int textX, int textY, int maxWidth, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
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

    public static int getWrappedTextHeight(UiFont font, String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return 10;
        }
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
