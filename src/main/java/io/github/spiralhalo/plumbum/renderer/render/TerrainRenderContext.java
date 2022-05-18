package io.github.spiralhalo.plumbum.renderer.render;

import io.vram.frex.api.buffer.QuadEmitter;
import io.vram.frex.api.material.MaterialConstants;
import io.vram.frex.api.math.MatrixStack;
import io.vram.frex.api.mesh.Mesh;
import io.vram.frex.api.model.BlockModel;
import io.vram.frex.api.model.fluid.FluidModel;
import io.github.spiralhalo.plumbum.renderer.aocalc.AoCalculator;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.occlusion.BlockOcclusionCache;
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
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

public class TerrainRenderContext implements BlockModel.BlockInputContext {
	private final TerrainBlockRenderInfo blockInfo = new TerrainBlockRenderInfo();
	private final ChunkRenderInfo chunkInfo = new ChunkRenderInfo();
	private final AoCalculator aoCalc = new AoCalculator(blockInfo, chunkInfo::cachedBrightness, chunkInfo::cachedAoLevel);

	private Vec3i origin;
	private Vec3d modelOffset;

	private final BaseMeshConsumer meshConsumer = new BaseMeshConsumer(new QuadBufferer(chunkInfo::getChunkModelBuilder), blockInfo, aoCalc);

	private final BaseFallbackConsumer fallbackConsumer = new BaseFallbackConsumer(new QuadBufferer(chunkInfo::getChunkModelBuilder), blockInfo, aoCalc);

	public void prepare(BlockRenderView blockView, ChunkBuildBuffers buffers, BlockOcclusionCache cache) {
		blockInfo.setBlockOcclusionCache(cache);
		blockInfo.setBlockView(blockView);
		chunkInfo.prepare(blockView, buffers);
	}

	public void release() {
		blockInfo.release();
		chunkInfo.release();
	}

	private BakedModel model;

	/** Called from chunk renderer hook. */
	public boolean tessellateBlock(BlockState blockState, BlockPos blockPos, BlockPos origin, final BakedModel model, Vec3d modelOffset) {
		this.origin = origin;
		this.modelOffset = modelOffset;

		try {
			chunkInfo.didOutput = false;
			aoCalc.clear();
			blockInfo.prepareForBlock(blockState, blockPos, model.useAmbientOcclusion());
			this.model = model;
//			((FabricBakedModel) model).emitBlockQuads(blockInfo.blockView, blockInfo.blockState, blockInfo.blockPos, blockInfo.randomSupplier, this);
			((BlockModel) model).renderDynamic(this, getEmitter());
			this.model = null;
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.create(throwable, "Tessellating block in world - Plumbum Renderer");
			CrashReportSection crashReportSection = crashReport.addElement("Block being tessellated");
			CrashReportSection.addBlockInfo(crashReportSection, chunkInfo.blockView, blockPos, blockState);
			throw new CrashException(crashReport);
		}

		return chunkInfo.didOutput;
	}

	@Override
	public BlockRenderView blockView() {
		return blockInfo.blockView;
	}

	@Override
	public boolean isFluidModel() {
		return model instanceof FluidModel; // TODO ??
	}

	@Override
	public @Nullable BakedModel bakedModel() {
		return model;
	}

	@Override
	public BlockState blockState() {
		return blockInfo.blockState;
	}

	@Override
	public BlockPos pos() {
		return blockInfo.blockPos;
	}

	@Override
	public Random random() {
		return blockInfo.randomSupplier.get();
	}

	@Override
	public int overlay() {
		return 0; // TODO ??
	}

	@Override
	public MatrixStack matrixStack() {
		return null; // TODO ??
	}

	@Override
	public boolean cullTest(int faceId) {
		return true; // TODO ??
	}

	@Override
	public int indexedColor(int colorIndex) {
		return 0;
	}

	@Override
	public RenderLayer defaultRenderType() {
		return blockInfo.defaultLayer;
	}

	@Override
	public int defaultPreset() {
		return blockInfo.defaultLayer.equals(RenderLayer.getTranslucent()) ? MaterialConstants.PRESET_TRANSLUCENT : MaterialConstants.PRESET_DEFAULT;
	}

	@Override
	public @Nullable Object blockEntityRenderData(BlockPos blockPos) {
		return null; // TODO ??
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

//	@Override
	public Consumer<Mesh> meshConsumer() {
		return meshConsumer;
	}

//	@Override
	public Consumer<BakedModel> fallbackConsumer() {
		return fallbackConsumer;
	}

//	@Override
	public QuadEmitter getEmitter() {
		return meshConsumer.getEmitter();
	}
}
