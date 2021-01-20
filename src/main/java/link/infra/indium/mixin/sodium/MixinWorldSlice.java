package link.infra.indium.mixin.sodium;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuilder;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachmentBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Implements {@link net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView} for WorldSlice
 * See also {@link net.fabricmc.fabric.mixin.rendering.data.attachment.client.MixinChunkRendererRegion}
 */
@Mixin(WorldSlice.class)
public abstract class MixinWorldSlice implements RenderAttachedBlockView {
	@Shadow @Final private static int SECTION_TABLE_ARRAY_SIZE;

	@Shadow private int minX;
	@Shadow private int minY;
	@Shadow private int minZ;
	@Shadow private int maxX;
	@Shadow private int maxY;
	@Shadow private int maxZ;
	@Shadow private int baseX;
	@Shadow private int baseY;
	@Shadow private int baseZ;
	@Shadow private WorldChunk[] chunks;

	@Shadow
	public static int getLocalBlockIndex(int x, int y, int z) {
		throw new RuntimeException("Shadow mixin failure");
	}

	@Shadow
	public static int getLocalChunkIndex(int x, int z) {
		throw new RuntimeException("Shadow mixin failure");
	}

	// Stores a map of int -> render attachment per chunk, indexed the same way as chunks
	private Int2ObjectOpenHashMap<Object>[] indium_renderDataObjects = null;

	@Override
	public @Nullable Object getBlockEntityRenderAttachment(BlockPos pos) {
		if (indium_renderDataObjects != null) {
			int relX = pos.getX() - this.baseX;
			int relY = pos.getY() - this.baseY;
			int relZ = pos.getZ() - this.baseZ;
			Int2ObjectOpenHashMap<Object> map = indium_renderDataObjects[getLocalChunkIndex(relX >> 4, relZ >> 4)];
			if (map != null) {
				return map.get(getLocalBlockIndex(relX & 15, relY, relZ & 15));
			}
		}
		return null;
	}

	// After the normal WorldSlice initialisation, fill indium_renderDataObjects with render
	// attachments in this WorldSlice
	@Inject(at = @At("TAIL"), method = "init", remap = false)
	public void afterInit(ChunkBuilder<?> builder, World world, ChunkSectionPos origin, WorldChunk[] chunks, CallbackInfo ci) {
		final int minChunkX = this.minX >> 4;
		final int minChunkZ = this.minZ >> 4;

		final int maxChunkX = this.maxX >> 4;
		final int maxChunkZ = this.maxZ >> 4;

		// Iterate over all sliced chunks
		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				// The local index for this chunk in the slice's data arrays
				int chunkIdx = getLocalChunkIndex(chunkX - minChunkX, chunkZ - minChunkZ);

				WorldChunk chunk = this.chunks[chunkIdx];

				this.indium_populateDataObjects(chunkIdx, chunkX, chunkZ, chunk);
			}
		}
	}

	@Inject(at = @At("TAIL"), method = "reset", remap = false)
	public void afterReset(CallbackInfo ci) {
		indium_renderDataObjects = null;
	}

	private void indium_populateDataObjects(int chunkIdx, int chunkX, int chunkZ, WorldChunk chunk) {
		int minBlockX = Math.max(this.minX, chunkX << 4);
		int maxBlockX = Math.min(this.maxX, (chunkX << 4) + 15);

		int minBlockZ = Math.max(this.minZ, chunkZ << 4);
		int maxBlockZ = Math.min(this.maxZ, (chunkZ << 4) + 15);

		Int2ObjectOpenHashMap<Object> attachmentMap = null;

		for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
			BlockPos bePos = entry.getKey();

			if (bePos.getX() >= minBlockX && bePos.getX() <= maxBlockX &&
				bePos.getY() >= this.minY && bePos.getY() <= this.maxY &&
				bePos.getZ() >= minBlockZ && bePos.getZ() <= maxBlockZ) {
				Object attachment = ((RenderAttachmentBlockEntity)entry.getValue()).getRenderAttachmentData();

				if (attachment != null) {
					// IntelliJ whines at this because it thinks the shadowed variables are always 0
					// Do not listen to it's harsh remarks :)
					if (attachmentMap == null) {
						attachmentMap = new Int2ObjectOpenHashMap<>();
					}
					attachmentMap.put(
						// Assumes Y is in the range accepted by getLocalBlockIndex!
						// TODO: will 1.17 break this?
						getLocalBlockIndex((bePos.getX() - this.baseX) & 15, (bePos.getY() - this.baseY), (bePos.getZ() - this.baseZ) & 15),
						attachment
					);
				}
			}
		}

		if (attachmentMap != null) {
			if (indium_renderDataObjects == null) {
				//noinspection unchecked
				indium_renderDataObjects = new Int2ObjectOpenHashMap[SECTION_TABLE_ARRAY_SIZE];
			}
			indium_renderDataObjects[chunkIdx] = attachmentMap;
		}
	}
}
