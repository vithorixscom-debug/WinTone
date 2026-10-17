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

package winton.utils;

import winton.Winton;
import winton.api.event.events.TickEvent;
import winton.api.event.listener.AbstractGameEventListener;
import winton.api.pathing.calc.IPathingControlManager;
import winton.api.pathing.goals.Goal;
import winton.api.process.IWintonProcess;
import winton.api.process.PathingCommand;
import winton.api.process.PathingCommandType;
import winton.behavior.PathingBehavior;
import winton.pathing.path.PathExecutor;
import net.minecraft.core.BlockPos;

import java.util.*;

public class PathingControlManager implements IPathingControlManager {

    private final Winton winton;
    private final HashSet<IWintonProcess> processes; // unGh
    private final List<IWintonProcess> active;
    private IWintonProcess inControlLastTick;
    private IWintonProcess inControlThisTick;
    private PathingCommand command;

    public PathingControlManager(Winton winton) {
        this.winton = winton;
        this.processes = new HashSet<>();
        this.active = new ArrayList<>();
        winton.getGameEventHandler().registerEventListener(new AbstractGameEventListener() { // needs to be after all behavior ticks
            @Override
            public void onTick(TickEvent event) {
                if (event.getType() == TickEvent.Type.IN) {
                    postTick();
                }
            }
        });
    }

    @Override
    public void registerProcess(IWintonProcess process) {
        process.onLostControl(); // make sure it's reset
        processes.add(process);
    }

    public void cancelEverything() { // called by PathingBehavior on TickEvent Type OUT
        inControlLastTick = null;
        inControlThisTick = null;
        command = null;
        active.clear();
        for (IWintonProcess proc : processes) {
            proc.onLostControl();
            if (proc.isActive() && !proc.isTemporary()) { // it's okay only for a temporary thing (like combat pause) to maintain control even if you say to cancel
                throw new IllegalStateException(proc.displayName() + " stayed active after being cancelled");
            }
        }
    }

    @Override
    public Optional<IWintonProcess> mostRecentInControl() {
        return Optional.ofNullable(inControlThisTick);
    }

    @Override
    public Optional<PathingCommand> mostRecentCommand() {
        return Optional.ofNullable(command);
    }

    public void preTick() {
        inControlLastTick = inControlThisTick;
        inControlThisTick = null;
        PathingBehavior p = winton.getPathingBehavior();
        command = executeProcesses();
        if (command == null) {
            p.cancelSegmentIfSafe();
            p.secretInternalSetGoal(null);
            return;
        }
        if (!Objects.equals(inControlThisTick, inControlLastTick) && command.commandType != PathingCommandType.REQUEST_PAUSE && inControlLastTick != null && !inControlLastTick.isTemporary()) {
            // if control has changed from a real process to another real process, and the new process wants to do something
            p.cancelSegmentIfSafe();
            // get rid of the in progress stuff from the last process
        }
        switch (command.commandType) {
            case SET_GOAL_AND_PAUSE:
                p.secretInternalSetGoalAndPath(command);
            case REQUEST_PAUSE:
                p.requestPause();
                break;
            case CANCEL_AND_SET_GOAL:
                p.secretInternalSetGoal(command.goal);
                p.cancelSegmentIfSafe();
                break;
            case FORCE_REVALIDATE_GOAL_AND_PATH:
            case REVALIDATE_GOAL_AND_PATH:
                if (!p.isPathing() && !p.getInProgress().isPresent()) {
                    p.secretInternalSetGoalAndPath(command);
                }
                break;
            case SET_GOAL_AND_PATH:
                // now this i can do
                if (command.goal != null) {
                    p.secretInternalSetGoalAndPath(command);
                }
                break;
            default:
                throw new IllegalStateException("Unexpected command type " + command.commandType);
        }
    }

    private void postTick() {
        // if we did this in pretick, it would suck
        // we use the time between ticks as calculation time
        // therefore, we only cancel and recalculate after the tick for the current path has executed
        // "it would suck" means it would actually execute a path every other tick
        if (command == null) {
            return;
        }
        PathingBehavior p = winton.getPathingBehavior();
        switch (command.commandType) {
            case FORCE_REVALIDATE_GOAL_AND_PATH:
                if (command.goal == null || forceRevalidate(command.goal) || revalidateGoal(command.goal)) {
                    // pwnage
                    p.softCancelIfSafe();
                }
                if (p.isPathing() || p.hasPathCalcInProgress()) {
                    p.secretInternalSetGoal(command.goal);
                } else {
                    p.secretInternalSetGoalAndPath(command);
                }
                break;
            case REVALIDATE_GOAL_AND_PATH:
                if (Winton.settings().cancelOnGoalInvalidation.value && (command.goal == null || revalidateGoal(command.goal))) {
                    p.softCancelIfSafe();
                }
                if (p.isPathing() || p.hasPathCalcInProgress()) {
                    p.secretInternalSetGoal(command.goal);
                } else {
                    p.secretInternalSetGoalAndPath(command);
                }
                break;
            default:
        }
    }

    public boolean forceRevalidate(Goal newGoal) {
        PathExecutor current = winton.getPathingBehavior().getCurrent();
        if (current != null) {
            if (newGoal.isInGoal(current.getPath().getDest())) {
                return false;
            }
            return !newGoal.equals(current.getPath().getGoal());
        }
        return false;
    }

    public boolean revalidateGoal(Goal newGoal) {
        PathExecutor current = winton.getPathingBehavior().getCurrent();
        if (current != null) {
            Goal intended = current.getPath().getGoal();
            BlockPos end = current.getPath().getDest();
            if (intended.isInGoal(end) && !newGoal.isInGoal(end)) {
                // this path used to end in the goal
                // but the goal has changed, so there's no reason to continue...
                return true;
            }
        }
        return false;
    }


    public PathingCommand executeProcesses() {
        for (IWintonProcess process : processes) {
            if (process.isActive()) {
                if (!active.contains(process)) {
                    // put a newly active process at the very front of the queue
                    active.add(0, process);
                }
            } else {
                active.remove(process);
            }
        }
        // ties are broken by which was added to the beginning of the list first
        active.sort(Comparator.comparingDouble(IWintonProcess::priority).reversed());

        Iterator<IWintonProcess> iterator = active.iterator();
        while (iterator.hasNext()) {
            IWintonProcess proc = iterator.next();

            PathingCommand exec = proc.onTick(Objects.equals(proc, inControlLastTick) && winton.getPathingBehavior().calcFailedLastTick(), winton.getPathingBehavior().isSafeToCancel());
            if (exec == null) {
                if (proc.isActive()) {
                    throw new IllegalStateException(proc.displayName() + " actively returned null PathingCommand");
                }
                // no need to call onLostControl; they are reporting inactive.
            } else if (exec.commandType != PathingCommandType.DEFER) {
                inControlThisTick = proc;
                if (!proc.isTemporary()) {
                    iterator.forEachRemaining(IWintonProcess::onLostControl);
                }
                return exec;
            }
        }
        return null;
    }
}
