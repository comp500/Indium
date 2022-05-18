package io.github.spiralhalo.plumbum.renderer.render;

import java.util.function.Function;

import io.vram.frex.api.math.FastMatrix3f;
import io.vram.frex.api.math.PackedVector3f;
import io.github.spiralhalo.plumbum.other.SpriteFinderCache;
import io.github.spiralhalo.plumbum.renderer.mesh.QuadEmitterImpl;
import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Matrix4f;

public abstract class VertexConsumerQuadBufferer implements BaseQuadRenderer.QuadBufferer {
	protected final Function<RenderLayer, VertexConsumer> bufferFunc;
	protected int packedNormalFrex;

	protected abstract Matrix4f matrix();

	protected abstract FastMatrix3f normalMatrix();

	protected abstract int overlay();

	VertexConsumerQuadBufferer(Function<RenderLayer, VertexConsumer> bufferFunc) {
		this.bufferFunc = bufferFunc;
	}

	@Override
	public void bufferQuad(QuadEmitterImpl quad, RenderLayer renderLayer) {
		bufferQuad(bufferFunc.apply(renderLayer), quad, matrix(), overlay(), normalMatrix(), packedNormalFrex);
	}

	public static void bufferQuad(VertexConsumer buff, QuadEmitterImpl quad, Matrix4f matrix, int overlay, FastMatrix3f normalMatrix, int packedNormal) {
		final boolean useNormals = quad.hasVertexNormals();

		if (useNormals) {
			quad.populateMissingNormals();
		} else {
			packedNormal = quad.packedFaceNormal();
			packedNormal = normalMatrix.f_transformPacked3f(packedNormal);
		}

		for (int i = 0; i < 4; i++) {
			buff.vertex(matrix, quad.x(i), quad.y(i), quad.z(i));
			final int color = quad.vertexColor(i);
			buff.color(color & 0xFF, (color >> 8) & 0xFF, (color >> 16) & 0xFF, (color >> 24) & 0xFF);
			buff.texture(quad.u(i), quad.v(i));
			buff.overlay(overlay);
			buff.light(quad.lightmap(i));

			if (useNormals) {
				packedNormal = quad.packedNormal(i);
				packedNormal = normalMatrix.f_transformPacked3f(packedNormal);
			}

			buff.normal(PackedVector3f.unpackX(packedNormal), PackedVector3f.unpackY(packedNormal), PackedVector3f.unpackZ(packedNormal));
			buff.next();
		}

		Sprite sprite = quad.cachedSprite();

		if (sprite == null) {
			sprite = SpriteFinderCache.forBlockAtlas().find(quad);
		}

		SpriteUtil.markSpriteActive(sprite);
	}
}
