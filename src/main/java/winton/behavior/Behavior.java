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

package winton.behavior;

import winton.Winton;
import winton.api.behavior.IBehavior;
import winton.api.utils.IPlayerContext;

/**
 * A type of game event listener that is given {@link Winton} instance context.
 *
 * @author Brady
 * @since 8/1/2018
 */
public class Behavior implements IBehavior {

    public final Winton winton;
    public final IPlayerContext ctx;

    protected Behavior(Winton winton) {
        this.winton = winton;
        this.ctx = winton.getPlayerContext();
    }
}
