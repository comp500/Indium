package link.infra.indium.renderer.render;

import link.infra.indium.mixin.sodium.AccessBlockRenderer;
import link.infra.indium.renderer.accessor.AccessBlockRenderCache;
import link.infra.indium.renderer.aocalc.AoCalculator;
import me.jellysquid.mods.sodium.client.gl.compile.ChunkBuildContext;
import me.jellysquid.mods.sodium.client.model.light.cache.ArrayLightDataCache;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderCache;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.render.occlusion.BlockOcclusionCache;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3fc;

import java.util.function.Consumer;
import java.util.function.Function;

public class TerrainRenderContext extends AbstractRenderContext {
	private final TerrainBlockRenderInfo blockInfo;
	private final ChunkRenderInfo chunkInfo;
	private final AoCalculator aoCalc;

	private Vector3fc origin;
	private Vec3d modelOffset;

	private final BaseMeshConsumer meshConsumer;
	private final BaseFallbackConsumer fallbackConsumer;
	private ChunkRenderBounds.Builder bounds;

	public TerrainRenderContext(BlockRenderCache renderCache) {
		WorldSlice worldSlice = renderCache.getWorldSlice();
		BlockOcclusionCache blockOcclusionCache = ((AccessBlockRenderer) renderCache.getBlockRenderer()).indium$occlusionCache();
		ArrayLightDataCache lightCache = ((AccessBlockRenderCache) renderCache).indium$getLightDataCache();

		blockInfo = new TerrainBlockRenderInfo(blockOcclusionCache);
		blockInfo.prepareForWorld(worldSlice, true);
		chunkInfo = new ChunkRenderInfo(worldSlice);
		aoCalc = new AoCalculator(blockInfo, lightCache);

		meshConsumer = new BaseMeshConsumer(new QuadBufferer(chunkInfo::getChunkModelBuilder), blockInfo, aoCalc, this::transform);
		fallbackConsumer = new BaseFallbackConsumer(new QuadBufferer(chunkInfo::getChunkModelBuilder), blockInfo, aoCalc, this::transform);
	}

	public static TerrainRenderContext get(ChunkBuildContext buildContext) {
		return ((AccessBlockRenderCache) buildContext.cache).indium$getTerrainRenderContext();
	}

	public void prepare(ChunkBuildContext buildContext) {
		chunkInfo.prepare(buildContext.buffers);
	}

	public void release() {
		blockInfo.release();
		chunkInfo.release();
	}

	/** Called from chunk renderer hook. */
	public boolean tessellateBlock(BlockRenderContext ctx, ChunkRenderBounds.Builder bounds) {
		this.origin = ctx.origin();
		this.modelOffset = ctx.state().getModelOffset(ctx.world(), ctx.pos());
		this.bounds = bounds;

		try {
			chunkInfo.didOutput = false;
			aoCalc.clear();
			blockInfo.prepareForBlock(ctx.state(), ctx.pos(), ctx.model().useAmbientOcclusion(), ctx.seed());
			((FabricBakedModel) ctx.model()).emitBlockQuads(blockInfo.blockView, blockInfo.blockState, blockInfo.blockPos, blockInfo.randomSupplier, this);
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.create(throwable, "Tessellating block in world - Indium Renderer");
			CrashReportSection crashReportSection = crashReport.addElement("Block being tessellated");
			CrashReportSection.addBlockInfo(crashReportSection, chunkInfo.blockView, ctx.pos(), ctx.state());
			throw new CrashException(crashReport);
		}

		return chunkInfo.didOutput;
	}

	private class QuadBufferer extends ChunkQuadBufferer {
		QuadBufferer(Function<RenderLayer, ChunkModelBuilder> builderFunc) {
			super(builderFunc);
		}

		@Override
		protected Vector3fc origin() {
			return origin;
		}

		@Override
		protected Vec3d blockOffset() {
			return modelOffset;
		}

		@Override
		protected ChunkRenderBounds.Builder bounds() {
			return bounds;
		}
	}

	@Override
	public Consumer<Mesh> meshConsumer() {
		return meshConsumer;
	}

	@Override
	public BakedModelConsumer bakedModelConsumer() {
		return fallbackConsumer;
	}

	@Override
	public QuadEmitter getEmitter() {
		return meshConsumer.getEmitter();
	}
}
