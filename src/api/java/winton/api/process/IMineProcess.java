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

import winton.api.utils.BlockOptionalMeta;
import winton.api.utils.BlockOptionalMetaLookup;
import java.util.stream.Stream;
import net.minecraft.world.level.block.Block;

/**
 * @author Brady
 * @since 9/23/2018
 */
public interface IMineProcess extends IWintonProcess {

    /**
     * Begin to search for and mine the specified blocks until
     * the number of specified items to get from the blocks that
     * are mined.
     *
     * @param quantity The total number of items to get
     * @param blocks   The blocks to mine
     */
    void mineByName(int quantity, String... blocks);

    /**
     * Begin to search for and mine the specified blocks until
     * the number of specified items to get from the blocks that
     * are mined. This is based on the first target block to mine.
     *
     * @param quantity The number of items to get from blocks mined
     * @param filter   The blocks to mine
     */
    void mine(int quantity, BlockOptionalMetaLookup filter);

    /**
     * Begin to search for and mine the specified blocks.
     *
     * @param filter The blocks to mine
     */
    default void mine(BlockOptionalMetaLookup filter) {
        mine(0, filter);
    }

    /**
     * Begin to search for and mine the specified blocks.
     *
     * @param blocks The blocks to mine
     */
    default void mineByName(String... blocks) {
        mineByName(0, blocks);
    }

    /**
     * Begin to search for and mine the specified blocks.
     *
     * @param boms The blocks to mine
     */
    default void mine(int quantity, BlockOptionalMeta... boms) {
        mine(quantity, new BlockOptionalMetaLookup(boms));
    }

    /**
     * Begin to search for and mine the specified blocks.
     *
     * @param boms The blocks to mine
     */
    default void mine(BlockOptionalMeta... boms) {
        mine(0, boms);
    }

    /**
     * Begin to search for and mine the specified blocks.
     *
     * @param quantity The total number of items to get
     * @param blocks   The blocks to mine
     */
    default void mine(int quantity, Block... blocks) {
        mine(quantity, new BlockOptionalMetaLookup(
                Stream.of(blocks)
                        .map(BlockOptionalMeta::new)
                        .toArray(BlockOptionalMeta[]::new)
        ));
    }

    /**
     * Begin to search for and mine the specified blocks.
     *
     * @param blocks The blocks to mine
     */
    default void mine(Block... blocks) {
        mine(0, blocks);
    }

    /**
     * Cancels the current mining task
     */
    default void cancel() {
        onLostControl();
    }
}
