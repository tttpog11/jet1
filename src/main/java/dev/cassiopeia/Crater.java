package dev.cassiopeia;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * One star's crater: a paraboloid bowl dug out of the land and lined with scorched stone, a glowing star core standing
 * in its middle. It is dug column by column from the middle outward, a few columns every tick, so the bowl spreads
 * out like a shock front instead of stalling the server on one big edit.
 *
 * <p>Plain vanilla block edits only (setBlock): nothing here depends on RimuLib's carving tools.
 */
final class Crater {
    /** The bowl is this fraction of its radius deep at the middle. */
    private static final double DEPTH = 0.55;
    private static final int KEEP_QUIET = 18;
    private static final int NOTIFY = 3;

    private final ServerLevel level;
    private final int cx;
    private final int cy;
    private final int cz;
    private final double radius;
    private final long seed;
    private final int[] dx;
    private final int[] dz;
    private int next;
    private boolean cored;

    /** {@code impact} is where the star lands: the first free block above the ground, as {@link Plan} stores it. */
    Crater(ServerLevel level, Vec3 impact, double radius, long seed) {
        this.level = level;
        this.cx = Mth.floor(impact.x);
        this.cz = Mth.floor(impact.z);
        this.cy = Mth.floor(impact.y) - 1;
        this.radius = radius;
        this.seed = seed;
        int reach = (int) Math.ceil(radius);
        List<int[]> columns = new ArrayList<>();
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                if (x * x + z * z <= radius * radius) {
                    columns.add(new int[]{x, z});
                }
            }
        }
        columns.sort(Comparator.comparingInt(column -> column[0] * column[0] + column[1] * column[1]));
        this.dx = new int[columns.size()];
        this.dz = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            this.dx[i] = columns.get(i)[0];
            this.dz[i] = columns.get(i)[1];
        }
    }

    /** Digs the next {@code columns} columns. Returns true once the crater (and its core) is finished. */
    boolean step(int columns) {
        int end = Math.min(this.dx.length, this.next + columns);
        for (; this.next < end; this.next++) {
            this.dig(this.dx[this.next], this.dz[this.next]);
        }
        if (this.next >= this.dx.length) {
            if (!this.cored) {
                this.cored = true;
                this.core();
            }
            return true;
        }
        return false;
    }

    /** The y of the bowl's surface block {@code distance} columns from the middle. */
    private int floorAt(double distance) {
        double relative = distance / this.radius;
        return this.cy - (int) Math.round(this.radius * DEPTH * (1.0 - relative * relative));
    }

    private void dig(int offsetX, int offsetZ) {
        int x = this.cx + offsetX;
        int z = this.cz + offsetZ;
        double distance = Math.sqrt((double) (offsetX * offsetX + offsetZ * offsetZ));
        int floor = this.floorAt(distance);
        // the highest block of any kind in this column (leaves and all), so trees over the bowl go too
        int surface = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        // the rim also tells its neighbours (water and sand next to the bowl can then move in); the inside doesn't
        int flags = distance > this.radius * 0.88 ? NOTIFY : KEEP_QUIET;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = surface - 1; y > floor; y--) {
            pos.set(x, y, z);
            BlockState state = this.level.getBlockState(pos);
            if (!state.isAir() && state.getDestroySpeed(this.level, pos) >= 0.0F) {
                this.level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
            }
        }
        pos.set(x, floor, z);
        BlockState at = this.level.getBlockState(pos);
        if (!at.isAir() && at.getFluidState().isEmpty() && at.getDestroySpeed(this.level, pos) >= 0.0F) {
            this.level.setBlock(pos, this.lining(x, floor, z, distance / this.radius), NOTIFY);
        }
    }

    /** Scorched stone with speckles of star dust; magma near the middle. */
    private BlockState lining(int x, int y, int z, double relative) {
        long h = mix(this.seed ^ (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L));
        int roll = (int) ((h >>> 33) & 0xFF);
        if (relative < 0.22 && roll < 110) {
            return Blocks.MAGMA_BLOCK.defaultBlockState();
        }
        if (roll < 120) {
            return Blocks.BLACKSTONE.defaultBlockState();
        }
        if (roll < 180) {
            return Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        if (roll < 235) {
            return Blocks.CALCITE.defaultBlockState();
        }
        return Blocks.AMETHYST_BLOCK.defaultBlockState();
    }

    /** The star's core: a two-block spire of sea lantern in the middle of the floor, amethyst growing around it. */
    private void core() {
        int base = this.floorAt(0.0);
        this.level.setBlock(new BlockPos(this.cx, base, this.cz), Blocks.SEA_LANTERN.defaultBlockState(), NOTIFY);
        this.level.setBlock(new BlockPos(this.cx, base + 1, this.cz), Blocks.SEA_LANTERN.defaultBlockState(), NOTIFY);
        this.level.setBlock(new BlockPos(this.cx, base + 2, this.cz), Blocks.AMETHYST_CLUSTER.defaultBlockState(), NOTIFY);
        int[][] ring = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] offset : ring) {
            double distance = Math.sqrt((double) (offset[0] * offset[0] + offset[1] * offset[1]));
            BlockPos at = new BlockPos(this.cx + offset[0], this.floorAt(distance) + 1, this.cz + offset[1]);
            if (this.level.getBlockState(at).isAir()) {
                this.level.setBlock(at, Blocks.AMETHYST_CLUSTER.defaultBlockState(), NOTIFY);
            }
        }
    }

    /** SplitMix64: a cheap hash, so the lining is the same pattern for the same seed. */
    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
