package link.infra.indium.mixin.sodium;

import me.jellysquid.mods.sodium.client.model.light.LightPipeline;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.quad.blender.BiomeColorBlender;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuffers;
import me.jellysquid.mods.sodium.client.render.pipeline.FluidRenderer;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.texture.Sprite;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * An implementation of fabric-rendering-fluids-v1 for Sodium's FluidRenderer
 */
@Mixin(FluidRenderer.class)
public class MixinFluidRendererSodium {
	@Redirect(method = "render", at = @At(value = "FIELD", target = "Lme/jellysquid/mods/sodium/client/render/pipeline/FluidRenderer;waterSprites"), remap = false)
	private Sprite[] onGetWaterSprites(FluidRenderer fluidRenderer, BlockRenderView world, FluidState fluidState, BlockPos pos, ChunkModelBuffers buffers) {
		Fluid fluid = fluidState.getFluid();
		FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid);
		return handler.getFluidSprites(world, pos, fluidState);
	}

	// Not strictly necessary, but I feel like a mod should be able to add a fluid that has the lava tag with custom sprites!
	@Redirect(method = "render", at = @At(value = "FIELD", target = "Lme/jellysquid/mods/sodium/client/render/pipeline/FluidRenderer;lavaSprites"), remap = false)
	private Sprite[] onGetLavaSprites(FluidRenderer fluidRenderer, BlockRenderView world, FluidState fluidState, BlockPos pos, ChunkModelBuffers buffers) {
		Fluid fluid = fluidState.getFluid();
		FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid);
		return handler.getFluidSprites(world, pos, fluidState);
	}

	@Shadow(remap = false) @Final private QuadLightData quadLightData;
	@Shadow(remap = false) @Final private int[] quadColors;
	@Shadow(remap = false) @Final private BiomeColorBlender biomeColorBlender;

	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/pipeline/FluidRenderer;calculateQuadColors"), remap = false)
	private void onCalculateQuadColors(FluidRenderer fluidRenderer, ModelQuadView quad, BlockRenderView world, BlockPos pos, LightPipeline lighter, Direction dir, float brightness, boolean notLava, BlockRenderView _world, FluidState fluidState, BlockPos _pos, ChunkModelBuffers buffers) {
		// When implementing this you'd probably want to reuse the calculations done earlier, this is just an easier way of implementing the mixin
		Fluid fluid = fluidState.getFluid();
		boolean isWater = fluid.isIn(FluidTags.WATER);
		FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid);

		QuadLightData light = this.quadLightData;
		lighter.calculate(quad, pos, light, dir, false);

		int[] biomeColors = null;
		int tint = 0xFFFFFFFF;

		if (isWater) {
			// This should probably be cleaned up a bit, maybe refactor getColors?
			biomeColors = this.biomeColorBlender.getColors((
				(bs, view1, pos1, tintIndex) -> handler.getFluidColor(view1, pos1, fluidState)), world, world.getBlockState(pos), pos, quad);
		} else {
			tint = handler.getFluidColor(world, pos, fluidState);
		}

		for (int i = 0; i < 4; i++) {
			this.quadColors[i] = ColorABGR.mul(biomeColors != null ? biomeColors[i] : tint, light.br[i] * brightness);
		}
	}
}
