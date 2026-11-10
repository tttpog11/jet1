package com.tttpog.oreseer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * See-through blue/purple square with the modules down the left side.
 * Left click a module = on/off, right click = open its settings.
 */
public class MenuScreen extends Screen {
    private static final int SIDE_W = 100;
    private static final int C_PANEL = 0x8A6A55F0; // (unused, kept for reference)
    private static final int C_SIDE = 0x70161060;
    private static final int C_EDGE = 0xFFB58CFF;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_DIM = 0xFFC3B2EE;

    private enum Module {
        FINDER("Finder"), OVERWORLD("Overworld Ores"), DEEPSLATE("Deepslate Ores"), NETHER("Nether Ores"),
        MARKERS("Markers"), LIST("Ore List");

        final String label;

        Module(String label) {
            this.label = label;
        }
    }

    // categories: the sidebar first lists categories, opening one shows its modules
    private static boolean inCategory = false;
    private Module selected = Module.FINDER;
    private EditBox seedBox;
    private int px, py, ps;

    public MenuScreen() {
        super(Component.literal("OreSeer"));
    }

    @Override
    protected void init() {
        ps = Math.max(230, Math.min(280, Math.min(width, height) - 16));
        px = (width - ps) / 2;
        py = (height - ps) / 2;

        OreConfig c = OreConfig.get();
        seedBox = new EditBox(font, px + SIDE_W + 8, py + 40, ps - SIDE_W - 16, 16, Component.literal("Seed"));
        seedBox.setMaxLength(20);
        seedBox.setFilter(s -> s.matches("-?\\d{0,19}"));
        seedBox.setValue(Long.toString(c.seed));
        seedBox.setResponder(s -> {
            try {
                long v = Long.parseLong(s);
                OreConfig cfg = OreConfig.get();
                if (v != cfg.seed) cfg.seed = v;
            } catch (NumberFormatException ignored) {
                // incomplete input like "-" is fine, keep the old seed
            }
        });
        seedBox.visible = inCategory && selected == Module.FINDER;
        addRenderableWidget(seedBox);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // keep the world visible: no blur / dim
    }

    @Override
    public void removed() {
        OreConfig.get().save();
    }

    // ------------------------------------------------------------------ rows

    private abstract static class Row {
        final int h;

        Row(int h) {
            this.h = h;
        }

        abstract void draw(MenuScreen s, GuiGraphics g, int x, int y, int w, int mx, int my);

        boolean click(int x, int y, int w, double mx, double my, int button) {
            return false;
        }

        boolean scroll(int x, int y, int w, double mx, double my, double dy) {
            return false;
        }
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static final class LabelRow extends Row {
        final String text;

        LabelRow(String text) {
            super(12);
            this.text = text;
        }

        @Override
        void draw(MenuScreen s, GuiGraphics g, int x, int y, int w, int mx, int my) {
            g.drawString(s.font, text, x, y + 2, C_DIM);
        }
    }

    private static final class SeedRow extends Row {
        SeedRow() {
            super(22);
        }

        @Override
        void draw(MenuScreen s, GuiGraphics g, int x, int y, int w, int mx, int my) {
            s.seedBox.setX(x);
            s.seedBox.setY(y + 2);
            s.seedBox.setWidth(w);
        }
    }

    private static final class StepRow extends Row {
        final String label;
        final IntSupplier get;
        final IntConsumer set;
        final int min, max, step;

        StepRow(String label, IntSupplier get, IntConsumer set, int min, int max, int step) {
            super(18);
            this.label = label;
            this.get = get;
            this.set = set;
            this.min = min;
            this.max = max;
            this.step = step;
        }

        private void bump(int dir) {
            set.accept(Math.max(min, Math.min(max, get.getAsInt() + dir * step)));
        }

        @Override
        void draw(MenuScreen s, GuiGraphics g, int x, int y, int w, int mx, int my) {
            g.drawString(s.font, label, x, y + 5, C_TEXT);
            int right = x + w;
            drawBtn(s, g, right - 14, y + 1, 14, 14, "+", mx, my);
            drawBtn(s, g, right - 14 - 40, y + 1, 14, 14, "-", mx, my);
            String v = Integer.toString(get.getAsInt());
            g.drawString(s.font, v, right - 14 - 20 - s.font.width(v) / 2, y + 5, C_TEXT);
        }

        @Override
        boolean click(int x, int y, int w, double mx, double my, int button) {
            int right = x + w;
            if (in(mx, my, right - 14, y + 1, 14, 14)) {
                bump(1);
                return true;
            }
            if (in(mx, my, right - 54, y + 1, 14, 14)) {
                bump(-1);
                return true;
            }
            return false;
        }

        @Override
        boolean scroll(int x, int y, int w, double mx, double my, double dy) {
            if (in(mx, my, x, y, w, h)) {
                bump(dy > 0 ? 1 : -1);
                return true;
            }
            return false;
        }
    }

    private static final class ToggleRow extends Row {
        final String label;
        final BooleanSupplier get;
        final Runnable flip;

        ToggleRow(String label, BooleanSupplier get, Runnable flip) {
            super(18);
            this.label = label;
            this.get = get;
            this.flip = flip;
        }

        @Override
        void draw(MenuScreen s, GuiGraphics g, int x, int y, int w, int mx, int my) {
            g.drawString(s.font, label, x, y + 5, C_TEXT);
            boolean on = get.getAsBoolean();
            int bx = x + w - 30;
            g.fill(bx, y + 2, bx + 30, y + 15, on ? 0xCC63D68A : 0x66000020);
            String t = on ? "ON" : "OFF";
            g.drawString(s.font, t, bx + 15 - s.font.width(t) / 2, y + 5, C_TEXT);
        }

        @Override
        boolean click(int x, int y, int w, double mx, double my, int button) {
            if (button != 0) return false;
            flip.run();
            return true;
        }
    }

    private static final class OreRow extends Row {
        final OreType type;

        OreRow(OreType type) {
            super(15);
            this.type = type;
        }

        @Override
        void draw(MenuScreen s, GuiGraphics g, int x, int y, int w, int mx, int my) {
            boolean on = OreConfig.get().isOreOn(type);
            if (in(mx, my, x, y, w, h)) g.fill(x - 2, y, x + w, y + h - 1, 0x33FFFFFF);
            g.fill(x, y + 2, x + 9, y + 11, 0xFF000020);
            if (on) g.fill(x + 1, y + 3, x + 8, y + 10, type.color);
            g.drawString(s.font, type.label, x + 14, y + 3, on ? C_TEXT : C_DIM);
        }

        @Override
        boolean click(int x, int y, int w, double mx, double my, int button) {
            if (button != 0) return false;
            OreConfig.get().toggleOre(type);
            return true;
        }
    }

    private static void drawBtn(MenuScreen s, GuiGraphics g, int x, int y, int w, int h, String t, int mx, int my) {
        g.fill(x, y, x + w, y + h, in(mx, my, x, y, w, h) ? 0xAAFFFFFF : 0x66000020);
        g.drawString(s.font, t, x + w / 2 - s.font.width(t) / 2, y + 3, C_TEXT);
    }

    private List<Row> rows() {
        OreConfig c = OreConfig.get();
        List<Row> rows = new ArrayList<>();
        if (!inCategory) return rows;
        switch (selected) {
            case FINDER -> {
                rows.add(new LabelRow("World seed"));
                rows.add(new SeedRow());
                rows.add(new StepRow("Chunk radius", () -> c.radius, v -> c.radius = v, 1, 24, 1));
                rows.add(new StepRow("Min height (Y)", () -> c.minY, v -> c.minY = Math.min(v, c.maxY), -64, 320, 8));
                rows.add(new StepRow("Max height (Y)", () -> c.maxY, v -> c.maxY = Math.max(v, c.minY), -64, 320, 8));
            }
            case OVERWORLD -> addOreRows(rows, OreType.Group.OVERWORLD);
            case DEEPSLATE -> addOreRows(rows, OreType.Group.DEEPSLATE);
            case NETHER -> addOreRows(rows, OreType.Group.NETHER);
            case MARKERS -> {
                rows.add(new ToggleRow("Hide mined / air", () -> c.hideMissing, () -> c.hideMissing = !c.hideMissing));
                rows.add(new ToggleRow("Show distance", () -> c.markerDistance, () -> c.markerDistance = !c.markerDistance));
                rows.add(new StepRow("Max distance", () -> c.markerMaxDist, v -> c.markerMaxDist = v, 32, 512, 16));
                rows.add(new StepRow("Marker size", () -> c.markerSize, v -> c.markerSize = v, 3, 12, 1));
            }
            case LIST -> rows.add(new StepRow("Entries", () -> c.listSize, v -> c.listSize = v, 3, 15, 1));
        }
        return rows;
    }

    private static void addOreRows(List<Row> rows, OreType.Group group) {
        rows.add(new LabelRow("Blocks to find (x = vein size)"));
        for (OreType t : OreType.values()) {
            if (t.group == group) rows.add(new OreRow(t));
        }
    }

    private int mainX() {
        return px + SIDE_W + 8;
    }

    private int mainW() {
        return ps - SIDE_W - 16;
    }

    private int rowsTop() {
        return py + 26;
    }

    // ---------------------------------------------------------------- module state

    private boolean isOn(Module m) {
        OreConfig c = OreConfig.get();
        return switch (m) {
            case FINDER -> c.finderEnabled;
            case OVERWORLD -> c.overworldEnabled;
            case DEEPSLATE -> c.deepslateEnabled;
            case NETHER -> c.netherEnabled;
            case MARKERS -> c.markersEnabled;
            case LIST -> c.listEnabled;
        };
    }

    private void toggle(Module m) {
        OreConfig c = OreConfig.get();
        switch (m) {
            case FINDER -> c.finderEnabled = !c.finderEnabled;
            case OVERWORLD -> c.overworldEnabled = !c.overworldEnabled;
            case DEEPSLATE -> c.deepslateEnabled = !c.deepslateEnabled;
            case NETHER -> c.netherEnabled = !c.netherEnabled;
            case MARKERS -> c.markersEnabled = !c.markersEnabled;
            case LIST -> c.listEnabled = !c.listEnabled;
        }
    }

    private int moduleY(int i) {
        return py + 48 + i * 21;
    }


    // ---------------------------------------------------------------- space theme

    private static long lcg(long v) {
        return v * 6364136223846793005L + 1442695040888963407L;
    }

    /** Soft glow: concentric translucent discs, clipped to the panel. */
    private void glow(GuiGraphics g, int cx, int cy, int radius, int rgb) {
        for (int ring = 0; ring < 5; ring++) {
            int r = radius - ring * radius / 6;
            for (int dy = -r; dy <= r; dy += 2) {
                int w = (int) Math.sqrt((double) r * r - (double) dy * dy);
                int y = cy + dy;
                int x0 = Math.max(px + 1, cx - w), x1 = Math.min(px + ps - 1, cx + w);
                if (y < py + 1 || y + 2 > py + ps - 1 || x0 >= x1) continue;
                g.fill(x0, y, x1, y + 2, (0x12 << 24) | rgb);
            }
        }
    }

    private void drawSpace(GuiGraphics g) {
        // deep violet sky, still see-through
        g.fillGradient(px, py, px + ps, py + ps, 0xB0140A36, 0xB02A0F55);
        glow(g, px + ps - 40, py + 50, 70, 0xB04DFF);
        glow(g, px + SIDE_W + 30, py + ps - 30, 60, 0xFF4DC8);
        glow(g, px + 30, py + ps / 2, 50, 0x4D6BFF);
        // darker sidebar
        g.fillGradient(px, py, px + SIDE_W, py + ps, 0x90070318, 0x90120830);

        // twinkling stars
        long t = System.currentTimeMillis();
        long seed = 0x5EEDL;
        for (int i = 0; i < 90; i++) {
            seed = lcg(seed);
            int sx = px + 2 + (int) ((seed >>> 33) % (ps - 4));
            seed = lcg(seed);
            int sy = py + 2 + (int) ((seed >>> 33) % (ps - 4));
            seed = lcg(seed);
            double phase = (seed >>> 40) % 628 / 100.0;
            double tw = 0.5 + 0.5 * Math.sin(t / (500.0 + (i % 5) * 170.0) + phase);
            int a = 0x40 + (int) (0xB0 * tw);
            int col = (a << 24) | (i % 7 == 0 ? 0xFFD9F0 : i % 5 == 0 ? 0xD9C8FF : 0xFFFFFF);
            g.fill(sx, sy, sx + 1, sy + 1, col);
            if (i % 11 == 0) { // a few brighter stars get a little cross
                int arm = (a / 2) << 24 | 0xFFFFFF;
                g.fill(sx - 1, sy, sx, sy + 1, arm);
                g.fill(sx + 1, sy, sx + 2, sy + 1, arm);
                g.fill(sx, sy - 1, sx + 1, sy, arm);
                g.fill(sx, sy + 1, sx + 1, sy + 2, arm);
            }
        }

        drawSaturn(g, px + SIDE_W / 2, py + (48 + 6 * 21 + ps - 24) / 2 + 2);

        // borders with a faint outer glow
        g.fill(px - 1, py - 1, px + ps + 1, py, 0x55B58CFF);
        g.fill(px - 1, py + ps, px + ps + 1, py + ps + 1, 0x55B58CFF);
        g.fill(px - 1, py, px, py + ps, 0x55B58CFF);
        g.fill(px + ps, py, px + ps + 1, py + ps, 0x55B58CFF);
        g.fill(px, py, px + ps, py + 1, C_EDGE);
        g.fill(px, py + ps - 1, px + ps, py + ps, C_EDGE);
        g.fill(px, py, px + 1, py + ps, C_EDGE);
        g.fill(px + ps - 1, py, px + ps, py + ps, C_EDGE);
        g.fill(px + SIDE_W, py + 1, px + SIDE_W + 1, py + ps - 1, 0x99B58CFF);
    }

    /** Small pixel Saturn: back ring, banded planet, front ring. */
    private void drawSaturn(GuiGraphics g, int cx, int cy) {
        final int r = 9;
        final double tilt = Math.toRadians(-18);
        final double cos = Math.cos(tilt), sin = Math.sin(tilt);
        final double[] radii = {20, 23, 26, 29};
        final int[] ringCols = {0xE0D8B8F0, 0xC0A07CD0, 0xE0E6CFFF, 0x90B48CF0};

        for (int pass = 0; pass < 2; pass++) { // 0 = behind the planet, 1 = in front
            if (pass == 1) drawPlanet(g, cx, cy, r);
            for (int k = 0; k < radii.length; k++) {
                double rx = radii[k], ry = radii[k] * 0.28;
                for (int i = 0; i < 90; i++) {
                    double a = i * Math.PI * 2 / 90;
                    boolean front = Math.sin(a) > 0;
                    if (front != (pass == 1)) continue;
                    double ex = Math.cos(a) * rx, ey = Math.sin(a) * ry;
                    int x = cx + (int) Math.round(ex * cos - ey * sin);
                    int y = cy + (int) Math.round(ex * sin + ey * cos);
                    if (x < px + 2 || x > px + SIDE_W - 3) continue;
                    g.fill(x, y, x + 1, y + 1, ringCols[k]);
                }
            }
        }
    }

    private void drawPlanet(GuiGraphics g, int cx, int cy, int r) {
        final int[] bands = {0xFFB9A2FF, 0xFFE2B8C8, 0xFFA07CE0, 0xFFD9A2B0, 0xFF8A66D0, 0xFFC8A8F0};
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            int col = bands[Math.floorMod((dy + r) / 3, bands.length)];
            int dark = 0xFF000000 | ((col >> 17 & 0x7F) << 16) | ((col >> 9 & 0x7F) << 8) | (col >> 1 & 0x7F);
            g.fill(cx - w, cy + dy, cx + w, cy + dy + 1, col);
            g.fill(cx + w / 3, cy + dy, cx + w, cy + dy + 1, dark); // shaded side
        }
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawSpace(g);

        g.drawString(font, "OreSeer", px + 8, py + 9, 0xFFE6D6FF);

        // modules
        if (!inCategory) {
            // category list
            int bx = px + 6, by = py + 26, bw = SIDE_W - 12, bh = 24;
            boolean hover = in(mouseX, mouseY, bx, by, bw, bh);
            g.fillGradient(bx, by, bx + bw, by + bh, hover ? 0xE0B07CFF : 0xC09B5CFF, hover ? 0xE06A3DD0 : 0xC05A2FB8);
            g.drawString(font, "Ore Predictor", bx + 8, by + 8, C_TEXT);
            g.drawString(font, ">", bx + bw - 12, by + 8, C_TEXT);
            g.drawString(font, "Categories", px + 8, py + ps - 13, C_DIM);

            g.drawString(font, "Categories", mainX(), py + 9, C_TEXT);
            g.drawString(font, "Ore Predictor", mainX(), py + 34, 0xFFE6D6FF);
            g.drawString(font, "Find ores from your world seed:", mainX(), py + 48, C_DIM);
            g.drawString(font, "overworld, deepslate and nether.", mainX(), py + 59, C_DIM);
            g.drawString(font, "Click it on the left to open.", mainX(), py + 78, C_DIM);
        } else {
            int bx = px + 6, by = py + 24, bw = SIDE_W - 12, bh = 18;
            boolean hover = in(mouseX, mouseY, bx, by, bw, bh);
            g.fill(bx, by, bx + bw, by + bh, hover ? 0xA0402A80 : 0x7018104A);
            g.drawString(font, "< Ore Predictor", bx + 6, by + 5, 0xFFE6D6FF);

            Module[] mods = Module.values();
            for (int i = 0; i < mods.length; i++) {
                Module m = mods[i];
                int mx0 = px + 6, my0 = moduleY(i), mw = SIDE_W - 12, mh = 18;
                boolean on = isOn(m);
                boolean mh2 = in(mouseX, mouseY, mx0, my0, mw, mh);
                int top = on ? (mh2 ? 0xE0B07CFF : 0xC09B5CFF) : (mh2 ? 0xA0402A80 : 0x7018104A);
                int bot = on ? (mh2 ? 0xE06A3DD0 : 0xC05A2FB8) : (mh2 ? 0xA0281860 : 0x700C0830);
                g.fillGradient(mx0, my0, mx0 + mw, my0 + mh, top, bot);
                if (m == selected) g.fill(mx0, my0, mx0 + 2, my0 + mh, 0xFFFFFFFF);
                g.drawString(font, m.label, mx0 + 8, my0 + 5, on ? C_TEXT : C_DIM);
            }
            g.drawString(font, "LMB: toggle", px + 8, py + ps - 24, C_DIM);
            g.drawString(font, "RMB: configure", px + 8, py + ps - 13, C_DIM);

            g.drawString(font, selected.label + " settings", mainX(), py + 9, C_TEXT);
        }
        seedBox.visible = inCategory && selected == Module.FINDER;
        int y = rowsTop();
        for (Row r : rows()) {
            r.draw(this, g, mainX(), y, mainW(), mouseX, mouseY);
            y += r.h;
        }

        if (inCategory && selected == Module.FINDER) drawStatus(g);

        super.render(g, mouseX, mouseY, partialTick); // widgets (seed box) on top
    }

    private void drawStatus(GuiGraphics g) {
        int x = mainX();
        int y = py + ps - 34;
        String line1;
        int col = C_DIM;
        if (OreScanner.wrongDimension) {
            line1 = "Overworld / Nether only";
            col = 0xFFFFB0B0;
        } else if (OreScanner.error != null) {
            line1 = "Error: " + font.plainSubstrByWidth(OreScanner.error, mainW() - 34);
            col = 0xFFFF9090;
        } else {
            line1 = "Chunks " + OreScanner.cachedChunks() + " (" + OreScanner.pendingChunks() + " queued)  Veins " + OreScanner.visible.size();
        }
        g.drawString(font, line1, x, y, col);

        String line2;
        if (OreScanner.checked < 8) {
            line2 = "Seed check: not enough loaded ore nearby";
        } else {
            int pct = Math.round(100f * OreScanner.matched / OreScanner.checked);
            line2 = "Seed check: " + pct + "% (" + OreScanner.matched + "/" + OreScanner.checked + ")";
        }
        g.drawString(font, line2, x, y + 11, C_DIM);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true; // seed box
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        if (!inCategory) {
            if (in(mx, my, px + 6, py + 26, SIDE_W - 12, 24)) inCategory = true;
            return true;
        }
        if (in(mx, my, px + 6, py + 24, SIDE_W - 12, 18)) {
            inCategory = false;
            seedBox.setFocused(false);
            return true;
        }

        Module[] mods = Module.values();
        for (int i = 0; i < mods.length; i++) {
            if (in(mx, my, px + 6, moduleY(i), SIDE_W - 12, 18)) {
                if (button == 0) {
                    toggle(mods[i]);
                } else if (button == 1) {
                    selected = mods[i];
                    seedBox.setFocused(false);
                }
                return true;
            }
        }

        int y = rowsTop();
        for (Row r : rows()) {
            if (in(mx, my, mainX(), y, mainW(), r.h) && r.click(mainX(), y, mainW(), mx, my, button)) return true;
            y += r.h;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int y = rowsTop();
        for (Row r : rows()) {
            if (r.scroll(mainX(), y, mainW(), mx, my, scrollY)) return true;
            y += r.h;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }
}
