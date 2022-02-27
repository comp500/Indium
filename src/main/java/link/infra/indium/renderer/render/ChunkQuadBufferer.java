package link.infra.indium.renderer.render;

import java.util.function.Function;

import link.infra.indium.other.SpriteFinderCache;
import link.infra.indium.renderer.mesh.MutableQuadViewImpl;
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
	public void bufferQuad(MutableQuadViewImpl quad, RenderLayer renderLayer) {
		bufferQuad(builderFunc.apply(renderLayer), quad, origin(), blockOffset());
	}

	public static void bufferQuad(ChunkModelBuilder builder, MutableQuadViewImpl quad, Vec3i origin, Vec3d blockOffset) {
		ModelVertexSink vertices = builder.getVertexSink();
		vertices.ensureCapacity(4);

		Direction cullFace = quad.cullFace();
		IndexBufferBuilder indices = builder.getIndexBufferBuilder(cullFace != null ? ModelQuadFacing.fromDirection(cullFace) : ModelQuadFacing.UNASSIGNED);

		int vertexStart = vertices.getVertexCount();

		for (int i = 0; i < 4; i++) {
			float x = quad.x(i) + (float) blockOffset.getX();
			float y = quad.y(i) + (float) blockOffset.getY();
			float z = quad.z(i) + (float) blockOffset.getZ();

			int color = quad.spriteColor(i, 0);
			color = ColorABGR.pack(color & 0xFF, (color >> 8) & 0xFF, (color >> 16) & 0xFF, (color >> 24) & 0xFF);

			float u = quad.spriteU(i, 0);
			float v = quad.spriteV(i, 0);

			int lm = quad.lightmap(i);

			vertices.writeVertex(origin, x, y, z, color, u, v, lm, builder.getChunkId());
		}

		indices.add(vertexStart, ModelQuadWinding.CLOCKWISE);

		Sprite sprite = quad.cachedSprite();

		if (sprite == null) {
			sprite = SpriteFinderCache.forBlockAtlas().find(quad, 0);
		}

		builder.addSprite(sprite);

		vertices.flush();
	}
}
