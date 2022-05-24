package io.github.spiralhalo.plumbum.mixin.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderCacheLocal;

import io.github.spiralhalo.plumbum.other.AccessChunkRenderCacheLocal;
import io.github.spiralhalo.plumbum.renderer.render.TerrainRenderContext;

@Mixin(ChunkRenderCacheLocal.class)
public class MixinChunkRenderCacheLocal implements AccessChunkRenderCacheLocal {
	@Unique
	private final TerrainRenderContext terrainRenderContext = new TerrainRenderContext();

	@Override
	public TerrainRenderContext plumbum_getTerrainRenderContext() {
		return terrainRenderContext;
	}
}
