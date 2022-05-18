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

package io.github.spiralhalo.plumbum.renderer.render;

import io.vram.frex.api.buffer.QuadEmitter;
import io.vram.frex.api.model.util.FaceUtil;
import io.github.spiralhalo.plumbum.renderer.PlumbumRenderer;
import io.github.spiralhalo.plumbum.renderer.RenderMaterialImpl.Value;
import io.github.spiralhalo.plumbum.renderer.aocalc.AoCalculator;
import io.github.spiralhalo.plumbum.renderer.mesh.EncodingFormat;
import io.github.spiralhalo.plumbum.renderer.mesh.MutableQuadViewImpl;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.Direction;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Consumer for vanilla baked models. Generally intended to give visual results matching a vanilla render,
 * however there could be subtle (and desirable) lighting variations so is good to be able to render
 * everything consistently.
 *
 * <p>Also, the API allows multi-part models that hold multiple vanilla models to render them without
 * combining quad lists, but the vanilla logic only handles one model per block. To route all of
 * them through vanilla logic would require additional hooks.
 *
 *  <p>Works by copying the quad data to an "editor" quad held in the instance,
 *  where all transformations are applied before buffering. Transformations should be
 *  the same as they would be in a vanilla render - the editor is serving mainly
 *  as a way to access vertex data without magical numbers. It also allows a consistent interface
 *  for downstream tesselation routines.
 *
 *  <p>Another difference from vanilla render is that all transformation happens before the
 *  vertex data is sent to the byte buffer.  Generally POJO array access will be faster than
 *  manipulating the data via NIO.
 */
public class BaseFallbackConsumer extends BaseQuadRenderer implements Consumer<BakedModel> {
	private static final Value MATERIAL_FLAT = (Value) PlumbumRenderer.INSTANCE.materials().materialFinder().disableAo(true).find();
	private static final Value MATERIAL_SHADED = (Value) PlumbumRenderer.INSTANCE.materials().materialFinder().find();

	BaseFallbackConsumer(QuadBufferer bufferer, BlockRenderInfo blockInfo, AoCalculator aoCalc) {
		super(bufferer, blockInfo, aoCalc);
	}

	private final MutableQuadViewImpl editorQuad = new MutableQuadViewImpl() {
		{
			data = new int[EncodingFormat.TOTAL_STRIDE];
			material(MATERIAL_SHADED);
		}

		@Override
		public QuadEmitter emit() {
			// should not be called
			throw new UnsupportedOperationException("Fallback consumer does not support .emit()");
		}
	};

	@Override
	public void accept(BakedModel model) {
		final Supplier<Random> random = blockInfo.randomSupplier;
		final Value defaultMaterial = blockInfo.defaultAo && model.useAmbientOcclusion() ? MATERIAL_SHADED : MATERIAL_FLAT;
		final BlockState blockState = blockInfo.blockState;

		for (int i = 0; i < FaceUtil.FACE_INDEX_COUNT; i++) {
			final Direction cullFace = FaceUtil.faceFromIndex(i);
			final List<BakedQuad> quads = model.getQuads(blockState, cullFace, random.get());
			final int count = quads.size();

			if (count != 0) {
				for (int j = 0; j < count; j++) {
					final BakedQuad q = quads.get(j);
					renderQuad(q, cullFace, defaultMaterial);
				}
			}
		}
	}

	private void renderQuad(BakedQuad quad, Direction cullFace, Value defaultMaterial) {
		final MutableQuadViewImpl editorQuad = this.editorQuad;
		editorQuad.fromVanilla(quad, defaultMaterial, cullFace);

		cullFace = editorQuad.cullFace();

		if (cullFace != null && !blockInfo.shouldDrawFace(cullFace)) {
			return;
		}

		if (!editorQuad.material().disableAo()) {
			// needs to happen before offsets are applied
			aoCalc.compute(editorQuad, true);
			tessellateSmooth(editorQuad, blockInfo.defaultLayer, editorQuad.colorIndex());
		} else {
			// Recomputing whether the quad has a light face is only needed if it doesn't also have a cull face,
			// as in those cases, the cull face will always be used to offset the light sampling position
			if (cullFace == null) {
				// Can't rely on lazy computation in tessellateFlat() because needs to happen before offsets are applied
				editorQuad.geometryFlags();
			}

			tessellateFlat(editorQuad, blockInfo.defaultLayer, editorQuad.colorIndex());
		}
	}
}
