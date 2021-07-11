package link.infra.indium.other;

import link.infra.indium.renderer.render.TerrainBlockRenderInfo;
import me.jellysquid.mods.sodium.client.model.IndexBufferBuilder;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadWinding;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import net.minecraft.client.render.VertexConsumer;

public class VertexConsumerSinkShim implements VertexConsumer {
    private final ModelVertexSink sink;
    private final IndexBufferBuilder indices;
    private final TerrainBlockRenderInfo blockRenderInfo;
    private final int chunkId;

    public VertexConsumerSinkShim(ModelVertexSink sink, IndexBufferBuilder indices, TerrainBlockRenderInfo blockRenderInfo, int chunkId) {
        this.sink = sink;
        this.indices = indices;
        this.blockRenderInfo = blockRenderInfo;
        this.chunkId = chunkId;
    }

    private float x, y, z;
    private int color;
    private float u, v;
    private int light;
    private int i = 0; // For writing indices

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        this.x = (float) x;
        this.y = (float) y;
        this.z = (float) z;
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        // TODO: premultiply light? or does indigo do this already?
        color = ColorABGR.pack(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        this.u = u;
        this.v = v;
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
		// Not used in Sodium's ModelVertexSink
        return this;
    }

	@Override
	public VertexConsumer light(int uv) {
		this.light = uv;
		return this;
	}

	@Override
    public VertexConsumer light(int u, int v) {
        this.light = ((v & 0xFF) << 16) | (u & 0xFF);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        // Not used in Sodium's ModelVertexSink
        return this;
    }

    @Override
    public void next() {
        // TODO: move this up into the actual rendering code, batch writes when possible
		sink.ensureCapacity(1);
        sink.writeVertex(blockRenderInfo.origin, x, y, z, color, u, v, light, chunkId);
		sink.flush();

		i++;
		if (i == 4) {
			// Should be done before writing any vertices, easier to
			// just subtract 4 for now
			indices.add(sink.getVertexCount() - 4, ModelQuadWinding.CLOCKWISE);
			i = 0;
		}
    }

	@Override
	public void fixedColor(int red, int green, int blue, int alpha) {
		// Appears to be unused
		throw new UnsupportedOperationException();
	}

	@Override
	public void unfixColor() {
		// Appears to be unused
		throw new UnsupportedOperationException();
	}
}
