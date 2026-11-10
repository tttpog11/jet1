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
    private static final int C_PANEL = 0x8A6A55F0;
    private static final int C_SIDE = 0x70161060;
    private static final int C_EDGE = 0xFFB3A6FF;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_DIM = 0xFFB9B3D6;

    private enum Module {
        FINDER("Ore Finder"), MARKERS("Markers"), LIST("Ore List");

        final String label;

        Module(String label) {
            this.label = label;
        }
    }

    private Module selected = Module.FINDER;
    private EditBox seedBox;
    private int px, py, ps;

    public MenuScreen() {
        super(Component.literal("OreSeer"));
    }

    @Override
    protected void init() {
        ps = Math.max(200, Math.min(270, Math.min(width, height) - 16));
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
        seedBox.visible = selected == Module.FINDER;
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
        switch (selected) {
            case FINDER -> {
                rows.add(new LabelRow("World seed"));
                rows.add(new SeedRow());
                rows.add(new StepRow("Chunk radius", () -> c.radius, v -> c.radius = v, 1, 24, 1));
                rows.add(new LabelRow("Blocks to find"));
                for (OreType t : OreType.values()) rows.add(new OreRow(t));
            }
            case MARKERS -> {
                rows.add(new ToggleRow("Show distance", () -> c.markerDistance, () -> c.markerDistance = !c.markerDistance));
                rows.add(new StepRow("Max distance", () -> c.markerMaxDist, v -> c.markerMaxDist = v, 32, 512, 16));
                rows.add(new StepRow("Marker size", () -> c.markerSize, v -> c.markerSize = v, 3, 12, 1));
            }
            case LIST -> rows.add(new StepRow("Entries", () -> c.listSize, v -> c.listSize = v, 3, 15, 1));
        }
        return rows;
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
            case MARKERS -> c.markersEnabled;
            case LIST -> c.listEnabled;
        };
    }

    private void toggle(Module m) {
        OreConfig c = OreConfig.get();
        switch (m) {
            case FINDER -> c.finderEnabled = !c.finderEnabled;
            case MARKERS -> c.markersEnabled = !c.markersEnabled;
            case LIST -> c.listEnabled = !c.listEnabled;
        }
    }

    private int moduleY(int i) {
        return py + 26 + i * 24;
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // panel
        g.fill(px, py, px + ps, py + ps, C_PANEL);
        g.fill(px, py, px + SIDE_W, py + ps, C_SIDE);
        g.fill(px, py, px + ps, py + 1, C_EDGE);
        g.fill(px, py + ps - 1, px + ps, py + ps, C_EDGE);
        g.fill(px, py, px + 1, py + ps, C_EDGE);
        g.fill(px + ps - 1, py, px + ps, py + ps, C_EDGE);
        g.fill(px + SIDE_W, py + 1, px + SIDE_W + 1, py + ps - 1, C_EDGE);

        g.drawString(font, "OreSeer", px + 8, py + 9, C_TEXT);

        // modules
        Module[] mods = Module.values();
        for (int i = 0; i < mods.length; i++) {
            Module m = mods[i];
            int bx = px + 6, by = moduleY(i), bw = SIDE_W - 12, bh = 20;
            boolean on = isOn(m);
            boolean hover = in(mouseX, mouseY, bx, by, bw, bh);
            int bg = on ? (hover ? 0xE08E80FF : 0xC07B6CFF) : (hover ? 0x90302A70 : 0x66101040);
            g.fill(bx, by, bx + bw, by + bh, bg);
            if (m == selected) g.fill(bx, by, bx + 2, by + bh, 0xFFFFFFFF);
            g.drawString(font, m.label, bx + 8, by + 6, on ? C_TEXT : C_DIM);
        }
        g.drawString(font, "LMB: toggle", px + 8, py + ps - 24, C_DIM);
        g.drawString(font, "RMB: configure", px + 8, py + ps - 13, C_DIM);

        // settings of the selected module
        g.drawString(font, selected.label + " settings", mainX(), py + 9, C_TEXT);
        seedBox.visible = selected == Module.FINDER;
        int y = rowsTop();
        for (Row r : rows()) {
            r.draw(this, g, mainX(), y, mainW(), mouseX, mouseY);
            y += r.h;
        }

        if (selected == Module.FINDER) drawStatus(g);

        super.render(g, mouseX, mouseY, partialTick); // widgets (seed box) on top
    }

    private void drawStatus(GuiGraphics g) {
        int x = mainX();
        int y = py + ps - 34;
        String line1;
        int col = C_DIM;
        if (OreScanner.wrongDimension) {
            line1 = "Overworld only";
            col = 0xFFFFB0B0;
        } else if (OreScanner.error != null) {
            line1 = "Error: " + font.plainSubstrByWidth(OreScanner.error, mainW() - 34);
            col = 0xFFFF9090;
        } else {
            line1 = "Chunks " + OreScanner.cachedChunks() + "  (" + OreScanner.pendingChunks() + " queued)";
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

        Module[] mods = Module.values();
        for (int i = 0; i < mods.length; i++) {
            if (in(mx, my, px + 6, moduleY(i), SIDE_W - 12, 20)) {
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
            if (r.click(mainX(), y, mainW(), mx, my, button)) return true;
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
