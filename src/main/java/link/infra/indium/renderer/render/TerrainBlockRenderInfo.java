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

import me.jellysquid.mods.sodium.client.render.occlusion.BlockOcclusionCache;
import net.minecraft.util.math.Direction;

public class TerrainBlockRenderInfo extends BlockRenderInfo {
	protected final BlockOcclusionCache blockOcclusionCache;

	public TerrainBlockRenderInfo(BlockOcclusionCache blockOcclusionCache) {
		this.blockOcclusionCache = blockOcclusionCache;
	}

	@Override
	boolean shouldDrawFaceInner(Direction face) {
		return blockOcclusionCache.shouldDrawSide(blockState, blockView, blockPos, face);
	}
}
