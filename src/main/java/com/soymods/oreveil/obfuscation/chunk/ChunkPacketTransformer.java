package com.soymods.oreveil.obfuscation.chunk;

import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.comphenix.protocol.wrappers.WrappedLevelChunkData;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class ChunkPacketTransformer {
    private static final int BLOCKS_PER_SECTION = 16 * 16 * 16;
    private static final int BIOMES_PER_SECTION = 4 * 4 * 4;
    private static final int MAX_BLOCK_PALETTE_BITS = 8;
    private static final int MAX_BIOME_PALETTE_BITS = 3;

    private final Logger logger;
    private final Map<Material, Integer> replacementStateIds = new HashMap<>();
    private final Method blockStateIdMethod;

    public ChunkPacketTransformer(Logger logger) {
        this.logger = logger;
        this.blockStateIdMethod = resolveBlockStateIdMethod();
    }

    public boolean rewriteChunkData(
        World world,
        int chunkX,
        int chunkZ,
        WrappedLevelChunkData.ChunkData chunkData,
        java.util.function.Function<Block, Material> materialResolver
    ) {
        byte[] original = chunkData.getBuffer();
        if (original == null || original.length == 0) {
            return false;
        }

        int sectionCount = (world.getMaxHeight() - world.getMinHeight()) >> 4;
        ChunkBufferReader reader = new ChunkBufferReader(original);
        ByteArrayOutputStream rewritten = new ByteArrayOutputStream(original.length);

        try {
            for (int sectionIndex = 0; sectionIndex < sectionCount; sectionIndex++) {
                int sectionY = (world.getMinHeight() >> 4) + sectionIndex;

                int blockCountHigh = reader.readUnsignedByte();
                int blockCountLow = reader.readUnsignedByte();
                rewritten.write(blockCountHigh);
                rewritten.write(blockCountLow);

                int[] stateIds = readBlockStateContainer(reader, BLOCKS_PER_SECTION, MAX_BLOCK_PALETTE_BITS);
                boolean changed = applyObfuscation(stateIds, world, chunkX, sectionY, chunkZ, materialResolver);
                writeBlockStateContainer(rewritten, changed ? stateIds : stateIds);

                int biomeStart = reader.position();
                skipContainer(reader, BIOMES_PER_SECTION, MAX_BIOME_PALETTE_BITS);
                rewritten.write(original, biomeStart, reader.position() - biomeStart);
            }

            if (reader.remaining() > 0) {
                rewritten.write(original, reader.position(), reader.remaining());
            }

            chunkData.setBuffer(rewritten.toByteArray());
            return true;
        } catch (RuntimeException exception) {
            logger.warning("Failed to rewrite chunk packet for " + chunkX + ", " + chunkZ + ": " + exception.getMessage());
            return false;
        }
    }

    private boolean applyObfuscation(
        int[] stateIds,
        World world,
        int chunkX,
        int sectionY,
        int chunkZ,
        java.util.function.Function<Block, Material> materialResolver
    ) {
        boolean changed = false;
        int baseX = chunkX << 4;
        int baseY = sectionY << 4;
        int baseZ = chunkZ << 4;

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = flattenIndex(x, y, z);
                    Block block = world.getBlockAt(baseX + x, baseY + y, baseZ + z);
                    Material visibleMaterial = materialResolver.apply(block);
                    if (visibleMaterial != block.getType()) {
                        stateIds[index] = getReplacementStateId(visibleMaterial);
                        changed = true;
                    }
                }
            }
        }

        return changed;
    }

    private int[] readBlockStateContainer(ChunkBufferReader reader, int containerSize, int maxPaletteBits) {
        int bits = reader.readUnsignedByte();
        if (bits == 0) {
            int singleState = reader.readVarInt();
            int longArrayLength = reader.readVarInt();
            reader.skipBytes(longArrayLength * Long.BYTES);
            int[] states = new int[containerSize];
            Arrays.fill(states, singleState);
            return states;
        }

        if (bits <= maxPaletteBits) {
            int paletteLength = reader.readVarInt();
            int[] palette = new int[paletteLength];
            for (int i = 0; i < paletteLength; i++) {
                palette[i] = reader.readVarInt();
            }

            long[] data = reader.readLongArray();
            int[] paletteIndexes = unpackIndices(data, bits, containerSize);
            int[] states = new int[containerSize];
            for (int i = 0; i < containerSize; i++) {
                int paletteIndex = paletteIndexes[i];
                states[i] = paletteIndex >= 0 && paletteIndex < palette.length ? palette[paletteIndex] : 0;
            }
            return states;
        }

        long[] data = reader.readLongArray();
        return unpackIndices(data, bits, containerSize);
    }

    private void skipContainer(ChunkBufferReader reader, int containerSize, int maxPaletteBits) {
        int bits = reader.readUnsignedByte();
        if (bits == 0) {
            reader.readVarInt();
            int longArrayLength = reader.readVarInt();
            reader.skipBytes(longArrayLength * Long.BYTES);
            return;
        }

        if (bits <= maxPaletteBits) {
            int paletteLength = reader.readVarInt();
            for (int i = 0; i < paletteLength; i++) {
                reader.readVarInt();
            }
        }

        int longArrayLength = reader.readVarInt();
        reader.skipBytes(longArrayLength * Long.BYTES);
    }

    private void writeBlockStateContainer(ByteArrayOutputStream output, int[] stateIds) {
        LinkedHashMap<Integer, Integer> paletteIndexByState = new LinkedHashMap<>();
        for (int stateId : stateIds) {
            if (!paletteIndexByState.containsKey(stateId)) {
                paletteIndexByState.put(stateId, paletteIndexByState.size());
            }
        }

        if (paletteIndexByState.size() == 1) {
            output.write(0);
            writeVarInt(output, paletteIndexByState.keySet().iterator().next());
            writeVarInt(output, 0);
            return;
        }

        if (paletteIndexByState.size() <= (1 << MAX_BLOCK_PALETTE_BITS)) {
            int bits = Math.max(4, ceilLog2(paletteIndexByState.size()));
            output.write(bits);
            writeVarInt(output, paletteIndexByState.size());
            for (int stateId : paletteIndexByState.keySet()) {
                writeVarInt(output, stateId);
            }

            int[] paletteIndexes = new int[stateIds.length];
            for (int i = 0; i < stateIds.length; i++) {
                paletteIndexes[i] = paletteIndexByState.get(stateIds[i]);
            }
            long[] packed = packIndices(paletteIndexes, bits);
            writeLongArray(output, packed);
            return;
        }

        int bits = Math.max(9, ceilLog2(maxStateId(stateIds) + 1));
        output.write(bits);
        long[] packed = packIndices(stateIds, bits);
        writeLongArray(output, packed);
    }

    private int getReplacementStateId(Material material) {
        Integer cached = replacementStateIds.get(material);
        if (cached != null) {
            return cached;
        }

        try {
            WrappedBlockData wrapped = WrappedBlockData.createData(material);
            int id = (Integer) blockStateIdMethod.invoke(null, wrapped.getHandle());
            replacementStateIds.put(material, id);
            return id;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to resolve block state ID for " + material, exception);
        }
    }

    private Method resolveBlockStateIdMethod() {
        Class<?> blockClass = MinecraftReflection.getBlockClass();
        Class<?> blockDataClass = MinecraftReflection.getIBlockDataClass();
        for (Method method : blockClass.getDeclaredMethods()) {
            if ((method.getModifiers() & java.lang.reflect.Modifier.STATIC) == 0) {
                continue;
            }
            if (method.getReturnType() != int.class) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 1 && parameters[0] == blockDataClass) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new IllegalStateException("Unable to locate Block -> state ID accessor.");
    }

    private static int[] unpackIndices(long[] packed, int bits, int count) {
        int[] result = new int[count];
        long mask = (1L << bits) - 1L;
        for (int i = 0; i < count; i++) {
            int bitIndex = i * bits;
            int longIndex = bitIndex >>> 6;
            int startBit = bitIndex & 63;
            long value = packed[longIndex] >>> startBit;
            int endBit = startBit + bits;
            if (endBit > 64) {
                value |= packed[longIndex + 1] << (64 - startBit);
            }
            result[i] = (int) (value & mask);
        }
        return result;
    }

    private static long[] packIndices(int[] values, int bits) {
        int longCount = (values.length * bits + 63) >>> 6;
        long[] packed = new long[longCount];
        long mask = (1L << bits) - 1L;
        for (int i = 0; i < values.length; i++) {
            long value = values[i] & mask;
            int bitIndex = i * bits;
            int longIndex = bitIndex >>> 6;
            int startBit = bitIndex & 63;
            packed[longIndex] |= value << startBit;
            int endBit = startBit + bits;
            if (endBit > 64) {
                packed[longIndex + 1] |= value >>> (64 - startBit);
            }
        }
        return packed;
    }

    private static void writeLongArray(ByteArrayOutputStream output, long[] values) {
        writeVarInt(output, values.length);
        for (long value : values) {
            for (int shift = 56; shift >= 0; shift -= 8) {
                output.write((int) ((value >>> shift) & 0xFF));
            }
        }
    }

    private static void writeVarInt(ByteArrayOutputStream output, int value) {
        while ((value & ~0x7F) != 0) {
            output.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.write(value);
    }

    private static int ceilLog2(int value) {
        if (value <= 1) {
            return 0;
        }
        return 32 - Integer.numberOfLeadingZeros(value - 1);
    }

    private static int maxStateId(int[] values) {
        int max = 0;
        for (int value : values) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    private static int flattenIndex(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }
}
