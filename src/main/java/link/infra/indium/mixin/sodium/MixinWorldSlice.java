package link.infra.indium.mixin.sodium;

import link.infra.indium.other.LocalRenderAttachedBlockView;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import me.jellysquid.mods.sodium.client.world.cloned.ClonedChunkSection;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Implements {@link net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView} for WorldSlice
 * See also {@link net.fabricmc.fabric.mixin.rendering.data.attachment.client.MixinChunkRendererRegion}
 */
@Mixin(WorldSlice.class)
public abstract class MixinWorldSlice implements RenderAttachedBlockView {
	@Shadow(remap = false) private ClonedChunkSection[] sections;
	@Shadow(remap = false) private int baseX;
	@Shadow(remap = false) private int baseY;
	@Shadow(remap = false) private int baseZ;

	@Override
	public @Nullable Object getBlockEntityRenderAttachment(BlockPos pos) {
		int relX = pos.getX() - this.baseX;
		int relY = pos.getY() - this.baseY;
		int relZ = pos.getZ() - this.baseZ;

		return ((LocalRenderAttachedBlockView)this.sections[WorldSlice.getLocalSectionIndex(relX >> 4, relY >> 4, relZ >> 4)])
			.getBlockEntityRenderAttachment(relX & 15, relY & 15, relZ & 15);
	}
}
