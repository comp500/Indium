package io.github.spiralhalo.plumbum.renderer.mesh;

import io.vram.frex.api.buffer.PooledQuadEmitter;
import io.vram.frex.api.buffer.PooledVertexEmitter;
import io.vram.frex.api.buffer.QuadEmitter;
import io.vram.frex.api.buffer.QuadTransform;
import io.vram.frex.api.model.InputContext;
import io.vram.frex.base.renderer.mesh.MeshEncodingHelper;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class PlumbumTransformStack {
	protected final ObjectArrayList<TransformingQuadEmitter> POOL = new ObjectArrayList<>();

	public TransformingQuadEmitter createTransform(InputContext context, QuadTransform transform, MutableQuadViewImpl output) {
		return (POOL.isEmpty() ? new TransformingQuadEmitter(this) : POOL.pop()).prepare(context, transform, output);
	}

	public void reclaim(TransformingQuadEmitter transformingQuadEmitter) {
		POOL.add(transformingQuadEmitter);
	}

	public static class TransformingQuadEmitter extends MutableQuadViewImpl implements PooledQuadEmitter, PooledVertexEmitter {
		protected final PlumbumTransformStack transformStack;

		protected InputContext context;
		protected QuadEmitter output;
		protected QuadTransform transform;

		public TransformingQuadEmitter(PlumbumTransformStack transformStack) {
			this.transformStack = transformStack;
			data = new int[MeshEncodingHelper.TOTAL_MESH_QUAD_STRIDE];
		}

		public TransformingQuadEmitter prepare(InputContext context, QuadTransform transform, QuadEmitter output) {
			this.context = context;
			this.transform = transform;
			this.output = output;
			clear();
			return this;
		}

		@Override
		public TransformingQuadEmitter withTransformQuad(InputContext context, QuadTransform transform) {
			return transformStack.createTransform(context, transform, this);
		}

		@Override
		public TransformingQuadEmitter withTransformVertex(InputContext context, QuadTransform transform) {
			return transformStack.createTransform(context, transform, this);
		}

		@Override
		public QuadEmitter emit() {
			transform.transform(context, this, output);
			return this;
		}

		@Override
		public void close() {
			transformStack.reclaim(this);
		}

		@Override
		public boolean isTransformer() {
			return true;
		}
	}
}
