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

import io.vram.frex.api.buffer.*;
import io.vram.frex.api.material.RenderMaterial;
import io.vram.frex.api.math.FastMatrix3f;
import io.vram.frex.api.math.FastMatrix4f;
import io.vram.frex.api.model.InputContext;
import io.vram.frex.api.model.util.FaceUtil;
import io.vram.frex.base.renderer.mesh.MeshEncodingHelper;
import io.github.spiralhalo.plumbum.renderer.RenderMaterialImpl.Value;
import io.github.spiralhalo.plumbum.renderer.helper.NormalHelper;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;
import org.jetbrains.annotations.Nullable;

import static io.github.spiralhalo.plumbum.renderer.mesh.EncodingFormat.*;

/**
 * Almost-concrete implementation of a mutable quad. The only missing part is {@link #emit()},
 * because that depends on where/how it is used. (Mesh encoding vs. render-time transformation).
 */
public abstract class MutableQuadViewImpl extends QuadViewImpl implements QuadEmitter, VertexEmitter {
	private final PlumbumTransformStack transformStack = createTransformStack();
	private Sprite cachedSprite;
	protected RenderMaterial defaultMaterial = RenderMaterial.defaultMaterial();
	private int vertexIndex = 0;

	public final void begin(int[] data, int baseIndex) {
		this.data = data;
		this.baseIndex = baseIndex;
		clear();
	}

	public void clear() {
		System.arraycopy(EMPTY, 0, data, baseIndex, EncodingFormat.TOTAL_STRIDE);
		isGeometryInvalid = true;
		nominalFace = null;
		normalFlags(0);
		tag(0);
		colorIndex(-1);
		cullFace(null);
		material(defaultMaterial);
		cachedSprite(null);
	}

	@Override
	public final void load() {
		super.load();
		cachedSprite(null);
	}

	@Override
	public final MutableQuadViewImpl material(RenderMaterial material) {
		if (material == null) {
			material = defaultMaterial;
		}

		data[baseIndex + HEADER_BITS] = EncodingFormat.material(data[baseIndex + HEADER_BITS], (Value) material);
		return this;
	}

	@Override
	public final MutableQuadViewImpl cullFace(Direction face) {
		data[baseIndex + HEADER_BITS] = EncodingFormat.cullFace(data[baseIndex + HEADER_BITS], face);
		nominalFace(face);
		return this;
	}

	@Override
	public final MutableQuadViewImpl nominalFace(Direction face) {
		nominalFace = face;
		return this;
	}

	@Override
	public final MutableQuadViewImpl colorIndex(int colorIndex) {
		data[baseIndex + HEADER_COLOR_INDEX] = colorIndex;
		return this;
	}

	@Override
	public final MutableQuadViewImpl tag(int tag) {
		data[baseIndex + HEADER_TAG] = tag;
		return this;
	}

	@Override
	public final MutableQuadViewImpl fromVanilla(int[] quadData, int startIndex) {
		System.arraycopy(quadData, startIndex, data, baseIndex + HEADER_STRIDE, QUAD_STRIDE);
		isGeometryInvalid = true;
		cachedSprite(null);
		return this;
	}

	@Override
	public final MutableQuadViewImpl fromVanilla(BakedQuad quad, RenderMaterial material, Direction cullFace) {
		System.arraycopy(quad.getVertexData(), 0, data, baseIndex + HEADER_STRIDE, QUAD_STRIDE);
		data[baseIndex + HEADER_BITS] = EncodingFormat.cullFace(0, cullFace);
		nominalFace(quad.getFace());
		colorIndex(quad.getColorIndex());
		material(material);
		tag(0);
		shade(quad.hasShade());
		isGeometryInvalid = true;
		cachedSprite(quad.getSprite());
		return this;
	}

	@Override
	public final MutableQuadViewImpl defaultMaterial(RenderMaterial material) {
		defaultMaterial = material;
		return this;
	}

	@Override
	public final MutableQuadViewImpl fromVanilla(BakedQuad quad, RenderMaterial material, int cullFaceId) {
		return fromVanilla(quad, material, FaceUtil.faceFromIndex(cullFaceId));
	}

	@Override
	public final MutableQuadViewImpl tangent(int vertexIndex, float x, float y, float z) {
		// unsupported
		return this;
	}

	@Override
	public MutableQuadViewImpl pos(int vertexIndex, float x, float y, float z) {
		final int index = baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_X;
		data[index] = Float.floatToRawIntBits(x);
		data[index + 1] = Float.floatToRawIntBits(y);
		data[index + 2] = Float.floatToRawIntBits(z);
		isGeometryInvalid = true;
		return this;
	}

	protected void normalFlags(int flags) {
		data[baseIndex + HEADER_BITS] = EncodingFormat.normalFlags(data[baseIndex + HEADER_BITS], flags);
	}

	@Override
	public MutableQuadViewImpl normal(int vertexIndex, float x, float y, float z) {
		normalFlags(normalFlags() | (1 << vertexIndex));
		data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_NORMAL] = NormalHelper.packNormal(x, y, z, 0);
		return this;
	}

	/**
	 * Internal helper method. Copies face normals to vertex normals lacking one.
	 */
	public final void populateMissingNormals() {
		final int normalFlags = this.normalFlags();

		if (normalFlags == 0b1111) return;

		final int packedFaceNormal = NormalHelper.packNormal(packedFaceNormalFrex, 0);

		for (int v = 0; v < 4; v++) {
			if ((normalFlags & (1 << v)) == 0) {
				data[baseIndex + v * VERTEX_STRIDE + VERTEX_NORMAL] = packedFaceNormal;
			}
		}

		normalFlags(0b1111);
	}

	@Override
	public MutableQuadViewImpl lightmap(int vertexIndex, int lightmap) {
		data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_LIGHTMAP] = lightmap;
		return this;
	}

	@Override
	public MutableQuadViewImpl vertexColor(int vertexIndex, int color) {
		data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_COLOR] = color;
		return this;
	}

	@Override
	public QuadEmitter uv(int vertexIndex, float u, float v) {
		return null;
	}

	@Override
	public QuadEmitter uvSprite(@Nullable Sprite sprite, float v, float v1, float v2, float v3, float v4, float v5, float v6, float v7) {
		return null;
	}

	@Override
	public QuadEmitter spriteBake(Sprite sprite, int i) {
		return null;
	}

//	@Override
//	public MutableQuadViewImpl sprite(int vertexIndex, int spriteIndex, float u, float v) {
//		Preconditions.checkArgument(spriteIndex == 0, "Unsupported sprite index: %s", spriteIndex);
//
//		final int i = baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_U;
//		data[i] = Float.floatToRawIntBits(u);
//		data[i + 1] = Float.floatToRawIntBits(v);
//		cachedSprite = null;
//		return this;
//	}
//
//	@Override
//	public MutableQuadViewImpl spriteBake(int spriteIndex, Sprite sprite, int bakeFlags) {
//		Preconditions.checkArgument(spriteIndex == 0, "Unsupported sprite index: %s", spriteIndex);
//
//		TextureHelper.bakeSprite(this, spriteIndex, sprite, bakeFlags);
//		cachedSprite(sprite);
//		return this;
//	}

	public Sprite cachedSprite() {
		return cachedSprite;
	}

	public void cachedSprite(Sprite cachedSprite) {
		this.cachedSprite = cachedSprite;
	}

	/** Override to use custom stack. */
	protected PlumbumTransformStack createTransformStack() {
		return new PlumbumTransformStack();
	}

	@Override
	public PooledQuadEmitter withTransformQuad(InputContext context, QuadTransform transform) {
		return transformStack.createTransform(context, transform, this);
	}

	@Override
	public PooledVertexEmitter withTransformVertex(InputContext context, QuadTransform transform) {
		return transformStack.createTransform(context, transform, this);
	}

	@Override
	public VertexEmitter asVertexEmitter() {
		return this;
	}

	/** VERTEX EMITTER **/

	@Override
	public VertexEmitter vertex(float x, float y, float z) {
		pos(vertexIndex, x, y, z);
		return this;
	}

	@Override
	public VertexEmitter color(int color) {
		vertexColor(vertexIndex, color);
		return this;
	}

	@Override
	public VertexEmitter vertex(Matrix4f matrix, float x, float y, float z) {
		final FastMatrix4f mat = (FastMatrix4f) (Object) matrix;

		final float tx = mat.f_m00() * x + mat.f_m10() * y + mat.f_m20() * z + mat.f_m30();
		final float ty = mat.f_m01() * x + mat.f_m11() * y + mat.f_m21() * z + mat.f_m31();
		final float tz = mat.f_m02() * x + mat.f_m12() * y + mat.f_m22() * z + mat.f_m32();

		return this.vertex(tx, ty, tz);
	}

	@Override
	public VertexEmitter normal(Matrix3f matrix, float x, float y, float z) {
		final FastMatrix3f mat = (FastMatrix3f) (Object) matrix;

		final float tx = mat.f_m00() * x + mat.f_m10() * y + mat.f_m20() * z;
		final float ty = mat.f_m01() * x + mat.f_m11() * y + mat.f_m21() * z;
		final float tz = mat.f_m02() * x + mat.f_m12() * y + mat.f_m22() * z;

		return this.normal(tx, ty, tz);
	}

	@Override
	public VertexEmitter color(int red, int green, int blue, int alpha) {
		return color(MeshEncodingHelper.packColor(red, green, blue, alpha));
	}

	@Override
	public VertexEmitter uv(float u, float v) {
		uv(vertexIndex, u, v);
		return this;
	}

	@Override
	public VertexEmitter overlayCoords(int u, int v) {
		final var mat = material();
		final var oMat = mat.withOverlay(u, v);

		if (oMat != mat) {
			material(oMat);
		}

		return this;
	}

	@Override
	public VertexEmitter uv2(int block, int sky) {
		lightmap(vertexIndex, (block & 0xFF) | ((sky & 0xFF) << 8));
		return this;
	}

	@Override
	public VertexEmitter normal(float x, float y, float z) {
		return normal(vertexIndex, x, y, z);
	}

	@Override
	public void next() {
		if (this.vertexIndex == 3) {
			this.emit();
		} else {
			++this.vertexIndex;
		}
	}

	/** VERTEX CONSUMER **/

	@Override
	public void fixedColor(int red, int green, int blue, int alpha) {
		assert false : "fixedColor call encountered in quad rendering";
	}

	@Override
	public void unfixColor() {
		assert false : "unfixColor call encountered in quad rendering";
	}

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
}
