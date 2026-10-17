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

package winton.utils.pathing;

import winton.Winton;
import winton.api.utils.BetterBlockPos;
import winton.api.utils.IPlayerContext;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Avoidance {

    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final double coefficient;
    private final int radius;
    private final int radiusSq;

    public Avoidance(BlockPos center, double coefficient, int radius) {
        this(center.getX(), center.getY(), center.getZ(), coefficient, radius);
    }

    public Avoidance(int centerX, int centerY, int centerZ, double coefficient, int radius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.coefficient = coefficient;
        this.radius = radius;
        this.radiusSq = radius * radius;
    }

    public double coefficient(int x, int y, int z) {
        int xDiff = x - centerX;
        int yDiff = y - centerY;
        int zDiff = z - centerZ;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff <= radiusSq ? coefficient : 1.0D;
    }

    public static List<Avoidance> create(IPlayerContext ctx) {
        List<Avoidance> res = new ArrayList<>();
        if (Winton.settings().avoidance.value) {
            double mobSpawnerCoeff = Winton.settings().mobSpawnerAvoidanceCoefficient.value;
            double mobCoeff = Winton.settings().mobAvoidanceCoefficient.value;
            if (mobSpawnerCoeff != 1.0D) {
                ctx.worldData().getCachedWorld().getLocationsOf("mob_spawner", 1, ctx.playerFeet().x, ctx.playerFeet().z, 2)
                        .forEach(mobspawner -> res.add(new Avoidance(mobspawner, mobSpawnerCoeff, Winton.settings().mobSpawnerAvoidanceRadius.value)));
            }
            if (mobCoeff != 1.0D) {
                ctx.entitiesStream()
                        .filter(entity -> entity instanceof Mob)
                        .filter(entity -> (!(entity instanceof Spider)) || ctx.player().getLightLevelDependentMagicValue() < 0.5)
                        .filter(entity -> !(entity instanceof ZombifiedPiglin) || ((ZombifiedPiglin) entity).getLastHurtByMob() != null)
                        .filter(entity -> !(entity instanceof EnderMan) || ((EnderMan) entity).isCreepy())
                        .forEach(entity -> res.add(new Avoidance(entity.blockPosition(), mobCoeff, Winton.settings().mobAvoidanceRadius.value)));
            }
        }
        if (ctx.world() != null && ctx.player() != null) {
            BlockPos feet = ctx.playerFeet();
            // Hard keep-out: the path may not enter the 3-block box around lava.
            // Nearest lava is kept first so a lake in front is not dropped for lava behind.
            final int keep = 3;
            final int scan = 28;
            List<BlockPos> lava = new ArrayList<>();
            for (int dx = -scan; dx <= scan; dx++) {
                for (int dz = -scan; dz <= scan; dz++) {
                    if (dx * dx + dz * dz > scan * scan) {
                        continue;
                    }
                    for (int dy = -12; dy <= 8; dy++) {
                        BlockPos p = feet.offset(dx, dy, dz);
                        if (ctx.world().getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA)) {
                            lava.add(p);
                        }
                    }
                }
            }
            lava.sort(java.util.Comparator.comparingDouble(feet::distSqr));
            int limit = Math.min(lava.size(), 768);
            for (int i = 0; i < limit; i++) {
                res.add(new Avoidance(lava.get(i), HARD_LAVA, keep));
            }
        }
        return res;
    }

    /** Path cost multiplier that A* treats as "do not step here". */
    public static final double HARD_LAVA = 10000.0D;

    public void applySpherical(Long2DoubleOpenHashMap map) {
        boolean box = coefficient >= HARD_LAVA;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean inside = box
                            ? Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) <= radius
                            : x * x + y * y + z * z <= radius * radius;
                    if (inside) {
                        long hash = BetterBlockPos.longHash(centerX + x, centerY + y, centerZ + z);
                        double next = map.get(hash) * coefficient;
                        if (next > HARD_LAVA) {
                            next = HARD_LAVA;
                        }
                        map.put(hash, next);
                    }
                }
            }
        }
    }
}
