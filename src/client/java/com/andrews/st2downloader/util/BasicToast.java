package com.andrews.st2downloader.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

public class BasicToast implements Toast {
    private static final int WIDTH = 240;
    private static final int MIN_HEIGHT = 32;

    private final FormattedText title;
    private final long displayMillis;
    private final java.util.List<FormattedCharSequence> wrappedTitle;
    private final java.util.List<FormattedCharSequence> wrappedBody;
    private long startTime = -1L;
    private Visibility visibility = Visibility.SHOW;
    private int computedHeight = MIN_HEIGHT;

    public BasicToast(FormattedText title, FormattedText body) {
        this(title, body, 4000L);
    }

    public BasicToast(FormattedText title, FormattedText body, long displayMillis) {
        this.title = title != null ? title : FormattedText.of("");
        this.displayMillis = Math.max(1500L, displayMillis);
        Font renderer = Minecraft.getInstance().font;
        int maxLineWidth = WIDTH - 24;
        this.wrappedTitle = renderer.split(this.title, maxLineWidth);
        this.wrappedBody = (body != null && !body.getString().isEmpty())
            ? renderer.split(body, maxLineWidth)
            : java.util.List.of();
        int lines = wrappedTitle.size() + wrappedBody.size();
        this.computedHeight = Math.max(MIN_HEIGHT, 14 + lines * 9); // padding top+bottom plus line heights
    }

    @Override
    public void render(GuiGraphics context, Font textRenderer, long startTime) {
        int height = height();
        // simple background rectangle
        int bgColor = 0xCC1E1E1E; // semi-transparent dark
        int border = 0xFF3A3A3A;
        context.fill(0, 0, WIDTH, height, bgColor);
        RenderUtil.drawBorder(context, 0, 0, WIDTH, height, border);
        int y = 7;
        for (int i = 0; i < wrappedTitle.size(); i++) {
            context.drawString(textRenderer, wrappedTitle.get(i), 12, y, 0xFFFFFFFF, false);
            y += 9;
        }
        for (var line : wrappedBody) {
            context.drawString(textRenderer, line, 12, y, 0xFFAAAAAA, false);
            y += 9;
        }
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return computedHeight;
    }

    @Override
    public Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long time) {
        if (startTime < 0L) {
            startTime = time;
        }
        double duration = displayMillis * manager.getNotificationDisplayTimeMultiplier();
        visibility = (time - startTime) >= duration ? Visibility.HIDE : Visibility.SHOW;
    }
}
