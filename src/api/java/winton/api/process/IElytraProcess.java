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

package winton.api.process;

import winton.api.pathing.goals.Goal;
import winton.api.pathing.goals.GoalXZ;
import winton.api.utils.BetterBlockPos;
import net.minecraft.core.BlockPos;

import java.util.List;

public interface IElytraProcess extends IWintonProcess {

    void repackChunks();

    /**
     * @return Where it is currently flying to, null if not active
     */
    BlockPos currentDestination();

    /**
     * @return Current active path, empty if not active or no path has been calculated yet
     */
    List<BetterBlockPos> getPath();

    void pathTo(BlockPos destination);

    void pathTo(Goal destination);

    /**
     * Fly to X/Z; Y is the highest surface at that column (overworld/end).
     */
    default void pathToXZ(int x, int z) {
        pathTo(new GoalXZ(x, z));
    }

    /**
     * Resets the state of the process but will maintain the same destination and will try to keep flying
     */
    void resetState();

    /**
     * @return {@code true} if the native library loaded and elytra is actually usable
     */
    boolean isLoaded();

    /*
     * FOR INTERNAL USE ONLY. MAY BE REMOVED AT ANY TIME.
     */
    boolean isSafeToCancel();
}
