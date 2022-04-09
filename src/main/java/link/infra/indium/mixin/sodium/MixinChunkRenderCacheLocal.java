package link.infra.indium.mixin.sodium;

import link.infra.indium.other.AccessBlockRenderer;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import link.infra.indium.other.AccessChunkRenderCacheLocal;
import link.infra.indium.renderer.render.TerrainRenderContext;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderCacheLocal;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkRenderCacheLocal.class)
public class MixinChunkRenderCacheLocal implements AccessChunkRenderCacheLocal {
	@Shadow
	@Final
	private BlockRenderer blockRenderer;

	@Unique
	private TerrainRenderContext terrainRenderContext;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void onInit(MinecraftClient client, World world, CallbackInfo ci) {
		this.terrainRenderContext = new TerrainRenderContext(((AccessBlockRenderer) this.blockRenderer).indium$getBlockOcclusionCache());
	}

	@Override
	public TerrainRenderContext indium$getTerrainRenderContext() {
		return terrainRenderContext;
	}
}
