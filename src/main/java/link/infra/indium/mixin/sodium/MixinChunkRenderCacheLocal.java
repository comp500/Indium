package link.infra.indium.mixin.sodium;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import link.infra.indium.renderer.accessor.AccessChunkRenderCacheLocal;
import link.infra.indium.renderer.render.TerrainRenderContext;
import me.jellysquid.mods.sodium.client.model.light.cache.ArrayLightDataCache;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderCacheLocal;

@Mixin(ChunkRenderCacheLocal.class)
public class MixinChunkRenderCacheLocal implements AccessChunkRenderCacheLocal {
	@Shadow(remap = false)
	@Final
	private ArrayLightDataCache lightDataCache;

	@Unique
	private TerrainRenderContext terrainRenderContext;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void onInit(CallbackInfo ci) {
		terrainRenderContext = new TerrainRenderContext((ChunkRenderCacheLocal) (Object) this);
	}

	@Override
	public ArrayLightDataCache indium$getLightDataCache() {
		return lightDataCache;
	}

	@Override
	public TerrainRenderContext indium$getTerrainRenderContext() {
		return terrainRenderContext;
	}
}
