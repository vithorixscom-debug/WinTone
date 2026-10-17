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

package winton;

import winton.api.IWinton;
import winton.api.IWintonProvider;
import winton.api.cache.IWorldScanner;
import winton.api.command.ICommandSystem;
import winton.api.schematic.ISchematicSystem;
import winton.cache.FasterWorldScanner;
import winton.command.CommandSystem;
import winton.command.ExampleWintonControl;
import winton.utils.schematic.SchematicSystem;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Brady
 * @since 9/29/2018
 */
public final class WintonProvider implements IWintonProvider {

    private final List<IWinton> all;
    private final List<IWinton> allView;

    public WintonProvider() {
        this.all = new CopyOnWriteArrayList<>();
        this.allView = Collections.unmodifiableList(this.all);

        // Setup chat control, just for the primary instance
        final Winton primary = (Winton) this.createWinton(Minecraft.getInstance());
        primary.registerBehavior(ExampleWintonControl::new);
    }

    @Override
    public IWinton getPrimaryWinton() {
        return this.all.get(0);
    }

    @Override
    public List<IWinton> getAllWintons() {
        return this.allView;
    }

    @Override
    public synchronized IWinton createWinton(Minecraft minecraft) {
        IWinton winton = this.getWintonForMinecraft(minecraft);
        if (winton == null) {
            this.all.add(winton = new Winton(minecraft));
        }
        return winton;
    }

    @Override
    public synchronized boolean destroyWinton(IWinton winton) {
        return winton != this.getPrimaryWinton() && this.all.remove(winton);
    }

    @Override
    public IWorldScanner getWorldScanner() {
        return FasterWorldScanner.INSTANCE;
    }

    @Override
    public ICommandSystem getCommandSystem() {
        return CommandSystem.INSTANCE;
    }

    @Override
    public ISchematicSystem getSchematicSystem() {
        return SchematicSystem.INSTANCE;
    }
}
