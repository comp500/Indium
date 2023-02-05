package link.infra.indium.mixin.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import me.jellysquid.mods.sodium.client.render.occlusion.BlockOcclusionCache;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;

@Mixin(BlockRenderer.class)
public interface AccessBlockRenderer {
	@Accessor(value = "occlusionCache", remap = false)
	BlockOcclusionCache indium$occlusionCache();
}
