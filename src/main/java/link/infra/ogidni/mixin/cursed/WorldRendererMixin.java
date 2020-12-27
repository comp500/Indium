package link.infra.ogidni.mixin.cursed;

import link.infra.ogidni.renderer.render.VertexBufferStuff;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
	/**
	 * Inject into rendering after all other BERs have been rendered (and as such all BERs have populated the BufferBuilders)
	 * and draw the buffers to the screen.
	 * TODO: Use WorldRenderEvents when it is merged
	 */
	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lnet/minecraft/util/math/Matrix4f;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;checkEmpty(Lnet/minecraft/client/util/math/MatrixStack;)V", ordinal = 0),
		slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderDispatcher;render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V"))
	)
	public void afterRenderBlockEntities(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f matrix4f, CallbackInfo ci) {
		VertexBufferStuff.VertexBufferManager.INSTANCE.render(matrices, camera);
	}

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
