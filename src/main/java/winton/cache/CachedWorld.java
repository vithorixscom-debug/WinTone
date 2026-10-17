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

package winton.cache;

import winton.Winton;
import winton.api.WintonAPI;
import winton.api.IWinton;
import winton.api.cache.ICachedWorld;
import winton.api.cache.IWorldData;
import winton.api.utils.Helper;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Brady
 * @since 8/4/2018
 */
public final class CachedWorld implements ICachedWorld, Helper {

    /**
     * The maximum number of regions in any direction from (0,0)
     */
    private static final int REGION_MAX = 30_000_000 / 512 + 1;

    /**
     * A map of all of the cached regions.
     */
    private Long2ObjectMap<CachedRegion> cachedRegions = new Long2ObjectOpenHashMap<>();

    /**
     * The directory that the cached region files are saved to
     */
    private final String directory;

    /**
     * Queue of positions to pack. Refers to the toPackMap, in that every element of this queue will be a
     * key in that map.
     */
    private final LinkedBlockingQueue<ChunkPos> toPackQueue = new LinkedBlockingQueue<>();

    /**
     * Pending packs — strong refs (softValues caused GC → null pack / lost chunks).
     */
    private final Map<ChunkPos, LevelChunk> toPackMap = new java.util.concurrent.ConcurrentHashMap<>();

    private final DimensionType dimension;

    private final ResourceKey<Level> dimensionId;

    private volatile boolean running = true;

    CachedWorld(Path directory, DimensionType dimension, ResourceKey<Level> dimensionId) {
        if (!Files.exists(directory)) {
            try {
                Files.createDirectories(directory);
            } catch (IOException ignored) {
            }
        }
        this.directory = directory.toString();
        this.dimension = dimension;
        this.dimensionId = dimensionId;
        System.out.println("Cached world directory: " + directory);
        Winton.getExecutor().execute(new PackerThread());
        Winton.getExecutor().execute(() -> {
            try {
                Thread.sleep(30000);
                while (running) {
                    // Save hourly — cuts rare multi-second hitches vs every 10 minutes
                    save();
                    Thread.sleep(3_600_000L);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** Stop packer / save loops and drop pending chunk refs. Safe to call more than once. */
    public void close() {
        running = false;
        toPackMap.clear();
        // Wake packer if blocked on take()
        toPackQueue.offer(new ChunkPos(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Override
    public final void queueForPacking(LevelChunk chunk) {
        if (!running || chunk == null) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        int max = Winton.settings().chunkPackerQueueMaxSize.value;
        // Backpressure: update already-queued chunk, otherwise drop brand-new when overloaded
        if (toPackQueue.size() >= max && !toPackMap.containsKey(pos)) {
            return;
        }
        if (toPackMap.put(pos, chunk) == null) {
            toPackQueue.add(pos);
        }
    }

    @Override
    public final boolean isCached(int blockX, int blockZ) {
        CachedRegion region = getRegion(blockX >> 9, blockZ >> 9);
        if (region == null) {
            return false;
        }
        return region.isCached(blockX & 511, blockZ & 511);
    }

    public final boolean regionLoaded(int blockX, int blockZ) {
        return getRegion(blockX >> 9, blockZ >> 9) != null;
    }

    @Override
    public final ArrayList<BlockPos> getLocationsOf(String block, int maximum, int centerX, int centerZ, int maxRegionDistanceSq) {
        ArrayList<BlockPos> res = new ArrayList<>();
        int centerRegionX = centerX >> 9;
        int centerRegionZ = centerZ >> 9;

        int searchRadius = 0;
        while (searchRadius <= maxRegionDistanceSq) {
            for (int xoff = -searchRadius; xoff <= searchRadius; xoff++) {
                for (int zoff = -searchRadius; zoff <= searchRadius; zoff++) {
                    int distance = xoff * xoff + zoff * zoff;
                    if (distance != searchRadius) {
                        continue;
                    }
                    int regionX = xoff + centerRegionX;
                    int regionZ = zoff + centerRegionZ;
                    CachedRegion region = getOrCreateRegion(regionX, regionZ);
                    if (region != null) {
                        // TODO: 100% verify if this or addAll is faster.
                        res.addAll(region.getLocationsOf(block));
                    }
                }
            }
            if (res.size() >= maximum) {
                return res;
            }
            searchRadius++;
        }
        return res;
    }

    private void updateCachedChunk(CachedChunk chunk) {
        CachedRegion region = getOrCreateRegion(chunk.x >> 5, chunk.z >> 5);
        region.updateCachedChunk(chunk.x & 31, chunk.z & 31, chunk);
    }

    @Override
    public final void save() {
        if (!Winton.settings().chunkCaching.value) {
            System.out.println("Not saving to disk; chunk caching is disabled.");
            allRegions().forEach(region -> {
                if (region != null) {
                    region.removeExpired();
                }
            }); // even if we aren't saving to disk, still delete expired old chunks from RAM
            prune();
            return;
        }
        long start = System.nanoTime() / 1000000L;
        allRegions().parallelStream().forEach(region -> {
            if (region != null) {
                region.save(this.directory);
            }
        });
        long now = System.nanoTime() / 1000000L;
        System.out.println("World save took " + (now - start) + "ms");
        prune();
    }

    /**
     * Delete regions that are too far from the player
     */
    private synchronized void prune() {
        if (!Winton.settings().pruneRegionsFromRAM.value) {
            return;
        }
        BlockPos pruneCenter = guessPosition();
        for (CachedRegion region : allRegions()) {
            if (region == null) {
                continue;
            }
            int distX = ((region.getX() << 9) + 256) - pruneCenter.getX();
            int distZ = ((region.getZ() << 9) + 256) - pruneCenter.getZ();
            double dist = Math.sqrt(distX * distX + distZ * distZ);
            if (dist > 1024) {
                logDebug("Deleting cached region from ram");
                cachedRegions.remove(getRegionID(region.getX(), region.getZ()));
            }
        }
    }

    /**
     * If we are still in this world and dimension, return player feet, otherwise return most recently modified chunk
     */
    private BlockPos guessPosition() {
        for (IWinton iwinton : WintonAPI.getProvider().getAllWintons()) {
            IWorldData data = iwinton.getWorldProvider().getCurrentWorld();
            if (data != null && data.getCachedWorld() == this && iwinton.getPlayerContext().player() != null) {
                return iwinton.getPlayerContext().playerFeet();
            }
        }
        CachedChunk mostRecentlyModified = null;
        for (CachedRegion region : allRegions()) {
            if (region == null) {
                continue;
            }
            CachedChunk ch = region.mostRecentlyModified();
            if (ch == null) {
                continue;
            }
            if (mostRecentlyModified == null || mostRecentlyModified.cacheTimestamp < ch.cacheTimestamp) {
                mostRecentlyModified = ch;
            }
        }
        if (mostRecentlyModified == null) {
            return new BlockPos(0, 0, 0);
        }
        return new BlockPos((mostRecentlyModified.x << 4) + 8, 0, (mostRecentlyModified.z << 4) + 8);
    }

    private synchronized List<CachedRegion> allRegions() {
        return new ArrayList<>(this.cachedRegions.values());
    }

    @Override
    public final void reloadAllFromDisk() {
        long start = System.nanoTime() / 1000000L;
        allRegions().forEach(region -> {
            if (region != null) {
                region.load(this.directory);
            }
        });
        long now = System.nanoTime() / 1000000L;
        System.out.println("World load took " + (now - start) + "ms");
    }

    @Override
    public final synchronized CachedRegion getRegion(int regionX, int regionZ) {
        return cachedRegions.get(getRegionID(regionX, regionZ));
    }

    /**
     * Returns the region at the specified region coordinates. If a
     * region is not found, then a new one is created.
     *
     * @param regionX The region X coordinate
     * @param regionZ The region Z coordinate
     * @return The region located at the specified coordinates
     */
    private synchronized CachedRegion getOrCreateRegion(int regionX, int regionZ) {
        return cachedRegions.computeIfAbsent(getRegionID(regionX, regionZ), id -> {
            CachedRegion newRegion = new CachedRegion(regionX, regionZ, dimension, dimensionId);
            newRegion.load(this.directory);
            return newRegion;
        });
    }

    public void tryLoadFromDisk(int regionX, int regionZ) {
        getOrCreateRegion(regionX, regionZ);
    }

    /**
     * Returns the region ID based on the region coordinates. 0 will be
     * returned if the specified region coordinates are out of bounds.
     *
     * @param regionX The region X coordinate
     * @param regionZ The region Z coordinate
     * @return The region ID
     */
    private long getRegionID(int regionX, int regionZ) {
        if (!isRegionInWorld(regionX, regionZ)) {
            return 0;
        }

        return (long) regionX & 0xFFFFFFFFL | ((long) regionZ & 0xFFFFFFFFL) << 32;
    }

    /**
     * Returns whether or not the specified region coordinates is within the world bounds.
     *
     * @param regionX The region X coordinate
     * @param regionZ The region Z coordinate
     * @return Whether or not the region is in world bounds
     */
    private boolean isRegionInWorld(int regionX, int regionZ) {
        return regionX <= REGION_MAX && regionX >= -REGION_MAX && regionZ <= REGION_MAX && regionZ >= -REGION_MAX;
    }

    private class PackerThread implements Runnable {

        public void run() {
            while (running) {
                try {
                    ChunkPos pos = toPackQueue.take();
                    if (!running) {
                        break;
                    }
                    LevelChunk chunk = toPackMap.remove(pos);
                    if (chunk == null) {
                        continue;
                    }
                    CachedChunk cached = ChunkPacker.pack(chunk);
                    if (cached != null) {
                        CachedWorld.this.updateCachedChunk(cached);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        }
    }
}
