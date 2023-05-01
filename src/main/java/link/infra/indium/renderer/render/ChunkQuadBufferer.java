package link.infra.indium.renderer.render;

import java.util.function.Function;

import link.infra.indium.other.SpriteFinderCache;
import link.infra.indium.renderer.mesh.MutableQuadViewImpl;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadOrientation;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadWinding;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public abstract class ChunkQuadBufferer implements BaseQuadRenderer.QuadBufferer {
	protected final Function<RenderLayer, ChunkModelBuilder> builderFunc;
	private ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();

	protected abstract Vector3fc origin();

	protected abstract Vec3d blockOffset();

	protected abstract ChunkRenderBounds.Builder bounds();

	ChunkQuadBufferer(Function<RenderLayer, ChunkModelBuilder> builderFunc) {
		this.builderFunc = builderFunc;
	}

	@Override
	public void bufferQuad(MutableQuadViewImpl quad, RenderLayer renderLayer) {
		bufferQuad(builderFunc.apply(renderLayer), quad, origin(), blockOffset(), bounds(), DefaultMaterials.forRenderLayer(renderLayer));
	}

	public void bufferQuad(ChunkModelBuilder builder, MutableQuadViewImpl quad, Vector3fc origin, Vec3d blockOffset, ChunkRenderBounds.Builder bounds, Material material) {
		var vertices = this.vertices;

		Direction cullFace = quad.cullFace();
		ModelQuadFacing lightFace = ModelQuadFacing.fromDirection(quad.lightFace());

		for (int i = 0; i < 4; i++) {
			var out = vertices[i];
			out.x = origin.x() + quad.x(i) + (float) blockOffset.getX();
			out.y = origin.y() + quad.y(i) + (float) blockOffset.getY();
			out.z = origin.z() + quad.z(i) + (float) blockOffset.getZ();

			int color = quad.spriteColor(i, 0);
			out.color = ColorABGR.pack(color & 0xFF, (color >> 8) & 0xFF, (color >> 16) & 0xFF, (color >> 24) & 0xFF);

			out.u = quad.spriteU(i, 0);
			out.v = quad.spriteV(i, 0);

			out.light = quad.lightmap(i);
			bounds.add(out.x, out.y, out.z, lightFace);
		}

		builder.getVertexBuffer(cullFace != null ? ModelQuadFacing.fromDirection(cullFace) : ModelQuadFacing.UNASSIGNED).push(vertices, material);

		Sprite sprite = quad.cachedSprite();

		if (sprite == null) {
			sprite = SpriteFinderCache.forBlockAtlas().find(quad, 0);
		}

		builder.addSprite(sprite);
	}
}
