package link.infra.indium.mixin.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache;

@Mixin(BlockRenderer.class)
public interface AccessBlockRenderer {
	@Accessor(value = "occlusionCache", remap = false)
	BlockOcclusionCache indium$occlusionCache();
}
