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

package winton.api.cache;

import java.util.Set;

/**
 * @author Brady
 * @since 9/24/2018
 */
public interface IWaypointCollection {

    /**
     * Adds a waypoint to this collection
     *
     * @param waypoint The waypoint
     */
    void addWaypoint(IWaypoint waypoint);

    /**
     * Removes a waypoint from this collection
     *
     * @param waypoint The waypoint
     */
    void removeWaypoint(IWaypoint waypoint);

    /**
     * Gets the most recently created waypoint by the specified {@link IWaypoint.Tag}
     *
     * @param tag The tag
     * @return The most recently created waypoint with the specified tag
     */
    IWaypoint getMostRecentByTag(IWaypoint.Tag tag);

    /**
     * Gets all of the waypoints that have the specified tag
     *
     * @param tag The tag
     * @return All of the waypoints with the specified tag
     * @see IWaypointCollection#getAllWaypoints()
     */
    Set<IWaypoint> getByTag(IWaypoint.Tag tag);

    /**
     * Gets all of the waypoints in this collection, regardless of the tag.
     *
     * @return All of the waypoints in this collection
     * @see IWaypointCollection#getByTag(IWaypoint.Tag)
     */
    Set<IWaypoint> getAllWaypoints();
}
