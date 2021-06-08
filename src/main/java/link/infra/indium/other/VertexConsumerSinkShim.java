package link.infra.indium.other;

import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import net.minecraft.client.render.VertexConsumer;

public class VertexConsumerSinkShim implements VertexConsumer {
    private final ModelVertexSink sink;

    public VertexConsumerSinkShim(ModelVertexSink sink) {
        this.sink = sink;
    }

    private float x, y, z;
    private int color;
    private float u, v;
    private int light;

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
        // TODO: overlay? indigo probably doesn't use this
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        // TODO: check these are correct
        this.light = ((v & 0xFF) << 16) | (u & 0xFF);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        // TODO: normal? indigo probably doesn't use this
        return this;
    }

    @Override
    public void next() {
        // TODO: move this up into the actual rendering code, batch writes when possible
        sink.ensureCapacity(4);
        sink.writeQuad(x, y, z, color, u, v, light);
        sink.flush();
    }

	@Override
	public void fixedColor(int red, int green, int blue, int alpha) {
		// Appears to be unused
	}

	@Override
	public void unfixColor() {
		// Appears to be unused
	}
}
