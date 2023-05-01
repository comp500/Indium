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

import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.world.BlockRenderView;

/**
 * Holds, manages and provides access to the chunk-related state
 * needed by fallback and mesh consumers during terrain rendering.
 */
public class ChunkRenderInfo {
	final BlockRenderView blockView;

	ChunkBuildBuffers buffers;
	boolean didOutput = false;

	ChunkRenderInfo(BlockRenderView blockView) {
		this.blockView = blockView;
	}

	void prepare(ChunkBuildBuffers buffers) {
		this.buffers = buffers;
	}

	void release() {
		buffers = null;
	}

	public ChunkModelBuilder getChunkModelBuilder(RenderLayer renderLayer) {
		didOutput = true;
		return buffers.get(DefaultMaterials.forRenderLayer(renderLayer));
	}
}
