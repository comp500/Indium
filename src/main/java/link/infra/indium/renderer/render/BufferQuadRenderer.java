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
import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext.QuadTransform;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.*;

import java.util.function.Function;

public abstract class BufferQuadRenderer extends AbstractQuadRenderer {
	protected final Function<RenderLayer, VertexConsumer> bufferFunc;
	protected final Vec3f normalVec = new Vec3f();

	protected abstract Matrix4f matrix();

	protected abstract Matrix3f normalMatrix();

	protected abstract int overlay();

	BufferQuadRenderer(BlockRenderInfo blockInfo, Function<RenderLayer, VertexConsumer> bufferFunc, AoCalculator aoCalc, QuadTransform transform) {
		super(blockInfo, aoCalc, transform);
		this.bufferFunc = bufferFunc;
	}

	@Override
	protected void bufferQuad(MutableQuadViewImpl quad, RenderLayer renderLayer) {
		bufferQuad(bufferFunc.apply(renderLayer), quad, matrix(), overlay(), normalMatrix(), normalVec);
	}

	public static void bufferQuad(VertexConsumer buff, MutableQuadViewImpl quad, Matrix4f matrix, int overlay, Matrix3f normalMatrix, Vec3f normalVec) {
		final boolean useNormals = quad.hasVertexNormals();

		if (useNormals) {
			quad.populateMissingNormals();
		} else {
			final Vec3f faceNormal = quad.faceNormal();
			normalVec.set(faceNormal.getX(), faceNormal.getY(), faceNormal.getZ());
			normalVec.transform(normalMatrix);
		}

		for (int i = 0; i < 4; i++) {
			buff.vertex(matrix, quad.x(i), quad.y(i), quad.z(i));
			final int color = quad.spriteColor(i, 0);
			buff.color(color & 0xFF, (color >> 8) & 0xFF, (color >> 16) & 0xFF, (color >> 24) & 0xFF);
			buff.texture(quad.spriteU(i, 0), quad.spriteV(i, 0));
			buff.overlay(overlay);
			buff.light(quad.lightmap(i));

			if (useNormals) {
				normalVec.set(quad.normalX(i), quad.normalY(i), quad.normalZ(i));
				normalVec.transform(normalMatrix);
			}

			buff.normal(normalVec.getX(), normalVec.getY(), normalVec.getZ());
			buff.next();
		}

		Sprite sprite = quad.cachedSprite();

		if (sprite == null) {
			sprite = SpriteFinderCache.forBlockAtlas().find(quad, 0);
		}

		SpriteUtil.markSpriteActive(sprite);
	}
}
