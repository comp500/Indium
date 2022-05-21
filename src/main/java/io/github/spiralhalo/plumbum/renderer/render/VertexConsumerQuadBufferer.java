package io.github.spiralhalo.plumbum.renderer.render;

import io.github.spiralhalo.plumbum.other.SpriteFinderCache;
import io.github.spiralhalo.plumbum.renderer.mesh.QuadEmitterImpl;
import io.vram.frex.api.math.FastMatrix3f;
import io.vram.frex.api.math.PackedVector3f;
import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Matrix4f;

import java.util.function.Function;

public abstract class VertexConsumerQuadBufferer implements BaseQuadRenderer.QuadBufferer {
	protected final Function<RenderLayer, VertexConsumer> bufferFunc;

	protected abstract Matrix4f matrix();

	protected abstract FastMatrix3f normalMatrix();

	protected abstract int overlay();

	VertexConsumerQuadBufferer(Function<RenderLayer, VertexConsumer> bufferFunc) {
		this.bufferFunc = bufferFunc;
	}

	@Override
	public void bufferQuad(QuadEmitterImpl quad, RenderLayer renderLayer) {
		bufferQuad(bufferFunc.apply(renderLayer), quad, matrix(), overlay(), normalMatrix());
	}

	public static void bufferQuad(VertexConsumer buff, QuadEmitterImpl quad, Matrix4f matrix, int overlay, FastMatrix3f normalMatrix) {
		final boolean useNormals = quad.hasVertexNormals();
		final int packedFaceNormal;

		if (useNormals) {
			quad.populateMissingNormals();
			packedFaceNormal = 0;
		} else {
			packedFaceNormal = normalMatrix.f_transformPacked3f(quad.packedFaceNormal());
		}

		for (int i = 0; i < 4; i++) {
			buff.vertex(matrix, quad.x(i), quad.y(i), quad.z(i));
			final int color = quad.vertexColor(i);
			buff.color(color & 0xFF, (color >> 8) & 0xFF, (color >> 16) & 0xFF, (color >> 24) & 0xFF);
			buff.texture(quad.u(i), quad.v(i));
			buff.overlay(overlay);
			buff.light(quad.lightmap(i));

			final int packedNormal = useNormals ? normalMatrix.f_transformPacked3f(quad.packedNormal(i)) : packedFaceNormal;

			buff.normal(PackedVector3f.unpackX(packedNormal), PackedVector3f.unpackY(packedNormal), PackedVector3f.unpackZ(packedNormal));
			buff.next();
		}

		// most vanilla sprites will return non-null
		Sprite sprite = quad.material().texture().spriteIndex().fromIndex(quad.spriteId());

		if (sprite == null) {
			sprite = quad.material().texture().spriteFinder().find(quad);
		}

		SpriteUtil.markSpriteActive(sprite);
	}
}
