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

import net.minecraft.core.BlockPos;

public interface IFarmProcess extends IWintonProcess {

    /**
     * Begin to search for crops to farm with in specified aria
     * from specified location.
     *
     * @param range The distance from center to farm from
     * @param pos   The center position to base the range from
     */
    void farm(int range, BlockPos pos);

    /**
     * Begin to search for nearby crops to farm.
     */
    default void farm() {farm(0, null);}

    /**
     * Begin to search for crops to farm with in specified aria
     * from the position the command was executed.
     *
     * @param range The distance to search for crops to farm
     */
    default void farm(int range) {farm(range, null);}
}
