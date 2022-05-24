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

package link.infra.indium.renderer.render;

import me.jellysquid.mods.sodium.client.render.occlusion.BlockOcclusionCache;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;

import io.vram.frex.api.material.MaterialConstants;
import io.vram.frex.api.material.MaterialMap;
import io.vram.frex.api.material.RenderMaterial;
import io.vram.frex.base.renderer.context.input.BaseBlockInputContext;

/**
 * Holds, manages and provides access to the block/world related state
 * needed by fallback and mesh consumers.
 *
 * <p>Exception: per-block position offsets are tracked in {@link ChunkRenderInfo}
 * so they can be applied together with chunk offsets.
 */
public class BlockRenderInfo extends BaseBlockInputContext<BlockRenderView> {
	protected BlockOcclusionCache blockOcclusionCache;
	MaterialMap materialMap = MaterialMap.defaultMaterialMap();
	boolean defaultAo;
	RenderLayer defaultLayer;

	public void setBlockView(BlockRenderView blockView) {
		this.blockView = blockView;
	}

	public void setBlockOcclusionCache(BlockOcclusionCache blockOcclusionCache) {
		this.blockOcclusionCache = blockOcclusionCache;
	}

	@Override
	public void prepareForBlock(BakedModel bakedModel, BlockState blockState, BlockPos blockPos) {
		super.prepareForBlock(bakedModel, blockState, blockPos);
		materialMap = isFluidModel() ? MaterialMap.get(blockState.getFluidState()) : MaterialMap.get(blockState);
		defaultAo = bakedModel.useAmbientOcclusion() && MinecraftClient.isAmbientOcclusionEnabled() && blockState.getLuminance() == 0;
		defaultLayer = RenderLayers.getBlockLayer(blockState);
	}

	@Override
	public void release() {
		super.release();
		blockPos = null;
		blockState = null;
		blockOcclusionCache = null;
	}

	RenderLayer effectiveRenderLayer(RenderMaterial mat) {
		return switch (mat.preset()) {
			case MaterialConstants.PRESET_SOLID -> RenderLayer.getSolid();
			case MaterialConstants.PRESET_CUTOUT_MIPPED -> RenderLayer.getCutoutMipped();
			case MaterialConstants.PRESET_CUTOUT -> RenderLayer.getCutout();
			case MaterialConstants.PRESET_TRANSLUCENT -> RenderLayer.getTranslucent();
			case MaterialConstants.PRESET_DEFAULT -> defaultLayer;
			default -> {
				if (mat.target() == MaterialConstants.TARGET_MAIN) {
					if (mat.cutout() == MaterialConstants.CUTOUT_NONE) {
						yield RenderLayer.getSolid();
					} else {
						yield mat.unmipped() ? RenderLayer.getCutout() : RenderLayer.getCutoutMipped();
					}
				} else {
					yield RenderLayer.getTranslucent();
				}
			}
		};
	}
}
