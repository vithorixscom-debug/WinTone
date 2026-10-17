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

package winton.process.elytra;

import dev.babbaj.pathfinder.NetherPathfinder;
import dev.babbaj.pathfinder.Octree;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * @author Brady
 */
public final class BlockStateOctreeInterface {

    private final NetherPathfinderContext context;
    private final long contextPtr;
    private final int minY;
    transient long chunkPtr;

    // Guarantee that the first lookup will fetch the context by setting MAX_VALUE
    private int prevChunkX = Integer.MAX_VALUE;
    private int prevChunkZ = Integer.MAX_VALUE;

    public BlockStateOctreeInterface(final NetherPathfinderContext context) {
        this.context = context;
        this.contextPtr = context.context;
        this.minY = context.minY;
    }

    public boolean get0(final int x, final int y, final int z) {
        final int adjustedY = y - this.minY;
        if (adjustedY < 0 || adjustedY > 383) {
            return false;
        }
        final int chunkX = x >> 4;
        final int chunkZ = z >> 4;
        if (this.chunkPtr == 0 | ((chunkX ^ this.prevChunkX) | (chunkZ ^ this.prevChunkZ)) != 0) {
            this.prevChunkX = chunkX;
            this.prevChunkZ = chunkZ;
            this.chunkPtr = NetherPathfinder.getChunkOrDefault(this.contextPtr, chunkX, chunkZ, true);
        }
        return Octree.getBlock(this.chunkPtr, x & 0xF, adjustedY, z & 0xF);
    }
}
