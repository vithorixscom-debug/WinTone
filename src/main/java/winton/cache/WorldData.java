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

import winton.Winton;
import winton.api.cache.ICachedWorld;
import winton.api.cache.IWaypointCollection;
import winton.api.cache.IWorldData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import java.nio.file.Path;

/**
 * Data about a world, from winton's point of view. Includes cached chunks, waypoints, and map data.
 *
 * @author leijurv
 */
public class WorldData implements IWorldData {

    public final CachedWorld cache;
    private final WaypointCollection waypoints;
    //public final MapData map;
    public final Path directory;
    public final DimensionType dimension;

    WorldData(Path directory, DimensionType dimension, ResourceKey<Level> dimensionId) {
        this.directory = directory;
        this.cache = new CachedWorld(directory.resolve("cache"), dimension, dimensionId);
        this.waypoints = new WaypointCollection(directory.resolve("waypoints"));
        this.dimension = dimension;
    }

    public void onClose() {
        cache.close();
        Winton.getExecutor().execute(() -> {
            System.out.println("Started saving the world in a new thread");
            cache.save();
        });
    }

    @Override
    public ICachedWorld getCachedWorld() {
        return this.cache;
    }

    @Override
    public IWaypointCollection getWaypoints() {
        return this.waypoints;
    }
}
