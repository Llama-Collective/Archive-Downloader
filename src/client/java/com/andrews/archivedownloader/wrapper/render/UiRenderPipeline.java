package com.andrews.archivedownloader.wrapper.render;

//? >=1.21.6 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
//? } else {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.Identifier;
import java.util.function.Function;
*///? }

public enum UiRenderPipeline {
    //? >=1.21.6 {
     GUI_TEXTURED(RenderPipelines.GUI_TEXTURED);
    //? } else if >=1.21.2 {
    // GUI_TEXTURED(RenderType::guiTextured);
    //? } else {
    /*GUI_TEXTURED(null);
    *///? }

    //? >=1.21.6 {
     private final RenderPipeline nativePipeline;

    UiRenderPipeline(RenderPipeline nativePipeline) {
        this.nativePipeline = nativePipeline;
    }

    public RenderPipeline nativePipeline() {
        return nativePipeline;
    }
    //? } else {
    /*private final Function<Identifier, RenderType> nativePipeline;

    UiRenderPipeline(Function<Identifier, RenderType> nativePipeline) {
        this.nativePipeline = nativePipeline;
    }

    public Function<Identifier, RenderType> nativePipeline() {
        return nativePipeline;
    }
    *///? }
}
