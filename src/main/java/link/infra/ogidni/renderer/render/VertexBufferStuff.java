package link.infra.ogidni.renderer.render;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.objects.*;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.*;

public class VertexBufferStuff {
	private static final RenderLayer[] RENDER_LAYERS = RenderLayer.getBlockLayers().toArray(new RenderLayer[0]);
	private static final Reference2IntOpenHashMap<RenderLayer> RENDER_LAYER_ID_MAP = new Reference2IntOpenHashMap<>();
	private static final int TRANSLUCENT_LAYER;

	static {
		for (int i = 0; i < RENDER_LAYERS.length; i++) {
			RENDER_LAYER_ID_MAP.put(RENDER_LAYERS[i], i);
		}
		TRANSLUCENT_LAYER = RENDER_LAYER_ID_MAP.getInt(RenderLayer.getTranslucent());
	}

	public static class VertexBufferManager {
		public static final VertexBufferManager INSTANCE = new VertexBufferManager();

		private final Map<ChunkPos, RegionBuffer> builtRegions = new HashMap<>();
		private final ObjectSet<ChunkPos> invalidRegions = new ObjectArraySet<>();

		private ClientWorld currWorld = null;

		public VertexBufferManager() {
			// Register callback, to rebuild all when fonts/render chunks are changed
			InvalidateRenderStateCallback.EVENT.register(this::reset);
		}

		private static class RegionBuffer {
			private final VertexBuffer[] layerBuffers = new VertexBuffer[RENDER_LAYERS.length];
			public final BlockPos origin;

			public RegionBuffer(ChunkPos chunk) {
				origin = chunk.getStartPos();
			}

			public void render(int renderLayerId, MatrixStack matrices) {
				VertexBuffer buf = layerBuffers[renderLayerId];
				buf.bind();
				RENDER_LAYERS[renderLayerId].getVertexFormat().startDrawing(0L);
				buf.draw(matrices.peek().getModel(), RENDER_LAYERS[renderLayerId].getDrawMode());
			}

			public void rebuild(int renderLayerId, BufferBuilder newBuf) {
				if (layerBuffers[renderLayerId] == null) {
					layerBuffers[renderLayerId] = new VertexBuffer(RENDER_LAYERS[renderLayerId].getVertexFormat());
				}
				if (renderLayerId == TRANSLUCENT_LAYER) {
					newBuf.sortQuads(0, 0, 0);
				}
				newBuf.end();
				layerBuffers[renderLayerId].upload(newBuf);
			}

			public void deallocate() {
				for (VertexBuffer buf : layerBuffers) {
					if (buf != null) {
						buf.close();
					}
				}
			}

			public boolean hasLayer(int renderLayerId) {
				return layerBuffers[renderLayerId] != null;
			}
		}

		static class RegionBuilder {
			private final BufferBuilder[] bufs = new BufferBuilder[RENDER_LAYERS.length];
			private final boolean[] usedLayers = new boolean[RENDER_LAYERS.length];

			public RegionBuilder() {
				for (int i = 0; i < RENDER_LAYERS.length; i++) {
					bufs[i] = new BufferBuilder(RENDER_LAYERS[i].getExpectedBufferSize());
					bufs[i].begin(RENDER_LAYERS[i].getDrawMode(), RENDER_LAYERS[i].getVertexFormat());
				}
			}

			public BufferBuilder getBuffer(RenderLayer layer) {
				int id = RENDER_LAYER_ID_MAP.getInt(layer);
				usedLayers[id] = true;
				return bufs[id];
			}

			public void build(RegionBuffer targetBuffer) {
				for (int i = 0; i < RENDER_LAYERS.length; i++) {
					if (usedLayers[i]) {
						targetBuffer.rebuild(i, bufs[i]);
						bufs[i].begin(RENDER_LAYERS[i].getDrawMode(), RENDER_LAYERS[i].getVertexFormat());
						usedLayers[i] = false;
					}
				}
			}
		}

		private final RegionBuilder builder = new RegionBuilder();

		// TODO: move chunk baking off-thread? multiple threads?

		public void render(MatrixStack matrices, Camera camera) {
			Vec3d vec3d = camera.getPos();
			double camX = vec3d.getX();
			double camY = vec3d.getY();
			double camZ = vec3d.getZ();

			CursedRenderContext renderContext = CursedRenderContext.POOL.get();

			// Iterate over all invalid regions, render and upload to RegionBuffers
			BlockRenderManager renderManager = MinecraftClient.getInstance().getBlockRenderManager();
			BlockModels models = renderManager.getModels();
			for (ChunkPos rrp : invalidRegions) {
				renderContext.prepare(currWorld, builder);
				boolean wasRendered = false;

				Chunk chunk = currWorld.getChunk(rrp.x, rrp.z);
				for (ChunkSection section : chunk.getSectionArray()) {
					if (section == null || section.isEmpty()) {
						continue;
					}
					BlockPos origin = new BlockPos(rrp.getStartPos().add(0, section.getYOffset(), 0));
					BlockPos.Mutable pos = new BlockPos.Mutable();
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								BlockState state = section.getBlockState(x, y, z);
								if (state.isAir()) {
									continue;
								}

								Block block = state.getBlock();

								if (block.getRenderType(state) == BlockRenderType.MODEL) {
									FabricBakedModel model = (FabricBakedModel)models.getModel(state);
									if (!model.isVanillaAdapter()) {
										if (!wasRendered) {
											wasRendered = true;
										}
										MatrixStack bakeStack = new MatrixStack();
										bakeStack.translate(x, origin.getY() + y, z);
										pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
										renderContext.tesselateBlock(state, pos, (BakedModel) model, bakeStack);
									}
								}
							}
						}
					}
				}

				RegionBuffer buf = builtRegions.get(rrp);
				if (!wasRendered) {
					if (buf != null) {
						builtRegions.remove(rrp);
						buf.deallocate();
					}
					continue;
				}
				if (buf == null) {
					buf = new RegionBuffer(rrp);
					builtRegions.put(rrp, buf);
				}
				builder.build(buf);
			}
			invalidRegions.clear();

			// TODO: reuse VBOs?

			// Iterate over all RegionBuffers, render them
			for (int i = 0; i < RENDER_LAYERS.length; i++) {
				RenderLayer layer = RENDER_LAYERS[i];
				layer.startDrawing();
				for (RegionBuffer cb : builtRegions.values()) {
					if (cb.hasLayer(i)) {
						BlockPos origin = cb.origin;
						matrices.push();
						matrices.translate(origin.getX() - camX, origin.getY() - camY, origin.getZ() - camZ);
						cb.render(i, matrices);
						matrices.pop();
					}
				}
				VertexBuffer.unbind();
				layer.getVertexFormat().endDrawing();
				layer.endDrawing();
			}
		}

		public void invalidate(ChunkPos pos) {
			// Mark a region as invalid. After the current set of rebuilding regions (invalid regions from the last frame) have been
			// built, a RegionBuilder will be created for this region and passed to all BERs to render to
			invalidRegions.add(pos);
			//System.out.println("Invalidated for scheduled rebuild " + pos.x + " " + pos.z);
		}

		public void loadChunk(int x, int z) {
			ChunkPos pos = new ChunkPos(x, z);
			if (!builtRegions.containsKey(pos)) {
				invalidRegions.add(pos);
				//System.out.println("Invalidated for load " + x + " " + z);
			}
		}

		public void unloadChunk(int x, int z) {
			ChunkPos pos = new ChunkPos(x, z);
			invalidRegions.remove(pos);
			RegionBuffer buf = builtRegions.remove(pos);
			if (buf != null) {
				buf.deallocate();
				//System.out.println("Deallocated for unload " + x + " " + z);
			}
		}

		private void reset() {
			// Reset everything
			for (RegionBuffer buf : builtRegions.values()) {
				buf.deallocate();
			}
			builtRegions.clear();
			invalidRegions.clear();
		}

		public void setWorld(ClientWorld world) {
			reset();
			currWorld = world;
		}
	}
}
