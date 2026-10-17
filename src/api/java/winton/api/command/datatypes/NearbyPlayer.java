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

import winton.api.IWinton;
import winton.api.command.exception.CommandException;
import winton.api.command.helpers.TabCompleteHelper;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * An {@link IDatatype} used to resolve nearby players, those within
 * render distance of the target {@link IWinton} instance.
 */
public enum NearbyPlayer implements IDatatypeFor<Player> {
    INSTANCE;

    @Override
    public Player get(IDatatypeContext ctx) throws CommandException {
        final String username = ctx.getConsumer().getString();
        return getPlayers(ctx).stream()
                .filter(s -> s.getName().getString().equalsIgnoreCase(username))
                .findFirst().orElse(null);
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        return new TabCompleteHelper()
                .append(getPlayers(ctx).stream().map(Player::getName).map(Component::getString))
                .filterPrefix(ctx.getConsumer().getString())
                .sortAlphabetically()
                .stream();
    }

    private static List<? extends Player> getPlayers(IDatatypeContext ctx) {
        return ctx.getWinton().getPlayerContext().world().players();
    }
}
