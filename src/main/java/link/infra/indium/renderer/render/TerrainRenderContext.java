package link.infra.indium.renderer.render;

import link.infra.indium.renderer.aocalc.AoCalculator;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.occlusion.BlockOcclusionCache;
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
import net.minecraft.world.BlockRenderView;

import java.util.function.Consumer;
import java.util.function.Function;

public class TerrainRenderContext extends AbstractRenderContext {
	private final TerrainBlockRenderInfo blockInfo;
	private final ChunkRenderInfo chunkInfo = new ChunkRenderInfo();
	private final AoCalculator aoCalc;

	private Vec3i origin;
	private Vec3d modelOffset;

	private final BaseMeshConsumer meshConsumer;

	private final BaseFallbackConsumer fallbackConsumer;

	public TerrainRenderContext(BlockOcclusionCache blockOcclusionCache) {
		this.blockInfo = new TerrainBlockRenderInfo(blockOcclusionCache);
		this.aoCalc = new AoCalculator(blockInfo, chunkInfo::cachedBrightness, chunkInfo::cachedAoLevel);
		this.meshConsumer = new BaseMeshConsumer(new QuadBufferer(chunkInfo::getChunkModelBuilder), blockInfo, aoCalc, this::transform);
		this.fallbackConsumer = new BaseFallbackConsumer(new QuadBufferer(chunkInfo::getChunkModelBuilder), blockInfo, aoCalc, this::transform);
	}

	public TerrainRenderContext prepare(BlockRenderView blockView, ChunkBuildBuffers buffers) {
		blockInfo.setBlockView(blockView);
		chunkInfo.prepare(blockView, buffers);
		return this;
	}

	public void release() {
		blockInfo.release();
		chunkInfo.release();
	}

	/** Called from chunk renderer hook. */
	public boolean tesselateBlock(BlockState blockState, BlockPos blockPos, BlockPos origin, final BakedModel model, Vec3d modelOffset) {
		this.origin = origin;
		this.modelOffset = modelOffset;

		try {
			chunkInfo.didOutput = false;
			aoCalc.clear();
			blockInfo.prepareForBlock(blockState, blockPos, model.useAmbientOcclusion());
			((FabricBakedModel) model).emitBlockQuads(blockInfo.blockView, blockInfo.blockState, blockInfo.blockPos, blockInfo.randomSupplier, this);
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.create(throwable, "Tesselating block in world - Indium Renderer");
			CrashReportSection crashReportSection = crashReport.addElement("Block being tesselated");
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
		protected Vec3i origin() {
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
	public Consumer<BakedModel> fallbackConsumer() {
		return fallbackConsumer;
	}

	@Override
	public QuadEmitter getEmitter() {
		return meshConsumer.getEmitter();
	}
}
