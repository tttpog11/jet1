package dev.kelphunt;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Natural geode positions predicted from the world seed by an outside tool (for example the geode-finder
 * program). Kelp Hunt does not predict them itself; it reads a text file with one position per line
 * (the first two integers on each line are used) and marks which ones you have and haven't scanned.
 */
public final class GeodeTargets {
    public static final String FILE_NAME = "kelphunt-geodes.csv";
    private static final Pattern INT = Pattern.compile("-?\\d+");

    /** Chunk coordinates {chunkX, chunkZ}. */
    public static volatile List<int[]> chunks = List.of();

    private GeodeTargets() {}

    public static Path file() {
        return FabricLoader.getInstance().getGameDir().resolve(FILE_NAME);
    }

    /** Returns how many positions were read. {@code blockCoords}: the file holds block coordinates, not chunk coordinates. */
    public static int load(boolean blockCoords) throws IOException {
        List<int[]> out = new ArrayList<>();
        for (String line : Files.readAllLines(file())) {
            Matcher m = INT.matcher(line);
            if (!m.find()) continue;
            long a = Long.parseLong(m.group());
            if (!m.find()) continue;
            long b = Long.parseLong(m.group());
            int cx = (int) (blockCoords ? Math.floorDiv(a, 16) : a);
            int cz = (int) (blockCoords ? Math.floorDiv(b, 16) : b);
            out.add(new int[] {cx, cz});
        }
        chunks = List.copyOf(out);
        return out.size();
    }

    public static void clear() {
        chunks = List.of();
    }
}
