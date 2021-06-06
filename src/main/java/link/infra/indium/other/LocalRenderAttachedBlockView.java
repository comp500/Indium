package link.infra.indium.other;

import org.jetbrains.annotations.Nullable;

/**
 * Like {@link net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView} but passing coordinates relative to the ChunkSection instead.
 */
public interface LocalRenderAttachedBlockView {
	@Nullable Object getBlockEntityRenderAttachment(int relX, int relY, int relZ);
}
