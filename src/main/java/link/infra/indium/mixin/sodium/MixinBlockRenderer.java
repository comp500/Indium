package link.infra.indium.mixin.sodium;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import me.jellysquid.mods.sodium.client.render.occlusion.BlockOcclusionCache;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;

import link.infra.indium.other.AccessBlockRenderer;

@Mixin(BlockRenderer.class)
public class MixinBlockRenderer implements AccessBlockRenderer {
    @Shadow
    @Final
    private BlockOcclusionCache occlusionCache;

    @Override
    public BlockOcclusionCache indium_getBlockOcclusionCache() {
        return this.occlusionCache;
    }
}
