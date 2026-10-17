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

import winton.api.command.argument.IArgConsumer;
import winton.api.command.exception.CommandException;
import winton.api.utils.BetterBlockPos;

import java.util.stream.Stream;

public enum RelativeBlockPos implements IDatatypePost<BetterBlockPos, BetterBlockPos> {
    INSTANCE;

    @Override
    public BetterBlockPos apply(IDatatypeContext ctx, BetterBlockPos origin) throws CommandException {
        if (origin == null) {
            origin = BetterBlockPos.ORIGIN;
        }

        final IArgConsumer consumer = ctx.getConsumer();
        return new BetterBlockPos(
                consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.x),
                consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.y),
                consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double) origin.z)
        );
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        final IArgConsumer consumer = ctx.getConsumer();
        if (consumer.hasAny() && !consumer.has(4)) {
            while (consumer.has(2)) {
                if (consumer.peekDatatypeOrNull(RelativeCoordinate.INSTANCE) == null) {
                    break;
                }
                consumer.get();
            }
            return consumer.tabCompleteDatatype(RelativeCoordinate.INSTANCE);
        }
        return Stream.empty();
    }
}
