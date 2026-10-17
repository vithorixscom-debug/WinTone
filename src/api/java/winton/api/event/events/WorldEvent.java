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

package winton.api.event.events;

import winton.api.event.events.type.EventState;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * @author Brady
 * @since 8/4/2018
 */
public final class WorldEvent {

    /**
     * The new world that is being loaded. {@code null} if being unloaded.
     */
    private final ClientLevel world;

    /**
     * The state of the event
     */
    private final EventState state;

    public WorldEvent(ClientLevel world, EventState state) {
        this.world = world;
        this.state = state;
    }

    /**
     * @return The new world that is being loaded. {@code null} if being unloaded.
     */
    public final ClientLevel getWorld() {
        return this.world;
    }

    /**
     * @return The state of the event
     */
    public final EventState getState() {
        return this.state;
    }
}
