/*
 * This file is part of Winton.
 *
 * Derived from Tweakeroo (https://github.com/maruohon/tweakeroo).
 * Copyright (c) masa. Modifications Copyright (c) WinTone contributors.
 * Runtime for implemented client tweaks on Minecraft 1.21.11.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * LGPL-3.0-only. See LICENSE_TWEAKEROO.txt and NOTICE_TWEAKEROO.txt.
 */

package winton.tweakeroo;

import winton.api.event.events.TickEvent;
import winton.api.event.listener.AbstractGameEventListener;
import winton.menu.WintonFreecam;
import winton.menu.WintonLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;

/** Tick-driven Tweakeroo features (implemented subset). */
public final class TweakerooBehavior implements AbstractGameEventListener {
	private static TweakerooBehavior instance;
	private static final SimpleDateFormat CHAT_TIME = new SimpleDateFormat("HH:mm:ss");

	private float aimYaw, aimPitch;
	private boolean aimCaptured;
	private int periodicAttackTick;
	private int periodicUseTick;
	private BlockPos lastDeathLogged;

	private boolean freecamFromTweak;

	private TweakerooBehavior() {
	}

	public static TweakerooBehavior get() {
		if (instance == null) {
			instance = new TweakerooBehavior();
			TweakerooConfig.ensureLoaded();
		}
		return instance;
	}

	@Override
	public void onTick(TickEvent event) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			aimCaptured = false;
			return;
		}

		if (TweakerooConfig.feature(TweakFeature.GAMMA_OVERRIDE)) {
			try {
				mc.options.gamma().set(TweakerooConfig.gammaValue());
			} catch (Throwable ignored) {
			}
		}

		if (TweakerooConfig.feature(TweakFeature.FREE_CAMERA)) {
			if (!WintonFreecam.isActive()) {
				WintonFreecam.enable();
				freecamFromTweak = true;
			}
		} else if (freecamFromTweak) {
			WintonFreecam.disable();
			freecamFromTweak = false;
		}

		if (TweakerooConfig.feature(TweakFeature.PERMANENT_SNEAK)
			|| TweakerooConfig.feature(TweakFeature.FAKE_SNEAKING)) {
			mc.options.keyShift.setDown(true);
		}

		if (TweakerooConfig.feature(TweakFeature.PERMANENT_SPRINT)
			&& (mc.options.keyUp.isDown() || hasForward(p))) {
			p.setSprinting(true);
		}

		if (TweakerooConfig.feature(TweakFeature.FLY_SPEED) && p.getAbilities().flying) {
			p.getAbilities().setFlyingSpeed((float) TweakerooConfig.flySpeed());
		}

		if (TweakerooConfig.feature(TweakFeature.AIM_LOCK)
			|| TweakerooConfig.feature(TweakFeature.SNAP_AIM_LOCK)) {
			if (!aimCaptured) {
				aimYaw = p.getYRot();
				aimPitch = p.getXRot();
				aimCaptured = true;
			}
			p.setYRot(aimYaw);
			p.setXRot(aimPitch);
		} else if (TweakerooConfig.feature(TweakFeature.SNAP_AIM)) {
			float step = TweakerooConfig.snapYawStep();
			p.setYRot(Math.round(p.getYRot() / step) * step);
			aimCaptured = false;
		} else {
			aimCaptured = false;
		}

		// Hold / fast / periodic clicks — keep key state; private MC methods avoided
		if (TweakerooConfig.feature(TweakFeature.HOLD_ATTACK)) {
			mc.options.keyAttack.setDown(true);
		}
		if (TweakerooConfig.feature(TweakFeature.HOLD_USE)) {
			mc.options.keyUse.setDown(true);
		}
		if (TweakerooConfig.feature(TweakFeature.FAST_LEFT_CLICK) && mc.options.keyAttack.isDown()) {
			mc.options.keyAttack.setDown(true);
		}
		if (TweakerooConfig.feature(TweakFeature.FAST_RIGHT_CLICK) && mc.options.keyUse.isDown()) {
			mc.options.keyUse.setDown(true);
		}
		if (TweakerooConfig.feature(TweakFeature.PERIODIC_ATTACK)) {
			if (++periodicAttackTick >= TweakerooConfig.periodicAttackInterval()) {
				periodicAttackTick = 0;
				mc.options.keyAttack.setDown(true);
			}
		}
		if (TweakerooConfig.feature(TweakFeature.PERIODIC_USE)) {
			if (++periodicUseTick >= TweakerooConfig.periodicUseInterval()) {
				periodicUseTick = 0;
				mc.options.keyUse.setDown(true);
			}
		}

		if (TweakerooConfig.feature(TweakFeature.HAND_RESTOCK)) {
			restockHand(p);
		}
		if (TweakerooConfig.feature(TweakFeature.SWAP_ALMOST_BROKEN_TOOLS)
			|| TweakerooConfig.feature(TweakFeature.REPAIR_MODE)) {
			swapLowDurability(p);
		}

		if (TweakerooConfig.feature(TweakFeature.POTION_WARNING)) {
			for (MobEffectInstance eff : p.getActiveEffects()) {
				if (eff.getDuration() > 0 && eff.getDuration() < 200 && eff.getDuration() % 40 == 0) {
					p.displayClientMessage(Component.literal(WintonLang.format(
						"winton.tweak.msg.potion",
						eff.getEffect().value().getDisplayName().getString(),
						eff.getDuration() / 20)), true);
					break;
				}
			}
		}

		if (TweakerooConfig.feature(TweakFeature.PRINT_DEATH_COORDINATES) && p.getHealth() <= 0) {
			BlockPos pos = p.blockPosition();
			if (lastDeathLogged == null || !lastDeathLogged.equals(pos)) {
				lastDeathLogged = pos.immutable();
				p.displayClientMessage(Component.literal(WintonLang.format(
					"winton.tweak.msg.death", pos.getX(), pos.getY(), pos.getZ())), false);
			}
		} else if (p.getHealth() > 0) {
			lastDeathLogged = null;
		}

		if (TweakerooConfig.disable(TweakDisable.SOUNDS_ALL)) {
			mc.getSoundManager().stop();
		}
	}

	private static boolean hasForward(LocalPlayer p) {
		try {
			return p.input != null && p.input.hasForwardImpulse();
		} catch (Throwable t) {
			return false;
		}
	}

	public static String maybeTimestamp(String plain) {
		if (!TweakerooConfig.feature(TweakFeature.CHAT_TIMESTAMP) || plain == null) {
			return plain;
		}
		return "[" + CHAT_TIME.format(new Date()) + "] " + plain;
	}

	public static Float overrideFov(Float vanilla) {
		TweakerooConfig.ensureLoaded();
		if (TweakerooConfig.feature(TweakFeature.ZOOM)) {
			return TweakerooConfig.zoomFov();
		}
		if (TweakerooConfig.feature(TweakFeature.STATIC_FOV)) {
			return TweakerooConfig.staticFov();
		}
		return null;
	}

	public static Double overrideGamma() {
		if (TweakerooConfig.feature(TweakFeature.GAMMA_OVERRIDE)) {
			return TweakerooConfig.gammaValue();
		}
		return null;
	}

	private static void restockHand(LocalPlayer p) {
		ItemStack main = p.getMainHandItem();
		if (!main.isEmpty() && main.getCount() > 1) {
			return;
		}
		Inventory inv = p.getInventory();
		for (int i = 9; i < 36; i++) {
			ItemStack st = inv.getItem(i);
			if (!st.isEmpty() && (main.isEmpty() || ItemStack.isSameItemSameComponents(main, st))) {
				inv.setItem(inv.getSelectedSlot(), st.copy());
				inv.setItem(i, main.isEmpty() ? ItemStack.EMPTY : main.copy());
				return;
			}
		}
	}

	private static void swapLowDurability(LocalPlayer p) {
		ItemStack main = p.getMainHandItem();
		if (!main.isDamageableItem()) {
			return;
		}
		int left = main.getMaxDamage() - main.getDamageValue();
		if (left > TweakerooConfig.durabilitySwapThreshold()) {
			return;
		}
		Inventory inv = p.getInventory();
		int best = -1;
		int bestLeft = -1;
		for (int i = 0; i < 36; i++) {
			if (i == inv.getSelectedSlot()) {
				continue;
			}
			ItemStack st = inv.getItem(i);
			if (!st.isDamageableItem() || !ItemStack.isSameItem(main, st)) {
				continue;
			}
			int l = st.getMaxDamage() - st.getDamageValue();
			if (l > bestLeft) {
				bestLeft = l;
				best = i;
			}
		}
		if (best >= 0 && bestLeft > left) {
			ItemStack other = inv.getItem(best);
			inv.setItem(best, main.copy());
			inv.setItem(inv.getSelectedSlot(), other.copy());
		}
	}
}
