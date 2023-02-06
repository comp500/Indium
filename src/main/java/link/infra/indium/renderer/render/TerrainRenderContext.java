package link.infra.indium.renderer.render;

import java.util.function.Consumer;
import java.util.function.Function;

import link.infra.indium.mixin.sodium.AccessBlockRenderer;
import link.infra.indium.renderer.accessor.AccessBlockRenderCache;
import link.infra.indium.renderer.aocalc.AoCalculator;
import me.jellysquid.mods.sodium.client.gl.compile.ChunkBuildContext;
import me.jellysquid.mods.sodium.client.model.light.cache.ArrayLightDataCache;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderCache;
import me.jellysquid.mods.sodium.client.render.occlusion.BlockOcclusionCache;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class TerrainRenderContext extends AbstractRenderContext {
	private final TerrainBlockRenderInfo blockInfo;
	private final ChunkRenderInfo chunkInfo;
	private final AoCalculator aoCalc;

	private Vector3fc origin;
	private Vec3d modelOffset;

	private final BaseMeshConsumer meshConsumer;
	private final BaseFallbackConsumer fallbackConsumer;

	public TerrainRenderContext(BlockRenderCache renderCache) {
		WorldSlice worldSlice = renderCache.getWorldSlice();
		BlockOcclusionCache blockOcclusionCache = ((AccessBlockRenderer) renderCache.getBlockRenderer()).indium$occlusionCache();
		ArrayLightDataCache lightCache = ((AccessBlockRenderCache) renderCache).indium$getLightDataCache();

		blockInfo = new TerrainBlockRenderInfo(blockOcclusionCache);
		blockInfo.setBlockView(worldSlice);
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
	public boolean tessellateBlock(BlockState blockState, BlockPos blockPos, Vector3fc origin, final BakedModel model, Vec3d modelOffset) {
		this.origin = origin;
		this.modelOffset = modelOffset;

		try {
			chunkInfo.didOutput = false;
			aoCalc.clear();
			blockInfo.prepareForBlock(blockState, blockPos, model.useAmbientOcclusion());
			((FabricBakedModel) model).emitBlockQuads(blockInfo.blockView, blockInfo.blockState, blockInfo.blockPos, blockInfo.randomSupplier, this);
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.create(throwable, "Tessellating block in world - Indium Renderer");
			CrashReportSection crashReportSection = crashReport.addElement("Block being tessellated");
			CrashReportSection.addBlockInfo(crashReportSection, chunkInfo.blockView, blockPos, blockState);
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
