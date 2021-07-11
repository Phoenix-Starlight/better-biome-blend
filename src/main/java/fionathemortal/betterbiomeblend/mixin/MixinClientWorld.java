package fionathemortal.betterbiomeblend.mixin;

import java.util.function.Supplier;

import fionathemortal.betterbiomeblend.*;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.world.BiomeColorCache;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.level.ColorResolver;

@Mixin(ClientWorld.class)
public abstract class MixinClientWorld extends World
{
    @Shadow
    private Object2ObjectArrayMap<ColorResolver, BiomeColorCache> colorCache =
        new Object2ObjectArrayMap<>();

    @Unique
    private final ColorCache blendColorCache = new ColorCache(2048);

    @Unique
    private final ColorCache rawColorCache   = new ColorCache(512);

    @Unique
    private final ThreadLocal<ColorChunk> threadLocalWaterChunk   =
        ThreadLocal.withInitial(
            () ->
            {
                ColorChunk chunk = new ColorChunk();
                chunk.acquire();
                return chunk;
            });

    @Unique
    private final ThreadLocal<ColorChunk> threadLocalGrassChunk   =
        ThreadLocal.withInitial(
            () ->
            {
                ColorChunk chunk = new ColorChunk();
                chunk.acquire();
                return chunk;
            });

    @Unique
    private final ThreadLocal<ColorChunk> threadLocalFoliageChunk =
        ThreadLocal.withInitial(
            () ->
            {
                ColorChunk chunk = new ColorChunk();
                chunk.acquire();
                return chunk;
            });

	protected
	MixinClientWorld(
        MutableWorldProperties worldInfo,
        RegistryKey<World>     dimension,
        DimensionType          dimensionType,
        Supplier<Profiler>     profiler,
        boolean                isRemote,
        boolean                isDebug,
        long                   seed)
	{
		super(worldInfo, dimension, dimensionType, profiler, isRemote, isDebug, seed);
	}

	@Inject(method = "reloadColor", at = @At("HEAD"))
    public void
    onReloadColor(CallbackInfo ci)
    {
        blendColorCache.invalidateAll();
        rawColorCache.invalidateAll();
    }

    @Inject(method = "resetChunkColor", at = @At("HEAD"))
    public void
    onResetChunkColor(ChunkPos position, CallbackInfo ci)
    {
        blendColorCache.invalidateNeighbourhood(position.x, position.z);
        rawColorCache.invalidateSmallNeighbourhood(position.x, position.z);
    }

    @Overwrite
    public int
    getColor(BlockPos blockPosIn, ColorResolver colorResolverIn)
    {
        int                     colorType;
        ThreadLocal<ColorChunk> threadLocalChunk;

        if (colorResolverIn == BiomeColors.GRASS_COLOR)
        {
            colorType        = BiomeColorType.GRASS;
            threadLocalChunk = threadLocalGrassChunk;
        }
        else if (colorResolverIn == BiomeColors.WATER_COLOR)
        {
            colorType        = BiomeColorType.WATER;
            threadLocalChunk = threadLocalWaterChunk;
        }
        else
        {
            colorType        = BiomeColorType.FOLIAGE;
            threadLocalChunk = threadLocalFoliageChunk;
        }

        int x = blockPosIn.getX();
        int z = blockPosIn.getZ();

        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        ColorChunk chunk = ColorCaching.getThreadLocalChunk(threadLocalChunk, chunkX, chunkZ, colorType);

        if (chunk == null)
        {
            chunk = ColorCaching.getBlendedColorChunk(this, colorResolverIn, colorType, chunkX, chunkZ, blendColorCache, rawColorCache);

            ColorCaching.setThreadLocalChunk(threadLocalChunk, chunk, blendColorCache);
        }

        int result = chunk.getColor(x, z);

        return result;
    }
}
