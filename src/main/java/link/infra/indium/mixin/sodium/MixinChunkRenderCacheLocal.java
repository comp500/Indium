package link.infra.indium.mixin.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import link.infra.indium.other.AccessChunkRenderCacheLocal;
import link.infra.indium.renderer.render.TerrainRenderContext;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderCacheLocal;

@Mixin(ChunkRenderCacheLocal.class)
public class MixinChunkRenderCacheLocal implements AccessChunkRenderCacheLocal {
	@Unique
	private final TerrainRenderContext terrainRenderContext = new TerrainRenderContext();

	@Override
	public TerrainRenderContext indium$getTerrainRenderContext() {
		return terrainRenderContext;
	}
}
