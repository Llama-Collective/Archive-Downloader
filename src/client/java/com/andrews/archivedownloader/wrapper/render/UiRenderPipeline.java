package com.andrews.archivedownloader.wrapper.render;

//? >=1.21.6 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
//? } else {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Function;
*///? }

public enum UiRenderPipeline {
    //? >=1.21.6 {
    GUI_TEXTURED(RenderPipelines.GUI_TEXTURED);

    private final RenderPipeline nativePipeline;

    UiRenderPipeline(RenderPipeline nativePipeline) {
        this.nativePipeline = nativePipeline;
    }

    public RenderPipeline nativePipeline() {
        return nativePipeline;
    }
    //? } else {
    /*GUI_TEXTURED(RenderType::guiTextured);

    private final Function<ResourceLocation, RenderType> nativePipeline;

    UiRenderPipeline(Function<ResourceLocation, RenderType> nativePipeline) {
        this.nativePipeline = nativePipeline;
    }

    public Function<ResourceLocation, RenderType> nativePipeline() {
        return nativePipeline;
    }
    *///? }
}
