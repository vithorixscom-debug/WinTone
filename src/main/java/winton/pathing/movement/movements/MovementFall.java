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

package winton.pathing.movement.movements;

import winton.Winton;
import winton.api.IWinton;
import winton.api.pathing.movement.MovementStatus;
import winton.api.utils.BetterBlockPos;
import winton.api.utils.IPlayerContext;
import winton.api.utils.Rotation;
import winton.api.utils.RotationUtils;
import winton.api.utils.VecUtils;
import winton.api.utils.input.Input;
import winton.pathing.movement.CalculationContext;
import winton.pathing.movement.Movement;
import winton.pathing.movement.MovementHelper;
import winton.pathing.movement.MovementState;
import winton.pathing.movement.MovementState.MovementTarget;
import winton.utils.pathing.MutableMoveResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class MovementFall extends Movement {

    public MovementFall(IWinton winton, BetterBlockPos src, BetterBlockPos dest) {
        super(winton, src, dest, MovementFall.buildPositionsToBreak(src, dest));
    }

    @Override
    public double calculateCost(CalculationContext context) {
        MutableMoveResult result = new MutableMoveResult();
        MovementDescend.cost(context, src.x, src.y, src.z, dest.x, dest.z, result);
        if (result.y != dest.y) {
            return COST_INF; // doesn't apply to us, this position is a descend not a fall
        }
        return result.cost;
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        Set<BetterBlockPos> set = new HashSet<>();
        set.add(src);
        for (int y = src.y - dest.y; y >= 0; y--) {
            set.add(dest.above(y));
        }
        return set;
    }

    private boolean willPlaceBucket() {
        CalculationContext context = new CalculationContext(winton);
        MutableMoveResult result = new MutableMoveResult();
        return MovementDescend.dynamicFallCost(context, src.x, src.y, src.z, dest.x, dest.z, 0, context.get(dest.x, src.y - 2, dest.z), result);
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }

        BlockPos playerFeet = ctx.playerFeet();
        Rotation toDest = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), VecUtils.getBlockPosCenter(dest), ctx.playerRotations());
        Rotation targetRotation = null;
        BlockState destState = ctx.world().getBlockState(dest);
        Block destBlock = destState.getBlock();

        if (ctx.world().getBlockState(dest.below()).is(Blocks.MAGMA_BLOCK) && MovementHelper.steppingOnBlocks(ctx).stream().allMatch(block -> MovementHelper.canWalkThrough(ctx, block))) {
            state.setInput(Input.SNEAK, true);
        }

        boolean isWater = destState.getFluidState().getType() instanceof WaterFluid;
        if (!isWater && willPlaceBucket() && !playerFeet.equals(dest)) {
            if (ctx.world().dimension() == Level.NETHER) {
                return state.setStatus(MovementStatus.UNREACHABLE);
            }
            int waterSlot = findHotbarItem(ctx, Items.WATER_BUCKET);
            if (waterSlot < 0 && Winton.settings().allowInventory.value) {
                ((Winton) winton).getInventoryBehavior().throwaway(true, stack -> stack.is(Items.WATER_BUCKET), true);
                waterSlot = findHotbarItem(ctx, Items.WATER_BUCKET);
            }
            if (waterSlot < 0) {
                return state.setStatus(MovementStatus.UNREACHABLE);
            }

            double fallLeft = ctx.player().position().y - dest.getY();
            // Prep bucket early; click when near landing
            if (fallLeft < ctx.playerController().getBlockReachDistance() + 2.0 && !ctx.player().onGround()) {
                ctx.player().getInventory().setSelectedSlot(waterSlot);
                targetRotation = new Rotation(toDest.getYaw(), 90.0F);

                // Force MLG — smoothLook often never hits isLookingAt before impact
                if (ctx.isLookingAt(dest) || ctx.isLookingAt(dest.below())
                        || ctx.playerRotations().getPitch() > 55.0F
                        || fallLeft < 3.5) {
                    state.setInput(Input.CLICK_RIGHT, true);
                }
            }
        }
        if (targetRotation != null) {
            state.setTarget(new MovementTarget(targetRotation, true));
        } else {
            state.setTarget(new MovementTarget(toDest, false));
        }
        if (playerFeet.equals(dest) && (ctx.player().position().y - playerFeet.getY() < 0.094 || isWater)) { // 0.094 because lilypads
            if (isWater) { // only match water, not flowing water (which we cannot pick up with a bucket)
                int emptySlot = findHotbarItem(ctx, Items.BUCKET);
                if (emptySlot >= 0) {
                    ctx.player().getInventory().setSelectedSlot(emptySlot);
                    if (ctx.player().getDeltaMovement().y >= 0) {
                        return state.setInput(Input.CLICK_RIGHT, true);
                    } else {
                        return state;
                    }
                } else {
                    if (ctx.player().getDeltaMovement().y >= 0) {
                        return state.setStatus(MovementStatus.SUCCESS);
                    } // don't else return state; we need to stay centered because this water might be flowing under the surface
                }
            } else {
                return state.setStatus(MovementStatus.SUCCESS);
            }
        }
        Vec3 destCenter = VecUtils.getBlockPosCenter(dest); // we are moving to the 0.5 center not the edge (like if we were falling on a ladder)
        if (Math.abs(ctx.player().position().x + ctx.player().getDeltaMovement().x - destCenter.x) > 0.1 || Math.abs(ctx.player().position().z + ctx.player().getDeltaMovement().z - destCenter.z) > 0.1) {
            if (!ctx.player().onGround() && Math.abs(ctx.player().getDeltaMovement().y) > 0.4) {
                state.setInput(Input.SNEAK, true);
            }
            state.setInput(Input.MOVE_FORWARD, true);
        }
        Vec3i avoid = Optional.ofNullable(avoid()).map(Direction::getUnitVec3i).orElse(null);
        if (avoid == null) {
            avoid = src.subtract(dest);
        } else {
            double dist = Math.abs(avoid.getX() * (destCenter.x - avoid.getX() / 2.0 - ctx.player().position().x)) + Math.abs(avoid.getZ() * (destCenter.z - avoid.getZ() / 2.0 - ctx.player().position().z));
            if (dist < 0.6) {
                state.setInput(Input.MOVE_FORWARD, true);
            } else if (!ctx.player().onGround()) {
                state.setInput(Input.SNEAK, false);
            }
        }
        if (targetRotation == null) {
            Vec3 destCenterOffset = new Vec3(destCenter.x + 0.125 * avoid.getX(), destCenter.y, destCenter.z + 0.125 * avoid.getZ());
            state.setTarget(new MovementTarget(RotationUtils.calcRotationFromVec3d(ctx.playerHead(), destCenterOffset, ctx.playerRotations()), false));
        }
        return state;
    }

    private static int findHotbarItem(IPlayerContext ctx, Item item) {
        for (int i = 0; i < 9; i++) {
            if (ctx.player().getInventory().getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    private Direction avoid() {
        for (int i = 0; i < 15; i++) {
            BlockState state = ctx.world().getBlockState(ctx.playerFeet().below(i));
            if (state.getBlock() == Blocks.LADDER) {
                return state.getValue(LadderBlock.FACING);
            }
        }
        return null;
    }

    @Override
    public boolean safeToCancel(MovementState state) {
        // if we haven't started walking off the edge yet, or if we're in the process of breaking blocks before doing the fall
        // then it's safe to cancel this
        return ctx.playerFeet().equals(src) || state.getStatus() != MovementStatus.RUNNING;
    }

    private static BetterBlockPos[] buildPositionsToBreak(BetterBlockPos src, BetterBlockPos dest) {
        BetterBlockPos[] toBreak;
        int diffX = src.getX() - dest.getX();
        int diffZ = src.getZ() - dest.getZ();
        int diffY = Math.abs(src.getY() - dest.getY());
        toBreak = new BetterBlockPos[diffY + 2];
        for (int i = 0; i < toBreak.length; i++) {
            toBreak[i] = new BetterBlockPos(src.getX() - diffX, src.getY() + 1 - i, src.getZ() - diffZ);
        }
        return toBreak;
    }

    @Override
    protected boolean prepared(MovementState state) {
        if (state.getStatus() == MovementStatus.WAITING) {
            return true;
        }
        // only break if one of the first three needs to be broken
        // specifically ignore the last one which might be water
        for (int i = 0; i < 4 && i < positionsToBreak.length; i++) {
            if (!MovementHelper.canWalkThrough(ctx, positionsToBreak[i])) {
                return super.prepared(state);
            }
        }
        return true;
    }
}
