package link.infra.indium.mixin.sodium;

import link.infra.indium.Indigo;
import link.infra.indium.renderer.render.IndiumTerrainRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildResult;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderBuildTask;
import me.jellysquid.mods.sodium.client.render.chunk.tasks.ChunkRenderRebuildTask;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderCacheLocal;
import me.jellysquid.mods.sodium.client.util.task.CancellationSource;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
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
public abstract class MixinChunkRenderRebuildTask extends ChunkRenderBuildTask<ChunkGraphicsState> {
	// Store a rendering context per rebuild task
	private final IndiumTerrainRenderContext indiumContext = new IndiumTerrainRenderContext();

	@Inject(at = @At("HEAD"), method = "performBuild", remap = false)
	public void beforePerformBuild(ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers, CancellationSource cancellationSource, CallbackInfoReturnable<ChunkBuildResult<ChunkGraphicsState>> cir) {
		// Set up our rendering context
		indiumContext.prepare(cache.getWorldSlice(), buffers);
	}

	@Inject(at = @At("RETURN"), method = "performBuild", remap = false)
	public void afterPerformBuild(ChunkRenderCacheLocal cache, ChunkBuildBuffers buffers, CancellationSource cancellationSource, CallbackInfoReturnable<ChunkBuildResult<ChunkGraphicsState>> cir) {
		// Tear down our rendering context
		indiumContext.release();
	}

	// Can't specify the arguments here, as the arguments wouldn't get remapped
	// and remap = true fails as it tries to find a mapping for renderBlock
	// so I just let MinecraftDev yell at me here
	@Redirect(method = "performBuild", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/pipeline/BlockRenderer;renderModel"), remap = false)
	public boolean onRenderBlock(BlockRenderer blockRenderer, BlockRenderView world, BlockState state, BlockPos pos, BakedModel model, ChunkModelBuffers buffers, boolean cull, long seed) {
		// We need to get the model with a bit more context than BlockRenderer has, so we do it here

		if (!Indigo.ALWAYS_TESSELATE_INDIGO && ((FabricBakedModel) model).isVanillaAdapter()) {
			return blockRenderer.renderModel(world, state, pos, model, buffers, cull, seed);
		} else {
			// TODO: replace MatrixStack with just a Vec3d
			MatrixStack stack = new MatrixStack();
			Vec3d offset = state.getModelOffset(world, pos);
			stack.translate(offset.x, offset.y, offset.z);
			indiumContext.tesselateBlock(state, pos, model, stack);
			// TODO: determine if a block was actually rendered
			return true;
		}
	}
}
