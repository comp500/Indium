package link.infra.indium.mixin.sodium;

import link.infra.indium.other.AccessBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import link.infra.indium.Indium;
import link.infra.indium.other.AccessChunkRenderCacheLocal;
import link.infra.indium.renderer.render.TerrainRenderContext;
import me.jellysquid.mods.sodium.client.gl.compile.ChunkBuildContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderBuildTask;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderRebuildTask;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import me.jellysquid.mods.sodium.client.util.task.CancellationSource;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;

/**
 * The main injection point into Sodium - here we stop Sodium from rendering FRAPI block models, and do it ourselves
 */
@Mixin(ChunkRenderRebuildTask.class)
public abstract class MixinChunkRenderRebuildTask extends ChunkRenderBuildTask {
	@Inject(method = "performBuild", at = @At("HEAD"), remap = false)
	public void beforePerformBuild(ChunkBuildContext buildContext, CancellationSource cancellationSource, CallbackInfoReturnable<ChunkBuildResult> cir) {
		TerrainRenderContext context = ((AccessChunkRenderCacheLocal) buildContext.cache).indium$getTerrainRenderContext();
		// Set up our rendering context
		context.prepare(buildContext.cache.getWorldSlice(), buildContext.buffers, ((AccessBlockRenderer) buildContext.cache.getBlockRenderer()).indium$getBlockOcclusionCache());
	}

	@Inject(method = "performBuild", at = @At("RETURN"), remap = false)
	public void afterPerformBuild(ChunkBuildContext buildContext, CancellationSource cancellationSource, CallbackInfoReturnable<ChunkBuildResult> cir) {
		TerrainRenderContext context = ((AccessChunkRenderCacheLocal) buildContext.cache).indium$getTerrainRenderContext();
		// Tear down our rendering context
		context.release();
	}

	// Can't specify the arguments here, as the arguments wouldn't get remapped
	// and remap = true fails as it tries to find a mapping for renderBlock
	// so I just let MinecraftDev yell at me here
	@Redirect(method = "performBuild", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/pipeline/BlockRenderer;renderModel"), remap = false)
	public boolean onRenderBlock(BlockRenderer blockRenderer, BlockRenderView world, BlockState state, BlockPos pos, BlockPos origin, BakedModel model, ChunkModelBuilder buffers, boolean cull, long seed, ChunkBuildContext buildContext, CancellationSource cancellationSource) {
		// We need to get the model with a bit more context than BlockRenderer has, so we do it here

		if (!Indium.ALWAYS_TESSELATE_INDIUM && ((FabricBakedModel) model).isVanillaAdapter()) {
			return blockRenderer.renderModel(world, state, pos, origin, model, buffers, cull, seed);
		} else {
			TerrainRenderContext context = ((AccessChunkRenderCacheLocal) buildContext.cache).indium$getTerrainRenderContext();
			Vec3d modelOffset = state.getModelOffset(world, pos);
			return context.tesselateBlock(state, pos, origin, model, modelOffset);
		}
	}
}
