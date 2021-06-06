package link.infra.indium.mixin.sodium;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import link.infra.indium.other.LocalRenderAttachedBlockView;
import me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSection;
import me.jellysquid.mods.sodium.client.world.cloned.PalettedContainerExtended;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachmentBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import java.util.Map;

@Mixin(ClonedChunkSection.class)
public abstract class MixinClonedChunkSection implements LocalRenderAttachedBlockView {
	// Stores a map of long -> render attachment, with the same layout as the block entities map
	private Long2ObjectOpenHashMap<Object> indium_renderDataObjects = null;

	public @Nullable Object getBlockEntityRenderAttachment(int relX, int relY, int relZ) {
		if (indium_renderDataObjects != null) {
			return indium_renderDataObjects.get(BlockPos.asLong(relX, relY, relZ));
		}
		return null;
	}

	@Inject(at = @At("HEAD"), method = "init", remap = false)
	private void indium_beforeInit(ChunkSectionPos pos, CallbackInfo ci) {
		indium_renderDataObjects = null;
	}

	@Inject(at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;put(JLjava/lang/Object;)Ljava/lang/Object;"), method = "init", locals = LocalCapture.CAPTURE_FAILHARD, remap = false)
	private void indium_onBlockEntity(ChunkSectionPos pos, CallbackInfo ci, WorldChunk chunk, ChunkSection section, PalettedContainerExtended<BlockState> container, BlockBox box, Iterator<?> var6, Map.Entry<BlockPos, BlockEntity> entry, BlockPos entityPos) {
		indium_populateDataObject(entityPos, entry.getValue());
	}

	private void indium_populateDataObject(BlockPos entityPos, BlockEntity blockEntity) {
		Object attachment = ((RenderAttachmentBlockEntity)blockEntity).getRenderAttachmentData();
		if (attachment != null) {
			if (indium_renderDataObjects == null) {
				indium_renderDataObjects = new Long2ObjectOpenHashMap<>();
			}
			indium_renderDataObjects.put(BlockPos.asLong(entityPos.getX() & 15, entityPos.getY() & 15, entityPos.getZ() & 15), attachment);
		}
	}
}
