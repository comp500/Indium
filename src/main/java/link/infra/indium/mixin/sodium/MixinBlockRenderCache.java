package link.infra.indium.mixin.sodium;

import me.jellysquid.mods.sodium.client.model.light.data.ArrayLightDataCache;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import link.infra.indium.renderer.accessor.AccessBlockRenderCache;
import link.infra.indium.renderer.render.TerrainRenderContext;

@Mixin(BlockRenderCache.class)
public class MixinBlockRenderCache implements AccessBlockRenderCache {
	@Shadow(remap = false)
	@Final
	private ArrayLightDataCache lightDataCache;

	@Unique
	private TerrainRenderContext terrainRenderContext;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void onInit(CallbackInfo ci) {
		terrainRenderContext = new TerrainRenderContext((BlockRenderCache) (Object) this);
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
