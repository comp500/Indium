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
public class BlockRenderContext {
	private final BlockRenderInfo blockInfo = new BlockRenderInfo();
	private final AoCalculator aoCalc = new AoCalculator(blockInfo, this::brightness, this::aoLevel);
	private final BaseMeshConsumer meshConsumer = new BaseMeshConsumer(new QuadBufferer(this::outputBuffer), blockInfo, aoCalc);
	private VertexConsumer bufferBuilder;
	private boolean didOutput = false;
	protected Matrix4f matrix;
	protected FastMatrix3f normalMatrix;

	private int brightness(BlockPos pos) {
		if (blockInfo.blockView() == null) {
			return LightmapTextureManager.MAX_LIGHT_COORDINATE;
		}

		return WorldRenderer.getLightmapCoordinates(blockInfo.blockView(), blockInfo.blockView().getBlockState(pos), pos);
	}

	private float aoLevel(BlockPos pos) {
		final BlockRenderView blockView = blockInfo.blockView();
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

		this.didOutput = false;
		aoCalc.clear();
		blockInfo.prepare(overlay);
		blockInfo.prepareForWorld(blockView, false, (io.vram.frex.api.math.MatrixStack) matrixStack);
		blockInfo.prepareForBlock(model, state, pos);

		((BlockModel) model).renderAsBlock(blockInfo, meshConsumer.getEmitter());

		blockInfo.release();
		this.bufferBuilder = null;

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
			return blockInfo.overlay();
		}
	}
}
