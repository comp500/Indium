package link.infra.indium.renderer.render;

import java.util.function.Function;

import link.infra.indium.other.SpriteFinderCache;
import link.infra.indium.renderer.mesh.MutableQuadViewImpl;
import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3f;

public abstract class VertexConsumerQuadBufferer implements BaseQuadRenderer.QuadBufferer {
	protected final Function<RenderLayer, VertexConsumer> bufferFunc;
	protected final Vec3f normalVec = new Vec3f();

	protected abstract Matrix4f matrix();

	protected abstract Matrix3f normalMatrix();

	protected abstract int overlay();

	VertexConsumerQuadBufferer(Function<RenderLayer, VertexConsumer> bufferFunc) {
		this.bufferFunc = bufferFunc;
	}

	@Override
	public void bufferQuad(MutableQuadViewImpl quad, RenderLayer renderLayer) {
		bufferQuad(bufferFunc.apply(renderLayer), quad, matrix(), overlay(), normalMatrix(), normalVec);
	}

	public static void bufferQuad(VertexConsumer buff, MutableQuadViewImpl quad, Matrix4f matrix, int overlay, Matrix3f normalMatrix, Vec3f normalVec) {
		final boolean useNormals = quad.hasVertexNormals();

		if (useNormals) {
			quad.populateMissingNormals();
		} else {
			final Vec3f faceNormal = quad.faceNormal();
			normalVec.set(faceNormal.getX(), faceNormal.getY(), faceNormal.getZ());
			normalVec.transform(normalMatrix);
		}

		for (int i = 0; i < 4; i++) {
			buff.vertex(matrix, quad.x(i), quad.y(i), quad.z(i));
			final int color = quad.spriteColor(i, 0);
			buff.color(color & 0xFF, (color >> 8) & 0xFF, (color >> 16) & 0xFF, (color >> 24) & 0xFF);
			buff.texture(quad.spriteU(i, 0), quad.spriteV(i, 0));
			buff.overlay(overlay);
			buff.light(quad.lightmap(i));

			if (useNormals) {
				normalVec.set(quad.normalX(i), quad.normalY(i), quad.normalZ(i));
				normalVec.transform(normalMatrix);
			}

			buff.normal(normalVec.getX(), normalVec.getY(), normalVec.getZ());
			buff.next();
		}

		Sprite sprite = quad.cachedSprite();

		if (sprite == null) {
			sprite = SpriteFinderCache.forBlockAtlas().find(quad, 0);
		}

		SpriteUtil.markSpriteActive(sprite);
	}
}
