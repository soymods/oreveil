package com.soymods.oreveil.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public final class BlockNeighborhoods {
    private static final BlockFace[] CARDINAL_FACES = {
        BlockFace.UP,
        BlockFace.DOWN,
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST,
    };

    private BlockNeighborhoods() {
    }

    public static List<Block> cardinalNeighbors(Block block) {
        return List.of(
            block.getRelative(CARDINAL_FACES[0]),
            block.getRelative(CARDINAL_FACES[1]),
            block.getRelative(CARDINAL_FACES[2]),
            block.getRelative(CARDINAL_FACES[3]),
            block.getRelative(CARDINAL_FACES[4]),
            block.getRelative(CARDINAL_FACES[5])
        );
    }

    public static Set<Block> cardinalNeighborhood(Block origin, int radius) {
        Set<Block> visited = new LinkedHashSet<>();
        Set<Block> frontier = new LinkedHashSet<>();
        visited.add(origin);
        frontier.add(origin);

        for (int step = 0; step < radius; step++) {
            Set<Block> next = new LinkedHashSet<>();
            for (Block block : frontier) {
                for (Block neighbor : cardinalNeighbors(block)) {
                    if (visited.add(neighbor)) {
                        next.add(neighbor);
                    }
                }
            }
            frontier = next;
        }

        return visited;
    }
}
