package chihalu.horror.client.mixin;

import chihalu.horror.client.terrain.TerrainRenderer;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.commands.RenderPass;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the terrain surface in the opaque pass, after vanilla terrain and before entities. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Shadow
    @Final
    private LevelRenderState levelRenderState;
    @Shadow
    @Final
    private OptionsRenderState optionsRenderState;

    @Shadow
    public abstract ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections();

    @Inject(method = "executeSolid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeSolid(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void horror$drawTerrainSurface(CallbackInfo ci, @Local(argsOnly = true) RenderPass renderPass) {
        TerrainRenderer.draw(renderPass, visibleSections(), levelRenderState.cameraRenderState,
                optionsRenderState.chunkSectionFadeInTime);
    }
}
