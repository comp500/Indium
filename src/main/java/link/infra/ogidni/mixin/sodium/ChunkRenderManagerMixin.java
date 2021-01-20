package link.infra.ogidni.mixin.sodium;

import link.infra.ogidni.renderer.render.VertexBufferStuff;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ChunkRenderManager.class)
public class ChunkRenderManagerMixin {
	@Inject(method = "loadChunk", at = @At("TAIL"), remap = false)
	private void onLoadChunk(int x, int z, CallbackInfo ci) {
		VertexBufferStuff.VertexBufferManager.INSTANCE.loadChunk(x, z);
	}

	@Inject(method = "unloadChunk", at = @At("TAIL"), remap = false)
	private void onUnloadChunk(int x, int z, CallbackInfo ci) {
		VertexBufferStuff.VertexBufferManager.INSTANCE.unloadChunk(x, z);
	}

	@Inject(method = "scheduleRebuild", at = @At("TAIL"), remap = false)
	private void onScheduleRebuild(int x, int y, int z, boolean important, CallbackInfo ci) {
		// TODO: use y and important!!
		VertexBufferStuff.VertexBufferManager.INSTANCE.invalidate(new ChunkPos(x, z));
	}
}
