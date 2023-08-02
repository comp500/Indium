package link.infra.indium.mixin.sodium;

import link.infra.indium.Indium;
import link.infra.indium.renderer.render.TerrainRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import me.jellysquid.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;
import me.jellysquid.mods.sodium.client.util.task.CancellationToken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The main injection point into Sodium - here we stop Sodium from rendering FRAPI block models, and do it ourselves
 */
@Mixin(ChunkBuilderMeshingTask.class)
public abstract class MixinChunkBuilderMeshingTask extends ChunkBuilderTask<ChunkBuildOutput> {
	@Inject(method = "execute(Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lme/jellysquid/mods/sodium/client/util/task/CancellationToken;)Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
		at = @At("HEAD"), remap = false)
	public void beforePerformBuild(ChunkBuildContext buildContext, CancellationToken cancellationToken, CallbackInfoReturnable<ChunkBuildOutput> cir) {
		// Set up our rendering context
		TerrainRenderContext.get(buildContext).prepare(buildContext);
	}

	@Inject(method = "execute(Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lme/jellysquid/mods/sodium/client/util/task/CancellationToken;)Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
		at = @At("RETURN"), remap = false)
	public void afterPerformBuild(ChunkBuildContext buildContext, CancellationToken cancellationToken, CallbackInfoReturnable<ChunkBuildOutput> cir) {
		// Tear down our rendering context
		TerrainRenderContext.get(buildContext).release();
	}

	@Redirect(method = "execute(Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lme/jellysquid/mods/sodium/client/util/task/CancellationToken;)Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
		at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;renderModel(Lme/jellysquid/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderContext;Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildBuffers;)V", remap = false), remap = false)
	public void onRenderBlock(BlockRenderer blockRenderer, BlockRenderContext ctx, ChunkBuildBuffers buffers, ChunkBuildContext buildContext, CancellationToken cancellationToken) {
		// We need to get the model with a bit more context than BlockRenderer has, so we do it here
		if (Indium.ALWAYS_TESSELLATE_INDIUM || !ctx.model().isVanillaAdapter()) {
			TerrainRenderContext.get(buildContext).tessellateBlock(ctx);
		} else {
			blockRenderer.renderModel(ctx, buffers);
		}
	}
}
