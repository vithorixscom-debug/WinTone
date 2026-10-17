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
import winton.api.cache.IWaypoint;
import winton.api.cache.Waypoint;
import winton.api.event.events.BlockInteractEvent;
import winton.api.utils.BetterBlockPos;
import winton.api.utils.Helper;
import winton.utils.BlockStateInterface;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.Set;

import static winton.api.command.IWintonChatControl.FORCE_COMMAND_PREFIX;

public class WaypointBehavior extends Behavior {


    public WaypointBehavior(Winton winton) {
        super(winton);
    }

    @Override
    public void onBlockInteract(BlockInteractEvent event) {
        if (!Winton.settings().doBedWaypoints.value)
            return;
        if (event.getType() == BlockInteractEvent.Type.USE) {
            BetterBlockPos pos = BetterBlockPos.from(event.getPos());
            BlockState state = BlockStateInterface.get(ctx, pos);
            if (state.getBlock() instanceof BedBlock) {
                if (state.getValue(BedBlock.PART) == BedPart.FOOT) {
                    pos = pos.relative(state.getValue(BedBlock.FACING));
                }
                Set<IWaypoint> waypoints = winton.getWorldProvider().getCurrentWorld().getWaypoints().getByTag(IWaypoint.Tag.BED);
                boolean exists = waypoints.stream().map(IWaypoint::getLocation).filter(pos::equals).findFirst().isPresent();
                if (!exists) {
                    winton.getWorldProvider().getCurrentWorld().getWaypoints().addWaypoint(new Waypoint("bed", Waypoint.Tag.BED, pos));
                }
            }
        }
    }

    @Override
    public void onPlayerDeath() {
        if (!Winton.settings().doDeathWaypoints.value)
            return;
        Waypoint deathWaypoint = new Waypoint("death", Waypoint.Tag.DEATH, ctx.playerFeet());
        winton.getWorldProvider().getCurrentWorld().getWaypoints().addWaypoint(deathWaypoint);
        MutableComponent component = Component.literal("Death position saved.");
        component.setStyle(component.getStyle()
                .withColor(ChatFormatting.WHITE)
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("Click to goto death")
                ))
                .withClickEvent(new ClickEvent.RunCommand(
                        String.format(
                                "%s%s goto %s @ %d",
                                FORCE_COMMAND_PREFIX,
                                "wp",
                                deathWaypoint.getTag().getName(),
                                deathWaypoint.getCreationTimestamp()
                        )
                )));
        Helper.HELPER.logDirect(component);
    }

}
