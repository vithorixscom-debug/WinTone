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

package winton.launch.mixins;

import winton.api.utils.accessor.IItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class MixinItemStack implements IItemStack {

    @Shadow
    @Final
    private Item item;

    @Unique
    private int wintonHash;

    @Shadow
    public abstract int getDamageValue();

    private void recalculateHash() {
        wintonHash = item == null ? -1 : item.hashCode() + getDamageValue();
    }

    @Inject(
            method = "setDamageValue",
            at = @At("TAIL")
    )
    private void onItemDamageSet(CallbackInfo ci) {
        recalculateHash();
    }

    @Override
    public int getWintonHash() {
        // cannot do this in an init mixin because silentlib likes creating new
        // items in getDamageValue, which we call in recalculateHash
        if (wintonHash == 0) recalculateHash();
        return wintonHash;
    }
}
