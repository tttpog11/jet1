package com.tttpog.oreseer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Draws predicted ores as 2D markers projected onto the HUD (no world-render hooks needed),
 * plus a small "nearest ores" list.
 */
public final class OreHud {
    private OreHud() {}

    public static void render(GuiGraphics g, Minecraft mc, float partial) {
        OreConfig c = OreConfig.get();
        if (!c.finderEnabled || mc.player == null || mc.options.hideGui) return;
        List<OrePredictor.Blob> blobs = OreScanner.visible;
        if (blobs.isEmpty()) return;

        int w = g.guiWidth();
        int h = g.guiHeight();

        if (c.markersEnabled) drawMarkers(g, mc, c, blobs, partial, w, h);
        if (c.listEnabled) drawList(g, mc, c, blobs, w);
    }

    private static void drawMarkers(GuiGraphics g, Minecraft mc, OreConfig c, List<OrePredictor.Blob> blobs,
                                    float partial, int w, int h) {
        Vec3 eye = mc.player.getEyePosition(partial);
        double yaw = Math.toRadians(mc.player.getViewYRot(partial));
        double pitch = Math.toRadians(mc.player.getViewXRot(partial));

        // camera basis (forward / right / up)
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), ry = 0, rz = -Math.sin(yaw);
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;

        double tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double aspect = (double) w / (double) h;
        int half = c.markerSize;
        double maxDist = c.markerMaxDist;
        int labels = 0;

        for (OrePredictor.Blob b : blobs) {
            double dx = b.x() - eye.x, dy = b.y() - eye.y, dz = b.z() - eye.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > maxDist) continue;
            double depth = dx * fx + dy * fy + dz * fz;
            if (depth < 0.5) continue;

            double sx = w / 2.0 * (1.0 + ((dx * rx + dy * ry + dz * rz) / depth) / (tanHalf * aspect));
            double sy = h / 2.0 * (1.0 - ((dx * ux + dy * uy + dz * uz) / depth) / tanHalf);
            int x = (int) sx;
            int y = (int) sy;
            if (x < -half || x > w + half || y < -half || y > h + half) continue;

            int col = b.type().color;
            int fill = (col & 0x00FFFFFF) | 0x55000000;
            g.fill(x - half, y - half, x + half, y + half, fill);
            g.fill(x - half, y - half, x + half, y - half + 1, col);
            g.fill(x - half, y + half - 1, x + half, y + half, col);
            g.fill(x - half, y - half, x - half + 1, y + half, col);
            g.fill(x + half - 1, y - half, x + half, y + half, col);

            if (labels < 40) {
                labels++;
                String t = c.markerDistance ? "x" + b.count() + " " + (int) dist + "m" : "x" + b.count();
                g.drawString(mc.font, t, x - mc.font.width(t) / 2, y + half + 2, col);
            }
        }
    }

    private static void drawList(GuiGraphics g, Minecraft mc, OreConfig c, List<OrePredictor.Blob> blobs, int w) {
        Vec3 eye = mc.player.getEyePosition();
        int y = 6;
        String head = "OreSeer - nearest";
        g.drawString(mc.font, head, w - mc.font.width(head) - 6, y, 0xFFC9BEFF);
        y += 11;
        int n = Math.min(c.listSize, blobs.size());
        for (int i = 0; i < n; i++) {
            OrePredictor.Blob b = blobs.get(i);
            double dx = b.x() - eye.x, dy = b.y() - eye.y, dz = b.z() - eye.z;
            int dist = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
            String t = b.type().label + " x" + b.count() + "  " + dist + "m  ["
                    + (int) Math.floor(b.x()) + " " + (int) Math.floor(b.y()) + " " + (int) Math.floor(b.z()) + "]";
            g.drawString(mc.font, t, w - mc.font.width(t) - 6, y, b.type().color);
            y += 10;
        }
    }
}
