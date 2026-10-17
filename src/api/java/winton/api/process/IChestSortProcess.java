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

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

import java.util.Set;

/**
 * Sorts items across chests in a radius or selection.
 * <p>
 * Category chests keep their item types (learned from contents, or set via {@link #markChest}).
 * Misplaced stacks are moved into matching category chests; leftovers go into empty dump chests.
 */
public interface IChestSortProcess extends IWintonProcess {

	/** Sort all chests within {@code radius} of the player. */
	void sortRadius(int radius);

	/** Sort all chests inside the current world selection (pos1/pos2). */
	void sortSelection();

	/**
	 * Mark the looked-at chest as a category destination.
	 * If {@code items} is empty, uses the held item; otherwise those item types.
	 */
	boolean markLookChest(Set<Item> items);

	/** Clear all manually marked category chests. */
	void clearMarks();

	Set<BlockPos> getMarkedChests();
}
