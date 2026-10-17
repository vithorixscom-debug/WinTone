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
import winton.api.IWinton;
import winton.api.event.events.*;
import winton.api.event.events.type.EventState;
import winton.api.event.listener.AbstractGameEventListener;
import winton.api.pathing.goals.Goal;
import winton.api.pathing.goals.GoalBlock;
import winton.api.pathing.goals.GoalXZ;
import winton.api.pathing.goals.GoalYLevel;
import winton.api.pathing.movement.IMovement;
import winton.api.pathing.path.IPathExecutor;
import winton.api.process.IWintonProcess;
import winton.api.process.IElytraProcess;
import winton.api.process.PathingCommand;
import winton.api.process.PathingCommandType;
import winton.api.utils.BetterBlockPos;
import winton.api.utils.Rotation;
import winton.api.utils.RotationUtils;
import winton.api.utils.input.Input;
import winton.pathing.movement.CalculationContext;
import winton.pathing.movement.movements.MovementFall;
import winton.process.elytra.*;
import winton.cache.WorldData;
import winton.utils.WintonProcessHelper;
import winton.utils.PathingCommandContext;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static winton.api.pathing.movement.ActionCosts.COST_INF;

public class ElytraProcess extends WintonProcessHelper implements IWintonProcess, IElytraProcess, AbstractGameEventListener {
    public State state;
    private boolean goingToLandingSpot;
    private BetterBlockPos landingSpot;
    private boolean reachedGoal; // this basically just prevents potential notification spam
    private Goal goal;
    private ElytraBehavior behavior;
    private NetherPathfinderContext npfContext;
    private boolean predictingTerrain;
    private boolean allowTight;
    private boolean allowAboveBuildLimit;
    private boolean allowAboveRoof;
    private final Semaphore npfSema = new Semaphore(1);

    private static final int SHORT_LANDING_COLUMN_HEIGHT = 15;
    private static final int LONG_LANDING_COLUMN_HEIGHT = 39;
    private static final long LANDING_SEARCH_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(25); // half a tick
    private int landingColumnHeight = SHORT_LANDING_COLUMN_HEIGHT;
    private Set<BetterBlockPos> badLandingSpots = new HashSet<>();
    private LandingSearchState landingSearchState;
    private int takeoffAirTicks;
    private int takeoffBoostTicks;
    private boolean pendingTakeoffBoost;
    private boolean landingUsedMlg;

    @Override
    public void onLostControl() {
        onLostControl(true);
    }

    public void onLostControl(boolean destroyNpf) {
        this.state = State.START_FLYING; // TODO: null state?
        this.goingToLandingSpot = false;
        this.landingSpot = null;
        this.landingSearchState = null;
        this.reachedGoal = false;
        this.goal = null;
        this.takeoffAirTicks = 0;
        this.takeoffBoostTicks = 0;
        this.pendingTakeoffBoost = false;
        this.landingUsedMlg = false;
        destroyBehaviorAsync();
        if (destroyNpf) {
            destroyNpfContextAsync();
        }
    }

    private ElytraProcess(Winton winton) {
        super(winton);
        winton.getGameEventHandler().registerEventListener(this);
    }

    public static IElytraProcess create(final Winton winton) {
        return NetherPathfinderContext.isSupported()
                ? new ElytraProcess(winton)
                : new NullElytraProcess(winton);
    }

    @Override
    public boolean isActive() {
        return this.behavior != null;
    }

    @Override
    public void resetState() {
        BlockPos destination = this.currentDestination();
        this.onLostControl();
        if (destination != null) {
            this.pathTo(destination);
            this.repackChunks();
        }
    }

    private static final String AUTO_JUMP_FAILURE_MSG = "Failed to compute a walking path to a spot to jump off from. Consider starting from a higher location, near an overhang. Or, you can disable elytraAutoJump and just manually begin gliding.";

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        try {
            final long seedSetting = Winton.settings().elytraNetherSeed.value;
            if (seedSetting != this.behavior.npfContext.getSeed()) {
                logDirect("Nether seed changed, recalculating path");
                this.resetState();
            }
            if (predictingTerrain != Winton.settings().elytraPredictTerrain.value && ctx.player().level().dimension() == Level.NETHER) {
                logDirect("elytraPredictTerrain setting changed, recalculating path from scratch");
                predictingTerrain = Winton.settings().elytraPredictTerrain.value;
                this.resetState();
            }
            if (allowTight != Winton.settings().elytraAllowTightSpaces.value) {
                logDirect("elytraAllowTightSpaces setting changed, recalculating path from scratch");
                allowTight = Winton.settings().elytraAllowTightSpaces.value;
                this.resetState();
            }
            if (allowAboveBuildLimit != Winton.settings().elytraAllowAboveBuildLimit.value) {
                logDirect("elytraAllowAboveBuildLimit setting changed, recalculating path from scratch");
                allowAboveBuildLimit = Winton.settings().elytraAllowAboveBuildLimit.value;
                this.resetState();
            }
            if (allowAboveRoof != Winton.settings().elytraAllowAboveRoof.value && ctx.player().level().dimension() == Level.NETHER) {
                logDirect("elytraAllowAboveRoof setting changed, recalculating path from scratch");
                allowAboveRoof = Winton.settings().elytraAllowAboveRoof.value;
                this.resetState();
            }
        } catch (IllegalArgumentException e) {
            logDirect(e.getMessage(), ChatFormatting.RED);
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        this.behavior.onTick();

        if (calcFailed) {
            onLostControl();
            logDirect(AUTO_JUMP_FAILURE_MSG);
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        boolean safetyLanding = false;
        if (ctx.player().isFallFlying() && shouldLandForSafety()) {
            if (Winton.settings().elytraAllowEmergencyLand.value) {
                logDirect("Emergency landing - almost out of elytra durability or fireworks");
                safetyLanding = true;
            } else {
                logDirect("almost out of elytra durability or fireworks, but I'm going to continue since elytraAllowEmergencyLand is false");
            }
        }
        if (ctx.player().isFallFlying() && this.state != State.LANDING) {
            final BetterBlockPos dest = this.behavior != null ? this.behavior.destination : null;
            boolean nearDestXZ = false;
            if (dest != null) {
                double dx = ctx.player().getX() - (dest.x + 0.5);
                double dz = ctx.player().getZ() - (dest.z + 0.5);
                nearDestXZ = dx * dx + dz * dz < 32 * 32;
            }

            // Land when over destination XZ — never pathTo0 a new air marker (that caused orbit)
            if (nearDestXZ) {
                if (dest != null) {
                    int surfaceY = isChunkLoaded(dest) ? resolveSurfaceY(dest.x, dest.z) : dest.y;
                    this.landingColumnHeight = Math.max(6, (int) Math.ceil(ctx.player().getY()) - surfaceY);
                    this.landingSpot = new BetterBlockPos(dest.x, surfaceY + 2, dest.z);
                    this.goingToLandingSpot = true;
                    this.reachedGoal = true;
                    this.state = State.LANDING;
                    if (Winton.settings().notificationOnPathComplete.value) {
                        logNotification("Pathing complete", false);
                    }
                    logDirect("Descending to surface...");
                }
            } else if (safetyLanding && dest != null && !goingToLandingSpot) {
                // Emergency but still far — land when we get close
                this.goingToLandingSpot = true;
                int surfaceY = isChunkLoaded(dest) ? resolveSurfaceY(dest.x, dest.z) : dest.y;
                this.landingSpot = new BetterBlockPos(dest.x, surfaceY + 2, dest.z);
            }
        }

        if (ctx.player().isFallFlying()) {
            if (this.state == State.LANDING) {
                return tickSafeLanding();
            }

            if (this.pendingTakeoffBoost) {
                this.pendingTakeoffBoost = false;
                this.takeoffBoostTicks = 25;
            }
            behavior.landingMode = false;
            this.goal = null;
            this.state = State.FLYING;
            winton.getInputOverrideHandler().clearAllKeys();
            if (this.behavior.npfContext.tryAcquireReadLock()) {
                try {
                    if (this.takeoffBoostTicks > 0) {
                        this.takeoffBoostTicks--;
                        behavior.forceFireworkThisTick = true;
                    }
                    // Retry empty path at most every 40 ticks (was every tick → rebuild spam)
                    if (this.behavior.pathManager.getPath().isEmpty()) {
                        if ((ctx.player().tickCount % 40) == 0) {
                            this.behavior.pathTo();
                        }
                    }
                    behavior.tick();
                } finally {
                    this.behavior.npfContext.releaseReadLock();
                }
            }
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        } else if (this.state == State.LANDING) {
            // Touchdown — pick up MLG water if we placed it
            if (landingUsedMlg) {
                tryPickupLandingWater();
            }
            if (ctx.playerMotion().multiply(1, 0, 1).length() > 0.001) {
                logDirect("Landed, but still moving, waiting for velocity to die down... ");
                winton.getInputOverrideHandler().setInputForceState(Input.SNEAK, true);
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            logDirect("Done :)");
            winton.getInputOverrideHandler().clearAllKeys();
            this.onLostControl();
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Ground takeoff: hop + open elytra (works on flat ground; no cliff required)
        if (this.state == State.FLYING || this.state == State.START_FLYING) {
            this.state = State.START_FLYING;
        }

        if (this.state == State.LOCATE_JUMP) {
            // Legacy cliff path — if we ever enter it, fall back to hop on failure
            if (shouldLandForSafety()) {
                logDirect("Not taking off, because elytra durability or fireworks are so low that I would immediately emergency land anyway.");
                onLostControl();
                return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
            }
            this.goal = null;
            this.state = State.START_FLYING;
        }

        if (this.state == State.PAUSE) {
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        if (this.state == State.GET_TO_JUMP) {
            this.state = State.START_FLYING;
        }

        if (this.state == State.START_FLYING) {
            if (!isSafeToCancel) {
                winton.getPathingBehavior().secretInternalSegmentCancel();
            }
            winton.getInputOverrideHandler().clearAllKeys();
            if (ctx.player().isFallFlying()) {
                this.takeoffAirTicks = 0;
                this.state = State.FLYING;
                if (this.behavior != null && this.behavior.pathManager.getPath().isEmpty()) {
                    this.behavior.pathTo();
                }
            } else if (ctx.player().onGround()) {
                this.takeoffAirTicks = 0;
                if (Winton.settings().elytraAutoJump.value) {
                    // Hold jump + walk: vanilla re-jumps on landing
                    winton.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
                    winton.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
                }
            } else {
                this.takeoffAirTicks++;
                // Deploy elytra as soon as we're clearly airborne
                double vy = ctx.player().getDeltaMovement().y;
                if (this.takeoffAirTicks >= 1 && (vy <= 0.0 || ctx.player().fallDistance > 0.1f || this.takeoffAirTicks >= 4)) {
                    winton.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
                    this.pendingTakeoffBoost = true;
                }
            }
        }
        return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
    }

    /**
     * Safe touchdown: flare (no bucket) or MLG water bucket (if available, not Nether).
     */
    private PathingCommand tickSafeLanding() {
        final BetterBlockPos endPos = this.landingSpot != null ? this.landingSpot : behavior.pathManager.path.getLast();
        if (endPos == null) {
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }

        int groundY = isChunkLoaded(endPos) ? resolveSurfaceY(endPos.x, endPos.z) : (endPos.y - 6);
        Vec3 from = ctx.player().position();
        double heightAbove = from.y - groundY;
        double vy = ctx.player().getDeltaMovement().y;
        float yaw = RotationUtils.calcRotationFromVec3d(
                from,
                new Vec3(endPos.x + 0.5, groundY + 0.1, endPos.z + 0.5),
                ctx.playerRotations()
        ).getYaw();

        winton.getInputOverrideHandler().clearAllKeys();

        final boolean canMlg = canUseWaterMlg();
        if (canMlg && heightAbove < 8.0) {
            // Hard approach OK — dump water under feet
            ensureWaterBucketSelected();
            winton.getLookBehavior().updateTarget(new Rotation(yaw, 90.0f), true);
            if (heightAbove < 4.5 || ctx.playerRotations().getPitch() > 60.0f) {
                BlockPos below = BlockPos.containing(from.x, groundY, from.z);
                BlockPos placeOn = below;
                // Prefer the solid top under us
                if (ctx.world().getBlockState(below).isAir() && !ctx.world().getBlockState(below.below()).isAir()) {
                    placeOn = below.below();
                }
                Vec3 hit = new Vec3(placeOn.getX() + 0.5, placeOn.getY() + 1.0, placeOn.getZ() + 0.5);
                BlockHitResult bhr = new BlockHitResult(hit, Direction.UP, placeOn, false);
                winton.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), InteractionHand.MAIN_HAND, bhr);
                landingUsedMlg = true;
            }
        } else {
            // No bucket (or still high): descend gently, then flare to bleed speed
            float pitch;
            if (heightAbove > 25) {
                pitch = 28.0f; // shallow dive
            } else if (heightAbove > 12) {
                pitch = 18.0f;
            } else if (heightAbove > 6) {
                pitch = 5.0f; // level off
            } else {
                // Flare: look up to kill vertical speed before impact
                pitch = vy < -0.5 ? -35.0f : -15.0f;
            }
            winton.getLookBehavior().updateTarget(new Rotation(yaw, pitch), false);
        }

        if (from.y < groundY - 3) {
            logDirect("bad landing spot, trying again...");
            landingSpotIsBad(endPos);
        }
        return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
    }

    private boolean canUseWaterMlg() {
        if (ctx.world() == null || ctx.world().dimension() == Level.NETHER) {
            return false;
        }
        return findHotbarOrInv(Items.WATER_BUCKET) || ensureWaterBucketSelected();
    }

    private boolean findHotbarOrInv(Item item) {
        var inv = ctx.player().getInventory();
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean ensureWaterBucketSelected() {
        var inv = ctx.player().getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(Items.WATER_BUCKET)) {
                inv.setSelectedSlot(i);
                return true;
            }
        }
        if (Winton.settings().allowInventory.value) {
            return winton.getInventoryBehavior().throwaway(true, stack -> stack.is(Items.WATER_BUCKET), true);
        }
        return false;
    }

    private void tryPickupLandingWater() {
        var inv = ctx.player().getInventory();
        int empty = -1;
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(Items.BUCKET)) {
                empty = i;
                break;
            }
        }
        if (empty < 0) {
            return;
        }
        inv.setSelectedSlot(empty);
        BlockPos feet = ctx.playerFeet();
        BlockPos[] candidates = new BlockPos[]{feet, feet.below(), feet.above()};
        for (BlockPos p : candidates) {
            if (ctx.world().getBlockState(p).getBlock() == Blocks.WATER
                    && ctx.world().getFluidState(p).isSource()) {
                winton.getLookBehavior().updateTarget(new Rotation(ctx.playerRotations().getYaw(), 90.0f), true);
                Vec3 hit = new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                BlockHitResult bhr = new BlockHitResult(hit, Direction.UP, p, false);
                winton.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), InteractionHand.MAIN_HAND, bhr);
                landingUsedMlg = false;
                return;
            }
        }
    }

    public void landingSpotIsBad(BetterBlockPos endPos) {
        badLandingSpots.add(endPos);
        goingToLandingSpot = false;
        this.landingSpot = null;
        this.landingSearchState = null;
        this.state = State.FLYING;
    }

    private void destroyBehaviorAsync() {
        ElytraBehavior behavior = this.behavior;
        if (behavior != null) {
            this.behavior = null;
            Winton.getExecutor().execute(() -> {
                behavior.destroy();
            });
        }
    }

    @Override
    public double priority() {
        return 0; // higher priority than CustomGoalProcess
    }

    @Override
    public String displayName0() {
        return "Elytra - " + this.state.description;
    }

    @Override
    public void repackChunks() {
        if (this.npfContext == null) return;

        ChunkSource chunkProvider = ctx.world().getChunkSource();
        BetterBlockPos playerPos = ctx.playerFeet();

        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;

        int minX = playerChunkX - 40;
        int minZ = playerChunkZ - 40;
        int maxX = playerChunkX + 40;
        int maxZ = playerChunkZ + 40;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                LevelChunk chunk = chunkProvider.getChunk(x, z, false);

                if (chunk != null && !chunk.isEmpty()) {
                    npfContext.queueForPacking(chunk);
                }
            }
        }
    }

    @Override
    public BlockPos currentDestination() {
        return this.behavior != null ? this.behavior.destination : null;
    }

    @Override
    public List<BetterBlockPos> getPath() {
        return this.behavior != null ? behavior.pathManager.getPath() : Collections.emptyList();
    }

    @Override
    public void pathTo(BlockPos destination) {
        if (!isSupportedPos(destination)) {
            throw new IllegalArgumentException("The goal must be within bounds to use elytra flight.");
        }

        if (ctx.player() != null && !isSupportedPos(ctx.playerFeet())) {
            throw new IllegalArgumentException("The player must be within bounds to use elytra flight.");
        }

        this.pathTo0(destination, false);
    }

    private void pathTo0(BlockPos destination, boolean appendDestination) {
        if (ctx.player() == null || ctx.world() == null) {
            return;
        }
        this.onLostControl(false);
        this.predictingTerrain = ctx.player().level().dimension() == Level.NETHER && Winton.settings().elytraPredictTerrain.value;
        this.allowTight = Winton.settings().elytraAllowTightSpaces.value;
        this.allowAboveBuildLimit = Winton.settings().elytraAllowAboveBuildLimit.value;
        this.allowAboveRoof = Winton.settings().elytraAllowAboveRoof.value;
        this.behavior = new ElytraBehavior(this.winton, this, getNpfContext(), destination, appendDestination);

        this.repackChunks();
        this.behavior.pathTo();
    }

    @Override
    public void pathTo(Goal iGoal) {
        final int x;
        final int y;
        final int z;
        if (iGoal instanceof GoalXZ) {
            GoalXZ goal = (GoalXZ) iGoal;
            x = goal.getX();
            z = goal.getZ();
            y = resolveSurfaceY(x, z);
        } else if (iGoal instanceof GoalBlock) {
            GoalBlock goal = (GoalBlock) iGoal;
            x = goal.x;
            y = goal.y;
            z = goal.z;
        } else {
            throw new IllegalArgumentException("The goal must be a GoalXZ or GoalBlock");
        }

        this.pathTo(new BlockPos(x, y, z));
    }

    /**
     * Highest solid/surface Y at XZ (feet stand here). Used when Y is omitted.
     */
    public int resolveSurfaceY(int x, int z) {
        if (ctx.world() == null) {
            return 64;
        }
        if (ctx.world().dimension() == Level.NETHER) {
            // No overworld-style heightmap roof; cruise mid-nether
            return 64;
        }
        return ctx.world().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    @Override
    public void pathToXZ(int x, int z) {
        this.pathTo(new BlockPos(x, resolveSurfaceY(x, z), z));
    }

    private boolean isSupportedPos(BlockPos pos) {
        final boolean isNether = ctx.world().dimension() == Level.NETHER;
        final int minY = ctx.world().dimensionType().minY();
        final int maxY = (isNether && !Winton.settings().elytraAllowAboveRoof.value) ? 127 : Math.min(minY + 384, ctx.world().dimensionType().height() + minY);

        final boolean aboveRoof = Winton.settings().elytraAllowAboveRoof.value;
        final boolean aboveBuild = Winton.settings().elytraAllowAboveBuildLimit.value;

        final boolean enforceMaxY = isNether ? !(aboveRoof && aboveBuild) : !aboveBuild;

        if (pos.getY() < minY) {
            return false;
        }

        return !enforceMaxY || pos.getY() < maxY;
    }

    private boolean shouldLandForSafety() {
        ItemStack chest = ctx.player().getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() != Items.ELYTRA || chest.getMaxDamage() - chest.getDamageValue() < Winton.settings().elytraMinimumDurability.value) {
            // elytrabehavior replaces when durability <= minimumDurability, so if durability < minimumDurability then we can reasonably assume that the elytra will soon be broken without replacement
            return true;
        }

        NonNullList<ItemStack> inv = ctx.player().getInventory().getNonEquipmentItems();
        int qty = 0;
        for (int i = 0; i < 36; i++) {
            if (ElytraBehavior.isFireworks(inv.get(i))) {
                qty += inv.get(i).getCount();
            }
        }
        if (qty <= Winton.settings().elytraMinFireworksBeforeLanding.value) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isLoaded() {
        return true;
    }

    @Override
    public boolean isSafeToCancel() {
        return !this.isActive() || !(this.state == State.FLYING || this.state == State.START_FLYING);
    }

    public enum State {
        LOCATE_JUMP("Finding spot to jump off"),
        PAUSE("Waiting for elytra path"),
        GET_TO_JUMP("Walking to takeoff"),
        START_FLYING("Begin flying"),
        FLYING("Flying"),
        LANDING("Landing");

        public final String description;

        State(String desc) {
            this.description = desc;
        }
    }

    @Override
    public void onRenderPass(RenderEvent event) {
        if (this.behavior != null) this.behavior.onRenderPass(event);
    }

    @Override
    public void onWorldEvent(WorldEvent event) {
        if (event.getWorld() != null && event.getState() == EventState.POST) {
            // Exiting the world, just destroy
            destroyBehaviorAsync();
        }
    }

    @Override
    public void onChunkEvent(ChunkEvent event) {
        if (this.behavior != null) this.behavior.onChunkEvent(event);
    }

    @Override
    public void onBlockChange(BlockChangeEvent event) {
        if (this.behavior != null) this.behavior.onBlockChange(event);
    }

    @Override
    public void onReceivePacket(PacketEvent event) {
        if (this.behavior != null) this.behavior.onReceivePacket(event);
    }

    @Override
    public void onPostTick(TickEvent event) {
        IWintonProcess procThisTick = winton.getPathingControlManager().mostRecentInControl().orElse(null);
        if (this.behavior != null && procThisTick == this) this.behavior.onPostTick(event);
    }

    /**
     * Custom calculation context which makes the player fall into lava
     */
    public static final class WalkOffCalculationContext extends CalculationContext {

        public WalkOffCalculationContext(IWinton winton) {
            super(winton, true);
            this.allowFallIntoLava = true;
            this.minFallHeight = 8;
            this.maxFallHeightNoWater = 10000;
        }

        @Override
        public double costOfPlacingAt(int x, int y, int z, BlockState current) {
            return COST_INF;
        }

        @Override
        public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
            return COST_INF;
        }

        @Override
        public double placeBucketCost() {
            return COST_INF;
        }
    }

    private static boolean isInBounds(Level dim, BlockPos pos) {
        DimensionType dimType = dim.dimensionType();
        int minY = dimType.minY();
        int maxY = (dim.dimension() == Level.NETHER && !Winton.settings().elytraAllowAboveRoof.value) ? 127 : Math.min(minY + 384, dimType.height() + minY);
        return pos.getY() >= minY && pos.getY() < maxY;
    }

    private boolean isSafeBlock(Block block) {
        return block == Blocks.NETHERRACK || block == Blocks.GRAVEL || block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL || (block == Blocks.NETHER_BRICKS && Winton.settings().elytraAllowLandOnNetherFortress.value)
                || block == Blocks.STONE || block == Blocks.DEEPSLATE || block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT
                || block == Blocks.SAND || block == Blocks.RED_SAND || block == Blocks.TERRACOTTA
                || block == Blocks.SNOW || block == Blocks.SNOW_BLOCK || block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE
                || block == Blocks.MYCELIUM || block == Blocks.PODZOL || block == Blocks.DIRT_PATH
                || block == Blocks.DARK_OAK_LEAVES || block == Blocks.JUNGLE_LEAVES || block == Blocks.OAK_LEAVES || block == Blocks.BIRCH_LEAVES
                || block == Blocks.SPRUCE_LEAVES || block == Blocks.ACACIA_LEAVES || block == Blocks.CHERRY_LEAVES || block == Blocks.MANGROVE_LEAVES
                || block == Blocks.END_STONE || block == Blocks.BEDROCK
                || block == Blocks.OBSIDIAN || block == Blocks.COBBLESTONE || block == Blocks.MOSS_BLOCK || block == Blocks.MUD;
    }

    private boolean isSafeBlock(BlockPos pos) {
        return isSafeBlock(ctx.world().getBlockState(pos).getBlock());
    }

    private boolean isAtEdge(BlockPos pos) {
        return !isSafeBlock(pos.north())
                || !isSafeBlock(pos.south())
                || !isSafeBlock(pos.east())
                || !isSafeBlock(pos.west())
                // corners
                || !isSafeBlock(pos.north().west())
                || !isSafeBlock(pos.north().east())
                || !isSafeBlock(pos.south().west())
                || !isSafeBlock(pos.south().east());
    }

    private boolean isColumnAir(BlockPos landingSpot, int minHeight) {
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos(landingSpot.getX(), landingSpot.getY(), landingSpot.getZ());
        final int maxY = mut.getY() + minHeight;
        for (int y = mut.getY() + 1; y <= maxY; y++) {
            mut.set(mut.getX(), y, mut.getZ());
            if (!(ctx.world().getBlockState(mut).getBlock() instanceof AirBlock)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAirBubble(BlockPos pos) {
        final int radius = 4; // Half of the full width, rounded down, as we're counting blocks in each direction from the center
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    mut.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    if (!(ctx.world().getBlockState(mut).getBlock() instanceof AirBlock)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private BetterBlockPos checkLandingSpot(BlockPos pos, LongOpenHashSet checkedSpots) {
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
        while (mut.getY() >= ctx.world().dimensionType().minY()) {
            if (checkedSpots.contains(mut.asLong())) {
                return null;
            }
            checkedSpots.add(mut.asLong());
            Block block = ctx.world().getBlockState(mut).getBlock();

            if (isSafeBlock(block)) {
                if (!isAtEdge(mut)) {
                    return new BetterBlockPos(mut);
                }
                return null;
            } else if (block != Blocks.AIR) {
                return null;
            }
            mut.set(mut.getX(), mut.getY() - 1, mut.getZ());
        }
        return null; // void
    }

    private BetterBlockPos findSafeLandingSpot(BetterBlockPos start) {
        final boolean useHeightmap = ctx.player().getY() > ctx.world().getHeight(Heightmap.Types.MOTION_BLOCKING, start.getX(), start.getZ());
        if (this.landingSearchState == null || !this.landingSearchState.isCompatible(start, useHeightmap)) {
            this.landingSearchState = new LandingSearchState(start, this.behavior.destination, useHeightmap);
        } else {
            this.landingSearchState.updateStartPosition(start);
        }

        BetterBlockPos landingSpot = this.landingSearchState.advance();
        if (landingSpot != null || this.landingSearchState.exhausted) {
            this.landingSearchState = null;
        }
        return landingSpot;
    }

    private boolean nearLandingApproach(BetterBlockPos approach) {
        if (approach == null || ctx.player() == null) {
            return false;
        }
        double dx = ctx.player().getX() - (approach.x + 0.5);
        double dz = ctx.player().getZ() - (approach.z + 0.5);
        double horizSq = dx * dx + dz * dz;
        // Close enough horizontally — don't require sitting inside the exact air voxel
        if (horizSq > 12 * 12) {
            return false;
        }
        // And not far below the approach marker (would mean we already overshot badly)
        return ctx.player().getY() > approach.y - this.landingColumnHeight - 5;
    }

    private boolean isChunkLoaded(BetterBlockPos pos) {
        return ctx.world().getChunkSource().hasChunk(pos.x >> 4, pos.z >> 4);
    }

    private final class LandingSearchState {
        private final BetterBlockPos origin;
        private final boolean useHeightmap;
        private final Queue<BetterBlockPos> queue;
        private final Set<BetterBlockPos> visited = new HashSet<>();
        private final LongOpenHashSet checkedPositions = new LongOpenHashSet();
        private boolean exhausted;

        private LandingSearchState(BetterBlockPos origin, BetterBlockPos dest, boolean useHeightmap) {
            this.origin = origin;
            this.useHeightmap = useHeightmap;

            final BetterBlockPos target = isChunkLoaded(dest) ? dest : origin;
            this.queue = new PriorityQueue<>(Comparator.<BetterBlockPos>comparingInt(pos -> (pos.x - target.x) * (pos.x - target.x) + (pos.z - target.z) * (pos.z - target.z)).thenComparingInt(pos -> -pos.y));
            this.queue.add(target);
        }

        private boolean isCompatible(BetterBlockPos start, boolean useHeightmap) {
            // Restart if we've moved more than a chunk so the priority adjusts and newly loaded chunks get revisited
            return this.useHeightmap == useHeightmap && this.origin.distanceSq(start) <= (16 * 16);
        }

        private void updateStartPosition(BetterBlockPos start) {
            if (this.visited.add(start)) {
                this.queue.add(start);
            }
        }

        private BetterBlockPos advance() {
            final long deadline = System.nanoTime() + LANDING_SEARCH_BUDGET_NANOS;
            while (!this.queue.isEmpty()) {
                if (System.nanoTime() >= deadline) {
                    return null;
                }
                BetterBlockPos qPos = this.queue.poll();
                if (!isChunkLoaded(qPos)) {
                    continue;
                }
                BetterBlockPos landing = this.useHeightmap ? this.advanceHeightmap(qPos) : this.advanceUnderground(qPos);
                if (landing != null) {
                    return landing;
                }
            }
            this.exhausted = true;
            return null;
        }

        private BetterBlockPos advanceUnderground(BetterBlockPos pos) {
            if (isInBounds(ctx.world(), pos) && ctx.world().getBlockState(pos).getBlock() == Blocks.AIR) {
                BetterBlockPos actualLandingSpot = checkLandingSpot(pos, this.checkedPositions);
                if (actualLandingSpot != null) {
                    landingColumnHeight = SHORT_LANDING_COLUMN_HEIGHT;
                    if (isColumnAir(actualLandingSpot, landingColumnHeight) && hasAirBubble(actualLandingSpot.above(landingColumnHeight)) && !badLandingSpots.contains(actualLandingSpot.above(landingColumnHeight))) {
                        return actualLandingSpot.above(landingColumnHeight);
                    }
                }
                if (this.visited.add(pos.north())) this.queue.add(pos.north());
                if (this.visited.add(pos.east())) this.queue.add(pos.east());
                if (this.visited.add(pos.south())) this.queue.add(pos.south());
                if (this.visited.add(pos.west())) this.queue.add(pos.west());
                if (this.visited.add(pos.above())) this.queue.add(pos.above());
                if (this.visited.add(pos.below())) this.queue.add(pos.below());
            }
            return null;
        }

        private BetterBlockPos advanceHeightmap(BetterBlockPos qPos) {
            int height = ctx.world().getHeight(Heightmap.Types.MOTION_BLOCKING, qPos.getX(), qPos.getZ());
            BetterBlockPos pos = new BetterBlockPos(qPos.getX(), height + 1, qPos.getZ());
            if (isInBounds(ctx.world(), pos) && ctx.world().getBlockState(pos).getBlock() == Blocks.AIR) {
                BetterBlockPos actualLandingSpot = checkLandingSpot(pos, this.checkedPositions);
                if (actualLandingSpot != null) {
                    landingColumnHeight = ctx.playerFeet().y - actualLandingSpot.y < LONG_LANDING_COLUMN_HEIGHT ? SHORT_LANDING_COLUMN_HEIGHT : LONG_LANDING_COLUMN_HEIGHT;
                    if (hasAirBubble(actualLandingSpot.above(landingColumnHeight)) && !badLandingSpots.contains(actualLandingSpot.above(landingColumnHeight))) {
                        return actualLandingSpot.above(landingColumnHeight);
                    }
                }
                if (this.visited.add(pos.north())) this.queue.add(pos.north());
                if (this.visited.add(pos.east())) this.queue.add(pos.east());
                if (this.visited.add(pos.south())) this.queue.add(pos.south());
                if (this.visited.add(pos.west())) this.queue.add(pos.west());
            }
            return null;
        }
    }

    private NetherPathfinderContext getNpfContext() {
        if (this.npfContext == null) {
            if (ctx.world() == null) {
                throw new IllegalStateException("Cannot start elytra pathfinding without a loaded world");
            }
            npfSema.acquireUninterruptibly();
            try {
                java.nio.file.Path cacheDir = null;
                if (Winton.settings().elytraUseCache.value) {
                    WorldData worldData = winton.getWorldProvider().getCurrentWorld();
                    if (worldData != null) {
                        cacheDir = worldData.directory.resolve("cache");
                    }
                }
                this.npfContext = new NetherPathfinderContext(
                        Winton.settings().elytraNetherSeed.value,
                        cacheDir,
                        ctx.world()
                );
            } catch (Throwable t) {
                npfSema.release();
                throw t;
            }
        }
        return this.npfContext;
    }

    private void destroyNpfContextAsync() {
        NetherPathfinderContext npf = this.npfContext;
        if (npf != null) {
            this.npfContext = null;
            Winton.getExecutor().execute(() -> {
                npf.destroy();
                npfSema.release();
            });
        }
    }
}
