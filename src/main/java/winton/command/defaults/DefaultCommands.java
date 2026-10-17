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

package winton.command.defaults;

import winton.api.IWinton;
import winton.api.command.ICommand;

import java.util.*;

public final class DefaultCommands {

    private DefaultCommands() {
    }

    public static List<ICommand> createAll(IWinton winton) {
        Objects.requireNonNull(winton);
        List<ICommand> commands = new ArrayList<>(Arrays.asList(
                new HelpCommand(winton),
                new SetCommand(winton),
                new CommandAlias(winton, Arrays.asList("modified", "mod", "winton", "modifiedsettings"), "List modified settings", "set modified"),
                new CommandAlias(winton, "reset", "Reset all settings or just one", "set reset"),
                new GoalCommand(winton),
                new GotoCommand(winton),
                new PathCommand(winton),
                new ProcCommand(winton),
                new ETACommand(winton),
                new VersionCommand(winton),
                new RepackCommand(winton),
                new BuildCommand(winton),
                //new SchematicaCommand(winton),
                new LitematicaCommand(winton),
                new ComeCommand(winton),
                new AxisCommand(winton),
                new ForceCancelCommand(winton),
                new GcCommand(winton),
                new InvertCommand(winton),
                new TunnelCommand(winton),
                new RenderCommand(winton),
                new FarmCommand(winton),
                new FollowCommand(winton),
                new PickupCommand(winton),
                new ExploreFilterCommand(winton),
                new ReloadAllCommand(winton),
                new SaveAllCommand(winton),
                new ExploreCommand(winton),
                new BlacklistCommand(winton),
                new FindCommand(winton),
                new MineCommand(winton),
                new ClickCommand(winton),
                new SurfaceCommand(winton),
                new ThisWayCommand(winton),
                new WaypointsCommand(winton),
                new CommandAlias(winton, "sethome", "Sets your home waypoint", "waypoints save home"),
                new CommandAlias(winton, "home", "Path to your home waypoint", "waypoints goto home"),
                new SelCommand(winton),
                new ElytraCommand(winton),
                new ChestSortCommand(winton)
        ));
        ExecutionControlCommands prc = new ExecutionControlCommands(winton);
        commands.add(prc.pauseCommand);
        commands.add(prc.resumeCommand);
        commands.add(prc.pausedCommand);
        commands.add(prc.cancelCommand);
        return Collections.unmodifiableList(commands);
    }
}
