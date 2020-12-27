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

package link.infra.ogidni.renderer.mesh;

import com.google.common.base.Preconditions;

import link.infra.ogidni.renderer.helper.GeometryHelper;
import link.infra.ogidni.renderer.helper.NormalHelper;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.util.math.Direction;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import link.infra.ogidni.renderer.RenderMaterialImpl;

/**
 * Base class for all quads / quad makers. Handles the ugly bits
 * of maintaining and encoding the quad state.
 */
public class QuadViewImpl implements QuadView {
	protected Direction nominalFace;
	/** True when geometry flags or light face may not match geometry. */
	protected boolean isGeometryInvalid = true;
	protected final Vector3f faceNormal = new Vector3f();
	private boolean shade = true;

	/** Size and where it comes from will vary in subtypes. But in all cases quad is fully encoded to array. */
	protected int[] data;

	/** Beginning of the quad. Also the header index. */
	protected int baseIndex = 0;

	/**
	 * Use when subtype is "attached" to a pre-existing array.
	 * Sets data reference and index and decodes state from array.
	 */
	final void load(int[] data, int baseIndex) {
		this.data = data;
		this.baseIndex = baseIndex;
		load();
	}

	/**
	 * Like {@link #load(int[], int)} but assumes array and index already set.
	 * Only does the decoding part.
	 */
	public final void load() {
		isGeometryInvalid = false;
		nominalFace = lightFace();

		// face normal isn't encoded
		link.infra.ogidni.renderer.helper.NormalHelper.computeFaceNormal(faceNormal, this);
	}

	/** Reference to underlying array. Use with caution. Meant for fast renderer access */
	public int[] data() {
		return data;
	}

	public int normalFlags() {
		return EncodingFormat.normalFlags(data[baseIndex + EncodingFormat.HEADER_BITS]);
	}

	/** True if any vertex normal has been set. */
	public boolean hasVertexNormals() {
		return normalFlags() != 0;
	}

	/** gets flags used for lighting - lazily computed via {@link link.infra.ogidni.renderer.helper.GeometryHelper#computeShapeFlags(QuadView)}. */
	public int geometryFlags() {
		computeGeometry();
		return EncodingFormat.geometryFlags(data[baseIndex + EncodingFormat.HEADER_BITS]);
	}

	protected void computeGeometry() {
		if (isGeometryInvalid) {
			isGeometryInvalid = false;

			link.infra.ogidni.renderer.helper.NormalHelper.computeFaceNormal(faceNormal, this);

			// depends on face normal
			data[baseIndex + EncodingFormat.HEADER_BITS] = EncodingFormat.lightFace(data[baseIndex + EncodingFormat.HEADER_BITS], link.infra.ogidni.renderer.helper.GeometryHelper.lightFace(this));

			// depends on light face
			data[baseIndex + EncodingFormat.HEADER_BITS] = EncodingFormat.geometryFlags(data[baseIndex + EncodingFormat.HEADER_BITS], GeometryHelper.computeShapeFlags(this));
		}
	}

	@Override
	public final void toVanilla(int textureIndex, int[] target, int targetIndex, boolean isItem) {
		System.arraycopy(data, baseIndex + EncodingFormat.VERTEX_X, target, targetIndex, EncodingFormat.QUAD_STRIDE);
	}

	@Override
	public final RenderMaterialImpl.Value material() {
		return EncodingFormat.material(data[baseIndex + EncodingFormat.HEADER_BITS]);
	}

	@Override
	public final int colorIndex() {
		return data[baseIndex + EncodingFormat.HEADER_COLOR_INDEX];
	}

	@Override
	public final int tag() {
		return data[baseIndex + EncodingFormat.HEADER_TAG];
	}

	@Override
	public final Direction lightFace() {
		computeGeometry();
		return EncodingFormat.lightFace(data[baseIndex + EncodingFormat.HEADER_BITS]);
	}

	@Override
	public final Direction cullFace() {
		return EncodingFormat.cullFace(data[baseIndex + EncodingFormat.HEADER_BITS]);
	}

	@Override
	public final Direction nominalFace() {
		return nominalFace;
	}

	@Override
	public final Vector3f faceNormal() {
		computeGeometry();
		return faceNormal;
	}

	@Override
	public void copyTo(MutableQuadView target) {
		computeGeometry();

		final MutableQuadViewImpl quad = (MutableQuadViewImpl) target;
		// copy everything except the material
		System.arraycopy(data, baseIndex + 1, quad.data, quad.baseIndex + 1, EncodingFormat.TOTAL_STRIDE - 1);
		quad.faceNormal.set(faceNormal.getX(), faceNormal.getY(), faceNormal.getZ());
		quad.nominalFace = this.nominalFace;
		quad.isGeometryInvalid = false;
	}

	@Override
	public Vector3f copyPos(int vertexIndex, Vector3f target) {
		if (target == null) {
			target = new Vector3f();
		}

		final int index = baseIndex + vertexIndex * EncodingFormat.VERTEX_STRIDE + EncodingFormat.VERTEX_X;
		target.set(Float.intBitsToFloat(data[index]), Float.intBitsToFloat(data[index + 1]), Float.intBitsToFloat(data[index + 2]));
		return target;
	}

	@Override
	public float posByIndex(int vertexIndex, int coordinateIndex) {
		return Float.intBitsToFloat(data[baseIndex + vertexIndex * EncodingFormat.VERTEX_STRIDE + EncodingFormat.VERTEX_X + coordinateIndex]);
	}

	@Override
	public float x(int vertexIndex) {
		return Float.intBitsToFloat(data[baseIndex + vertexIndex * EncodingFormat.VERTEX_STRIDE + EncodingFormat.VERTEX_X]);
	}

	@Override
	public float y(int vertexIndex) {
		return Float.intBitsToFloat(data[baseIndex + vertexIndex * EncodingFormat.VERTEX_STRIDE + EncodingFormat.VERTEX_Y]);
	}

	@Override
	public float z(int vertexIndex) {
		return Float.intBitsToFloat(data[baseIndex + vertexIndex * EncodingFormat.VERTEX_STRIDE + EncodingFormat.VERTEX_Z]);
	}

	@Override
	public boolean hasNormal(int vertexIndex) {
		return (normalFlags() & (1 << vertexIndex)) != 0;
	}

	protected final int normalIndex(int vertexIndex) {
		return baseIndex + vertexIndex * EncodingFormat.VERTEX_STRIDE + EncodingFormat.VERTEX_NORMAL;
	}

	@Override
	public Vector3f copyNormal(int vertexIndex, Vector3f target) {
		if (hasNormal(vertexIndex)) {
			if (target == null) {
				target = new Vector3f();
			}

			final int normal = data[normalIndex(vertexIndex)];
			target.set(link.infra.ogidni.renderer.helper.NormalHelper.getPackedNormalComponent(normal, 0), link.infra.ogidni.renderer.helper.NormalHelper.getPackedNormalComponent(normal, 1), link.infra.ogidni.renderer.helper.NormalHelper.getPackedNormalComponent(normal, 2));
			return target;
		} else {
			return null;
		}
	}

	@Override
	public float normalX(int vertexIndex) {
		return hasNormal(vertexIndex) ? link.infra.ogidni.renderer.helper.NormalHelper.getPackedNormalComponent(data[normalIndex(vertexIndex)], 0) : Float.NaN;
	}

	@Override
	public float normalY(int vertexIndex) {
		return hasNormal(vertexIndex) ? link.infra.ogidni.renderer.helper.NormalHelper.getPackedNormalComponent(data[normalIndex(vertexIndex)], 1) : Float.NaN;
	}

	@Override
	public float normalZ(int vertexIndex) {
		return hasNormal(vertexIndex) ? NormalHelper.getPackedNormalComponent(data[normalIndex(vertexIndex)], 2) : Float.NaN;
	}

	@Override
	public int lightmap(int vertexIndex) {
		return data[baseIndex + vertexIndex * EncodingFormat.VERTEX_STRIDE + EncodingFormat.VERTEX_LIGHTMAP];
	}

	@Override
	public int spriteColor(int vertexIndex, int spriteIndex) {
		Preconditions.checkArgument(spriteIndex == 0, "Unsupported sprite index: %s", spriteIndex);

		return data[baseIndex + vertexIndex * EncodingFormat.VERTEX_STRIDE + EncodingFormat.VERTEX_COLOR];
	}

	@Override
	public float spriteU(int vertexIndex, int spriteIndex) {
		Preconditions.checkArgument(spriteIndex == 0, "Unsupported sprite index: %s", spriteIndex);

		return Float.intBitsToFloat(data[baseIndex + vertexIndex * EncodingFormat.VERTEX_STRIDE + EncodingFormat.VERTEX_U]);
	}

	@Override
	public float spriteV(int vertexIndex, int spriteIndex) {
		Preconditions.checkArgument(spriteIndex == 0, "Unsupported sprite index: %s", spriteIndex);

		return Float.intBitsToFloat(data[baseIndex + vertexIndex * EncodingFormat.VERTEX_STRIDE + EncodingFormat.VERTEX_V]);
	}

	public int vertexStart() {
		return baseIndex + EncodingFormat.HEADER_STRIDE;
	}

	public boolean hasShade() {
		return shade && !material().disableDiffuse(0);
	}

	public void shade(boolean shade) {
		this.shade = shade;
	}
}
