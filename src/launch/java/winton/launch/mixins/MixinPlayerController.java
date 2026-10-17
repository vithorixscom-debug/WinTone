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

import winton.utils.accessor.IPlayerControllerMP;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinPlayerController implements IPlayerControllerMP {

    @Accessor("isDestroying")
    @Override
    public abstract void setIsHittingBlock(boolean isHittingBlock);

    @Accessor("isDestroying")
    @Override
    public abstract boolean isHittingBlock();

    @Accessor("destroyBlockPos")
    @Override
    public abstract BlockPos getCurrentBlock();

    @Invoker("ensureHasSentCarriedItem")
    @Override
    public abstract void callSyncCurrentPlayItem();

    @Accessor("destroyDelay")
    @Override
    public abstract void setDestroyDelay(int destroyDelay);
}
