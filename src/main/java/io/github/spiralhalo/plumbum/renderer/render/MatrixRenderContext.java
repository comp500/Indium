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

import io.vram.frex.api.math.FastMatrix3f;
import io.vram.frex.api.math.MatrixStack;
import io.vram.frex.api.model.InputContext;
import net.minecraft.util.math.Matrix4f;

abstract class MatrixRenderContext implements InputContext {
	protected Matrix4f matrix;
	protected FastMatrix3f normalMatrix;
	protected net.minecraft.client.util.math.MatrixStack matrixStack;
	protected int overlay;

	@Override
	public int overlay() {
		return overlay;
	}

	@Override
	public MatrixStack matrixStack() {
		return (MatrixStack) matrixStack;
	}
}
