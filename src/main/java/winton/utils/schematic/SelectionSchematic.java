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

package winton.utils.schematic;

import winton.api.schematic.ISchematic;
import winton.api.schematic.MaskSchematic;
import winton.api.selection.ISelection;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;

import java.util.stream.Stream;

public class SelectionSchematic extends MaskSchematic {

    private final ISelection[] selections;

    public SelectionSchematic(ISchematic schematic, Vec3i origin, ISelection[] selections) {
        super(schematic);
        this.selections = Stream.of(selections).map(
                        sel -> sel
                                .shift(Direction.WEST, origin.getX())
                                .shift(Direction.DOWN, origin.getY())
                                .shift(Direction.NORTH, origin.getZ()))
                .toArray(ISelection[]::new);
    }

    @Override
    protected boolean partOfMask(int x, int y, int z, BlockState currentState) {
        for (ISelection selection : selections) {
            if (x >= selection.min().x && y >= selection.min().y && z >= selection.min().z
                    && x <= selection.max().x && y <= selection.max().y && z <= selection.max().z) {
                return true;
            }
        }
        return false;
    }
}
