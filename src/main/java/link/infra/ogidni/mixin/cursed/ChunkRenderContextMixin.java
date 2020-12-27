package link.infra.ogidni.mixin.cursed;

import me.jellysquid.mods.sodium.client.model.quad.sink.ModelQuadSinkDelegate;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderContext;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkRenderContext.class)
public class ChunkRenderContextMixin {
	// TODO: make this not break on different sodium versions?
	//(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/render/model/BakedModel;Lme/jellysquid/mods/sodium/client/model/quad/sink/ModelQuadSinkDelegate;ZJ)Z
	@Redirect(method = "renderBlock", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/pipeline/BlockRenderer;renderModel"), remap = false)
	public boolean onRenderModel(BlockRenderer blockRenderer, BlockRenderView world, BlockState state, BlockPos pos, BakedModel model, ModelQuadSinkDelegate builder, boolean cull, long seed) {
		if (((FabricBakedModel) model).isVanillaAdapter()) {
			return blockRenderer.renderModel(world, state, pos, model, builder, cull, seed);
		} else {
			// Ogidni will render this instead
			return true;
		}
	}
}
