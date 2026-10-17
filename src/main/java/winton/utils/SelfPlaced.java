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

package winton.utils;

import winton.api.utils.BetterBlockPos;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;

/** Blocks this client placed. Place-only mode may break only these. */
public final class SelfPlaced {

    private static final LongOpenHashSet PLACED = new LongOpenHashSet();

    private SelfPlaced() {
    }

    public static void mark(BlockPos pos) {
        if (pos == null) {
            return;
        }
        synchronized (PLACED) {
            PLACED.add(BetterBlockPos.longHash(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    public static boolean contains(int x, int y, int z) {
        synchronized (PLACED) {
            return PLACED.contains(BetterBlockPos.longHash(x, y, z));
        }
    }

    public static void clear() {
        synchronized (PLACED) {
            PLACED.clear();
        }
    }
}
