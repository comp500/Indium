package link.infra.indium;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import io.vram.frex.impl.RendererHolder;

public class IndiumMixinPlugin implements IMixinConfigPlugin {
	private final boolean shouldLoad;

	public IndiumMixinPlugin() {
		shouldLoad = RendererHolder.bestProvider() instanceof IndiumProvider;
	}

	@Override
	public void onLoad(String mixinPackage) {
		// NOOP
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return !mixinClassName.startsWith("link.infra.indium.mixin.") || shouldLoad;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
		// NOOP
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		// NOOP
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		// NOOP
	}
}
