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

package link.infra.indium.renderer;

import com.google.common.base.Preconditions;

import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder;
import net.fabricmc.fabric.api.renderer.v1.material.MaterialView;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.util.math.MathHelper;

/**
 * Default implementation of the standard render materials.
 * The underlying representation is simply an int with bit-wise
 * packing of the various material properties. This offers
 * easy/fast interning via int/object hashmap.
 */
public abstract class RenderMaterialImpl implements MaterialView {
	private static final BlendMode[] BLEND_MODES = BlendMode.values();

	private static final int BLEND_MODE_MASK = MathHelper.smallestEncompassingPowerOfTwo(BlendMode.values().length) - 1;
	private static final int COLOR_DISABLE_FLAG = BLEND_MODE_MASK + 1;
	private static final int EMISSIVE_FLAG = COLOR_DISABLE_FLAG << 1;
	private static final int DIFFUSE_FLAG = EMISSIVE_FLAG << 1;
	private static final int AO_FLAG = DIFFUSE_FLAG << 1;
	private static final int GLINT_FLAG = AO_FLAG << 1;
	public static final int VALUE_COUNT = (GLINT_FLAG << 1);

	private static final Value[] VALUES = new Value[VALUE_COUNT];

	static {
		for (int i = 0; i < VALUE_COUNT; i++) {
			VALUES[i] = new Value(i);
		}
	}

	public static RenderMaterialImpl.Value byIndex(int index) {
		return VALUES[index];
	}

	public static Value setDisableDiffuse(Value material, boolean disable) {
		if (material.disableDiffuse() != disable) {
			return byIndex(disable ? (material.bits | DIFFUSE_FLAG) : (material.bits & ~DIFFUSE_FLAG));
		}

		return material;
	}

	protected int bits;

	@Override
	public BlendMode blendMode() {
		return BLEND_MODES[bits & BLEND_MODE_MASK];
	}

	@Override
	public boolean disableColorIndex() {
		return (bits & COLOR_DISABLE_FLAG) != 0;
	}

	@Override
	public boolean emissive() {
		return (bits & EMISSIVE_FLAG) != 0;
	}

	@Override
	public boolean disableDiffuse() {
		return (bits & DIFFUSE_FLAG) != 0;
	}

	@Override
	public TriState ambientOcclusion() {
		return TriState.of((bits & AO_FLAG) != 0);
	}

	@Override
	public TriState glint() {
		return TriState.of((bits & GLINT_FLAG) != 0);
	}

	public static class Value extends RenderMaterialImpl implements RenderMaterial {
		private Value(int bits) {
			this.bits = bits;
		}

		public int index() {
			return bits;
		}
	}

	public static class Finder extends RenderMaterialImpl implements MaterialFinder {
		@Override
		public RenderMaterial find() {
			return VALUES[bits];
		}

		@Override
		public MaterialFinder clear() {
			bits = 0;
			return this;
		}

		@Override
		public MaterialFinder blendMode(BlendMode blendMode) {
			if (blendMode == null) {
				blendMode = BlendMode.DEFAULT;
			}

			bits = (bits & ~BLEND_MODE_MASK) | blendMode.ordinal();
			return this;
		}

		@Override
		public MaterialFinder disableColorIndex(boolean disable) {
			bits = disable ? (bits | COLOR_DISABLE_FLAG) : (bits & ~COLOR_DISABLE_FLAG);
			return this;
		}

		@Override
		public MaterialFinder spriteDepth(int depth) {
			Preconditions.checkArgument(depth == 1, "Unsupported sprite depth: %s", depth);

			return this;
		}

		@Override
		public MaterialFinder emissive(boolean isEmissive) {
			bits = isEmissive ? (bits | EMISSIVE_FLAG) : (bits & ~EMISSIVE_FLAG);
			return this;
		}

		@Override
		public MaterialFinder disableDiffuse(boolean disable) {
			bits = disable ? (bits | DIFFUSE_FLAG) : (bits & ~DIFFUSE_FLAG);
			return this;
		}

		@Override
		public MaterialFinder ambientOcclusion(TriState triState) {
			bits = triState.get() ? (bits | AO_FLAG) : (bits & ~AO_FLAG);
			return this;
		}

		@Override
		public MaterialFinder glint(TriState triState) {
			bits = triState.get() ? (bits | GLINT_FLAG) : (bits & ~GLINT_FLAG);
			return this;
		}

		@Override
		public MaterialFinder copyFrom(MaterialView material) {
			bits = ((RenderMaterialImpl) material).bits;
			return this;
		}
	}
}
