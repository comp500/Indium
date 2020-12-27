package link.infra.ogidni.mixin.cursed;

import link.infra.ogidni.cursed.DummyBakedModel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.model.BakedModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Environment(EnvType.CLIENT)
@Mixin(BlockModels.class)
public class BlockModelsMixin {
	// Now redirecting BlockRenderer.renderModel instead
//	@Shadow @Final private Map<BlockState, BakedModel> models;
//
//	@Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
//	public void hackGetModel(BlockState blockState, CallbackInfoReturnable<BakedModel> cir) {
//		FabricBakedModel bakedModel = (FabricBakedModel) this.models.get(blockState);
//		if (!bakedModel.isVanillaAdapter()) {
//			cir.setReturnValue(new DummyBakedModel((BakedModel) bakedModel));
//		}
//	}
}
