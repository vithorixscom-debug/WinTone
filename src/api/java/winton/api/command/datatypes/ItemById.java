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

package winton.api.command.datatypes;

import winton.api.command.exception.CommandException;
import winton.api.command.helpers.TabCompleteHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.stream.Stream;

public enum ItemById implements IDatatypeFor<Item> {
    INSTANCE;

    @Override
    public Item get(IDatatypeContext ctx) throws CommandException {
        Identifier id = Identifier.parse(ctx.getConsumer().getString());
        Item item;
        if ((item = BuiltInRegistries.ITEM.getOptional(id).orElse(null)) == null) {
            throw new IllegalArgumentException("No item found by that id");
        }
        return item;
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        return new TabCompleteHelper()
                .append(
                        BuiltInRegistries.ITEM.keySet()
                                .stream()
                                .map(Identifier::toString)
                )
                .filterPrefixNamespaced(ctx.getConsumer().getString())
                .sortAlphabetically()
                .stream();
    }
}
