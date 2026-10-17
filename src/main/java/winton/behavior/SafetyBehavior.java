/*
 * This file is part of Winton.
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
 *
 * Based on Baritone (https://github.com/cabaletta/baritone).
 * Copyright (c) Baritone contributors. Modifications Copyright (c) WinTone contributors.
 */

package winton.behavior;

import winton.Winton;
import winton.api.event.events.TickEvent;
import winton.api.utils.Rotation;
import winton.api.utils.RotationUtils;
import winton.api.utils.input.Input;
import winton.pathing.movement.MovementHelper;
import winton.utils.BlockStateInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * On fire / low HP: put water under feet (look down + right-click). Also eat from hotbar.
 * Water escape / look forcing only while the bot is actually controlling movement.
 */
public final class SafetyBehavior extends Behavior {

	private int cooldown;
	private int extinguishHold;
	private boolean holdingEscape;
	private boolean holdingExtinguish;

	public SafetyBehavior(Winton winton) {
		super(winton);
	}

	private boolean botIsBusy() {
		if (winton.getPathingBehavior().isPathing()) {
			return true;
		}
		return winton.getPathingControlManager().mostRecentInControl().isPresent();
	}

	@Override
	public void onTick(TickEvent event) {
		if (event.getType() != TickEvent.Type.IN) {
			return;
		}
		if (!Winton.settings().autoSafety.value || ctx.player() == null || ctx.world() == null) {
			releaseSafetyInputs();
			return;
		}

		boolean onFire = ctx.player().isOnFire() || ctx.world().getFluidState(ctx.playerFeet()).is(FluidTags.LAVA);
		boolean inWater = ctx.player().isInWater() || ctx.world().getFluidState(ctx.playerFeet()).is(FluidTags.WATER)
			|| ctx.world().getFluidState(ctx.playerFeet().above()).is(FluidTags.WATER);
		boolean busy = botIsBusy();

		// Idle player: never force swim/jump look (felt like "mod still moving me")
		if (!busy && !onFire) {
			releaseSafetyInputs();
			return;
		}

		if (extinguishHold > 0) {
			extinguishHold--;
			holdExtinguish();
			if (extinguishHold == 0) {
				cooldown = 8;
				releaseExtinguishInputs();
			}
			return;
		}

		if (cooldown > 0) {
			cooldown--;
			releaseEscapeInputsIfIdle();
			return;
		}

		// Swim out only while bot is pathing/processing — not when stopped
		if (busy && inWater && !onFire && ctx.player().onGround()) {
			escapeWater();
			return;
		}
		releaseEscapeInputsIfIdle();

		float hearts = ctx.player().getHealth() / 2.0F;
		boolean lowHp = hearts <= Winton.settings().safetyHealthThreshold.value;
		boolean hungry = ctx.player().getFoodData().needsFood();

		if (onFire || (busy && lowHp)) {
			if (startExtinguish()) {
				if (Winton.settings().safetySealLava.value) {
					trySealNearbyLava();
				}
				return;
			}
		}
		// autoEat is OFF by default — never steal hotbar when idle / unintended
		if (busy && Winton.settings().autoEat.value && hungry && tryEat()) {
			cooldown = 20;
		}
	}

	private void escapeWater() {
		float yaw = ctx.playerRotations().getYaw();
		float pitch = ctx.player().isUnderWater() ? -55 : -28;
		winton.getLookBehavior().updateTarget(new Rotation(yaw, pitch), false);
		winton.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
		winton.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
		if (Winton.settings().sprintInWater.value) {
			winton.getInputOverrideHandler().setInputForceState(Input.SPRINT, true);
		}
		holdingEscape = true;
	}

	private void releaseEscapeInputsIfIdle() {
		if (!holdingEscape) {
			return;
		}
		holdingEscape = false;
		if (!winton.getPathingBehavior().isPathing()) {
			winton.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
			winton.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
			winton.getInputOverrideHandler().setInputForceState(Input.SPRINT, false);
		}
	}

	private void releaseExtinguishInputs() {
		if (!holdingExtinguish) {
			return;
		}
		holdingExtinguish = false;
		if (!winton.getPathingBehavior().isPathing()) {
			winton.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
		}
	}

	private void releaseSafetyInputs() {
		releaseEscapeInputsIfIdle();
		if (extinguishHold > 0) {
			extinguishHold = 0;
		}
		releaseExtinguishInputs();
	}

	private boolean tryEat() {
		var inv = ctx.player().getInventory();
		for (int i = 0; i < 9; i++) {
			ItemStack stack = inv.getItem(i);
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (food == null) {
				continue;
			}
			inv.setSelectedSlot(i);
			ctx.playerController().processRightClick(ctx.player(), ctx.world(), InteractionHand.MAIN_HAND);
			return true;
		}
		return false;
	}

	private boolean startExtinguish() {
		if (!ensureWaterBucketSelected()) {
			return false;
		}
		holdExtinguish();
		extinguishHold = 6;
		return true;
	}

	private void holdExtinguish() {
		if (!ensureWaterBucketSelected()) {
			return;
		}
		Rotation lookDown = new Rotation(ctx.playerRotations().getYaw(), 90);
		winton.getLookBehavior().updateTarget(lookDown, true);
		winton.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
		holdingExtinguish = true;
		BlockPos below = ctx.playerFeet().below();
		Vec3 hit = new Vec3(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5);
		BlockHitResult bhr = new BlockHitResult(hit, Direction.UP, below, false);
		ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), InteractionHand.MAIN_HAND, bhr);
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

	private void trySealNearbyLava() {
		if (!Winton.settings().allowPlace.value) {
			return;
		}
		int throwSlot = findThrowaway();
		if (throwSlot < 0) {
			return;
		}
		int r = Math.max(1, Winton.settings().safetySealLavaRadius.value);
		BlockPos feet = ctx.playerFeet();
		BlockStateInterface bsi = winton.bsi != null ? winton.bsi : new BlockStateInterface(ctx);
		ctx.player().getInventory().setSelectedSlot(throwSlot);
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos p = feet.offset(dx, dy, dz);
					if (!ctx.world().getFluidState(p).is(FluidTags.LAVA)) {
						continue;
					}
					BlockPos above = p.above();
					if (!ctx.world().getBlockState(above).isAir()) {
						continue;
					}
					if (MovementHelper.avoidBreaking(bsi, p.getX(), p.getY(), p.getZ(), bsi.get0(p))) {
						continue;
					}
					Vec3 hit = Vec3.atCenterOf(p);
					Rotation rot = RotationUtils.calcRotationFromVec3d(ctx.player().getEyePosition(1.0F), hit, ctx.playerRotations());
					winton.getLookBehavior().updateTarget(rot, true);
					BlockHitResult bhr = new BlockHitResult(hit, Direction.UP, p, false);
					ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), InteractionHand.MAIN_HAND, bhr);
					return;
				}
			}
		}
	}

	private int findThrowaway() {
		var inv = ctx.player().getInventory();
		var ok = Winton.settings().acceptableThrowawayItems.value;
		for (int i = 0; i < 9; i++) {
			if (ok.contains(inv.getItem(i).getItem())) {
				return i;
			}
		}
		return -1;
	}
}
