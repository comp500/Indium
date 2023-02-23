/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
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

package link.infra.indium.renderer.render;


import java.util.function.Consumer;
import java.util.function.Function;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import link.infra.indium.renderer.aocalc.AoCalculator;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

/**
 * Context for non-terrain block rendering.
 */
public class NonTerrainBlockRenderContext extends MatrixRenderContext {
	private final BlockRenderInfo blockInfo = new BlockRenderInfo();
	private final SingleBlockLightDataCache lightCache = new SingleBlockLightDataCache();
	private final AoCalculator aoCalc = new AoCalculator(blockInfo, lightCache);
	private final BaseMeshConsumer meshConsumer = new BaseMeshConsumer(new QuadBufferer(this::outputBuffer), blockInfo, aoCalc, this::transform);
	private VertexConsumer bufferBuilder;

	/**
	 * Reuse the fallback consumer from the render context used during chunk rebuild to make it properly
	 * apply the current transforms to vanilla models.
	 */
	private final BaseFallbackConsumer fallbackConsumer = new BaseFallbackConsumer(new QuadBufferer(this::outputBuffer), blockInfo, aoCalc, this::transform);

	private VertexConsumer outputBuffer(RenderLayer renderLayer) {
		return bufferBuilder;
	}

	public void render(BlockRenderView blockView, BakedModel model, BlockState state, BlockPos pos, MatrixStack matrixStack, VertexConsumer buffer, boolean cull, Random random, long seed, int overlay) {
		this.bufferBuilder = buffer;
		this.matrix = matrixStack.peek().getPositionMatrix();
		this.normalMatrix = matrixStack.peek().getNormalMatrix();

		this.overlay = overlay;
		aoCalc.clear();
		blockInfo.prepareForWorld(blockView, cull);
		blockInfo.prepareForBlock(state, pos, model.useAmbientOcclusion(), seed);
		lightCache.reset(pos, blockView);

		((FabricBakedModel) model).emitBlockQuads(blockView, state, pos, blockInfo.randomSupplier, this);

		blockInfo.release();
		this.bufferBuilder = null;
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
		protected Matrix3f normalMatrix() {
			return normalMatrix;
		}

		@Override
		protected int overlay() {
			return overlay;
		}
	}

	@Override
	public Consumer<Mesh> meshConsumer() {
		return meshConsumer;
	}

	@Override
	public BakedModelConsumer bakedModelConsumer() {
		return fallbackConsumer;
	}

	@Override
	public QuadEmitter getEmitter() {
		return meshConsumer.getEmitter();
	}
}
