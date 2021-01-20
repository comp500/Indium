package link.infra.indium.mixin.sodium;

import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderContext;
import net.minecraft.client.render.block.BlockModels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkRenderContext.class)
public interface AccessorChunkRenderContext {
	@Accessor(remap = false)
	BlockModels getModels();
}
