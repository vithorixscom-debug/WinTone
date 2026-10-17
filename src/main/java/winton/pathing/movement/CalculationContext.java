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

package winton.pathing.movement;

import winton.Winton;
import winton.api.IWinton;
import winton.api.pathing.movement.ActionCosts;
import winton.cache.WorldData;
import winton.pathing.precompute.PrecomputedData;
import winton.utils.BlockStateInterface;
import winton.utils.ToolSet;
import winton.utils.pathing.BetterWorldBorder;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static winton.api.pathing.movement.ActionCosts.COST_INF;

/**
 * @author Brady
 * @since 8/7/2018
 */
public class CalculationContext {

    public final boolean safeForThreadedUse;
    public final IWinton winton;
    public final Level world;
    public final WorldData worldData;
    public final BlockStateInterface bsi;
    public final ToolSet toolSet;
    public final boolean hasWaterBucket;
    public final boolean hasThrowaway;
    public final boolean canSprint;
    protected final double placeBlockCost; // protected because you should call the function instead
    public final boolean allowBreak;
    public final List<Block> allowBreakAnyway;
    public final boolean allowParkour;
    public final boolean allowParkourPlace;
    public final boolean allowJumpAtBuildLimit;
    public final boolean allowParkourAscend;
    public final boolean assumeWalkOnWater;
    public boolean allowFallIntoLava;
    public final int frostWalker;
    public final boolean allowDiagonalDescend;
    public final boolean allowDiagonalAscend;
    public final boolean allowDownward;
    public int minFallHeight;
    public int maxFallHeightNoWater;
    public final int maxFallHeightBucket;
    public final double waterWalkSpeed;
    public final double breakBlockAdditionalCost;
    public double backtrackCostFavoringCoefficient;
    public double jumpPenalty;
    public final double walkOnWaterOnePenalty;
    public final boolean allowWalkOnMagmaBlocks;
    public final BetterWorldBorder worldBorder;

    public final PrecomputedData precomputedData;

    public CalculationContext(IWinton winton) {
        this(winton, false);
    }

    public CalculationContext(IWinton winton, boolean forUseOnAnotherThread) {
        this.precomputedData = new PrecomputedData();
        this.safeForThreadedUse = forUseOnAnotherThread;
        this.winton = winton;
        LocalPlayer player = winton.getPlayerContext().player();
        this.world = winton.getPlayerContext().world();
        if (player == null || this.world == null) {
            throw new IllegalStateException("Cannot build CalculationContext without player/world");
        }
        this.worldData = (WorldData) winton.getPlayerContext().worldData();
        this.bsi = new BlockStateInterface(winton.getPlayerContext(), forUseOnAnotherThread);
        this.toolSet = new ToolSet(player);
        this.hasThrowaway = Winton.settings().allowPlace.value && ((Winton) winton).getInventoryBehavior().hasGenericThrowaway();
        this.hasWaterBucket = Winton.settings().allowWaterBucketFall.value
                && world.dimension() != Level.NETHER
                && hasWaterBucket(player, Winton.settings().allowInventory.value);
        this.canSprint = Winton.settings().allowSprint.value && player.getFoodData().getFoodLevel() > 6;
        this.placeBlockCost = Winton.settings().blockPlacementPenalty.value;
        this.allowBreak = Winton.settings().allowBreak.value;
        this.allowBreakAnyway = new ArrayList<>(Winton.settings().allowBreakAnyway.value);
        this.allowParkour = Winton.settings().allowParkour.value;
        this.allowParkourPlace = Winton.settings().allowParkourPlace.value;
        this.allowJumpAtBuildLimit = Winton.settings().allowJumpAtBuildLimit.value;
        this.allowParkourAscend = Winton.settings().allowParkourAscend.value;
        this.assumeWalkOnWater = Winton.settings().assumeWalkOnWater.value;
        this.allowFallIntoLava = false; // Super secret internal setting for ElytraBehavior
        // todo: technically there can now be datapack enchants that replace blocks with any other at any range
        int frostWalkerLevel = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemEnchantments itemEnchantments = winton.getPlayerContext()
                .player()
                .getItemBySlot(slot)
                .getEnchantments();
            for (Holder<Enchantment> enchant : itemEnchantments.keySet()) {
                if (enchant.is(Enchantments.FROST_WALKER)) {
                    frostWalkerLevel = itemEnchantments.getLevel(enchant);
                }
            }
        }
        this.frostWalker = frostWalkerLevel;
        this.allowDiagonalDescend = Winton.settings().allowDiagonalDescend.value;
        this.allowDiagonalAscend = Winton.settings().allowDiagonalAscend.value;
        this.allowDownward = Winton.settings().allowDownward.value;
        this.minFallHeight = 3; // Minimum fall height used by MovementFall
        this.maxFallHeightNoWater = Winton.settings().maxFallHeightNoWater.value;
        this.maxFallHeightBucket = Winton.settings().maxFallHeightBucket.value;
        float waterSpeedMultiplier = 1.0f;
        OUTER: for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemEnchantments itemEnchantments = winton.getPlayerContext()
                .player()
                .getItemBySlot(slot)
                .getEnchantments();
            for (Holder<Enchantment> enchant : itemEnchantments.keySet()) {
                List<EnchantmentAttributeEffect> effects = enchant.value()
                    .getEffects(EnchantmentEffectComponents.ATTRIBUTES);
                for (EnchantmentAttributeEffect effect : effects) {
                    if (effect.attribute().is(Attributes.WATER_MOVEMENT_EFFICIENCY.unwrapKey().get())) {
                        waterSpeedMultiplier = effect.amount().calculate(itemEnchantments.getLevel(enchant));
                        break OUTER;
                    }
                }
            }
        }
        this.waterWalkSpeed = ActionCosts.WALK_ONE_IN_WATER_COST * (1 - waterSpeedMultiplier) + ActionCosts.WALK_ONE_BLOCK_COST * waterSpeedMultiplier;
        this.breakBlockAdditionalCost = Winton.settings().blockBreakAdditionalPenalty.value;
        this.backtrackCostFavoringCoefficient = Winton.settings().backtrackCostFavoringCoefficient.value;
        this.jumpPenalty = Winton.settings().jumpPenalty.value;
        this.walkOnWaterOnePenalty = Winton.settings().walkOnWaterOnePenalty.value;
        this.allowWalkOnMagmaBlocks = Winton.settings().allowWalkOnMagmaBlocks.value;
        // why cache these things here, why not let the movements just get directly from settings?
        // because if some movements are calculated one way and others are calculated another way,
        // then you get a wildly inconsistent path that isn't optimal for either scenario.
        this.worldBorder = new BetterWorldBorder(world.getWorldBorder());
    }

    public final IWinton getWinton() {
        return winton;
    }

    public BlockState get(int x, int y, int z) {
        return bsi.get0(x, y, z); // laughs maniacally
    }

    public boolean isLoaded(int x, int z) {
        return bsi.isLoaded(x, z);
    }

    public BlockState get(BlockPos pos) {
        return get(pos.getX(), pos.getY(), pos.getZ());
    }

    public Block getBlock(int x, int y, int z) {
        return get(x, y, z).getBlock();
    }

    public double costOfPlacingAt(int x, int y, int z, BlockState current) {
        if (!hasThrowaway) { // only true if allowPlace is true, see constructor
            return COST_INF;
        }
        if (isPossiblyProtected(x, y, z)) {
            return COST_INF;
        }
        if (!worldBorder.canPlaceAt(x, z)) {
            return COST_INF;
        }
        if (!Winton.settings().allowPlaceInFluidsSource.value && current.getFluidState().isSource()) {
            return COST_INF;
        }
        if (!Winton.settings().allowPlaceInFluidsFlow.value && !current.getFluidState().isEmpty() && !current.getFluidState().isSource()) {
            return COST_INF;
        }
        return placeBlockCost;
    }

    public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
        if (!allowBreak && !allowBreakAnyway.contains(current.getBlock())) {
            return COST_INF;
        }
        if (isPossiblyProtected(x, y, z)) {
            return COST_INF;
        }
        return 1;
    }

    public double placeBucketCost() {
        return placeBlockCost; // shrug
    }

    private static boolean hasWaterBucket(LocalPlayer player, boolean allowInventory) {
        Inventory inv = player.getInventory();
        int end = allowInventory ? 36 : 9;
        for (int i = 0; i < end; i++) {
            if (inv.getItem(i).is(Items.WATER_BUCKET)) {
                return true;
            }
        }
        return false;
    }

    public boolean isPossiblyProtected(int x, int y, int z) {
        // TODO more protection logic here; see #220
        return false;
    }
}
