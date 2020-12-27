package link.infra.ogidni.renderer.render;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.infra.ogidni.renderer.accessor.AccessChunkRenderer;
import link.infra.ogidni.renderer.accessor.AccessChunkRendererData;
import link.infra.ogidni.renderer.aocalc.AoLuminanceFix;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.BlockBufferBuilderStorage;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;

public class CursedRenderInfo {
	/**
	 * Serves same function as brightness cache in Mojang's AO calculator,
	 * with some differences as follows...
	 *
	 * <ul><li>Mojang uses Object2Int.  This uses Long2Int for performance and to avoid
	 * creating new immutable BlockPos references.  But will break if someone
	 * wants to expand Y limit or world borders.  If we want to support that may
	 * need to switch or make configurable.
	 *
	 * <li>Mojang overrides the map methods to limit the cache to 50 values.
	 * However, a render chunk only has 18^3 blocks in it, and the cache is cleared every chunk.
	 * For performance and simplicity, we just let map grow to the size of the render chunk.
	 *
	 * <li>Mojang only uses the cache for Ao.  Here it is used for all brightness
	 * lookups, including flat lighting.
	 *
	 * <li>The Mojang cache is a separate threadlocal with a threadlocal boolean to
	 * enable disable. Cache clearing happens with the disable. There's no use case for
	 * us when the cache needs to be disabled (and no apparent case in Mojang's code either)
	 * so we simply clear the cache at the start of each new chunk. It is also
	 * not a threadlocal because it's held within a threadlocal BlockRenderer.</ul>
	 */
	private final Long2IntOpenHashMap brightnessCache;
	private final Long2FloatOpenHashMap aoLevelCache;

	private final BlockPos.Mutable chunkOrigin = new BlockPos.Mutable();
	AccessChunkRendererData chunkData;
	BlockBufferBuilderStorage builders;
	BlockRenderView blockView;

	VertexBufferStuff.VertexBufferManager.RegionBuilder regionBuilder;

	private final Object2ObjectOpenHashMap<RenderLayer, BufferBuilder> buffers = new Object2ObjectOpenHashMap<>();

	CursedRenderInfo() {
		brightnessCache = new Long2IntOpenHashMap();
		brightnessCache.defaultReturnValue(Integer.MAX_VALUE);
		aoLevelCache = new Long2FloatOpenHashMap();
		aoLevelCache.defaultReturnValue(Float.MAX_VALUE);
	}

	void prepare(ClientWorld blockView, VertexBufferStuff.VertexBufferManager.RegionBuilder regionBuilder) {
		this.blockView = blockView;
		this.regionBuilder = regionBuilder;
		buffers.clear();
		brightnessCache.clear();
		aoLevelCache.clear();
	}

	void release() {
		chunkData = null;
		regionBuilder = null;
		buffers.clear();
	}

	/** Lazily retrieves output buffer for given layer, initializing as needed. */
	public BufferBuilder getInitializedBuffer(RenderLayer renderLayer) {
		return regionBuilder.getBuffer(renderLayer);
	}

	/**
	 * Cached values for {@link BlockState#getBlockBrightness(BlockRenderView, BlockPos)}.
	 * See also the comments for {@link #brightnessCache}.
	 */
	int cachedBrightness(BlockPos pos) {
		long key = pos.asLong();
		int result = brightnessCache.get(key);

		if (result == Integer.MAX_VALUE) {
			result = WorldRenderer.getLightmapCoordinates(blockView, blockView.getBlockState(pos), pos);
			brightnessCache.put(key, result);
		}

		return result;
	}

	float cachedAoLevel(BlockPos pos) {
		long key = pos.asLong();
		float result = aoLevelCache.get(key);

		if (result == Float.MAX_VALUE) {
			result = AoLuminanceFix.INSTANCE.apply(blockView, pos);
			aoLevelCache.put(key, result);
		}

		return result;
	}
}
