package link.infra.indium.mixin.sodium;

import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Implements {@link net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView} for WorldSlice
 * See also {@link net.fabricmc.fabric.mixin.rendering.data.attachment.client.MixinChunkRendererRegion}
 */
@Mixin(WorldSlice.class)
public abstract class MixinWorldSlice implements RenderAttachedBlockView {
	// TODO: add impl
}
