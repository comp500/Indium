package link.infra.indium.renderer.render;

import link.infra.indium.renderer.aocalc.AoCalculator;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.world.BlockRenderView;

import java.util.function.Consumer;

public class IndiumTerrainRenderContext extends AbstractRenderContext implements RenderContext {
    private final TerrainBlockRenderInfo blockInfo = new TerrainBlockRenderInfo();
    private final IndiumChunkRenderInfo chunkInfo = new IndiumChunkRenderInfo();
    private final AoCalculator aoCalc = new AoCalculator(blockInfo, chunkInfo::cachedBrightness, chunkInfo::cachedAoLevel);

    private final AbstractMeshConsumer meshConsumer = new AbstractMeshConsumer(blockInfo, chunkInfo::getInitializedBuffer, aoCalc, this::transform) {
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

    public IndiumTerrainRenderContext prepare(BlockRenderView blockView, ChunkBuildBuffers buffers) {
        blockInfo.setBlockView(blockView);
        chunkInfo.prepare(blockView, buffers, blockInfo);
        return this;
    }

    public void release() {
        blockInfo.release();
    }

    /** Called from chunk renderer hook. */
    public boolean tesselateBlock(BlockState blockState, BlockPos blockPos, BlockPos origin, final BakedModel model, MatrixStack matrixStack) {
        this.matrix = matrixStack.peek().getPositionMatrix();
        this.normalMatrix = matrixStack.peek().getNormalMatrix();

        try {
            aoCalc.clear();
            blockInfo.prepareForBlock(blockState, blockPos, model.useAmbientOcclusion());
            blockInfo.setOrigin(origin);
            ((FabricBakedModel) model).emitBlockQuads(blockInfo.blockView, blockInfo.blockState, blockInfo.blockPos, blockInfo.randomSupplier, this);
        } catch (Throwable var9) {
            CrashReport crashReport_1 = CrashReport.create(var9, "Tesselating block in world - Indium Renderer");
            CrashReportSection crashReportElement_1 = crashReport_1.addElement("Block being tesselated");
            CrashReportSection.addBlockInfo(crashReportElement_1, chunkInfo.blockView, blockPos, blockState);
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