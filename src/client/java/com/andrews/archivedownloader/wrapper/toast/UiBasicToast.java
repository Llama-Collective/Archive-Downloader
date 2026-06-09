package com.andrews.archivedownloader.wrapper.toast;

import com.andrews.archivedownloader.util.RenderUtil;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

//? >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? } else {
 /*import net.minecraft.client.gui.GuiGraphics;
*///? }

import net.minecraft.client.gui.components.toasts.Toast;
//? >=1.21.2 {
import net.minecraft.client.gui.components.toasts.ToastManager;
//? } else {
 /*import net.minecraft.client.gui.components.toasts.ToastComponent;
*///? }
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

public class UiBasicToast implements Toast {
    private static final int WIDTH = 240;
    private static final int MIN_HEIGHT = 32;

    private final FormattedText title;
    private final long displayMillis;
    private final java.util.List<FormattedCharSequence> wrappedTitle;
    private final java.util.List<FormattedCharSequence> wrappedBody;
    private long startTime = -1L;
    private Visibility visibility = Visibility.SHOW;
    private int computedHeight = MIN_HEIGHT;

    public UiBasicToast(FormattedText title, FormattedText body) {
        this(title, body, 4000L);
    }

    public UiBasicToast(FormattedText title, FormattedText body, long displayMillis) {
        this.title = title != null ? title : FormattedText.of("");
        this.displayMillis = Math.max(1500L, displayMillis);
        Font renderer = Minecraft.getInstance().font;
        int maxLineWidth = WIDTH - 24;
        this.wrappedTitle = renderer.split(this.title, maxLineWidth);
        this.wrappedBody = (body != null && !body.getString().isEmpty())
                ? renderer.split(body, maxLineWidth)
                : java.util.List.of();
        int lines = wrappedTitle.size() + wrappedBody.size();
        this.computedHeight = Math.max(MIN_HEIGHT, 14 + lines * 9);
    }

    //? >=26.1 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, Font textRenderer, long startTime) {
        renderToast(UiRenderContext.from(context), textRenderer, startTime);
    }
    //? } else if >=1.21.2 {
    //  @Override
    //  public void render(GuiGraphics context, Font textRenderer, long startTime) {
    //      renderToast(UiRenderContext.from(context), textRenderer, startTime);
    //  }
    //?} else {
     /*@Override
     public Visibility render(GuiGraphics context, ToastComponent component, long startTime) {
         if (this.startTime < 0L) {
             this.startTime = startTime;
         }
         double duration = displayMillis * component.getNotificationDisplayTimeMultiplier();
         visibility = (startTime - this.startTime) >= duration ? Visibility.HIDE : Visibility.SHOW;
         renderToast(UiRenderContext.from(context), component.getMinecraft().font, startTime);
         return visibility;
     }
    *///? }


    public void renderToast(UiRenderContext contextWrapper, Font textRenderer, long startTime) {
        int height = height();
        int bgColor = 0xCC1E1E1E;
        int border = 0xFF3A3A3A;
        var context = contextWrapper.graphics();
        context.fill(0, 0, WIDTH, height, bgColor);
        RenderUtil.drawBorder(UiRenderContext.from(context), 0, 0, WIDTH, height, border);
        int y = 7;

        //? >=26.1 {
        for (int i = 0; i < wrappedTitle.size(); i++) {
            context.text(textRenderer, wrappedTitle.get(i), 12, y, 0xFFFFFFFF, false);
            y += 9;
        }
        for (var line : wrappedBody) {
            context.text(textRenderer, line, 12, y, 0xFFAAAAAA, false);
            y += 9;
        }
        //? } else {
         /*for (int i = 0; i < wrappedTitle.size(); i++) {
             context.drawString(textRenderer, wrappedTitle.get(i), 12, y, 0xFFFFFFFF, false);
             y += 9;
         }
         for (var line : wrappedBody) {
             context.drawString(textRenderer, line, 12, y, 0xFFAAAAAA, false);
             y += 9;
         }
        *///? }
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return computedHeight;
    }

    //? >=1.21.2 {
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
    //? }
}
