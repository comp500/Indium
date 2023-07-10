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

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

/**
 * Holds, manages, and provides access to the block/world related state
 * needed to render quads.
 *
 * <p>Exception: per-block position offsets are tracked in {@link TerrainRenderContext}
 * so they can be applied together with chunk offsets.
 */
public class BlockRenderInfo {
	private final BlockColors blockColorMap = MinecraftClient.getInstance().getBlockColors();
	private final BlockPos.Mutable searchPos = new BlockPos.Mutable();

	public BlockRenderView blockView;
	public BlockPos blockPos;
	public BlockState blockState;

	boolean useAo;
	boolean defaultAo;
	RenderLayer defaultLayer;

	Random random;
	long seed;
	public final Supplier<Random> randomSupplier = () -> {
		random.setSeed(seed);
		return random;
	};

	private boolean enableCulling;
	private int cullCompletionFlags;
	private int cullResultFlags;

	public void prepareForWorld(BlockRenderView blockView, boolean enableCulling) {
		this.blockView = blockView;
		this.enableCulling = enableCulling;
	}

	public void prepareForBlock(BlockState blockState, BlockPos blockPos, long seed, boolean modelAo) {
		this.blockPos = blockPos;
		this.blockState = blockState;
		this.seed = seed;

		useAo = MinecraftClient.isAmbientOcclusionEnabled();
		defaultAo = useAo && modelAo && blockState.getLuminance() == 0;

		defaultLayer = RenderLayers.getBlockLayer(blockState);

		cullCompletionFlags = 0;
		cullResultFlags = 0;
	}

	/**
	 * Clean up context for reuse in another block
	 */
	public void releaseBlock() {
		blockPos = null;
		blockState = null;
	}

	/**
	 * Clean up context for reuse in another world (implies reuse in another block)
	 * Clears reference to world: must be called before unloading, or all references to it removed
	 */
	public void release() {
		releaseBlock();
		blockView = null;
	}

	int blockColor(int colorIndex) {
		return 0xFF000000 | blockColorMap.getColor(blockState, blockView, blockPos, colorIndex);
	}

	boolean shouldDrawFace(@Nullable Direction face) {
		if (face == null || !enableCulling) {
			return true;
		}

		final int mask = 1 << face.getId();

		if ((cullCompletionFlags & mask) == 0) {
			cullCompletionFlags |= mask;

			if (shouldDrawFaceInner(face)) {
				cullResultFlags |= mask;
				return true;
			} else {
				return false;
			}
		} else {
			return (cullResultFlags & mask) != 0;
		}
	}

	boolean shouldDrawFaceInner(Direction face) {
		return Block.shouldDrawSide(blockState, blockView, blockPos, face, searchPos.set(blockPos, face));
	}

	RenderLayer effectiveRenderLayer(BlendMode blendMode) {
		return blendMode == BlendMode.DEFAULT ? this.defaultLayer : blendMode.blockRenderLayer;
	}
}
