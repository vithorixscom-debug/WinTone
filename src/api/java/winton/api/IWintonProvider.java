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

package winton.api;

import winton.api.cache.IWorldScanner;
import winton.api.command.ICommand;
import winton.api.command.ICommandSystem;
import winton.api.schematic.ISchematicSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;
import java.util.Objects;

/**
 * Provides the present {@link IWinton} instances, as well as non-winton instance related APIs.
 *
 * @author leijurv
 */
public interface IWintonProvider {

    /**
     * Returns the primary {@link IWinton} instance. This instance is persistent, and
     * is represented by the local player that is created by the game itself, not a "bot"
     * player through Winton.
     *
     * @return The primary {@link IWinton} instance.
     */
    IWinton getPrimaryWinton();

    /**
     * Returns all of the active {@link IWinton} instances. This includes the local one
     * returned by {@link #getPrimaryWinton()}.
     *
     * @return All active {@link IWinton} instances.
     * @see #getWintonForPlayer(LocalPlayer)
     */
    List<IWinton> getAllWintons();

    /**
     * Provides the {@link IWinton} instance for a given {@link LocalPlayer}.
     *
     * @param player The player
     * @return The {@link IWinton} instance.
     */
    default IWinton getWintonForPlayer(LocalPlayer player) {
        for (IWinton winton : this.getAllWintons()) {
            if (Objects.equals(player, winton.getPlayerContext().player())) {
                return winton;
            }
        }
        return null;
    }

    /**
     * Provides the {@link IWinton} instance for a given {@link Minecraft}.
     *
     * @param minecraft The minecraft
     * @return The {@link IWinton} instance.
     */
    default IWinton getWintonForMinecraft(Minecraft minecraft) {
        for (IWinton winton : this.getAllWintons()) {
            if (Objects.equals(minecraft, winton.getPlayerContext().minecraft())) {
                return winton;
            }
        }
        return null;
    }

    /**
     * Provides the {@link IWinton} instance for the player with the specified connection.
     *
     * @param connection The connection
     * @return The {@link IWinton} instance.
     */
    default IWinton getWintonForConnection(ClientPacketListener connection) {
        for (IWinton winton : this.getAllWintons()) {
            final LocalPlayer player = winton.getPlayerContext().player();
            if (player != null && player.connection == connection) {
                return winton;
            }
        }
        return null;
    }

    /**
     * Creates and registers a new {@link IWinton} instance using the specified {@link Minecraft}. The existing
     * instance is returned if already registered.
     *
     * @param minecraft The minecraft
     * @return The {@link IWinton} instance
     */
    IWinton createWinton(Minecraft minecraft);

    /**
     * Destroys and removes the specified {@link IWinton} instance. If the specified instance is the
     * {@link #getPrimaryWinton() primary winton}, this operation has no effect and will return {@code false}.
     *
     * @param winton The winton instance to remove
     * @return Whether the winton instance was removed
     */
    boolean destroyWinton(IWinton winton);

    /**
     * Returns the {@link IWorldScanner} instance. This is not a type returned by
     * {@link IWinton} implementation, because it is not linked with {@link IWinton}.
     *
     * @return The {@link IWorldScanner} instance.
     */
    IWorldScanner getWorldScanner();

    /**
     * Returns the {@link ICommandSystem} instance. This is not bound to a specific {@link IWinton}
     * instance because {@link ICommandSystem} itself controls global behavior for {@link ICommand}s.
     *
     * @return The {@link ICommandSystem} instance.
     */
    ICommandSystem getCommandSystem();

    /**
     * @return The {@link ISchematicSystem} instance.
     */
    ISchematicSystem getSchematicSystem();
}
