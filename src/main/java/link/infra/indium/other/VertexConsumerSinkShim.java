package link.infra.indium.other;

import me.jellysquid.mods.sodium.client.model.PrimitiveSink;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import net.minecraft.client.render.VertexConsumer;

public class VertexConsumerSinkShim implements VertexConsumer {
    private final PrimitiveSink<ModelVertexSink> sink;
    private final int offset;

    public VertexConsumerSinkShim(PrimitiveSink<ModelVertexSink> sink, int offset) {
        this.sink = sink;
        this.offset = offset;
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
		sink.vertices.ensureCapacity(1);
        sink.vertices.writeVertex(x, y, z, color, u, v, light, offset);
		sink.vertices.flush();

		i++;
		if (i == 4) {
			// Should be done before writing any vertices, easier to
			// just subtract 4 for now
			int count = sink.vertices.getVertexCount() - 4;
			sink.indices.add(count + 0);
			sink.indices.add(count + 1);
			sink.indices.add(count + 2);
			sink.indices.add(count + 2);
			sink.indices.add(count + 3);
			sink.indices.add(count + 0);
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
