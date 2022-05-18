/*
 * Copyright (c) 2016-2022 Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.spiralhalo.plumbum.renderer.render;

import io.vram.frex.api.buffer.QuadEmitter;
import io.vram.frex.api.material.MaterialConstants;
import io.vram.frex.api.math.FastMatrix3f;
import io.vram.frex.api.mesh.Mesh;
import io.vram.frex.api.model.BlockModel;
import io.vram.frex.api.model.fluid.FluidModel;
import io.github.spiralhalo.plumbum.renderer.aocalc.AoCalculator;
import io.github.spiralhalo.plumbum.renderer.aocalc.AoLuminanceFix;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Context for non-terrain block rendering.
 */
public class BlockRenderContext extends MatrixRenderContext implements BlockModel.BlockInputContext {
	private final BlockRenderInfo blockInfo = new BlockRenderInfo();
	private final AoCalculator aoCalc = new AoCalculator(blockInfo, this::brightness, this::aoLevel);
	private final BaseMeshConsumer meshConsumer = new BaseMeshConsumer(new QuadBufferer(this::outputBuffer), blockInfo, aoCalc);
	private VertexConsumer bufferBuilder;
	private boolean didOutput = false;
	// These are kept as fields to avoid the heap allocation for a supplier.
	// BlockModelRenderer allows the caller to supply both the random object and seed.
	private Random random;
	private long seed;
	private BakedModel model;

	@Override
	public Random random() {
		random.setSeed(seed);
		return random;
	}

	@Override
	public boolean cullTest(int faceId) {
		return true; //TODO
	}

	@Override
	public int indexedColor(int colorIndex) {
		return 0xffffffff;
	}

	@Override
	public RenderLayer defaultRenderType() {
		return null;
	}

	@Override
	public int defaultPreset() {
		return MaterialConstants.PRESET_DEFAULT;
	}

	@Override
	public @Nullable Object blockEntityRenderData(BlockPos blockPos) {
		return null; // TODO
	}

	@Override
	public Type type() {
		return Type.BLOCK;
	}

	@Override
	public BlockRenderView blockView() {
		return blockInfo.blockView;
	}

	@Override
	public boolean isFluidModel() {
		return model instanceof FluidModel; // TODO ??
	}

	@Override
	public @Nullable BakedModel bakedModel() {
		return model;
	}

	@Override
	public BlockState blockState() {
		return blockInfo.blockState;
	}

	@Override
	public BlockPos pos() {
		return blockInfo.blockPos;
	}

	/**
	 * Reuse the fallback consumer from the render context used during chunk rebuild to make it properly
	 * apply the current transforms to vanilla models.
	 */
	private final BaseFallbackConsumer fallbackConsumer = new BaseFallbackConsumer(new QuadBufferer(this::outputBuffer), blockInfo, aoCalc);

	private int brightness(BlockPos pos) {
		if (blockInfo.blockView == null) {
			return LightmapTextureManager.MAX_LIGHT_COORDINATE;
		}

		return WorldRenderer.getLightmapCoordinates(blockInfo.blockView, blockInfo.blockView.getBlockState(pos), pos);
	}

	private float aoLevel(BlockPos pos) {
		final BlockRenderView blockView = blockInfo.blockView;
		return blockView == null ? 1f : AoLuminanceFix.INSTANCE.apply(blockView, pos);
	}

	private VertexConsumer outputBuffer(RenderLayer renderLayer) {
		didOutput = true;
		return bufferBuilder;
	}

	public boolean render(BlockRenderView blockView, BakedModel model, BlockState state, BlockPos pos, MatrixStack matrixStack, VertexConsumer buffer, Random random, long seed, int overlay) {
		this.bufferBuilder = buffer;
		this.matrix = matrixStack.peek().getPositionMatrix();
		this.normalMatrix = (FastMatrix3f) (Object) matrixStack.peek().getNormalMatrix();
		this.random = random;
		this.seed = seed;
		this.model = model;

		this.overlay = overlay;
		this.didOutput = false;
		aoCalc.clear();
		blockInfo.setBlockView(blockView);
		blockInfo.prepareForBlock(state, pos, model.useAmbientOcclusion());

//		((FabricBakedModel) model).emitBlockQuads(blockView, state, pos, randomSupplier, this);
		((BlockModel) model).renderAsBlock(this, getEmitter());

		blockInfo.release();
		this.bufferBuilder = null;
		this.random = null;
		this.seed = seed;
		this.model = null;

		return didOutput;
	}

	private class QuadBufferer extends VertexConsumerQuadBufferer {
		QuadBufferer(Function<RenderLayer, VertexConsumer> bufferFunc) {
			super(bufferFunc);
		}

		@Override
		protected Matrix4f matrix() {
			return matrix;
		}

		@Override
		protected FastMatrix3f normalMatrix() {
			return normalMatrix;
		}

		@Override
		protected int overlay() {
			return overlay;
		}
	}

//	@Override
	public Consumer<Mesh> meshConsumer() {
		return meshConsumer;
	}

//	@Override
	public Consumer<BakedModel> fallbackConsumer() {
		return fallbackConsumer;
	}

//	@Override
	public QuadEmitter getEmitter() {
		return meshConsumer.getEmitter();
	}
}
