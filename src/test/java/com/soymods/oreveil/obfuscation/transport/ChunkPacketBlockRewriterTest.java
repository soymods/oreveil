package com.soymods.oreveil.obfuscation.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.config.XrayProfile;
import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

final class ChunkPacketBlockRewriterTest {
    private static final UUID WORLD_ID = new UUID(1L, 2L);
    private static final int STONE = 1;
    private static final int DIAMOND_ORE = 2;
    private static final int DEEPSLATE_DIAMOND_ORE = 3;
    private static final int DEEPSLATE = 4;
    private static final int FAKE_GOLD = 5;

    @Test
    void rewritesProtectedOreInIndirectPalette() {
        ChunkPacketBlockRewriter rewriter = rewriter(
            Map.of(pack(1, 4, 2), Material.DIAMOND_ORE),
            Map.of()
        );
        int[] values = filled(STONE);
        values[index(1, 4, 2)] = DIAMOND_ORE;

        byte[] input = chunkBuffer(values);
        ChunkPacketBlockRewriter.RewriteResult result = rewriter.rewrite(
            input,
            WORLD_ID,
            World.Environment.NORMAL,
            0,
            0,
            0,
            16,
            config()
        );

        assertTrue(result.changed());
        assertEquals(1, result.rewrittenEntries());
        assertEquals(STONE, readBlockStates(result.bytes())[index(1, 4, 2)]);
    }

    @Test
    void usesOreOverrideForDeepslateOre() {
        ChunkPacketBlockRewriter rewriter = rewriter(
            Map.of(pack(3, 8, 9), Material.DEEPSLATE_DIAMOND_ORE),
            Map.of()
        );
        int[] values = filled(STONE);
        values[index(3, 8, 9)] = DEEPSLATE_DIAMOND_ORE;

        ChunkPacketBlockRewriter.RewriteResult result = rewriter.rewrite(
            chunkBuffer(values),
            WORLD_ID,
            World.Environment.NORMAL,
            0,
            0,
            0,
            16,
            config()
        );

        assertTrue(result.changed());
        assertEquals(DEEPSLATE, readBlockStates(result.bytes())[index(3, 8, 9)]);
    }

    @Test
    void rewritesSaltEntryToFakeOreId() {
        ChunkPacketBlockRewriter rewriter = rewriter(
            Map.of(),
            Map.of(pack(6, 2, 1), Material.GOLD_ORE)
        );
        int[] values = filled(STONE);

        ChunkPacketBlockRewriter.RewriteResult result = rewriter.rewrite(
            chunkBuffer(values),
            WORLD_ID,
            World.Environment.NORMAL,
            0,
            0,
            0,
            16,
            config()
        );

        assertTrue(result.changed());
        assertEquals(FAKE_GOLD, readBlockStates(result.bytes())[index(6, 2, 1)]);
    }

    @Test
    void unchangedWhenNoCachedEntriesExist() {
        ChunkPacketBlockRewriter rewriter = rewriter(Map.of(), Map.of());
        byte[] input = chunkBuffer(filled(STONE));

        ChunkPacketBlockRewriter.RewriteResult result = rewriter.rewrite(
            input,
            WORLD_ID,
            World.Environment.NORMAL,
            0,
            0,
            0,
            16,
            config()
        );

        assertFalse(result.changed());
        assertFalse(result.failed());
    }

    @Test
    void returnsFailureForMalformedChunkBuffer() {
        ChunkPacketBlockRewriter rewriter = rewriter(
            Map.of(pack(1, 4, 2), Material.DIAMOND_ORE),
            Map.of()
        );

        ChunkPacketBlockRewriter.RewriteResult result = rewriter.rewrite(
            new byte[] {0, 1, 2},
            WORLD_ID,
            World.Environment.NORMAL,
            0,
            0,
            0,
            16,
            config()
        );

        assertTrue(result.failed());
        assertNotNull(result.failure());
    }

    @Test
    void preservesTrailingBytesAfterSectionData() {
        ChunkPacketBlockRewriter rewriter = rewriter(
            Map.of(pack(1, 4, 2), Material.DIAMOND_ORE),
            Map.of()
        );
        byte[] input = chunkBuffer(filled(DIAMOND_ORE), new byte[] {9, 8, 7, 6});

        ChunkPacketBlockRewriter.RewriteResult result = rewriter.rewrite(
            input,
            WORLD_ID,
            World.Environment.NORMAL,
            0,
            0,
            0,
            16,
            config()
        );

        byte[] output = result.bytes();
        byte[] trailing = java.util.Arrays.copyOfRange(output, output.length - 4, output.length);
        assertArrayEquals(new byte[] {9, 8, 7, 6}, trailing);
    }

    private static ChunkPacketBlockRewriter rewriter(Map<Integer, Material> ores, Map<Integer, Material> salt) {
        ChunkPacketBlockRewriter.ChunkEntryProvider provider = new ChunkPacketBlockRewriter.ChunkEntryProvider() {
            @Override
            public Map<Integer, Material> protectedOreEntries(UUID worldId, int chunkX, int chunkZ) {
                return ores;
            }

            @Override
            public Map<Integer, Material> saltEntries(UUID worldId, int chunkX, int chunkZ) {
                return salt;
            }
        };
        return new ChunkPacketBlockRewriter(provider, material -> switch (material) {
            case STONE -> STONE;
            case DIAMOND_ORE -> DIAMOND_ORE;
            case DEEPSLATE_DIAMOND_ORE -> DEEPSLATE_DIAMOND_ORE;
            case DEEPSLATE -> DEEPSLATE;
            case GOLD_ORE -> FAKE_GOLD;
            default -> null;
        });
    }

    private static OreveilConfig config() {
        EnumMap<World.Environment, Material> defaults = new EnumMap<>(World.Environment.class);
        defaults.put(World.Environment.NORMAL, Material.STONE);
        defaults.put(World.Environment.NETHER, Material.NETHERRACK);
        defaults.put(World.Environment.THE_END, Material.END_STONE);
        EnumMap<Material, Material> overrides = new EnumMap<>(Material.class);
        overrides.put(Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE);
        return new OreveilConfig(
            true,
            true,
            true,
            6,
            64,
            0,
            false,
            XrayProfile.BALANCED,
            64,
            0L,
            "AUTO",
            EnumSet.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.GOLD_ORE),
            EnumSet.of(Material.AIR),
            EnumSet.noneOf(Material.class),
            defaults,
            overrides,
            new com.soymods.oreveil.config.OreveilWorldGenerationConfig(
                false,
                "oreveil",
                World.Environment.NORMAL,
                true,
                true,
                null,
                0L,
                18,
                8,
                0.02D
            )
        );
    }

    private static byte[] chunkBuffer(int[] blockStates) {
        return chunkBuffer(blockStates, new byte[0]);
    }

    private static byte[] chunkBuffer(int[] blockStates, byte[] trailing) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        out.write(0);
        writeContainer(out, blockStates, 4);
        writeContainer(out, filled(0, 64), 1);
        out.writeBytes(trailing);
        return out.toByteArray();
    }

    private static int[] readBlockStates(byte[] bytes) {
        Reader reader = new Reader(bytes);
        reader.readUnsignedByte();
        reader.readUnsignedByte();
        return readContainer(reader, 4096, 15);
    }

    private static void writeContainer(ByteArrayOutputStream out, int[] values, int minBits) {
        Map<Integer, Integer> paletteIndexes = new HashMap<>();
        int[] palette = new int[values.length];
        int[] local = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            int value = values[i];
            Integer existing = paletteIndexes.get(value);
            if (existing == null) {
                existing = paletteIndexes.size();
                paletteIndexes.put(value, existing);
                palette[existing] = value;
            }
            local[i] = existing;
        }

        if (paletteIndexes.size() == 1) {
            out.write(0);
            writeVarInt(out, values[0]);
            writeVarInt(out, 0);
            return;
        }

        int bits = Math.max(minBits, 32 - Integer.numberOfLeadingZeros(paletteIndexes.size() - 1));
        out.write(bits);
        writeVarInt(out, paletteIndexes.size());
        for (int i = 0; i < paletteIndexes.size(); i++) {
            writeVarInt(out, palette[i]);
        }
        long[] packed = pack(local, bits);
        writeVarInt(out, packed.length);
        for (long value : packed) {
            writeLong(out, value);
        }
    }

    private static int[] readContainer(Reader reader, int size, int directBits) {
        int bits = reader.readUnsignedByte();
        if (bits == 0) {
            int value = reader.readVarInt();
            reader.readVarInt();
            int[] values = new int[size];
            java.util.Arrays.fill(values, value);
            return values;
        }

        int[] palette = null;
        if (bits < directBits) {
            int paletteLength = reader.readVarInt();
            palette = new int[paletteLength];
            for (int i = 0; i < paletteLength; i++) {
                palette[i] = reader.readVarInt();
            }
        }
        int dataLength = reader.readVarInt();
        long[] data = reader.readLongArray(dataLength);
        int[] unpacked = unpack(data, bits, size);
        if (palette == null) {
            return unpacked;
        }
        int[] values = new int[size];
        for (int i = 0; i < size; i++) {
            values[i] = palette[unpacked[i]];
        }
        return values;
    }

    private static int[] filled(int value) {
        return filled(value, 4096);
    }

    private static int[] filled(int value, int size) {
        int[] values = new int[size];
        java.util.Arrays.fill(values, value);
        return values;
    }

    private static int index(int x, int y, int z) {
        return ((y & 0xF) << 8) | (z << 4) | x;
    }

    private static int pack(int x, int y, int z) {
        return (x & 0xF) | ((z & 0xF) << 4) | ((y + 2048) << 8);
    }

    private static long[] pack(int[] values, int bits) {
        int valuesPerLong = 64 / bits;
        long mask = (1L << bits) - 1L;
        long[] data = new long[(values.length + valuesPerLong - 1) / valuesPerLong];
        for (int i = 0; i < values.length; i++) {
            data[i / valuesPerLong] |= ((long) values[i] & mask) << ((i % valuesPerLong) * bits);
        }
        return data;
    }

    private static int[] unpack(long[] data, int bits, int size) {
        int valuesPerLong = 64 / bits;
        long mask = (1L << bits) - 1L;
        int[] values = new int[size];
        for (int i = 0; i < size; i++) {
            values[i] = (int) ((data[i / valuesPerLong] >>> ((i % valuesPerLong) * bits)) & mask);
        }
        return values;
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        int current = value;
        do {
            int temp = current & 0x7F;
            current >>>= 7;
            if (current != 0) {
                temp |= 0x80;
            }
            out.write(temp);
        } while (current != 0);
    }

    private static void writeLong(ByteArrayOutputStream out, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            out.write((int) (value >>> shift) & 0xFF);
        }
    }

    private static final class Reader {
        private final byte[] bytes;
        private int index;

        Reader(byte[] bytes) {
            this.bytes = bytes;
        }

        int readUnsignedByte() {
            return bytes[index++] & 0xFF;
        }

        int readVarInt() {
            int value = 0;
            int position = 0;
            while (true) {
                int current = readUnsignedByte();
                value |= (current & 0x7F) << position;
                if ((current & 0x80) == 0) {
                    return value;
                }
                position += 7;
            }
        }

        long[] readLongArray(int length) {
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
}
