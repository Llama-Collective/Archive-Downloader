package com.andrews.archivedownloader.util;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.text.Text;

public class BasicToast implements Toast {
    private static final int WIDTH = 240;
    private static final int MIN_HEIGHT = 32;

    private final Text title;
    private final long displayMillis;
    private final java.util.List<net.minecraft.text.OrderedText> wrappedTitle;
    private final java.util.List<net.minecraft.text.OrderedText> wrappedBody;
    private long startTime = -1L;
    private Visibility visibility = Visibility.SHOW;
    private int computedHeight = MIN_HEIGHT;

    public BasicToast(Text title, Text body) {
        this(title, body, 4000L);
    }

    public BasicToast(Text title, Text body, long displayMillis) {
        this.title = title != null ? title : Text.empty();
        this.displayMillis = Math.max(1500L, displayMillis);
        var renderer = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        int maxLineWidth = WIDTH - 24;
        this.wrappedTitle = renderer.wrapLines(this.title, maxLineWidth);
        this.wrappedBody = (body != null && !body.getString().isEmpty())
            ? renderer.wrapLines(body, maxLineWidth)
            : java.util.List.of();
        int lines = wrappedTitle.size() + wrappedBody.size();
        this.computedHeight = Math.max(MIN_HEIGHT, 14 + lines * 9); // padding top+bottom plus line heights
    }

    @Override
    public void draw(DrawContext context, net.minecraft.client.font.TextRenderer textRenderer, long startTime) {
        int height = getHeight();
        // simple background rectangle
        int bgColor = 0xCC1E1E1E; // semi-transparent dark
        int border = 0xFF3A3A3A;
        context.fill(0, 0, WIDTH, height, bgColor);
        context.drawBorder(0, 0, WIDTH, height, border);
        int y = 7;
        for (int i = 0; i < wrappedTitle.size(); i++) {
            context.drawText(textRenderer, wrappedTitle.get(i), 12, y, 0xFFFFFFFF, false);
            y += 9;
        }
        for (var line : wrappedBody) {
            context.drawText(textRenderer, line, 12, y, 0xFFAAAAAA, false);
            y += 9;
        }
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return computedHeight;
    }

    @Override
    public Visibility getVisibility() {
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
