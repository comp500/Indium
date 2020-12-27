package link.infra.ogidni.renderer.render;

import link.infra.ogidni.renderer.aocalc.AoCalculator;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;

import java.util.function.Consumer;

public class CursedRenderContext extends AbstractRenderContext implements RenderContext {
	public static final ThreadLocal<CursedRenderContext> POOL = ThreadLocal.withInitial(CursedRenderContext::new);
	private final TerrainBlockRenderInfo blockInfo = new TerrainBlockRenderInfo();
	private final CursedRenderInfo chunkInfo = new CursedRenderInfo();
	private final AoCalculator aoCalc = new AoCalculator(blockInfo, chunkInfo::cachedBrightness, chunkInfo::cachedAoLevel);

	private final link.infra.ogidni.renderer.render.AbstractMeshConsumer meshConsumer = new AbstractMeshConsumer(blockInfo, chunkInfo::getInitializedBuffer, aoCalc, this::transform) {
		@Override
		protected int overlay() {
			return overlay;
		}

		@Override
		protected Matrix4f matrix() {
			return matrix;
		}

		@Override
		protected Matrix3f normalMatrix() {
			return normalMatrix;
		}
	};

	private final TerrainFallbackConsumer fallbackConsumer = new TerrainFallbackConsumer(blockInfo, chunkInfo::getInitializedBuffer, aoCalc, this::transform) {
		@Override
		protected int overlay() {
			return overlay;
		}

		@Override
		protected Matrix4f matrix() {
			return matrix;
		}

		@Override
		protected Matrix3f normalMatrix() {
			return normalMatrix;
		}
	};

	public CursedRenderContext prepare(ClientWorld blockView, VertexBufferStuff.VertexBufferManager.RegionBuilder regionBuilder) {
		blockInfo.setBlockView(blockView);

		chunkInfo.prepare(blockView, regionBuilder);
		return this;
	}

	public void release() {
		chunkInfo.release();
		blockInfo.release();
	}

	public boolean tesselateBlock(BlockState blockState, BlockPos blockPos, final BakedModel model, MatrixStack matrixStack) {
		this.matrix = matrixStack.peek().getModel();
		this.normalMatrix = matrixStack.peek().getNormal();

		// TODO: translate by thingy offset

		try {
			aoCalc.clear();
			blockInfo.prepareForBlock(blockState, blockPos, model.useAmbientOcclusion());
			((FabricBakedModel) model).emitBlockQuads(blockInfo.blockView, blockInfo.blockState, blockInfo.blockPos, blockInfo.randomSupplier, this);
		} catch (Throwable var9) {
			CrashReport crashReport_1 = CrashReport.create(var9, "Tesselating block in world - Indigo Renderer");
			CrashReportSection crashReportElement_1 = crashReport_1.addElement("Block being tesselated");
			CrashReportSection.addBlockInfo(crashReportElement_1, blockPos, blockState);
			throw new CrashException(crashReport_1);
		}

		// false because we've already marked the chunk as populated - caller doesn't need to
		return false;
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
