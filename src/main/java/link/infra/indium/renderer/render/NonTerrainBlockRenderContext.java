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

import link.infra.indium.renderer.aocalc.AoCalculator;
import link.infra.indium.renderer.mesh.MutableQuadViewImpl;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

/**
 * Context for non-terrain block rendering.
 */
public class NonTerrainBlockRenderContext extends AbstractBlockRenderContext {
	private final SingleBlockLightDataCache lightCache = new SingleBlockLightDataCache();
	private VertexConsumer vertexConsumer;

	public NonTerrainBlockRenderContext() {
		blockInfo = new BlockRenderInfo();
		aoCalc = new AoCalculator(blockInfo, lightCache);
	}

	@Override
	protected void bufferQuad(MutableQuadViewImpl quad, Material material) {
		bufferQuad(quad, vertexConsumer);
	}

	public void render(BlockRenderView blockView, BakedModel model, BlockState state, BlockPos pos, MatrixStack matrixStack, VertexConsumer buffer, boolean cull, Random random, long seed, int overlay) {
		this.vertexConsumer = buffer;
		this.matrix = matrixStack.peek().getPositionMatrix();
		this.normalMatrix = matrixStack.peek().getNormalMatrix();
		this.overlay = overlay;

		blockInfo.random = random;

		aoCalc.clear();
		lightCache.reset(pos, blockView);
		blockInfo.prepareForWorld(blockView, cull);
		blockInfo.prepareForBlock(state, pos, seed, model.useAmbientOcclusion());

		model.emitBlockQuads(blockView, state, pos, blockInfo.randomSupplier, this);

		// blockInfo is thread-local, not cleaned up when leaving world (and could be called for arbitrary BlockRenderViews)
		blockInfo.release();
		lightCache.release();
		blockInfo.random = null;
		this.vertexConsumer = null;
	}
}
