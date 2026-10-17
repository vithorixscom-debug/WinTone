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

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

/**
 * @author Brady
 * @since 9/23/2018
 */
public interface IFollowProcess extends IWintonProcess {

    /**
     * Set the follow target to any entities matching this predicate
     *
     * @param filter the predicate
     */
    void follow(Predicate<Entity> filter);

    /**
     * Try to pick up any items matching this predicate
     *
     * @param filter the predicate
     */
    void pickup(Predicate<ItemStack> filter);

    /**
     * @return The entities that are currently being followed. null if not currently following, empty if nothing matches the predicate
     */
    List<Entity> following();

    Predicate<Entity> currentFilter();

    /**
     * Cancels the follow behavior, this will clear the current follow target.
     */
    default void cancel() {
        onLostControl();
    }
}
