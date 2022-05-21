package io.github.spiralhalo.plumbum.mixin.sodium;

import io.github.spiralhalo.plumbum.Plumbum;
import io.github.spiralhalo.plumbum.other.AccessBlockRenderer;
import io.github.spiralhalo.plumbum.other.AccessChunkRenderCacheLocal;
import io.github.spiralhalo.plumbum.renderer.render.TerrainRenderContext;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The main injection point into Sodium - here we stop Sodium from rendering FRAPI block models, and do it ourselves
 */
@Mixin(ChunkRenderRebuildTask.class)
public abstract class MixinChunkRenderRebuildTask extends ChunkRenderBuildTask {
	@Inject(method = "performBuild(Lme/jellysquid/mods/sodium/client/gl/compile/ChunkBuildContext;Lme/jellysquid/mods/sodium/client/util/task/CancellationSource;)Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildResult;", at = @At("HEAD"), remap = false)
	public void beforePerformBuild(ChunkBuildContext buildContext, CancellationSource cancellationSource, CallbackInfoReturnable<ChunkBuildResult> cir) {
		TerrainRenderContext context = ((AccessChunkRenderCacheLocal) buildContext.cache).plumbum_getTerrainRenderContext();
		// Set up our rendering context
		context.prepare(buildContext.cache.getWorldSlice(), buildContext.buffers, ((AccessBlockRenderer) buildContext.cache.getBlockRenderer()).plumbum_getBlockOcclusionCache());
	}

	@Inject(method = "performBuild(Lme/jellysquid/mods/sodium/client/gl/compile/ChunkBuildContext;Lme/jellysquid/mods/sodium/client/util/task/CancellationSource;)Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildResult;", at = @At("RETURN"), remap = false)
	public void afterPerformBuild(ChunkBuildContext buildContext, CancellationSource cancellationSource, CallbackInfoReturnable<ChunkBuildResult> cir) {
		TerrainRenderContext context = ((AccessChunkRenderCacheLocal) buildContext.cache).plumbum_getTerrainRenderContext();
		// Tear down our rendering context
		context.release();
	}

	@Redirect(method = "performBuild(Lme/jellysquid/mods/sodium/client/gl/compile/ChunkBuildContext;Lme/jellysquid/mods/sodium/client/util/task/CancellationSource;)Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildResult;",
			at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/pipeline/BlockRenderer;renderModel(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/render/model/BakedModel;Lme/jellysquid/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuilder;ZJ)Z"))
	public boolean onRenderBlock(BlockRenderer blockRenderer, BlockRenderView world, BlockState state, BlockPos pos, BlockPos origin, BakedModel model, ChunkModelBuilder buffers, boolean cull, long seed, ChunkBuildContext buildContext, CancellationSource cancellationSource) {
		// We need to get the model with a bit more context than BlockRenderer has, so we do it here

		if (!Plumbum.ALWAYS_TESSELLATE_PLUMBUM && ((FabricBakedModel) model).isVanillaAdapter()) {
			return blockRenderer.renderModel(world, state, pos, origin, model, buffers, cull, seed);
		} else {
			TerrainRenderContext context = ((AccessChunkRenderCacheLocal) buildContext.cache).plumbum_getTerrainRenderContext();
			Vec3d modelOffset = state.getModelOffset(world, pos);
			return context.tessellateBlock(state, pos, origin, model, modelOffset);
		}
	}
}
