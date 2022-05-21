package io.github.spiralhalo.plumbum.renderer.render;

import java.util.function.Function;

import io.github.spiralhalo.plumbum.other.SpriteFinderCache;
import io.github.spiralhalo.plumbum.renderer.mesh.QuadEmitterImpl;
import me.jellysquid.mods.sodium.client.model.IndexBufferBuilder;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadWinding;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

public abstract class ChunkQuadBufferer implements BaseQuadRenderer.QuadBufferer {
	protected final Function<RenderLayer, ChunkModelBuilder> builderFunc;

	protected abstract Vec3i origin();

	protected abstract Vec3d blockOffset();

	ChunkQuadBufferer(Function<RenderLayer, ChunkModelBuilder> builderFunc) {
		this.builderFunc = builderFunc;
	}

	@Override
	public void bufferQuad(QuadEmitterImpl quad, RenderLayer renderLayer) {
		bufferQuad(builderFunc.apply(renderLayer), quad, origin(), blockOffset());
	}

	public static void bufferQuad(ChunkModelBuilder builder, QuadEmitterImpl quad, Vec3i origin, Vec3d blockOffset) {
		ModelVertexSink vertices = builder.getVertexSink();
		vertices.ensureCapacity(4);

		Direction cullFace = quad.cullFace();
		IndexBufferBuilder indices = builder.getIndexBufferBuilder(cullFace != null ? ModelQuadFacing.fromDirection(cullFace) : ModelQuadFacing.UNASSIGNED);

		int vertexStart = vertices.getVertexCount();

		for (int i = 0; i < 4; i++) {
			float x = quad.x(i) + (float) blockOffset.getX();
			float y = quad.y(i) + (float) blockOffset.getY();
			float z = quad.z(i) + (float) blockOffset.getZ();

			int color = quad.vertexColor(i);
			color = ColorABGR.pack(color & 0xFF, (color >> 8) & 0xFF, (color >> 16) & 0xFF, (color >> 24) & 0xFF);

			float u = quad.u(i);
			float v = quad.v(i);

			int lm = quad.lightmap(i);

			vertices.writeVertex(origin, x, y, z, color, u, v, lm, builder.getChunkId());
		}

		indices.add(vertexStart, ModelQuadWinding.CLOCKWISE);

		Sprite sprite = quad.material().texture().spriteIndex().fromIndex(quad.spriteId());

		if (sprite == null) {
			sprite = SpriteFinderCache.forBlockAtlas().find(quad);
		}

		builder.addSprite(sprite);

		vertices.flush();
	}
}
