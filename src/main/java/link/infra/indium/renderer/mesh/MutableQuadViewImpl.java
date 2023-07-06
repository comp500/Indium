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

package link.infra.indium.renderer.mesh;

import static link.infra.indium.renderer.mesh.EncodingFormat.EMPTY;
import static link.infra.indium.renderer.mesh.EncodingFormat.HEADER_BITS;
import static link.infra.indium.renderer.mesh.EncodingFormat.HEADER_COLOR_INDEX;
import static link.infra.indium.renderer.mesh.EncodingFormat.HEADER_STRIDE;
import static link.infra.indium.renderer.mesh.EncodingFormat.HEADER_TAG;
import static link.infra.indium.renderer.mesh.EncodingFormat.VERTEX_COLOR;
import static link.infra.indium.renderer.mesh.EncodingFormat.VERTEX_LIGHTMAP;
import static link.infra.indium.renderer.mesh.EncodingFormat.VERTEX_NORMAL;
import static link.infra.indium.renderer.mesh.EncodingFormat.VERTEX_STRIDE;
import static link.infra.indium.renderer.mesh.EncodingFormat.VERTEX_U;
import static link.infra.indium.renderer.mesh.EncodingFormat.VERTEX_X;

import org.jetbrains.annotations.Nullable;

import link.infra.indium.renderer.IndiumRenderer;
import link.infra.indium.renderer.helper.ColorHelper;
import link.infra.indium.renderer.helper.NormalHelper;
import link.infra.indium.renderer.helper.TextureHelper;
import link.infra.indium.renderer.material.RenderMaterialImpl;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadOrientation;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;

/**
 * Almost-concrete implementation of a mutable quad. The only missing part is {@link #emitDirectly()},
 * because that depends on where/how it is used. (Mesh encoding vs. render-time transformation).
 *
 * <p>In many cases an instance of this class is used as an "editor quad". The editor quad's
 * {@link #emitDirectly()} method calls some other internal method that transforms the quad
 * data and then buffers it. Transformations should be the same as they would be in a vanilla
 * render - the editor is serving mainly as a way to access vertex data without magical
 * numbers. It also allows for a consistent interface for those transformations.
 */
public abstract class MutableQuadViewImpl extends QuadViewImpl implements QuadEmitter {
	@Nullable
	private Sprite cachedSprite;
	/** This field is only used during terrain rendering. */
	private ModelQuadOrientation orientation = ModelQuadOrientation.NORMAL;

	@Nullable
	public Sprite cachedSprite() {
		return cachedSprite;
	}

	public void cachedSprite(@Nullable Sprite sprite) {
		cachedSprite = sprite;
	}

	public ModelQuadOrientation orientation() {
		return orientation;
	}

	/** The set value is only respected during terrain rendering. */
	public void orientation(ModelQuadOrientation orientation) {
		this.orientation = orientation;
	}

	public void clear() {
		System.arraycopy(EMPTY, 0, data, baseIndex, EncodingFormat.TOTAL_STRIDE);
		isGeometryInvalid = true;
		nominalFace = null;
		normalFlags(0);
		tag(0);
		colorIndex(-1);
		cullFace(null);
		material(IndiumRenderer.MATERIAL_STANDARD);
		cachedSprite(null);
	}

	@Override
	public void load() {
		super.load();
		cachedSprite(null);
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

	@Override
	public MutableQuadViewImpl color(int vertexIndex, int color) {
		data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_COLOR] = color;
		return this;
	}

	@Override
	public MutableQuadViewImpl uv(int vertexIndex, float u, float v) {
		final int i = baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_U;
		data[i] = Float.floatToRawIntBits(u);
		data[i + 1] = Float.floatToRawIntBits(v);
		cachedSprite(null);
		return this;
	}

	@Override
	public MutableQuadViewImpl spriteBake(Sprite sprite, int bakeFlags) {
		TextureHelper.bakeSprite(this, sprite, bakeFlags);
		cachedSprite(sprite);
		return this;
	}

	@Override
	public MutableQuadViewImpl lightmap(int vertexIndex, int lightmap) {
		data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_LIGHTMAP] = lightmap;
		return this;
	}

	protected void normalFlags(int flags) {
		data[baseIndex + HEADER_BITS] = EncodingFormat.normalFlags(data[baseIndex + HEADER_BITS], flags);
	}

	@Override
	public MutableQuadViewImpl normal(int vertexIndex, float x, float y, float z) {
		normalFlags(normalFlags() | (1 << vertexIndex));
		data[baseIndex + vertexIndex * VERTEX_STRIDE + VERTEX_NORMAL] = NormalHelper.packNormal(x, y, z);
		return this;
	}

	/**
	 * Internal helper method. Copies face normals to vertex normals lacking one.
	 */
	public final void populateMissingNormals() {
		final int normalFlags = this.normalFlags();

		if (normalFlags == 0b1111) return;

		final int packedFaceNormal = packedFaceNormal();

		for (int v = 0; v < 4; v++) {
			if ((normalFlags & (1 << v)) == 0) {
				data[baseIndex + v * VERTEX_STRIDE + VERTEX_NORMAL] = packedFaceNormal;
			}
		}

		normalFlags(0b1111);
	}

	@Override
	public final MutableQuadViewImpl cullFace(@Nullable Direction face) {
		data[baseIndex + HEADER_BITS] = EncodingFormat.cullFace(data[baseIndex + HEADER_BITS], face);
		nominalFace(face);
		return this;
	}

	@Override
	public final MutableQuadViewImpl nominalFace(@Nullable Direction face) {
		nominalFace = face;
		return this;
	}

	@Override
	public final MutableQuadViewImpl material(RenderMaterial material) {
		if (material == null) {
			material = IndiumRenderer.MATERIAL_STANDARD;
		}

		data[baseIndex + HEADER_BITS] = EncodingFormat.material(data[baseIndex + HEADER_BITS], (RenderMaterialImpl) material);
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
	public MutableQuadViewImpl copyFrom(QuadView quad) {
		final QuadViewImpl q = (QuadViewImpl) quad;
		q.computeGeometry();

		System.arraycopy(q.data, q.baseIndex, data, baseIndex, EncodingFormat.TOTAL_STRIDE);
		faceNormal.set(q.faceNormal);
		nominalFace = q.nominalFace;
		isGeometryInvalid = false;

		if (quad instanceof MutableQuadViewImpl mutableQuad) {
			cachedSprite(mutableQuad.cachedSprite());
		} else {
			cachedSprite(null);
		}

		return this;
	}

	@Override
	public final MutableQuadViewImpl fromVanilla(int[] quadData, int startIndex) {
		System.arraycopy(quadData, startIndex, data, baseIndex + HEADER_STRIDE, VANILLA_QUAD_STRIDE);
		isGeometryInvalid = true;
		cachedSprite(null);

		int colorIndex = baseIndex + VERTEX_COLOR;

		for (int i = 0; i < 4; i++) {
			data[colorIndex] = ColorHelper.fromVanillaColor(data[colorIndex]);
			colorIndex += VERTEX_STRIDE;
		}

		return this;
	}

	@Override
	public final MutableQuadViewImpl fromVanilla(BakedQuad quad, RenderMaterial material, @Nullable Direction cullFace) {
		fromVanilla(quad.getVertexData(), 0);
		data[baseIndex + HEADER_BITS] = EncodingFormat.cullFace(0, cullFace);
		nominalFace(quad.getFace());
		colorIndex(quad.getColorIndex());

		if (!quad.hasShade()) {
			material = RenderMaterialImpl.setDisableDiffuse((RenderMaterialImpl) material, true);
		}

		material(material);
		tag(0);
		cachedSprite(quad.getSprite());
		return this;
	}

	/**
	 * Emit the quad without clearing the underlying data.
	 * Geometry is not guaranteed to be valid when called, but can be computed by calling {@link #computeGeometry()}.
	 */
	public abstract void emitDirectly();

	@Override
	public final MutableQuadViewImpl emit() {
		emitDirectly();
		clear();
		return this;
	}
}
