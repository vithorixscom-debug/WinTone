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

package fi.dy.masa.litematica.schematic.placement;

import java.util.List;

public class SchematicPlacementManager {

    //in case of a java.lang.NoSuchMethodError try change the name of this method to getAllSchematicPlacements()
    //there are inconsistencies in the litematica mod about the naming of this method
    public List<SchematicPlacement> getAllSchematicsPlacements() {
        throw new LinkageError();
    }
}
