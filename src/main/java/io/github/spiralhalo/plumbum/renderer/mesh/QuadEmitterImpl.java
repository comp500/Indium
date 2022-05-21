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

package io.github.spiralhalo.plumbum.renderer.mesh;

import io.vram.frex.api.buffer.QuadEmitter;
import io.vram.frex.api.buffer.VertexEmitter;
import io.vram.frex.api.math.PackedVector3f;
import io.vram.frex.base.renderer.mesh.BaseQuadEmitter;
import io.vram.frex.base.renderer.mesh.RootQuadEmitter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import org.jetbrains.annotations.Nullable;

/**
 * Same as {@link RootQuadEmitter} but with sprite cache and helper function
 */
public abstract class QuadEmitterImpl extends RootQuadEmitter {
	private Sprite cachedSprite;

	@Override
	public VertexConsumer texture(float u, float v) {
		return uv(u, v);
	}

	@Override
	public VertexConsumer overlay(int u, int v) {
		return overlayCoords(u, v);
	}

	@Override
	public VertexConsumer light(int u, int v) {
		return uv2(u, v);
	}

	/**
	 * Helper function to populate missing vertex normals
	 */
	public void populateMissingNormals() {
		int normalFlag = normalFlags();
		if (normalFlag == 0b1111) return;

		int packedNormal = packedFaceNormal();
		for (int i = 0; i < 4; i++) {
			if (((normalFlag >> i) & 1) == 0) {
				normal(i, PackedVector3f.unpackX(packedNormal), PackedVector3f.unpackY(packedNormal), PackedVector3f.unpackZ(packedNormal));
			}
		}
	}

	public boolean hasShade() {
		return !material().disableDiffuse();
	}

	public Sprite cachedSprite() {
		return cachedSprite;
	}

	/** CLEARING SPRITE CACHE **/

	@Override
	public void clear() {
		cachedSprite = null;
		super.clear();
	}

	@Override
	public QuadEmitter fromVanilla(int[] quadData, int startIndex) {
		cachedSprite = null;
		return super.fromVanilla(quadData, startIndex);
	}

	@Override
	public BaseQuadEmitter spritePrecise(int vertexIndex, int u, int v) {
		cachedSprite = null;
		return super.spritePrecise(vertexIndex, u, v);
	}

	@Override
	public BaseQuadEmitter spriteFloat(int vertexIndex, float u, float v) {
		cachedSprite = null;
		return super.spriteFloat(vertexIndex, u, v);
	}

	@Override
	protected void normalizeSprite() {
		cachedSprite = null;
		super.normalizeSprite();
	}

	@Override
	public void normalizeSpritesIfNeeded() {
		cachedSprite = null;
		super.normalizeSpritesIfNeeded();
	}

	@Override
	public BaseQuadEmitter spriteId(int spriteId) {
		cachedSprite = null;
		return super.spriteId(spriteId);
	}

	@Override
	public QuadEmitter uvUnitSquare() {
		cachedSprite = null;
		return super.uvUnitSquare();
	}

	@Override
	public BaseQuadEmitter uv(int vertexIndex, float u, float v) {
		cachedSprite = null;
		return super.uv(vertexIndex, u, v);
	}

	@Override
	public VertexEmitter uv(float u, float v) {
		cachedSprite = null;
		return super.uv(u, v);
	}

	/** SETTING SPRITE CACHE **/

	@Override
	public BaseQuadEmitter uvSprite(@Nullable Sprite sprite, float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) {
		if (sprite != null) cachedSprite = sprite;
		return super.uvSprite(sprite, u0, v0, u1, v1, u2, v2, u3, v3);
	}

	@Override
	public BaseQuadEmitter spriteBake(Sprite sprite, int bakeFlags) {
		cachedSprite = sprite;
		return super.spriteBake(sprite, bakeFlags);
	}

	@Override
	protected Sprite findSprite() {
		return cachedSprite = super.findSprite();
	}
}
