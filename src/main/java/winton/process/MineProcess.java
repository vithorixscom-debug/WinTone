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
import winton.api.WintonAPI;
import winton.api.pathing.goals.*;
import winton.api.process.IMineProcess;
import winton.api.process.PathingCommand;
import winton.api.process.PathingCommandType;
import winton.api.selection.ISelection;
import winton.api.utils.*;
import winton.api.utils.input.Input;
import winton.cache.CachedChunk;
import winton.pathing.movement.CalculationContext;
import winton.pathing.movement.MovementHelper;
import winton.utils.WintonProcessHelper;
import winton.utils.BlockStateInterface;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.stream.Collectors;

import static winton.api.pathing.movement.ActionCosts.COST_INF;

/**
 * Mine blocks of a certain type
 *
 * @author leijurv
 */
public final class MineProcess extends WintonProcessHelper implements IMineProcess {

    private BlockOptionalMetaLookup filter;
    private volatile List<BlockPos> knownOreLocations = new ArrayList<>();
    private final List<BlockPos> blacklist = Collections.synchronizedList(new ArrayList<>());
    private volatile Map<BlockPos, Long> anticipatedDrops = new HashMap<>();
    private BlockPos branchPoint;
    private GoalRunAway branchPointRunaway;
    private int desiredQuantity;
    private int tickCount;
    private volatile boolean rescanRunning;
    private List<BlockPos> cachedDropped = Collections.emptyList();
    private int lastDropScanTick = -999;
    private Goal lastMineGoal;
    private int lastGoalRebuildTick = -999;
    private PathingCommand cachedPathCommand;
    /** Auto scan radius — start at max so ores are visible immediately */
    private int autoScanRadius = 32;
    private int lastPathFailLogTick = -999;
    /** Locked cardinal heading for the stair down. */
    private Direction tunnelDir;
    private Goal tunnelGoal;
    /** Next block of the staircase. Advances only when the player reaches it. */
    private BlockPos digTarget;
    /** Ore we are already walking to, so the goal does not flip every scan. */
    private BlockPos heldOre;
    private int heldSince;
    /** Last diamond the eyes actually held onto. Brief look-aways do not cancel it. */
    private BlockPos committedOre;
    private int committedMiss;
    /** Nearest visible ore that is not committed yet. */
    private BlockPos pendingOre;
    private int pendingSince;

    /** Selection-bounded auto-shaft (server regen mines). */
    private boolean shaftMode;
    private BetterBlockPos shaftMin;
    private BetterBlockPos shaftMax;
    private boolean savedAllowPlace;
    private boolean savedExplore;
    private boolean savedParkour;
    private boolean savedParkourAscend;
    private boolean savedAllowPlaceInFluidsSource;
    private boolean savedAllowPlaceInFluidsFlow;
    private boolean shaftSettingsCaptured;
    private boolean addedLadderAvoid;

    public MineProcess(Winton winton) {
        super(winton);
    }

    @Override
    public boolean isActive() {
        return filter != null;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (desiredQuantity > 0) {
            int curr = ctx.player().getInventory().getNonEquipmentItems().stream()
                    .filter(stack -> filter.has(stack))
                    .mapToInt(ItemStack::getCount).sum();
            if (curr >= desiredQuantity) {
                logDirect("Have " + curr + " valid items");
                cancel();
                return null;
            }
        }
        if (calcFailed) {
            if (!knownOreLocations.isEmpty() && Winton.settings().blacklistClosestOnFailure.value) {
                if (tickCount - lastPathFailLogTick > 40) {
                    lastPathFailLogTick = tickCount;
                    logDirect("Path failed to ore, skipping closest and continuing...");
                }
                List<BlockPos> next = new ArrayList<>(knownOreLocations);
                // Soft-skip nearest + neighbors so we don't re-think the same dead vein
                next.stream().min(Comparator.comparingDouble(ctx.playerFeet()::distSqr)).ifPresent(closest -> {
                    blacklist.add(closest);
                    for (BlockPos p : next) {
                        if (p != closest && p.distSqr(closest) <= 2 * 2) {
                            blacklist.add(p);
                        }
                    }
                });
                next.removeIf(blacklist::contains);
                knownOreLocations = next;
                cachedPathCommand = null;
                lastMineGoal = null;
            } else if (Winton.settings().exploreForBlocks.value) {
                // Explicit explore only — never fall into aimless runaway by accident
                knownOreLocations = new ArrayList<>();
                cachedPathCommand = null;
                lastMineGoal = null;
                branchPoint = null;
                branchPointRunaway = null;
            } else {
                // Stair or goal could not be pathed. Turn and keep digging down. Do not stand still.
                cachedPathCommand = null;
                lastMineGoal = null;
                tunnelGoal = null;
                digTarget = null;
                if (shaftMode) {
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
                if (tunnelDir != null) {
                    tunnelDir = tunnelDir.getClockWise();
                }
            }
        }

        updateLoucaSystem();
        tickCount++;

        // Only pause for real fire / standing IN lava — lava under the floor is normal in caves
        boolean needExtinguish = Winton.settings().autoSafety.value && ctx.player() != null && (
            ctx.player().isOnFire()
                || ctx.world().getFluidState(ctx.playerFeet()).is(net.minecraft.tags.FluidTags.LAVA));
        if (needExtinguish) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        int mineGoalUpdateInterval = Winton.settings().mineGoalUpdateInterval.value;
        if (mineGoalUpdateInterval != 0 && tickCount % mineGoalUpdateInterval == 0 && !rescanRunning) {
            List<BlockPos> snapshot = new ArrayList<>(knownOreLocations);
            CalculationContext context = new CalculationContext(winton, true);
            rescanRunning = true;
            Winton.getExecutor().execute(() -> {
                try {
                    rescan(snapshot, context);
                } finally {
                    rescanRunning = false;
                }
            });
        }
        if (Winton.settings().legitMine.value && (tickCount % 5 == 0 || knownOreLocations.isEmpty())) {
            addNearby();
        }
        // Dig ceiling ore above feet (ore only — in shaft: still OK; no block place)
        List<BlockPos> curr = knownOreLocations;
        Optional<BlockPos> shaft = Optional.empty();
        BlockPos feet = ctx.playerFeet();
        for (BlockPos pos : curr) {
            if (!inShaftBox(pos)) {
                continue;
            }
            int dx = Math.abs(pos.getX() - feet.getX());
            int dz = Math.abs(pos.getZ() - feet.getZ());
            if (dx <= 1 && dz <= 1 && pos.getY() == feet.getY() + 1) {
                if (winton.bsi != null && MovementHelper.lavaWithin(winton.bsi, pos.getX(), pos.getY(), pos.getZ(), 1)) {
                    continue;
                }
                if (!(BlockStateInterface.get(ctx, pos).getBlock() instanceof AirBlock)) {
                    if (shaft.isEmpty() || pos.distSqr(feet.above()) < shaft.get().distSqr(feet.above())) {
                        shaft = Optional.of(pos);
                    }
                }
            }
        }
        winton.getInputOverrideHandler().clearAllKeys();
        if (shaft.isPresent() && ctx.player().onGround() && winton.bsi != null) {
            BlockPos pos = shaft.get();
            BlockState state = winton.bsi.get0(pos);
            boolean blocked = MovementHelper.lavaWithin(winton.bsi, pos.getX(), pos.getY(), pos.getZ(), 1)
                || (!Winton.settings().legitMine.value
                && MovementHelper.avoidBreaking(winton.bsi, pos.getX(), pos.getY(), pos.getZ(), state));
            if (!blocked) {
                Optional<Rotation> rot = RotationUtils.reachable(ctx, pos);
                if (rot.isPresent() && isSafeToCancel) {
                    Rotation want = rot.get();
                    winton.getLookBehavior().updateTarget(want, true);
                    MovementHelper.switchToBestToolFor(ctx, ctx.world().getBlockState(pos));
                    // Wider than isReallyCloseTo so smoothLook can dig without snapping aim
                    if (ctx.isLookingAt(pos) || ctx.playerRotations().isReallyCloseTo(want) || aimNear(ctx.playerRotations(), want, 12.0F)) {
                        winton.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
                    }
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
            }
        }
        PathingCommand command = updateGoal();
        if (command == null) {
            cancel();
            return null;
        }
        return command;
    }


    private void updateLoucaSystem() {
        Map<BlockPos, Long> copy = new HashMap<>(anticipatedDrops);
        ctx.getSelectedBlock().ifPresent(pos -> {
            if (knownOreLocations.contains(pos)) {
                copy.put(pos, System.currentTimeMillis() + Winton.settings().mineDropLoiterDurationMSThanksLouca.value);
            }
        });
        // elaborate dance to avoid concurrentmodificationexcepption since rescan thread reads this
        // don't want to slow everything down with a gross lock do we now
        for (BlockPos pos : anticipatedDrops.keySet()) {
            Long until = copy.get(pos);
            if (until == null || until < System.currentTimeMillis()) {
                copy.remove(pos);
            }
        }
        anticipatedDrops = copy;
    }

    @Override
    public void onLostControl() {
        restoreShaftSettings();
        this.filter = null;
        this.knownOreLocations = new ArrayList<>();
        this.blacklist.clear();
        this.branchPoint = null;
        this.branchPointRunaway = null;
        this.lastMineGoal = null;
        this.cachedPathCommand = null;
        this.shaftMode = false;
        this.shaftMin = null;
        this.shaftMax = null;
        this.desiredQuantity = 0;
        this.tunnelDir = null;
        this.tunnelGoal = null;
        this.digTarget = null;
        this.heldOre = null;
        this.committedOre = null;
        this.committedMiss = 0;
        this.pendingOre = null;
    }

    /**
     * Straight cardinal tunnel. Direction locks to where the player was looking
     * when mining started, so a lost ore does not spin the path around.
     */
    private PathingCommand tunnelAhead() {
        if (tunnelDir == null || tunnelGoal == null) {
            float yaw = Mth.wrapDegrees(ctx.player().getYRot());
            int quadrant = Math.floorMod(Math.round(yaw / 90.0F), 4);
            tunnelDir = Direction.from2DDataValue(quadrant);
            tunnelGoal = new GoalStrictDirection(ctx.playerFeet(), tunnelDir);
        }
        if (tunnelGoal.equals(lastMineGoal) && cachedPathCommand != null) {
            return cachedPathCommand;
        }
        lastMineGoal = tunnelGoal;
        PathingCommand cmd = new PathingCommand(tunnelGoal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
        cachedPathCommand = cmd;
        return cmd;
    }

    /**
     * No visible ore: one step forward and one down. Stops at the world floor
     * and only then walks a horizontal tunnel. Never aims at a fixed ore layer.
     */
    private PathingCommand digDown() {
        BetterBlockPos feet = ctx.playerFeet();
        int floor = ctx.world().getMinY() + 4;
        if (feet.y <= floor) {
            return tunnelAhead();
        }
        if (tunnelDir == null) {
            float yaw = Mth.wrapDegrees(ctx.player().getYRot());
            int quadrant = Math.floorMod(Math.round(yaw / 90.0F), 4);
            tunnelDir = Direction.from2DDataValue(quadrant);
        }
        if (digTarget == null || feet.y < digTarget.getY() - 1) {
            digTarget = feet.relative(tunnelDir).below();
        } else {
            if (feet.y <= digTarget.getY() && feet.x == digTarget.getX() && feet.z == digTarget.getZ()) {
                digTarget = digTarget.relative(tunnelDir).below();
            }
        }
        if (digTarget.getY() < floor) {
            return tunnelAhead();
        }
        Goal goal = new GoalBlock(digTarget);
        if (goal.equals(lastMineGoal) && cachedPathCommand != null) {
            return cachedPathCommand;
        }
        lastMineGoal = goal;
        PathingCommand cmd = new PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
        cachedPathCommand = cmd;
        return cmd;
    }

    /** Visible ore, or the one we already committed to. Null means keep tunneling. */
    private BlockPos legitTarget(List<BlockPos> visible) {
        if (committedOre != null) {
            BlockOptionalMetaLookup f = filterFilter();
            BlockState st = ctx.world().getBlockState(committedOre);
            boolean real = f != null && f.has(st) && !blacklist.contains(committedOre);
            if (!real) {
                committedOre = null;
                committedMiss = 0;
            } else if (visible.contains(committedOre)) {
                committedMiss = 0;
                pendingOre = null;
                return committedOre;
            } else if (committedMiss < 40) {
                committedMiss++;
                return committedOre;
            } else {
                committedOre = null;
                committedMiss = 0;
            }
        }
        if (visible.isEmpty()) {
            pendingOre = null;
            return null;
        }
        BlockPos nearest = visible.get(0);
        if (pendingOre == null || !pendingOre.equals(nearest)) {
            pendingOre = nearest;
            pendingSince = tickCount;
            return null;
        }
        if (tickCount - pendingSince < 8) {
            return null;
        }
        committedOre = nearest;
        committedMiss = 0;
        pendingOre = null;
        return nearest;
    }

    private PathingCommand legitCommand(List<BlockPos> locs) {
        List<BlockPos> visible = Collections.emptyList();
        CalculationContext context = null;
        if (!locs.isEmpty()) {
            context = new CalculationContext(winton);
            List<BlockPos> dropped = droppedItemsScanThrottled();
            int maxOre = Winton.settings().mineMaxOreLocationsCount.value;
            visible = pruneShaft(context, new ArrayList<>(locs), filterFilter(), maxOre, blacklist, dropped);
            int feetY = ctx.playerFeet().getY();
            visible.sort(Comparator.comparingDouble(p -> ctx.playerFeet().distSqr(p) + Math.max(0, p.getY() - feetY) * 8.0D));
        }
        BlockPos target = legitTarget(visible);
        if (target == null) {
            heldOre = null;
            if (shaftMode) {
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            return digDown();
        }
        digTarget = null;
        if (context == null) {
            context = new CalculationContext(winton);
        }
        List<BlockPos> goalLocs = new ArrayList<>(visible);
        if (!goalLocs.contains(target)) {
            goalLocs.add(target);
        }
        knownOreLocations = goalLocs;
        heldOre = target;
        Goal goal = coalesce(target, goalLocs, context);
        boolean same = goal.equals(lastMineGoal);
        lastMineGoal = goal;
        if (same && cachedPathCommand != null) {
            return cachedPathCommand;
        }
        PathingCommand cmd = new PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
        cachedPathCommand = cmd;
        return cmd;
    }

    @Override
    public String displayName0() {
        return "Mine " + filter;
    }

    private PathingCommand updateGoal() {
        BlockOptionalMetaLookup filter = filterFilter();
        if (filter == null) {
            return null;
        }

        boolean legit = Winton.settings().legitMine.value;
        List<BlockPos> locs = knownOreLocations;
        if (legit) {
            return legitCommand(locs);
        }
        if (!locs.isEmpty()) {
            // Rebuild goals every 2 ticks — snappy retarget without per-tick prune spam
            if (cachedPathCommand != null && tickCount - lastGoalRebuildTick < 2 && lastMineGoal != null) {
                return cachedPathCommand;
            }
            lastGoalRebuildTick = tickCount;
            List<BlockPos> dropped = droppedItemsScanThrottled();
            CalculationContext context = new CalculationContext(winton);
            int maxOre = Winton.settings().mineMaxOreLocationsCount.value;
            List<BlockPos> locs2 = pruneShaft(context, new ArrayList<>(locs), filter, maxOre, blacklist, dropped);
            int feetY = ctx.playerFeet().getY();
            if (!locs2.isEmpty()) {
                // Keep walking to the ore we already chose. Rebuilding the goal every
                // couple of ticks cancelled the path and the bot just stood there.
                if (heldOre != null && locs2.contains(heldOre) && cachedPathCommand != null && lastMineGoal != null && winton.getPathingBehavior().isPathing()) {
                    return cachedPathCommand;
                }
                List<BlockPos> goalSrc = new ArrayList<>(locs2);
                goalSrc.sort(Comparator.comparingDouble(p -> ctx.playerFeet().distSqr(p) + Math.max(0, p.getY() - feetY) * 64.0D));
                BlockPos primary = goalSrc.get(0);
                if (legit && heldOre != null && locs2.contains(heldOre)) {
                    double heldDist = ctx.playerFeet().distSqr(heldOre);
                    double newDist = ctx.playerFeet().distSqr(primary);
                    if (tickCount - heldSince < 50 && newDist + 12 >= heldDist) {
                        primary = heldOre;
                    }
                }
                if (!primary.equals(heldOre)) {
                    heldOre = primary;
                    heldSince = tickCount;
                }
                Goal goal;
                if (legit) {
                    goal = coalesce(primary, locs2, context);
                } else {
                    int goalCap = Math.min(4, goalSrc.size());
                    List<BlockPos> goalLocs = goalSrc.subList(0, goalCap);
                    Goal[] goals = new Goal[goalLocs.size()];
                    for (int i = 0; i < goalLocs.size(); i++) {
                        goals[i] = coalesce(goalLocs.get(i), locs2, context);
                    }
                    goal = new GoalComposite(goals);
                }
                knownOreLocations = locs2;
                boolean same = goal.equals(lastMineGoal);
                lastMineGoal = goal;
                if (same && cachedPathCommand != null) {
                    return cachedPathCommand;
                }
                PathingCommand cmd = new PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
                cachedPathCommand = cmd;
                return cmd;
            }
            // All known ores pruned — clear and fall through to strip/explore
            knownOreLocations = locs2;
            cachedPathCommand = null;
            lastMineGoal = null;
            heldOre = null;
        }
        // No real ore in loaded chunks. Dig a staircase. Do not stand still and do not wander at this Y.
        if (shaftMode) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        return digDown();
    }

    private void rescan(List<BlockPos> already, CalculationContext context) {
        BlockOptionalMetaLookup filter = filterFilter();
        if (filter == null) {
            return;
        }
        if (Winton.settings().legitMine.value) {
            return;
        }
        List<BlockPos> dropped = droppedItemsScan();
        int radius = effectiveScanRadius();
        List<BlockPos> locs = searchWorld(context, filter, Winton.settings().mineMaxOreLocationsCount.value, already, blacklist, dropped, radius);
        locs = pruneShaft(context, locs, filter, Winton.settings().mineMaxOreLocationsCount.value, blacklist, dropped);
        locs.addAll(dropped.stream().filter(this::inShaftBox).toList());
        if (locs.isEmpty() && autoScanRadius < 32 && !shaftMode) {
            autoScanRadius = 32;
            locs = searchWorld(context, filter, Winton.settings().mineMaxOreLocationsCount.value, already, blacklist, dropped, autoScanRadius);
            locs = pruneShaft(context, locs, filter, Winton.settings().mineMaxOreLocationsCount.value, blacklist, dropped);
            locs.addAll(dropped.stream().filter(this::inShaftBox).toList());
        }
        BlockPos walkingTo = heldOre;
        if (locs.isEmpty() && !Winton.settings().exploreForBlocks.value) {
            if (shaftMode) {
                // Server auto-mines respawn ores — wait in place
                knownOreLocations = locs;
                cachedPathCommand = null;
                lastMineGoal = null;
                return;
            }
            logDirect("No locations for " + filter + " known, cancelling");
            if (Winton.settings().notificationOnMineFail.value) {
                logNotification("No locations for " + filter + " known, cancelling", true);
            }
            cancel();
            return;
        }
        knownOreLocations = locs;
        // Don't throw away the current path if that ore is still in the new scan
        if (walkingTo == null || !locs.contains(walkingTo)) {
            cachedPathCommand = null;
            lastMineGoal = null;
        }
    }

    private int effectiveScanRadius() {
        // Always AUTO ladder 8 → 16 → 32 (manual radius removed)
        return Math.max(8, Math.min(32, autoScanRadius));
    }

    private boolean internalMiningGoal(BlockPos pos, CalculationContext context, List<BlockPos> locs) {
        // Here, BlockStateInterface is used because the position may be in a cached chunk (the targeted block is one that is kept track of)
        if (locs.contains(pos)) {
            return true;
        }
        BlockState state = context.bsi.get0(pos);
        if (Winton.settings().internalMiningAirException.value && state.getBlock() instanceof AirBlock) {
            return true;
        }
        return filter.has(state) && plausibleToBreak(context, pos);
    }

    private Goal coalesce(BlockPos loc, List<BlockPos> locs, CalculationContext context) {
        int feetY = ctx.playerFeet().getY();
        // Auto-shaft: never GoalGetToBlock (would pillar). Stand under / beside; climb ladders.
        if (shaftMode) {
            if (loc.getY() >= feetY + 2) {
                return new GoalTwoBlocks(loc);
            }
            return new GoalThreeBlocks(loc);
        }
        // Ore above the feet: stand at it or one below. GoalGetToBlock pillars.
        if (loc.getY() >= feetY + 2) {
            return new GoalTwoBlocks(loc);
        }
        boolean assumeVerticalShaftMine = winton.bsi == null
            || !(winton.bsi.get0(loc.above()).getBlock() instanceof FallingBlock);
        if (!Winton.settings().forceInternalMining.value) {
            if (assumeVerticalShaftMine) {
                // we can get directly below the block
                return new GoalThreeBlocks(loc);
            } else {
                // we need to get feet or head into the block
                return new GoalTwoBlocks(loc);
            }
        }
        boolean upwardGoal = internalMiningGoal(loc.above(), context, locs);
        boolean downwardGoal = internalMiningGoal(loc.below(), context, locs);
        boolean doubleDownwardGoal = internalMiningGoal(loc.below(2), context, locs);
        if (upwardGoal == downwardGoal) { // symmetric
            if (doubleDownwardGoal && assumeVerticalShaftMine) {
                // we have a checkerboard like pattern
                // this one, and the one two below it
                // therefore it's fine to path to immediately below this one, since your feet will be in the doubleDownwardGoal
                // but only if assumeVerticalShaftMine
                return new GoalThreeBlocks(loc);
            } else {
                // this block has nothing interesting two below, but is symmetric vertically so we can get either feet or head into it
                return new GoalTwoBlocks(loc);
            }
        }
        if (upwardGoal) {
            // downwardGoal known to be false
            // ignore the gap then potential doubleDownward, because we want to path feet into this one and head into upwardGoal
            return new GoalBlock(loc);
        }
        // upwardGoal known to be false, downwardGoal known to be true
        if (doubleDownwardGoal && assumeVerticalShaftMine) {
            // this block and two below it are goals
            // path into the center of the one below, because that includes directly below this one
            return new GoalTwoBlocks(loc.below());
        }
        // upwardGoal false, downwardGoal true, doubleDownwardGoal false
        // just this block and the one immediately below, no others
        return new GoalBlock(loc.below());
    }

    private static class GoalThreeBlocks extends GoalTwoBlocks {

        public GoalThreeBlocks(BlockPos pos) {
            super(pos);
        }

        @Override
        public boolean isInGoal(int x, int y, int z) {
            return x == this.x && (y == this.y || y == this.y - 1 || y == this.y - 2) && z == this.z;
        }

        @Override
        public double heuristic(int x, int y, int z) {
            int xDiff = x - this.x;
            int yDiff = y - this.y;
            int zDiff = z - this.z;
            return GoalBlock.calculate(xDiff, yDiff < -1 ? yDiff + 2 : yDiff == -1 ? 0 : yDiff, zDiff);
        }

        @Override
        public boolean equals(Object o) {
            return super.equals(o);
        }

        @Override
        public int hashCode() {
            return super.hashCode() * 393857768;
        }

        @Override
        public String toString() {
            return String.format(
                    "GoalThreeBlocks{x=%s,y=%s,z=%s}",
                    SettingsUtil.maybeCensor(x),
                    SettingsUtil.maybeCensor(y),
                    SettingsUtil.maybeCensor(z)
            );
        }
    }

    public List<BlockPos> droppedItemsScan() {
        if (!Winton.settings().mineScanDroppedItems.value) {
            return Collections.emptyList();
        }
        List<BlockPos> ret = new ArrayList<>();
        for (Entity entity : ((ClientLevel) ctx.world()).entitiesForRendering()) {
            if (entity instanceof ItemEntity) {
                ItemEntity ei = (ItemEntity) entity;
                if (filter.has(ei.getItem())) {
                    ret.add(entity.blockPosition());
                }
            }
        }
        ret.addAll(anticipatedDrops.keySet());
        return ret;
    }

    /** Cache entity drop scan — full world entity walk is expensive every tick. */
    private List<BlockPos> droppedItemsScanThrottled() {
        if (!Winton.settings().mineScanDroppedItems.value) {
            return Collections.emptyList();
        }
        if (tickCount - lastDropScanTick < 3) {
            return cachedDropped;
        }
        lastDropScanTick = tickCount;
        cachedDropped = droppedItemsScan();
        return cachedDropped;
    }

    public static List<BlockPos> searchWorld(CalculationContext ctx, BlockOptionalMetaLookup filter, int max, List<BlockPos> alreadyKnown, List<BlockPos> blacklist, List<BlockPos> dropped) {
        return searchWorld(ctx, filter, max, alreadyKnown, blacklist, dropped, 32);
    }

    public static List<BlockPos> searchWorld(CalculationContext ctx, BlockOptionalMetaLookup filter, int max, List<BlockPos> alreadyKnown, List<BlockPos> blacklist, List<BlockPos> dropped, int scanRadius) {
        List<BlockPos> locs = new ArrayList<>();
        List<Block> untracked = new ArrayList<>();
        for (BlockOptionalMeta bom : filter.blocks()) {
            Block block = bom.getBlock();
            if (CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(block)) {
                BetterBlockPos pf = ctx.winton.getPlayerContext().playerFeet();

                int regionReach = Math.max(4, (Math.max(1, scanRadius) + 15) / 16);
                locs.addAll(ctx.worldData.getCachedWorld().getLocationsOf(
                        BlockUtils.blockToString(block),
                        Winton.settings().maxCachedWorldScanCount.value,
                        pf.x,
                        pf.z,
                        regionReach
                ));
            } else {
                untracked.add(block);
            }
        }

        locs = prune(ctx, locs, filter, max, blacklist, dropped);

        if (!untracked.isEmpty() || (Winton.settings().extendCacheOnThreshold.value && locs.size() < max)) {
            locs.addAll(WintonAPI.getProvider().getWorldScanner().scanChunkRadius(
                    ctx.getWinton().getPlayerContext(),
                    filter,
                    max,
                    10,
                    Math.max(1, scanRadius)
            ));
        }

        locs.addAll(alreadyKnown);

        return prune(ctx, locs, filter, max, blacklist, dropped);
    }

    private boolean addNearby() {
        List<BlockPos> dropped = new ArrayList<>(droppedItemsScanThrottled());
        List<BlockPos> locs = new ArrayList<>(knownOreLocations);
        locs.addAll(dropped);

        BlockOptionalMetaLookup filter = filterFilter();
        if (filter == null) {
            return false;
        }

        // Only exposed (open to air/cave) ores — no buried vein chase
        BlockPos playerFeet = ctx.playerFeet();
        BlockStateInterface bsi = new BlockStateInterface(ctx);
        CalculationContext calc = new CalculationContext(winton);
        // Wider scan so legit strip actually sees ores in tunnels
        int searchDist = 16;
        double fakedBlockReachDistance = 4.5;
        boolean diagonals = Winton.settings().legitMineIncludeDiagonals.value;

        for (int x = playerFeet.getX() - searchDist; x <= playerFeet.getX() + searchDist; x++) {
            for (int y = playerFeet.getY() - searchDist; y <= playerFeet.getY() + searchDist; y++) {
                for (int z = playerFeet.getZ() - searchDist; z <= playerFeet.getZ() + searchDist; z++) {
                    if (!bsi.worldContainsLoadedChunk(x, z)) {
                        continue;
                    }
                    if (!filter.has(bsi.get0(x, y, z))) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!isNextToAir(calc, pos)) {
                        continue; // buried — skip
                    }
                    boolean nearVein = diagonals && locs.stream().anyMatch(ore ->
                        ore.distSqr(pos) <= 2 * 2 && isNextToAir(calc, ore));
                    // Accept any exposed ore in scan radius; reachability only for dig pause path
                    if (nearVein || playerFeet.distSqr(pos) <= searchDist * searchDist
                            || RotationUtils.reachable(ctx, pos, fakedBlockReachDistance).isPresent()) {
                        locs.add(pos);
                    }
                }
            }
        }

        knownOreLocations = pruneShaft(calc, locs, filter,
            Winton.settings().mineMaxOreLocationsCount.value, blacklist, dropped);
        return true;
    }

    private static List<BlockPos> prune(CalculationContext ctx, List<BlockPos> locs2, BlockOptionalMetaLookup filter, int max, List<BlockPos> blacklist, List<BlockPos> dropped) {
        return prune(ctx, locs2, filter, max, blacklist, dropped, null, null);
    }

    private List<BlockPos> pruneShaft(CalculationContext ctx, List<BlockPos> locs2, BlockOptionalMetaLookup filter, int max, List<BlockPos> blacklist, List<BlockPos> dropped) {
        return prune(ctx, locs2, filter, max, blacklist, dropped,
            shaftMode ? shaftMin : null, shaftMode ? shaftMax : null);
    }

    private static List<BlockPos> prune(CalculationContext ctx, List<BlockPos> locs2, BlockOptionalMetaLookup filter, int max, List<BlockPos> blacklist, List<BlockPos> dropped, BetterBlockPos shaftMin, BetterBlockPos shaftMax) {
        // dropped may be Collections.emptyList() — never mutate caller list in place
        List<BlockPos> droppedMutable = dropped.isEmpty() ? Collections.emptyList() : new ArrayList<>(dropped);
        if (!droppedMutable.isEmpty()) {
            droppedMutable.removeIf(drop -> {
                for (BlockPos pos : locs2) {
                    if (pos.distSqr(drop) <= 9 && filter.has(ctx.get(pos.getX(), pos.getY(), pos.getZ())) && MineProcess.plausibleToBreak(ctx, pos)) {
                        return true;
                    }
                }
                return false;
            });
        }
        final List<BlockPos> droppedFinal = droppedMutable;
        final BetterBlockPos sMin = shaftMin;
        final BetterBlockPos sMax = shaftMax;
        List<BlockPos> locs = locs2
                .stream()
                .distinct()

                // remove any that are within loaded chunks that aren't actually what we want
                // Unloaded chunks: trust the ore cache and path there. Loaded chunks: the block must still be that ore.
                .filter(pos -> !ctx.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())
                        || filter.has(ctx.get(pos.getX(), pos.getY(), pos.getZ()))
                        || droppedFinal.contains(pos))

                // remove any that are implausible to mine (encased in bedrock, or inside a lava cube)
                .filter(pos -> !ctx.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ()) || MineProcess.plausibleToBreak(ctx, pos))

                // 3×3×3 around lava is never a dig, legit or not
                .filter(pos -> !ctx.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())
                        || !MovementHelper.lavaWithin(ctx.bsi, pos.getX(), pos.getY(), pos.getZ(), 1))
                .filter(pos -> !Winton.settings().legitMine.value || !ctx.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ()) || droppedFinal.contains(pos) || isNextToAir(ctx, pos))
                .filter(pos -> !Winton.settings().legitMine.value || !ctx.bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ()) || droppedFinal.contains(pos) || seesStraight(ctx, pos))

                .filter(pos -> !blacklist.contains(pos))

                .filter(pos -> sMin == null || sMax == null
                    || (pos.getX() >= sMin.x && pos.getX() <= sMax.x
                    && pos.getY() >= sMin.y && pos.getY() <= sMax.y
                    && pos.getZ() >= sMin.z && pos.getZ() <= sMax.z))

                .sorted(Comparator.comparingDouble(ctx.getWinton().getPlayerContext().player().blockPosition()::distSqr))
                .collect(Collectors.toList());

        if (locs.size() > max) {
            return locs.subList(0, max);
        }
        return locs;
    }

    public static boolean isNextToAir(CalculationContext ctx, BlockPos pos) {
        int radius = Winton.settings().allowOnlyExposedOresDistance.value;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= radius
                            && MovementHelper.isTransparent(ctx.getBlock(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    /** Within deg of target — used so smoothLook can still trigger dig. */
    public static boolean aimNear(Rotation a, Rotation b, float deg) {
        float yawDiff = Math.abs(Rotation.normalizeYaw(a.getYaw()) - Rotation.normalizeYaw(b.getYaw()));
        if (yawDiff > 180.0F) {
            yawDiff = 360.0F - yawDiff;
        }
        return yawDiff <= deg && Math.abs(a.getPitch() - b.getPitch()) <= deg;
    }

    public static boolean plausibleToBreak(CalculationContext ctx, BlockPos pos) {
        if (MovementHelper.lavaWithin(ctx.bsi, pos.getX(), pos.getY(), pos.getZ(), 1)) {
            return false;
        }
        BlockState state = ctx.bsi.get0(pos);
        // Legit: do not drop ores because a neighbor is liquid/gravel — that hid face-to-face diamonds
        if (Winton.settings().legitMine.value) {
            if (!state.getFluidState().isEmpty()) {
                return false;
            }
            if (ctx.toolSet.getStrVsBlock(state) <= 0) {
                return false;
            }
            return !(ctx.bsi.get0(pos.above()).getBlock() == Blocks.BEDROCK && ctx.bsi.get0(pos.below()).getBlock() == Blocks.BEDROCK);
        }
        if (MovementHelper.getMiningDurationTicks(ctx, pos.getX(), pos.getY(), pos.getZ(), state, true) >= COST_INF) {
            return false;
        }
        if (MovementHelper.avoidBreaking(ctx.bsi, pos.getX(), pos.getY(), pos.getZ(), state)) {
            return false;
        }

        // bedrock above and below makes it implausible, otherwise we're good
        return !(ctx.bsi.get0(pos.above()).getBlock() == Blocks.BEDROCK && ctx.bsi.get0(pos.below()).getBlock() == Blocks.BEDROCK);
    }

    /** Ore block is lava, or a face of it is lava. Those are skipped. */
    private static boolean oreIsLava(CalculationContext ctx, BlockPos pos) {
        if (MovementHelper.isLava(ctx.bsi.get0(pos))) {
            return true;
        }
        return MovementHelper.isLava(ctx.bsi.get0(pos.above()))
                || MovementHelper.isLava(ctx.bsi.get0(pos.below()))
                || MovementHelper.isLava(ctx.bsi.get0(pos.north()))
                || MovementHelper.isLava(ctx.bsi.get0(pos.south()))
                || MovementHelper.isLava(ctx.bsi.get0(pos.east()))
                || MovementHelper.isLava(ctx.bsi.get0(pos.west()));
    }

    /** True when the block-selection ray from the eyes hits this ore and not another block. */
    private static boolean seesStraight(CalculationContext ctx, BlockPos ore) {
        var player = ctx.getWinton().getPlayerContext().player();
        if (player == null) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = new Vec3(ore.getX() + 0.5D, ore.getY() + 0.5D, ore.getZ() + 0.5D);
        if (eye.distanceToSqr(end) < 4.0D) {
            return true;
        }
        HitResult hit = player.level().clip(new ClipContext(
                eye,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return hit.getType() == HitResult.Type.MISS;
        }
        BlockPos hitPos = blockHit.getBlockPos();
        if (MovementHelper.isLava(ctx.get(hitPos.getX(), hitPos.getY(), hitPos.getZ()))) {
            return false;
        }
        return hitPos.equals(ore);
    }

    @Override
    public void mineByName(int quantity, String... blocks) {
        mine(quantity, new BlockOptionalMetaLookup(blocks));
    }

    @Override
    public void mine(int quantity, BlockOptionalMetaLookup filter) {
        restoreShaftSettings();
        shaftMode = false;
        shaftMin = null;
        shaftMax = null;

        if (filter != null && Winton.settings().mineAutoShaft.value) {
            ISelection sel = winton.getSelectionManager().getOnlySelection();
            if (sel == null) {
                logDirect("Автошахта: нет выделения — поставь Pos1 и Pos2");
                this.filter = null;
                return;
            }
            shaftMode = true;
            shaftMin = sel.min();
            shaftMax = sel.max();
            captureShaftSettings();
            Winton.settings().allowPlace.value = false;
            Winton.settings().allowPlaceInFluidsSource.value = false;
            Winton.settings().allowPlaceInFluidsFlow.value = false;
            Winton.settings().exploreForBlocks.value = false;
            Winton.settings().allowParkour.value = false;
            Winton.settings().allowParkourAscend.value = false;
            ensureLadderAvoid();
            logDirect("Автошахта: " + sel.size().getX() + "×" + sel.size().getY() + "×" + sel.size().getZ()
                + " — без постановки блоков, ждём респавн руд");
        } else if (filter != null) {
            // Non-shaft auto may pillar; legit never places (looks like a player)
            if (!Winton.settings().legitMine.value) {
                Winton.settings().allowPlace.value = true;
            } else {
                Winton.settings().allowPlace.value = false;
                Winton.settings().allowPlaceInFluidsSource.value = false;
                Winton.settings().allowPlaceInFluidsFlow.value = false;
            }
            Winton.settings().allowInventory.value = true;
            // Keep searching. Forcing this off made an empty scan cancel the mine and the bot stand still.
            Winton.settings().exploreForBlocks.value = true;
        }

        this.filter = filter;
        if (this.filterFilter() == null) {
            this.filter = null;
            restoreShaftSettings();
            shaftMode = false;
            return;
        }
        this.desiredQuantity = quantity;
        this.knownOreLocations = new ArrayList<>();
        this.blacklist.clear();
        this.branchPoint = null;
        this.branchPointRunaway = null;
        this.anticipatedDrops = new HashMap<>();
        this.lastMineGoal = null;
        this.cachedPathCommand = null;
        this.cachedDropped = Collections.emptyList();
        this.tunnelDir = null;
        this.tunnelGoal = null;
        this.digTarget = null;
        this.heldOre = null;
        this.committedOre = null;
        this.committedMiss = 0;
        this.pendingOre = null;
        if (!shaftMode && Winton.settings().legitMine.value && ctx.player() != null) {
            float yaw = Mth.wrapDegrees(ctx.player().getYRot());
            int quadrant = Math.floorMod(Math.round(yaw / 90.0F), 4);
            this.tunnelDir = Direction.from2DDataValue(quadrant);
            this.tunnelGoal = null;
        }
        this.autoScanRadius = shaftMode ? 8 : 32;
        if (filter != null && !rescanRunning) {
            CalculationContext context = new CalculationContext(winton, true);
            rescanRunning = true;
            Winton.getExecutor().execute(() -> {
                try {
                    rescan(new ArrayList<>(), context);
                } finally {
                    rescanRunning = false;
                }
            });
        }
    }

    private boolean inShaftBox(BlockPos pos) {
        if (!shaftMode || shaftMin == null || shaftMax == null) {
            return true;
        }
        return pos.getX() >= shaftMin.x && pos.getX() <= shaftMax.x
            && pos.getY() >= shaftMin.y && pos.getY() <= shaftMax.y
            && pos.getZ() >= shaftMin.z && pos.getZ() <= shaftMax.z;
    }

    private void captureShaftSettings() {
        if (shaftSettingsCaptured) {
            return;
        }
        savedAllowPlace = Winton.settings().allowPlace.value;
        savedExplore = Winton.settings().exploreForBlocks.value;
        savedParkour = Winton.settings().allowParkour.value;
        savedParkourAscend = Winton.settings().allowParkourAscend.value;
        savedAllowPlaceInFluidsSource = Winton.settings().allowPlaceInFluidsSource.value;
        savedAllowPlaceInFluidsFlow = Winton.settings().allowPlaceInFluidsFlow.value;
        shaftSettingsCaptured = true;
    }

    private void restoreShaftSettings() {
        if (!shaftSettingsCaptured) {
            return;
        }
        Winton.settings().allowPlace.value = savedAllowPlace;
        Winton.settings().exploreForBlocks.value = savedExplore;
        Winton.settings().allowParkour.value = savedParkour;
        Winton.settings().allowParkourAscend.value = savedParkourAscend;
        Winton.settings().allowPlaceInFluidsSource.value = savedAllowPlaceInFluidsSource;
        Winton.settings().allowPlaceInFluidsFlow.value = savedAllowPlaceInFluidsFlow;
        if (addedLadderAvoid) {
            List<Block> avoid = new ArrayList<>(Winton.settings().blocksToAvoidBreaking.value);
            avoid.remove(Blocks.LADDER);
            Winton.settings().blocksToAvoidBreaking.value = avoid;
            addedLadderAvoid = false;
        }
        shaftSettingsCaptured = false;
    }

    private void ensureLadderAvoid() {
        List<Block> avoid = Winton.settings().blocksToAvoidBreaking.value;
        if (avoid.contains(Blocks.LADDER)) {
            return;
        }
        List<Block> next = new ArrayList<>(avoid);
        next.add(Blocks.LADDER);
        Winton.settings().blocksToAvoidBreaking.value = next;
        addedLadderAvoid = true;
    }

    private BlockOptionalMetaLookup filterFilter() {
        if (this.filter == null) {
            return null;
        }
        if (!Winton.settings().allowBreak.value) {
            BlockOptionalMetaLookup f = new BlockOptionalMetaLookup(this.filter.blocks()
                    .stream()
                    .filter(e -> Winton.settings().allowBreakAnyway.value.contains(e.getBlock()))
                    .toArray(BlockOptionalMeta[]::new));
            if (f.blocks().isEmpty()) {
                logDirect("Unable to mine when allowBreak is false and target block is not in allowBreakAnyway!");
                return null;
            }
            return f;
        }
        return filter;
    }
}
