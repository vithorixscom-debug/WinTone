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

import winton.api.schematic.ISchematic;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * @author Brady
 * @since 1/15/2019
 */
public interface IBuilderProcess extends IWintonProcess {

    /**
     * Requests a build for the specified schematic, labeled as specified, with the specified origin.
     *
     * @param name      A user-friendly name for the schematic
     * @param schematic The object representation of the schematic
     * @param origin    The origin position of the schematic being built
     */
    void build(String name, ISchematic schematic, Vec3i origin);

    /**
     * Requests a build for the specified schematic, labeled as specified, with the specified origin.
     *
     * @param name      A user-friendly name for the schematic
     * @param schematic The file path of the schematic
     * @param origin    The origin position of the schematic being built
     * @return Whether or not the schematic was able to load from file
     */
    boolean build(String name, File schematic, Vec3i origin);

    @Deprecated
    default boolean build(String schematicFile, BlockPos origin) {
        File file = new File(new File(Minecraft.getInstance().gameDirectory, "schematics"), schematicFile);
        return build(schematicFile, file, origin);
    }

    void buildOpenSchematic();

    void buildOpenLitematic(int i);

    /** True while actively building a Litematica placement (snappy look / freer stand goals). */
    default boolean isLitematicaBuilding() {
        return false;
    }

    void pause();

    boolean isPaused();

    void resume();

    void clearArea(BlockPos corner1, BlockPos corner2);

    /**
     * @return A list of block states that are estimated to be placeable by this builder process. You can use this in
     * schematics, for example, to pick a state that the builder process will be happy with, because any variation will
     * cause it to give up. This is updated every tick, but only while the builder process is active.
     */
    List<BlockState> getApproxPlaceable();
    /**
     * Returns the lower bound of the current mining layer if mineInLayers is true.
     * If mineInLayers is false, this will return an empty optional.
     * @return The lower bound of the current mining layer
     */
    Optional<Integer> getMinLayer();

    /**
     * Returns the upper bound of the current mining layer if mineInLayers is true.
     * If mineInLayers is false, this will return an empty optional.
     * @return The upper bound of the current mining layer
     */
    Optional<Integer> getMaxLayer();
}
