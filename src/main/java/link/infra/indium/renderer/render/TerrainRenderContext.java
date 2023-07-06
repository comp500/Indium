package link.infra.indium.renderer.render;

import org.joml.Vector3fc;

import link.infra.indium.mixin.sodium.AccessBlockRenderer;
import link.infra.indium.other.SpriteFinderCache;
import link.infra.indium.renderer.accessor.AccessBlockRenderCache;
import link.infra.indium.renderer.aocalc.AoCalculator;
import link.infra.indium.renderer.mesh.MutableQuadViewImpl;
import me.jellysquid.mods.sodium.client.gl.compile.ChunkBuildContext;
import me.jellysquid.mods.sodium.client.model.IndexBufferBuilder;
import me.jellysquid.mods.sodium.client.model.light.cache.ArrayLightDataCache;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadOrientation;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadWinding;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderCache;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.occlusion.BlockOcclusionCache;
import me.jellysquid.mods.sodium.client.render.vertex.type.ChunkVertexEncoder;
import me.jellysquid.mods.sodium.client.util.color.ColorARGB;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.LocalRandom;

public class TerrainRenderContext extends AbstractBlockRenderContext {
	private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();

	private ChunkBuildBuffers buffers;
	private boolean didOutput = false;

	private Vector3fc origin;
	private Vec3d modelOffset;

	public TerrainRenderContext(BlockRenderCache renderCache) {
		WorldSlice worldSlice = renderCache.getWorldSlice();
		BlockOcclusionCache blockOcclusionCache = ((AccessBlockRenderer) renderCache.getBlockRenderer()).indium$occlusionCache();
		ArrayLightDataCache lightCache = ((AccessBlockRenderCache) renderCache).indium$getLightDataCache();

		blockInfo = new TerrainBlockRenderInfo(blockOcclusionCache);
		blockInfo.random = new LocalRandom(42L);
		blockInfo.prepareForWorld(worldSlice, true);
		aoCalc = new AoCalculator(blockInfo, lightCache);
	}

	public static TerrainRenderContext get(ChunkBuildContext buildContext) {
		return ((AccessBlockRenderCache) buildContext.cache).indium$getTerrainRenderContext();
	}

	@Override
	protected void bufferQuad(MutableQuadViewImpl quad, RenderLayer renderLayer) {
		didOutput = true;
		ChunkModelBuilder builder = buffers.get(renderLayer);

		Direction cullFace = quad.cullFace();
		IndexBufferBuilder indexBuffer = builder.getIndexBuffer(cullFace != null ? ModelQuadFacing.fromDirection(cullFace) : ModelQuadFacing.UNASSIGNED);

		Vector3fc origin = this.origin;
		Vec3d modelOffset = this.modelOffset;

		ModelQuadOrientation orientation = quad.orientation();
		var vertices = this.vertices;

		for (int dstIndex = 0; dstIndex < 4; dstIndex++) {
			int srcIndex = orientation.getVertexIndex(dstIndex);

			var out = vertices[dstIndex];
			out.x = origin.x() + quad.x(srcIndex) + (float) modelOffset.getX();
			out.y = origin.y() + quad.y(srcIndex) + (float) modelOffset.getY();
			out.z = origin.z() + quad.z(srcIndex) + (float) modelOffset.getZ();

			int color = quad.color(srcIndex);
			out.color = ColorARGB.toABGR(color, (color >>> 24) & 0xFF);

			out.u = quad.u(srcIndex);
			out.v = quad.v(srcIndex);

			out.light = quad.lightmap(srcIndex);
		}

		indexBuffer.add(builder.getVertexBuffer().push(vertices), ModelQuadWinding.CLOCKWISE);

		Sprite sprite = quad.cachedSprite();

		if (sprite == null) {
			sprite = SpriteFinderCache.forBlockAtlas().find(quad);
		}

		builder.addSprite(sprite);
	}

	@Override
	protected void shadeQuad(MutableQuadViewImpl quad, boolean isVanilla, boolean ao, boolean emissive) {
		super.shadeQuad(quad, isVanilla, ao, emissive);

		if (ao) {
			// Assumes aoCalc.ao holds the correct values for the current quad.
			quad.orientation(ModelQuadOrientation.orientByBrightness(aoCalc.ao));
		} else {
			// When using flat lighting, Sodium makes all quads use the flipped orientation.
			quad.orientation(ModelQuadOrientation.FLIP);
		}
	}

	public void prepare(ChunkBuildContext buildContext) {
		buffers = buildContext.buffers;
	}

	public void release() {
		blockInfo.release();
		buffers = null;
	}

	/** Called from chunk renderer hook. */
	public boolean tessellateBlock(BlockRenderContext ctx) {
		try {
			this.origin = ctx.origin();
			this.modelOffset = ctx.state().getModelOffset(ctx.world(), ctx.pos());

			didOutput = false;
			aoCalc.clear();
			blockInfo.prepareForBlock(ctx.state(), ctx.pos(), ctx.seed(), ctx.model().useAmbientOcclusion());
			ctx.model().emitBlockQuads(blockInfo.blockView, blockInfo.blockState, blockInfo.blockPos, blockInfo.randomSupplier, this);
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.create(throwable, "Tessellating block in world - Indium Renderer");
			CrashReportSection crashReportSection = crashReport.addElement("Block being tessellated");
			CrashReportSection.addBlockInfo(crashReportSection, ctx.world(), ctx.pos(), ctx.state());
			throw new CrashException(crashReport);
		}

		return didOutput;
	}
}
