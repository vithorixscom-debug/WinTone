/*
 * This file is part of Winton, a modified version of Baritone.
 * Copyright (c) Baritone contributors.
 * Copyright (c) WinTone contributors, for modifications.
 *
 * Winton is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Winton is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Winton.  If not, see <https://www.gnu.org/licenses/>.
 */

package winton.cache;

import winton.api.utils.BlockUtils;
import winton.pathing.movement.MovementHelper;
import winton.utils.pathing.PathingBlockType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static winton.utils.BlockStateInterface.getFromChunk;

/**
 * @author Brady
 * @since 8/3/2018
 */
public final class ChunkPacker {

    private ChunkPacker() {}

    /**
     * @return packed chunk, or {@code null} if packing failed (caller must not publish partial data)
     */
    public static CachedChunk pack(LevelChunk chunk) {
        Map<String, List<BlockPos>> specialBlocks = new HashMap<>();
        final int height = chunk.getLevel().dimensionType().height();
        BitSet bitSet = new BitSet(CachedChunk.size(height));
        try {
            LevelChunkSection[] chunkInternalStorageArray = chunk.getSections();
            for (int y0 = 0; y0 < height / 16; y0++) {
                LevelChunkSection extendedblockstorage = chunkInternalStorageArray[y0];
                if (extendedblockstorage == null) {
                    // any 16x16x16 area that's all air will have null storage
                    continue;
                }
                PalettedContainer<BlockState> bsc = extendedblockstorage.getStates();
                int yReal = y0 << 4;
                for (int y1 = 0; y1 < 16; y1++) {
                    int y = y1 | yReal;
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            int index = CachedChunk.getPositionIndex(x, y, z);
                            BlockState state = bsc.get(x, y1, z);
                            boolean[] bits = getPathingBlockType(state, chunk, x, y + chunk.getMinY(), z).getBits();
                            bitSet.set(index, bits[0]);
                            bitSet.set(index + 1, bits[1]);
                            Block block = state.getBlock();
                            if (CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(block)) {
                                String name = BlockUtils.blockToString(block);
                                // world Y — matches getAbsoluteBlocks / special map keys
                                specialBlocks.computeIfAbsent(name, b -> new ArrayList<>()).add(new BlockPos(x, y + chunk.getMinY(), z));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Do not publish a half-packed BitSet as a CachedChunk
            return null;
        }
        BlockState[] blocks = new BlockState[256];

        // get top block in columns
        // @formatter:off
        for (int z = 0; z < 16; z++) {
            https://www.ibm.com/developerworks/library/j-perry-writing-good-java-code/index.html
            for (int x = 0; x < 16; x++) {
                for (int y = height - 1; y >= 0; y--) {
                    int index = CachedChunk.getPositionIndex(x, y, z);
                    if (bitSet.get(index) || bitSet.get(index + 1)) {
                        blocks[z << 4 | x] = getFromChunk(chunk, x, y, z);
                        continue https;
                    }
                }
                blocks[z << 4 | x] = Blocks.AIR.defaultBlockState();
            }
        }
        // @formatter:on
        return new CachedChunk(chunk.getPos().x, chunk.getPos().z, height, bitSet, blocks, specialBlocks, System.currentTimeMillis());
    }

    private static PathingBlockType getPathingBlockType(BlockState state, LevelChunk chunk, int x, int y, int z) {
        Block block = state.getBlock();
        if (MovementHelper.isWater(state)) {
            if (MovementHelper.possiblyFlowing(state)) {
                return PathingBlockType.AVOID;
            }
            int adjY = y - chunk.getLevel().dimensionType().minY();
            if (
                    (x != 15 && MovementHelper.possiblyFlowing(getFromChunk(chunk, x + 1, adjY, z)))
                            || (x != 0 && MovementHelper.possiblyFlowing(getFromChunk(chunk, x - 1, adjY, z)))
                            || (z != 15 && MovementHelper.possiblyFlowing(getFromChunk(chunk, x, adjY, z + 1)))
                            || (z != 0 && MovementHelper.possiblyFlowing(getFromChunk(chunk, x, adjY, z - 1)))
            ) {
                return PathingBlockType.AVOID;
            }
            if (x == 0 || x == 15 || z == 0 || z == 15) {
                Vec3 flow = state.getFluidState().getFlow(chunk.getLevel(), new BlockPos(x + (chunk.getPos().x << 4), y, z + (chunk.getPos().z << 4)));
                if (flow.x != 0.0 || flow.z != 0.0) {
                    return PathingBlockType.WATER;
                }
                return PathingBlockType.AVOID;
            }
            return PathingBlockType.WATER;
        }

        if (MovementHelper.avoidWalkingInto(state) || MovementHelper.isBottomSlab(state)) {
            return PathingBlockType.AVOID;
        }
        if (block instanceof AirBlock || block instanceof TallGrassBlock || block instanceof DoublePlantBlock || block instanceof FlowerBlock) {
            return PathingBlockType.AIR;
        }

        return PathingBlockType.SOLID;
    }

    public static BlockState pathingTypeToBlock(PathingBlockType type, DimensionType dimension, ResourceKey<Level> dimensionId) {
        switch (type) {
            case AIR:
                return Blocks.AIR.defaultBlockState();
            case WATER:
                return Blocks.WATER.defaultBlockState();
            case AVOID:
                return Blocks.LAVA.defaultBlockState();
            case SOLID:
                if (dimensionId == Level.NETHER) {
                    return Blocks.NETHERRACK.defaultBlockState();
                } else if (dimensionId == Level.END) {
                    return Blocks.END_STONE.defaultBlockState();
                } else {
                    return Blocks.STONE.defaultBlockState();
                }
            default:
                return null;
        }
    }
}
