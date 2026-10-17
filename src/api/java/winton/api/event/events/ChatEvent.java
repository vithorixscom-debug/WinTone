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

import winton.api.event.events.type.Cancellable;

/**
 * @author Brady
 * @since 8/1/2018
 */
public final class ChatEvent extends Cancellable {

    /**
     * The message being sent
     */
    private final String message;

    public ChatEvent(String message) {
        this.message = message;
    }

    /**
     * @return The message being sent
     */
    public final String getMessage() {
        return this.message;
    }
}
