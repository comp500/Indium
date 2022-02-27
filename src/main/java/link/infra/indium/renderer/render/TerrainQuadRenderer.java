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

import link.infra.indium.other.SpriteFinderCache;
import link.infra.indium.renderer.aocalc.AoCalculator;
import link.infra.indium.renderer.mesh.MutableQuadViewImpl;
import me.jellysquid.mods.sodium.client.model.IndexBufferBuilder;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadWinding;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext.QuadTransform;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.*;

import java.util.function.Function;

public abstract class TerrainQuadRenderer extends AbstractQuadRenderer {
	protected final Function<RenderLayer, ChunkModelBuilder> builderFunc;

	protected abstract Vec3i origin();

	protected abstract Vec3d blockOffset();

	TerrainQuadRenderer(BlockRenderInfo blockInfo, Function<RenderLayer, ChunkModelBuilder> builderFunc, AoCalculator aoCalc, QuadTransform transform) {
		super(blockInfo, aoCalc, transform);
		this.builderFunc = builderFunc;
	}

	@Override
	protected void bufferQuad(MutableQuadViewImpl quad, RenderLayer renderLayer) {
		bufferQuad(builderFunc.apply(renderLayer), quad, origin(), blockOffset());
	}

	public static void bufferQuad(ChunkModelBuilder builder, MutableQuadViewImpl quad, Vec3i origin, Vec3d blockOffset) {
		ModelVertexSink vertices = builder.getVertexSink();
		vertices.ensureCapacity(4);

		Direction cullFace = quad.cullFace();
		IndexBufferBuilder indices = builder.getIndexBufferBuilder(cullFace != null ? ModelQuadFacing.fromDirection(cullFace) : ModelQuadFacing.UNASSIGNED);

		int vertexStart = vertices.getVertexCount();

		for (int i = 0; i < 4; i++) {
			float x = quad.x(i) + (float) blockOffset.getX();
			float y = quad.y(i) + (float) blockOffset.getY();
			float z = quad.z(i) + (float) blockOffset.getZ();

			int color = quad.spriteColor(i, 0);
			color = ColorABGR.pack(color & 0xFF, (color >> 8) & 0xFF, (color >> 16) & 0xFF, (color >> 24) & 0xFF);

			float u = quad.spriteU(i, 0);
			float v = quad.spriteV(i, 0);

			int lm = quad.lightmap(i);

			vertices.writeVertex(origin, x, y, z, color, u, v, lm, builder.getChunkId());
		}

		indices.add(vertexStart, ModelQuadWinding.CLOCKWISE);

		Sprite sprite = quad.cachedSprite();

		if (sprite == null) {
			sprite = SpriteFinderCache.forBlockAtlas().find(quad, 0);
		}

		builder.addSprite(sprite);

		vertices.flush();
	}
}
