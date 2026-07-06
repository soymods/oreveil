package com.soymods.oreveil.obfuscation.transport;

import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.obfuscation.HostBlockResolver;
import com.soymods.oreveil.world.AuthoritativeWorldModel;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;

final class ChunkPacketBlockRewriter {
    private static final int BLOCKS_PER_SECTION = 4096;
    private static final int BLOCK_MIN_INDIRECT_BITS = 4;
    private static final int BLOCK_DIRECT_BITS = 15;
    private static final int BIOME_MIN_INDIRECT_BITS = 1;
    private static final int BIOME_DIRECT_BITS = 6;

    private final ChunkEntryProvider chunkEntryProvider;
    private final HostBlockResolver hostBlockResolver = new HostBlockResolver();
    private final StateIdResolver stateIds;

    ChunkPacketBlockRewriter(AuthoritativeWorldModel worldModel) {
        this(
            new WorldModelChunkEntryProvider(worldModel),
            new ProtocolLibStateIdResolver()
        );
    }

    ChunkPacketBlockRewriter(ChunkEntryProvider chunkEntryProvider, StateIdResolver stateIds) {
        this.chunkEntryProvider = chunkEntryProvider;
        this.stateIds = stateIds;
    }

    RewriteResult rewrite(
        byte[] input,
        UUID worldId,
        World.Environment environment,
        int chunkX,
        int chunkZ,
        int minHeight,
        int maxHeight,
        OreveilConfig config
    ) {
        Map<Integer, Material> protectedOres = chunkEntryProvider.buriedProtectedOreEntries(worldId, chunkX, chunkZ);
        Map<Integer, Material> salt = chunkEntryProvider.saltEntries(worldId, chunkX, chunkZ);
        if (protectedOres.isEmpty() && salt.isEmpty()) {
            return RewriteResult.unchanged();
        }

        Map<Integer, Integer> replacements = buildReplacementStateIds(protectedOres, salt, environment, config);
        if (replacements.isEmpty()) {
            return RewriteResult.unchanged();
        }

        try {
            Reader reader = new Reader(input);
            ByteArrayOutputStream output = new ByteArrayOutputStream(input.length);
            int rewritten = 0;
            int sectionCount = Math.max(0, (maxHeight - minHeight) / 16);

            for (int section = 0; section < sectionCount; section++) {
                int sectionY = (minHeight >> 4) + section;
                output.write(reader.readByte());
                output.write(reader.readByte());

                PalettedContainer blocks = readContainer(reader, BLOCK_MIN_INDIRECT_BITS, BLOCK_DIRECT_BITS);
                RewriteContainerResult blockResult = rewriteBlockContainer(blocks, replacements, sectionY);
                writeContainer(output, blockResult.container(), BLOCK_MIN_INDIRECT_BITS, BLOCK_DIRECT_BITS);
                rewritten += blockResult.rewrittenEntries();

                PalettedContainer biomes = readContainer(reader, BIOME_MIN_INDIRECT_BITS, BIOME_DIRECT_BITS);
                writeContainer(output, biomes, BIOME_MIN_INDIRECT_BITS, BIOME_DIRECT_BITS);
            }

            output.write(input, reader.position(), input.length - reader.position());
            byte[] rewrittenBytes = output.toByteArray();
            return rewritten == 0 ? RewriteResult.unchanged() : RewriteResult.rewritten(rewrittenBytes, rewritten);
        } catch (RuntimeException exception) {
            return RewriteResult.failed(exception);
        }
    }

    private Map<Integer, Integer> buildReplacementStateIds(
        Map<Integer, Material> protectedOres,
        Map<Integer, Material> salt,
        World.Environment environment,
        OreveilConfig config
    ) {
        Map<Integer, Integer> replacements = new HashMap<>();
        for (Map.Entry<Integer, Material> entry : protectedOres.entrySet()) {
            Material host = hostBlockResolver.resolve(entry.getValue(), environment, config);
            Integer id = stateIds.idFor(host);
            if (id != null) {
                replacements.put(entry.getKey(), id);
            }
        }
        for (Map.Entry<Integer, Material> entry : salt.entrySet()) {
            Integer id = stateIds.idFor(entry.getValue());
            if (id != null) {
                replacements.put(entry.getKey(), id);
            }
        }
        return replacements;
    }

    private RewriteContainerResult rewriteBlockContainer(
        PalettedContainer container,
        Map<Integer, Integer> replacements,
        int sectionY
    ) {
        int[] values = container.expand(BLOCKS_PER_SECTION, BLOCK_DIRECT_BITS);
        int rewritten = 0;
        for (Map.Entry<Integer, Integer> replacement : replacements.entrySet()) {
            int[] loc = unpackLocal(replacement.getKey());
            if ((loc[1] >> 4) != sectionY) {
                continue;
            }

            int index = ((loc[1] & 0xF) << 8) | (loc[2] << 4) | loc[0];
            int replacementId = replacement.getValue();
            if (index >= 0 && index < values.length && values[index] != replacementId) {
                values[index] = replacementId;
                rewritten++;
            }
        }

        return new RewriteContainerResult(PalettedContainer.fromValues(values, BLOCK_MIN_INDIRECT_BITS, BLOCK_DIRECT_BITS), rewritten);
    }

    private static PalettedContainer readContainer(Reader reader, int minIndirectBits, int directBits) {
        int bits = reader.readUnsignedByte();
        if (bits == 0) {
            int singleValue = reader.readVarInt();
            int dataLength = reader.readVarInt();
            long[] data = reader.readLongArray(dataLength);
            return PalettedContainer.single(singleValue, data);
        }

        int effectiveBits = normalizeBits(bits, minIndirectBits, directBits);
        int[] palette = null;
        if (effectiveBits < directBits) {
            int paletteLength = reader.readVarInt();
            palette = new int[paletteLength];
            for (int i = 0; i < paletteLength; i++) {
                palette[i] = reader.readVarInt();
            }
        }

        int dataLength = reader.readVarInt();
        long[] data = reader.readLongArray(dataLength);
        return new PalettedContainer(effectiveBits, palette, data);
    }

    private static void writeContainer(ByteArrayOutputStream output, PalettedContainer container, int minIndirectBits, int directBits) {
        int bits = container.bits();
        output.write(bits);
        if (bits == 0) {
            writeVarInt(output, container.singleValue());
            writeVarInt(output, 0);
            return;
        }

        if (bits < directBits) {
            int[] palette = container.palette();
            writeVarInt(output, palette.length);
            for (int id : palette) {
                writeVarInt(output, id);
            }
        }

        long[] data = container.data();
        writeVarInt(output, data.length);
        for (long value : data) {
            writeLong(output, value);
        }
    }

    private static int normalizeBits(int bits, int minIndirectBits, int directBits) {
        if (bits == 0) {
            return 0;
        }
        if (bits < minIndirectBits) {
            return minIndirectBits;
        }
        return bits;
    }

    private static void writeVarInt(ByteArrayOutputStream output, int value) {
        int current = value;
        do {
            int temp = current & 0x7F;
            current >>>= 7;
            if (current != 0) {
                temp |= 0x80;
            }
            output.write(temp);
        } while (current != 0);
    }

    private static void writeLong(ByteArrayOutputStream output, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            output.write((int) (value >>> shift) & 0xFF);
        }
    }

    private static int[] unpackLocal(int packed) {
        return new int[]{packed & 0xF, (packed >> 8) - 2048, (packed >> 4) & 0xF};
    }

    record RewriteResult(byte[] bytes, int rewrittenEntries, RuntimeException failure) {
        static RewriteResult unchanged() {
            return new RewriteResult(null, 0, null);
        }

        static RewriteResult rewritten(byte[] bytes, int rewrittenEntries) {
            return new RewriteResult(bytes, rewrittenEntries, null);
        }

        static RewriteResult failed(RuntimeException failure) {
            return new RewriteResult(null, 0, failure);
        }

        boolean changed() {
            return bytes != null;
        }

        boolean failed() {
            return failure != null;
        }
    }

    private record RewriteContainerResult(PalettedContainer container, int rewrittenEntries) {
    }

    private record PalettedContainer(int bits, int[] palette, long[] data, int singleValue) {
        PalettedContainer(int bits, int[] palette, long[] data) {
            this(bits, palette, data, -1);
        }

        static PalettedContainer single(int value, long[] ignoredData) {
            return new PalettedContainer(0, null, new long[0], value);
        }

        static PalettedContainer fromValues(int[] values, int minIndirectBits, int directBits) {
            Map<Integer, Integer> paletteIndexes = new HashMap<>();
            int[] paletteBuffer = new int[Math.min(values.length, 1 << directBits)];
            int[] localValues = new int[values.length];

            for (int i = 0; i < values.length; i++) {
                int value = values[i];
                Integer existing = paletteIndexes.get(value);
                if (existing == null) {
                    int next = paletteIndexes.size();
                    paletteIndexes.put(value, next);
                    if (next < paletteBuffer.length) {
                        paletteBuffer[next] = value;
                    }
                    existing = next;
                }
                localValues[i] = existing;
            }

            int paletteSize = paletteIndexes.size();
            if (paletteSize == 1) {
                return single(values[0], new long[0]);
            }

            int bits = Math.max(minIndirectBits, bitsFor(paletteSize - 1));
            if (bits < directBits) {
                int[] palette = new int[paletteSize];
                System.arraycopy(paletteBuffer, 0, palette, 0, paletteSize);
                return new PalettedContainer(bits, palette, pack(localValues, bits));
            }

            int directValueBits = Math.max(directBits, bitsFor(max(values)));
            return new PalettedContainer(directValueBits, null, pack(values, directValueBits));
        }

        int[] expand(int size, int directBits) {
            if (bits == 0) {
                int[] values = new int[size];
                java.util.Arrays.fill(values, singleValue);
                return values;
            }

            int[] packed = unpack(data, bits, size);
            if (palette == null || bits >= directBits) {
                return packed;
            }

            int[] values = new int[size];
            for (int i = 0; i < size; i++) {
                int paletteIndex = packed[i];
                values[i] = paletteIndex >= 0 && paletteIndex < palette.length ? palette[paletteIndex] : 0;
            }
            return values;
        }

        private static int bitsFor(int maxValue) {
            return Math.max(1, 32 - Integer.numberOfLeadingZeros(maxValue));
        }

        private static long[] pack(int[] values, int bits) {
            int valuesPerLong = Math.max(1, 64 / bits);
            long mask = (1L << bits) - 1L;
            long[] data = new long[(values.length + valuesPerLong - 1) / valuesPerLong];
            for (int i = 0; i < values.length; i++) {
                int longIndex = i / valuesPerLong;
                int bitIndex = (i % valuesPerLong) * bits;
                data[longIndex] |= ((long) values[i] & mask) << bitIndex;
            }
            return data;
        }

        private static int[] unpack(long[] data, int bits, int size) {
            int valuesPerLong = Math.max(1, 64 / bits);
            long mask = (1L << bits) - 1L;
            int[] values = new int[size];
            for (int i = 0; i < size; i++) {
                int longIndex = i / valuesPerLong;
                if (longIndex >= data.length) {
                    break;
                }
                int bitIndex = (i % valuesPerLong) * bits;
                values[i] = (int) ((data[longIndex] >>> bitIndex) & mask);
            }
            return values;
        }

        private static int max(int[] values) {
            int max = 0;
            for (int value : values) {
                if (value > max) {
                    max = value;
                }
            }
            return max;
        }
    }

    private static final class Reader {
        private final byte[] input;
        private int index;

        Reader(byte[] input) {
            this.input = input;
        }

        int position() {
            return index;
        }

        int readUnsignedByte() {
            if (index >= input.length) {
                throw new IllegalStateException("Unexpected end of chunk buffer.");
            }
            return input[index++] & 0xFF;
        }

        int readByte() {
            return readUnsignedByte();
        }

        int readVarInt() {
            int value = 0;
            int position = 0;
            while (position < 35) {
                int current = readUnsignedByte();
                value |= (current & 0x7F) << position;
                if ((current & 0x80) == 0) {
                    return value;
                }
                position += 7;
            }
            throw new IllegalStateException("VarInt is too large.");
        }

        long[] readLongArray(int length) {
            if (length < 0 || input.length - index < length * Long.BYTES) {
                throw new IllegalStateException("Invalid long array length in chunk buffer.");
            }
            long[] result = new long[length];
            for (int i = 0; i < length; i++) {
                long value = 0L;
                for (int b = 0; b < Long.BYTES; b++) {
                    value = (value << 8) | (readUnsignedByte() & 0xFFL);
                }
                result[i] = value;
            }
            return result;
        }
    }

    interface ChunkEntryProvider {
        Map<Integer, Material> buriedProtectedOreEntries(UUID worldId, int chunkX, int chunkZ);

        Map<Integer, Material> saltEntries(UUID worldId, int chunkX, int chunkZ);
    }

    interface StateIdResolver {
        Integer idFor(Material material);
    }

    private record WorldModelChunkEntryProvider(AuthoritativeWorldModel worldModel) implements ChunkEntryProvider {
        @Override
        public Map<Integer, Material> buriedProtectedOreEntries(UUID worldId, int chunkX, int chunkZ) {
            return worldModel.getBuriedProtectedOreEntriesInChunk(worldId, chunkX, chunkZ);
        }

        @Override
        public Map<Integer, Material> saltEntries(UUID worldId, int chunkX, int chunkZ) {
            return worldModel.getSaltEntriesInChunk(worldId, chunkX, chunkZ);
        }
    }

    private static final class ProtocolLibStateIdResolver implements StateIdResolver {
        private final Map<Material, Integer> cache = new HashMap<>();
        private Method blockStateIdMethod;
        private boolean unavailable;

        @Override
        public Integer idFor(Material material) {
            if (unavailable) {
                return null;
            }
            return cache.computeIfAbsent(material, this::resolve);
        }

        private Integer resolve(Material material) {
            try {
                Object handle = WrappedBlockData.createData(material).getHandle();
                if (blockStateIdMethod == null) {
                    blockStateIdMethod = MinecraftReflection.getBlockClass().getMethod("getId", handle.getClass());
                }
                int id = (Integer) blockStateIdMethod.invoke(null, handle);
                return id < 0 ? null : id;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                unavailable = true;
                return null;
            }
        }
    }
}
