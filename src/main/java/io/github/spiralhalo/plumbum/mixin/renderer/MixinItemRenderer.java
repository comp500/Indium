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

package io.github.spiralhalo.plumbum.mixin.renderer;

import io.github.spiralhalo.plumbum.renderer.accessor.AccessItemRenderer;
import io.github.spiralhalo.plumbum.renderer.render.ItemRenderContext;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.item.ItemModels;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer implements AccessItemRenderer {
	@Final
	@Shadow
	private BuiltinModelItemRenderer builtinModelItemRenderer;

	@Shadow private ItemModels models;

	@Final
	@Shadow
	private ItemColors colors;

	@Unique
	private final ThreadLocal<ItemRenderContext> fabric_contexts = ThreadLocal.withInitial(() -> new ItemRenderContext(colors));

	@Inject(at = @At("HEAD"), method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformation$Mode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V", cancellable = true)
	public void hook_renderItem(ItemStack stack, ModelTransformation.Mode transformMode, boolean invert, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int light, int overlay, BakedModel model, CallbackInfo ci) {
		if (!stack.isEmpty()) {
			fabric_contexts.get().renderModel(models, stack, transformMode, invert, io.vram.frex.api.math.MatrixStack.fromVanilla(matrixStack), vertexConsumerProvider, light, overlay, model);
		}

		ci.cancel();
	}

	@Override
	public BuiltinModelItemRenderer plumbum_builtInRenderer() {
		return builtinModelItemRenderer;
	}
}
