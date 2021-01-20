package link.infra.ogidni.mixin.cursed;

import link.infra.ogidni.renderer.render.VertexBufferStuff;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
	@Inject(method = "setWorld", at = @At("HEAD"))
	public void onSetWorld(ClientWorld clientWorld, CallbackInfo ci) {
		VertexBufferStuff.VertexBufferManager.INSTANCE.setWorld(clientWorld);
	}

//	@Inject(method = "scheduleBlockRender", at = @At("HEAD"))
//	public void onScheduleBlockRender(int x, int y, int z, CallbackInfo ci) {
//		// TODO: better
//		VertexBufferStuff.VertexBufferManager.INSTANCE.invalidate(new ChunkPos(x, z).getStartPos());
//	}
//
//	@Inject(method = "scheduleBlockRenders(IIIIII)V", at = @At("HEAD"))
//	public void onScheduleBlockRenders(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
//		for(int i = minZ - 1; i <= maxZ + 1; ++i) {
//			for(int j = minX - 1; j <= maxX + 1; ++j) {
//				for(int k = minY - 1; k <= maxY + 1; ++k) {
//					VertexBufferStuff.VertexBufferManager.INSTANCE.invalidate(new BlockPos(j >> 4, k >> 4, i >> 4));
//				}
//			}
//		}
//	}

	// TODO: the other overloads

}
