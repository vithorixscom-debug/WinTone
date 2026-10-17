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

package winton.process;

import winton.Winton;
import winton.api.pathing.goals.Goal;
import winton.api.process.ICustomGoalProcess;
import winton.api.process.PathingCommand;
import winton.api.process.PathingCommandType;
import winton.utils.WintonProcessHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/**
 * As set by ExampleWintonControl or something idk
 *
 * @author leijurv
 */
public final class CustomGoalProcess extends WintonProcessHelper implements ICustomGoalProcess {

    /**
     * The current goal
     */
    private Goal goal;

    /**
     * The most recent goal. Not invalidated upon {@link #onLostControl()}
     */
    private Goal mostRecentGoal;

    /**
     * The current process state.
     *
     * @see State
     */
    private State state;

    public CustomGoalProcess(Winton winton) {
        super(winton);
    }

    @Override
    public void setGoal(Goal goal) {
        this.goal = goal;
        this.mostRecentGoal = goal;
        if (winton.getElytraProcess().isActive()) {
            try {
                winton.getElytraProcess().pathTo(goal);
            } catch (IllegalArgumentException e) {
                logDirect("Failed to update elytra goal because: " + e.getMessage(), ChatFormatting.RED);
            }
        }
        if (this.state == State.NONE) {
            this.state = State.GOAL_SET;
        }
        if (this.state == State.EXECUTING) {
            this.state = State.PATH_REQUESTED;
        }
    }

    @Override
    public void path() {
        this.state = State.PATH_REQUESTED;
    }

    @Override
    public Goal getGoal() {
        return this.goal;
    }

    @Override
    public Goal mostRecentGoal() {
        return this.mostRecentGoal;
    }

    @Override
    public boolean isActive() {
        return this.state != State.NONE;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        switch (this.state) {
            case GOAL_SET:
                return new PathingCommand(this.goal, PathingCommandType.CANCEL_AND_SET_GOAL);
            case PATH_REQUESTED:
                // return FORCE_REVALIDATE_GOAL_AND_PATH just once
                PathingCommand ret = new PathingCommand(this.goal, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
                this.state = State.EXECUTING;
                return ret;
            case EXECUTING:
                if (calcFailed) {
                    onLostControl();
                    return new PathingCommand(this.goal, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                if (this.goal == null || (this.goal.isInGoal(ctx.playerFeet()) && this.goal.isInGoal(winton.getPathingBehavior().pathStart()))) {
                    onLostControl(); // we're there xd
                    if (Winton.settings().disconnectOnArrival.value) {
                        if (ctx.world() instanceof ClientLevel clientLevel) {
                            clientLevel.disconnect(Component.literal("[Winton] Arrived at goal!"));
                        }
                    }
                    if (Winton.settings().notificationOnPathComplete.value) {
                        logNotification("Pathing complete", false);
                    }
                    return new PathingCommand(this.goal, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
                return new PathingCommand(this.goal, PathingCommandType.SET_GOAL_AND_PATH);
            default:
                throw new IllegalStateException("Unexpected state " + this.state);
        }
    }

    @Override
    public void onLostControl() {
        this.state = State.NONE;
        this.goal = null;
    }

    @Override
    public String displayName0() {
        return "Custom Goal " + this.goal;
    }

    protected enum State {
        NONE,
        GOAL_SET,
        PATH_REQUESTED,
        EXECUTING
    }
}
