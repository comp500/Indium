package link.infra.indium.renderer.helper;

import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.material.ShadeMode;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/**
 * Routines for adaptation of vanilla {@link BakedModel}s to FRAPI pipelines.
 * Copy of {@link net.fabricmc.fabric.impl.renderer.VanillaModelEncoder} to avoid issues if the Fabric impl class receives changes.
 */
public class VanillaModelEncoder {
	private static final Renderer RENDERER = RendererAccess.INSTANCE.getRenderer();
	private static final RenderMaterial STANDARD_MATERIAL = RENDERER.materialFinder().shadeMode(ShadeMode.VANILLA).find();
	private static final RenderMaterial NO_AO_MATERIAL = RENDERER.materialFinder().shadeMode(ShadeMode.VANILLA).ambientOcclusion(TriState.FALSE).find();

	public static void emitBlockQuads(BakedModel model, @Nullable BlockState state, Supplier<Random> randomSupplier, RenderContext context) {
		QuadEmitter emitter = context.getEmitter();
		final RenderMaterial defaultMaterial = model.useAmbientOcclusion() ? STANDARD_MATERIAL : NO_AO_MATERIAL;

		for (int i = 0; i <= ModelHelper.NULL_FACE_ID; i++) {
			final Direction cullFace = ModelHelper.faceFromIndex(i);

			if (!context.hasTransform() && context.isFaceCulled(cullFace)) {
				// Skip entire quad list if possible.
				continue;
			}

			final List<BakedQuad> quads = model.getQuads(state, cullFace, randomSupplier.get());
			final int count = quads.size();

			for (int j = 0; j < count; j++) {
				final BakedQuad q = quads.get(j);
				emitter.fromVanilla(q, defaultMaterial, cullFace);
				emitter.emit();
			}
		}
	}

	public static void emitItemQuads(BakedModel model, @Nullable BlockState state, Supplier<Random> randomSupplier, RenderContext context) {
		QuadEmitter emitter = context.getEmitter();

		for (int i = 0; i <= ModelHelper.NULL_FACE_ID; i++) {
			final Direction cullFace = ModelHelper.faceFromIndex(i);
			final List<BakedQuad> quads = model.getQuads(state, cullFace, randomSupplier.get());
			final int count = quads.size();

			for (int j = 0; j < count; j++) {
				final BakedQuad q = quads.get(j);
				emitter.fromVanilla(q, STANDARD_MATERIAL, cullFace);
				emitter.emit();
			}
		}
	}
}

