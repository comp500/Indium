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

import link.infra.indium.renderer.IndiumRenderer;
import link.infra.indium.renderer.RenderMaterialImpl.Value;
import link.infra.indium.renderer.aocalc.AoCalculator;
import link.infra.indium.renderer.mesh.EncodingFormat;
import link.infra.indium.renderer.mesh.MutableQuadViewImpl;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext.QuadTransform;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.Direction;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class TerrainFallbackConsumer extends TerrainQuadRenderer implements Consumer<BakedModel> {
	private static Value MATERIAL_FLAT = (Value) IndiumRenderer.INSTANCE.materialFinder().disableAo(0, true).find();
	private static Value MATERIAL_SHADED = (Value) IndiumRenderer.INSTANCE.materialFinder().find();

	private final int[] editorBuffer = new int[EncodingFormat.TOTAL_STRIDE];

	TerrainFallbackConsumer(BlockRenderInfo blockInfo, Function<RenderLayer, ChunkModelBuilder> builderFunc, AoCalculator aoCalc, QuadTransform transform) {
		super(blockInfo, builderFunc, aoCalc, transform);
	}

	private final MutableQuadViewImpl editorQuad = new MutableQuadViewImpl() {
		{
			data = editorBuffer;
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

		for (int i = 0; i < 6; i++) {
			final Direction face = ModelHelper.faceFromIndex(i);
			final List<BakedQuad> quads = model.getQuads(blockState, face, random.get());
			final int count = quads.size();

			if (count != 0) {
				for (int j = 0; j < count; j++) {
					final BakedQuad q = quads.get(j);
					renderQuad(q, face, defaultMaterial);
				}
			}
		}

		final List<BakedQuad> quads = model.getQuads(blockState, null, random.get());
		final int count = quads.size();

		if (count != 0) {
			for (int j = 0; j < count; j++) {
				final BakedQuad q = quads.get(j);
				renderQuad(q, null, defaultMaterial);
			}
		}

		editorQuad.cachedSprite(null);
	}

	private void renderQuad(BakedQuad quad, Direction cullFace, Value defaultMaterial) {
		final MutableQuadViewImpl editorQuad = this.editorQuad;
		editorQuad.fromVanilla(quad, defaultMaterial, cullFace);
		editorQuad.cachedSprite(quad.getSprite());

		if (!transform.transform(editorQuad)) {
			return;
		}

		cullFace = editorQuad.cullFace();

		if (cullFace != null && !blockInfo.shouldDrawFace(cullFace)) {
			return;
		}

		if (!editorQuad.material().disableAo(0)) {
			// needs to happen before offsets are applied
			aoCalc.compute(editorQuad, true);
			tesselateSmooth(editorQuad, blockInfo.defaultLayer, editorQuad.colorIndex());
		} else {
			// Recomputing whether the quad has a light face is only needed if it doesn't also have a cull face,
			// as in those cases, the cull face will always be used to offset the light sampling position
			if (cullFace == null) {
				// Can't rely on lazy computation in tesselateFlat() because needs to happen before offsets are applied
				editorQuad.geometryFlags();
			}

			tesselateFlat(editorQuad, blockInfo.defaultLayer, editorQuad.colorIndex());
		}
	}
}
