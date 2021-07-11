package link.infra.indium.mixin;

import link.infra.indium.other.CMETrackingHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.TickScheduler;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeArray;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Consumer;

@Mixin(WorldChunk.class)
public class WorldChunkCMEMixin {
	@Mutable
	@Shadow @Final private Map<BlockPos, BlockEntity> blockEntities;

	@Inject(method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/biome/source/BiomeArray;Lnet/minecraft/world/chunk/UpgradeData;Lnet/minecraft/world/TickScheduler;Lnet/minecraft/world/TickScheduler;J[Lnet/minecraft/world/chunk/ChunkSection;Ljava/util/function/Consumer;)V", at = @At("TAIL"))
	private void afterConstruction(World world, ChunkPos pos, BiomeArray biomes, UpgradeData upgradeData, TickScheduler<Block> blockTickScheduler, TickScheduler<Fluid> fluidTickScheduler, long inhabitedTime, ChunkSection[] sections, Consumer<WorldChunk> loadToWorldConsumer, CallbackInfo ci) {
		this.blockEntities = new CMETrackingHashMap<>();
	}
}
