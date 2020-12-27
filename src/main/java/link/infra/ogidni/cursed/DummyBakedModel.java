package link.infra.ogidni.cursed;

import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public class DummyBakedModel implements FabricBakedModel, BakedModel {
	private final BakedModel forwardingTo;

	public DummyBakedModel(BakedModel forwardingTo) {
		this.forwardingTo = forwardingTo;
	}

	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, Random random) {
		return Collections.emptyList();
	}

	@Override
	public boolean useAmbientOcclusion() {
		return forwardingTo.useAmbientOcclusion();
	}

	@Override
	public boolean hasDepth() {
		return forwardingTo.hasDepth();
	}

	@Override
	public boolean isSideLit() {
		return forwardingTo.isSideLit();
	}

	@Override
	public boolean isBuiltin() {
		return forwardingTo.isBuiltin();
	}

	@Override
	public Sprite getSprite() {
		return forwardingTo.getSprite();
	}

	@Override
	public ModelTransformation getTransformation() {
		return forwardingTo.getTransformation();
	}

	@Override
	public ModelOverrideList getOverrides() {
		return forwardingTo.getOverrides();
	}

	@Override
	public boolean isVanillaAdapter() {
		return false;
	}

	@Override
	public void emitBlockQuads(BlockRenderView blockRenderView, BlockState blockState, BlockPos blockPos, Supplier<Random> supplier, RenderContext renderContext) {
		((FabricBakedModel)forwardingTo).emitBlockQuads(blockRenderView, blockState, blockPos, supplier, renderContext);
	}

	@Override
	public void emitItemQuads(ItemStack itemStack, Supplier<Random> supplier, RenderContext renderContext) {
		((FabricBakedModel)forwardingTo).emitItemQuads(itemStack, supplier, renderContext);
	}
}
