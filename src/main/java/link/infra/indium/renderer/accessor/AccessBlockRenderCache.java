package link.infra.indium.renderer.accessor;

import link.infra.indium.renderer.render.TerrainRenderContext;
import me.jellysquid.mods.sodium.client.model.light.data.ArrayLightDataCache;

public interface AccessBlockRenderCache {
	ArrayLightDataCache indium$getLightDataCache();

	TerrainRenderContext indium$getTerrainRenderContext();
}
