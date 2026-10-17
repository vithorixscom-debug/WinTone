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

package winton.api.event.listener;

/**
 * A type of {@link IGameEventListener} that can have additional listeners
 * registered so that they receive the events that are dispatched to this
 * listener.
 *
 * @author Brady
 * @since 11/14/2018
 */
public interface IEventBus extends IGameEventListener {

    /**
     * Registers the specified {@link IGameEventListener} to this event bus
     *
     * @param listener The listener
     */
    void registerEventListener(IGameEventListener listener);
}
