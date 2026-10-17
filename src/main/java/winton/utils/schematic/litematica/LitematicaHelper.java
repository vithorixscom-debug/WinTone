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

package winton.utils.schematic.litematica;

import winton.api.schematic.CompositeSchematic;
import winton.api.schematic.IStaticSchematic;
import winton.utils.schematic.StaticSchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Soft Litematica bridge — NO hard class refs (safe when Litematica is not installed).
 * Prefers schematic world (Baritone path); falls back to sub-region containers.
 */
public final class LitematicaHelper {

	private LitematicaHelper() {
	}

	public static boolean isLitematicaPresent() {
		try {
			Class.forName("fi.dy.masa.litematica.Litematica", false, LitematicaHelper.class.getClassLoader());
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	public static boolean hasLoadedSchematic(int i) {
		return 0 <= i && i < placementCount();
	}

	public static int placementCount() {
		if (!isLitematicaPresent()) {
			return 0;
		}
		try {
			List<?> list = allPlacements();
			return list == null ? 0 : list.size();
		} catch (Throwable t) {
			System.err.println("[WinTone] Litematica placementCount: " + t);
			return 0;
		}
	}

	public static String placementName(int i) {
		if (!hasLoadedSchematic(i)) {
			return "?";
		}
		try {
			Object placement = allPlacements().get(i);
			Method getName = placement.getClass().getMethod("getName");
			Object name = getName.invoke(placement);
			return name == null ? "#" + (i + 1) : String.valueOf(name);
		} catch (Throwable t) {
			return "#" + (i + 1);
		}
	}

	@SuppressWarnings("unchecked")
	private static List<Object> allPlacements() throws Exception {
		Class<?> dataManager = Class.forName("fi.dy.masa.litematica.data.DataManager");
		Method getManager = dataManager.getMethod("getSchematicPlacementManager");
		Object manager = getManager.invoke(null);
		Method getAll;
		try {
			getAll = manager.getClass().getMethod("getAllSchematicsPlacements");
		} catch (NoSuchMethodException e) {
			getAll = manager.getClass().getMethod("getAllSchematicPlacements");
		}
		List<Object> raw = (List<Object>) getAll.invoke(manager);
		return raw == null ? Collections.emptyList() : raw;
	}

	private static Vec3i transform(Vec3i in, Mirror mirror, Rotation rotation) {
		int x = in.getX();
		int z = in.getZ();
		if (mirror == Mirror.LEFT_RIGHT) {
			z = -z;
		} else if (mirror == Mirror.FRONT_BACK) {
			x = -x;
		}
		return switch (rotation) {
			case CLOCKWISE_90 -> new Vec3i(-z, in.getY(), x);
			case CLOCKWISE_180 -> new Vec3i(-x, in.getY(), -z);
			case COUNTERCLOCKWISE_90 -> new Vec3i(z, in.getY(), -x);
			default -> new Vec3i(x, in.getY(), z);
		};
	}

	private static Vec3i asVec3i(Object o) {
		if (o == null) {
			return new Vec3i(0, 0, 0);
		}
		if (o instanceof Vec3i v) {
			return v;
		}
		// reflective BlockPos / Vec3i duck
		try {
			int x = (Integer) o.getClass().getMethod("getX").invoke(o);
			int y = (Integer) o.getClass().getMethod("getY").invoke(o);
			int z = (Integer) o.getClass().getMethod("getZ").invoke(o);
			return new Vec3i(x, y, z);
		} catch (Throwable t) {
			throw new IllegalStateException("Not a Vec3i: " + o.getClass(), t);
		}
	}

	@SuppressWarnings("unchecked")
	private static Object invokeFirst(Object target, String... names) throws Exception {
		Exception last = null;
		for (String name : names) {
			try {
				return target.getClass().getMethod(name).invoke(target);
			} catch (NoSuchMethodException e) {
				last = e;
			}
		}
		if (last != null) {
			throw last;
		}
		throw new NoSuchMethodException(String.join("|", names));
	}

	private static Level schematicWorldOrNull() {
		try {
			Class<?> worldHandler = Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler");
			Object world = worldHandler.getMethod("getSchematicWorld").invoke(null);
			return world instanceof Level ? (Level) world : null;
		} catch (Throwable t) {
			return null;
		}
	}

	private static BlockState safeState(BlockState state) {
		return state == null ? Blocks.AIR.defaultBlockState() : state;
	}

	/**
	 * Reflective schematic extract. Returns null if Litematica missing / error.
	 */
	@SuppressWarnings("unchecked")
	public static Tuple<IStaticSchematic, Vec3i> getSchematic(int i) {
		if (!hasLoadedSchematic(i)) {
			return null;
		}
		try {
			Object placement = allPlacements().get(i);
			Class<?> placementCl = placement.getClass();
			try {
				Object enabled = placementCl.getMethod("isEnabled").invoke(placement);
				if (enabled instanceof Boolean && !(Boolean) enabled) {
					System.err.println("[WinTone] Litematica placement is disabled — still reading blocks, enable it in the Litematica GUI");
				}
			} catch (Throwable ignored) {
			}

			Mirror mirror = (Mirror) placementCl.getMethod("getMirror").invoke(placement);
			Rotation rotation = (Rotation) placementCl.getMethod("getRotation").invoke(placement);
			BlockPos origin = (BlockPos) placementCl.getMethod("getOrigin").invoke(placement);
			Object schematic = placementCl.getMethod("getSchematic").invoke(placement);
			Map<String, Object> subMap = (Map<String, Object>) invokeFirst(placement,
				"getEnabledRelativeSubRegionPlacements");
			if (subMap == null || subMap.isEmpty()) {
				System.err.println("[WinTone] Litematica: no enabled sub-regions");
				return null;
			}

			Level schematicWorld = schematicWorldOrNull();

			int minX = Integer.MAX_VALUE;
			int minY = Integer.MAX_VALUE;
			int minZ = Integer.MAX_VALUE;
			HashMap<Vec3i, StaticSchematic> subRegions = new HashMap<>();
			int solidBlocks = 0;

			Method getAreaSize = null;
			try {
				getAreaSize = schematic.getClass().getMethod("getAreaSize", String.class);
			} catch (NoSuchMethodException e) {
				getAreaSize = schematic.getClass().getMethod("getAreaSizeAsVec3i", String.class);
			}
			Method getContainer = null;
			try {
				getContainer = schematic.getClass().getMethod("getSubRegionContainer", String.class);
			} catch (NoSuchMethodException ignored) {
			}

			for (Map.Entry<String, Object> entry : subMap.entrySet()) {
				Object subPlacement = entry.getValue();
				Class<?> subCl = subPlacement.getClass();
				Vec3i pos = transform(asVec3i(subCl.getMethod("getPos").invoke(subPlacement)), mirror, rotation);
				Vec3i size = asVec3i(getAreaSize.invoke(schematic, entry.getKey()));
				size = transform(size, mirror, rotation);
				Mirror subMirror = (Mirror) subCl.getMethod("getMirror").invoke(subPlacement);
				Rotation subRot = (Rotation) subCl.getMethod("getRotation").invoke(subPlacement);
				size = transform(size, subMirror, subRot);
				int mx = Math.min(size.getX() + 1, 0);
				int my = Math.min(size.getY() + 1, 0);
				int mz = Math.min(size.getZ() + 1, 0);
				minX = Math.min(minX, pos.getX() + mx);
				minY = Math.min(minY, pos.getY() + my);
				minZ = Math.min(minZ, pos.getZ() + mz);
				BlockPos regionOrigin = origin.offset(pos).offset(mx, my, mz);

				int sx = Math.abs(size.getX());
				int sy = Math.abs(size.getY());
				int sz = Math.abs(size.getZ());
				if (sx <= 0 || sy <= 0 || sz <= 0) {
					continue;
				}
				BlockState[][][] states = new BlockState[sx][sz][sy];
				BlockState air = Blocks.AIR.defaultBlockState();
				for (int x = 0; x < sx; x++) {
					for (int z = 0; z < sz; z++) {
						for (int y = 0; y < sy; y++) {
							states[x][z][y] = air;
						}
					}
				}
				int regionSolids = 0;

				// 1) Baritone path: schematic world (includes placement rotation)
				if (schematicWorld != null) {
					for (int x = 0; x < sx; x++) {
						for (int z = 0; z < sz; z++) {
							for (int y = 0; y < sy; y++) {
								BlockState st = safeState(schematicWorld.getBlockState(regionOrigin.offset(x, y, z)));
								states[x][z][y] = st;
								if (!st.isAir()) {
									regionSolids++;
								}
							}
						}
					}
				}
				// 2) Schematic world often stays air until chunks rebuild — read the container
				if (regionSolids == 0 && getContainer != null) {
					Object container = getContainer.invoke(schematic, entry.getKey());
					if (container != null) {
						try {
							Method get = container.getClass().getMethod("get", int.class, int.class, int.class);
							regionSolids = 0;
							for (int x = 0; x < sx; x++) {
								for (int z = 0; z < sz; z++) {
									for (int y = 0; y < sy; y++) {
										BlockState st = safeState((BlockState) get.invoke(container, x, y, z));
										states[x][z][y] = st;
										if (!st.isAir()) {
											regionSolids++;
										}
									}
								}
							}
						} catch (Throwable t) {
							System.err.println("[WinTone] Litematica container read failed for " + entry.getKey() + ": " + t);
						}
					}
				}
				if (regionSolids == 0 && schematicWorld == null && getContainer == null) {
					System.err.println("[WinTone] Litematica: no container/world for region " + entry.getKey());
					return null;
				}
				solidBlocks += regionSolids;
				subRegions.put(pos.offset(mx, my, mz), new StaticSchematic(states));
			}

			if (subRegions.isEmpty()) {
				System.err.println("[WinTone] Litematica: empty subregions");
				return null;
			}
			if (solidBlocks == 0) {
				System.err.println("[WinTone] Litematica: placement has 0 solid blocks (schematic world empty?)");
				return null;
			}

			String name = String.valueOf(placementCl.getMethod("getName").invoke(placement));
			LitematicaPlacementSchematic composite = new LitematicaPlacementSchematic(name);
			for (Map.Entry<Vec3i, StaticSchematic> entry : subRegions.entrySet()) {
				Vec3i pos = entry.getKey().offset(-minX, -minY, -minZ);
				composite.put(entry.getValue(), pos.getX(), pos.getY(), pos.getZ());
			}
			System.out.println("[WinTone] Litematica loaded \"" + name + "\" solids=" + solidBlocks
				+ " origin=" + origin.offset(minX, minY, minZ));
			return new Tuple<>(composite, origin.offset(minX, minY, minZ));
		} catch (Throwable t) {
			System.err.println("[WinTone] Litematica getSchematic failed: " + t);
			t.printStackTrace();
			return null;
		}
	}

	private static class LitematicaPlacementSchematic extends CompositeSchematic implements IStaticSchematic {
		private final String name;

		LitematicaPlacementSchematic(String name) {
			super(0, 0, 0);
			this.name = name;
		}

		@Override
		public BlockState getDirect(int x, int y, int z) {
			if (inSchematic(x, y, z, null)) {
				return desiredState(x, y, z, null, Collections.emptyList());
			}
			return null;
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
