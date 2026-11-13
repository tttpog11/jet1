package com.tttpog.oreseer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * "Sound Cards": a small info card in the top-left for every nearby sound, saying which block
 * (or mob / player) made it, what the sound was, and where it came from.
 */
public final class SoundCards {
    private static final class Card {
        String key;
        String title;
        String detail;   // action + distance + direction
        String soundId;  // raw sound event id
        int accent;
        ItemStack icon = ItemStack.EMPTY;
        int count = 1;
        long born;
        long last;
    }

    private record Raw(String id, String source, double x, double y, double z) {}

    private static final List<Card> CARDS = new ArrayList<>();
    private static final ConcurrentLinkedQueue<Raw> QUEUE = new ConcurrentLinkedQueue<>();
    private static boolean registered;

    private SoundCards() {}

    /** Hooks the same listener vanilla subtitles use; safe to call every tick. */
    public static void tryRegister(Minecraft mc) {
        if (registered) return;
        try {
            mc.getSoundManager().addListener((sound, accessor, range) -> onSound(sound));
            registered = true;
        } catch (Throwable t) {
            System.err.println("[OreSeer] could not hook sounds: " + t);
        }
    }

    private static void onSound(SoundInstance s) {
        try {
            OreConfig c = OreConfig.get();
            if (!c.soundEnabled || s.isRelative()) return;
            String source = s.getSource().name();
            if (source.equals("MASTER") || source.equals("MUSIC") || source.equals("RECORDS")
                    || source.equals("VOICE")) return;
            QUEUE.add(new Raw(s.getIdentifier().getPath(), source, s.getX(), s.getY(), s.getZ()));
            while (QUEUE.size() > 64) QUEUE.poll();
        } catch (Throwable ignored) {
            // never let a bad sound break playback
        }
    }

    /** Resolves queued sounds on the main thread (world access) and expires old cards. */
    public static void tick(Minecraft mc) {
        OreConfig c = OreConfig.get();
        long now = System.currentTimeMillis();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        Raw raw;
        while ((raw = QUEUE.poll()) != null) {
            if (level == null || player == null || !c.soundEnabled) continue;
            try {
                resolve(raw, level, player, c, now);
            } catch (Throwable ignored) {
                // skip this sound
            }
        }
        long life = c.soundSeconds * 1000L;
        CARDS.removeIf(card -> now - card.last > life);
        while (CARDS.size() > c.soundMaxCards) CARDS.remove(CARDS.size() - 1);
    }

    private static void resolve(Raw r, ClientLevel level, LocalPlayer player, OreConfig c, long now) {
        Vec3 at = new Vec3(r.x(), r.y(), r.z());
        double dist = player.position().distanceTo(at);
        if (dist > c.soundMaxDist) return;

        // who/what made it
        Entity entity = nearestEntity(level, at, player, r.source().equals("BLOCKS") ? 0.0 : 2.5);
        boolean mobSource = r.source().equals("HOSTILE") || r.source().equals("NEUTRAL") || r.source().equals("PLAYERS");
        if (entity == null && mobSource && dist < 1.5 && r.source().equals("PLAYERS")) return; // your own sounds

        String[] parts = r.id().split("\\.");
        String group = parts.length > 1 ? parts[1] : parts[0];
        String action = parts.length > 2 ? parts[parts.length - 1] : parts[0];

        String title;
        ItemStack icon = ItemStack.EMPTY;
        String key;
        if (entity != null && c.soundEntities) {
            title = entity.getName().getString();
            key = r.id() + "|e|" + entity.getId();
        } else if (c.soundBlocks) {
            BlockPos bp = findBlock(level, at);
            if (bp != null) {
                BlockState st = level.getBlockState(bp);
                title = st.getBlock().getName().getString();
                if (c.soundIcons) icon = new ItemStack(st.getBlock());
                key = r.id() + "|b|" + bp.asLong();
            } else {
                // block is gone (just broken) or not a block sound: fall back to the sound's own name
                title = pretty(group);
                key = r.id() + "|g|" + Math.round(r.x()) + "," + Math.round(r.y()) + "," + Math.round(r.z());
            }
        } else {
            return;
        }

        String detail = pretty(action) + "  -  " + (int) Math.round(dist) + "m " + direction(player, at);
        int accent = accentFor(r.source());

        for (Card card : CARDS) {
            if (card.key.equals(key)) {
                card.count++;
                card.last = now;
                card.detail = detail;
                return;
            }
        }
        Card card = new Card();
        card.key = key;
        card.title = title;
        card.detail = detail;
        card.soundId = r.id();
        card.accent = accent;
        card.icon = icon;
        card.born = now;
        card.last = now;
        CARDS.add(0, card);
    }

    private static Entity nearestEntity(ClientLevel level, Vec3 at, LocalPlayer player, double radius) {
        if (radius <= 0) return null;
        AABB box = new AABB(at.x - radius, at.y - radius - 1, at.z - radius, at.x + radius, at.y + radius + 1, at.z + radius);
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : level.getEntities((Entity) null, box)) {
            if (e == player) continue;
            double d = e.distanceToSqr(at.x, at.y, at.z);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** The block a sound belongs to: the block at the position, else the one just below (footsteps). */
    private static BlockPos findBlock(ClientLevel level, Vec3 at) {
        BlockPos here = BlockPos.containing(at.x, at.y, at.z);
        if (!level.getBlockState(here).isAir()) return here;
        BlockPos below = BlockPos.containing(at.x, at.y - 0.5, at.z);
        if (!level.getBlockState(below).isAir()) return below;
        return null;
    }

    private static String direction(LocalPlayer p, Vec3 at) {
        double dx = at.x - p.getX();
        double dz = at.z - p.getZ();
        double dy = at.y - p.getEyeY();
        String vert = dy > 3 ? ", above" : dy < -3 ? ", below" : "";
        if (dx * dx + dz * dz < 2.25) return "here" + vert;
        // angle of the sound relative to where you face (0 = straight ahead, positive = right)
        double target = Math.toDegrees(Math.atan2(-dx, dz)); // minecraft yaw convention
        double rel = target - p.getYRot();
        rel = ((rel % 360) + 540) % 360 - 180; // -180..180, positive = to your right (yaw grows clockwise)
        double right = rel;
        String[] names = {"ahead", "ahead-right", "right", "behind-right", "behind", "behind-left", "left", "ahead-left"};
        int idx = (int) Math.round(((right % 360 + 360) % 360) / 45.0) % 8;
        return names[idx] + vert;
    }

    private static int accentFor(String source) {
        return switch (source) {
            case "BLOCKS" -> 0xFFFFB04D;
            case "HOSTILE" -> 0xFFFF5A5A;
            case "NEUTRAL" -> 0xFF5FE08A;
            case "PLAYERS" -> 0xFF4DD8FF;
            case "WEATHER" -> 0xFF6A8CFF;
            default -> 0xFFB58CFF; // ambient and everything else
        };
    }

    private static String pretty(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.replace('_', ' ').split(" ")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return sb.toString();
    }

    private static int alpha(int argb, float a) {
        int base = argb >>> 24;
        int out = Math.max(0, Math.min(255, Math.round(base * a)));
        return (out << 24) | (argb & 0x00FFFFFF);
    }

    public static void render(GuiGraphics g, Minecraft mc) {
        OreConfig c = OreConfig.get();
        if (!c.soundEnabled || mc.options.hideGui || CARDS.isEmpty()) return;
        long now = System.currentTimeMillis();
        long life = c.soundSeconds * 1000L;
        int y = 6;
        final int h = 34;
        for (Card card : new ArrayList<>(CARDS)) {
            long age = now - card.born;
            long left = life - (now - card.last);
            float fade = left < 400 ? Math.max(0f, left / 400f) : 1f;
            float slide = age < 250 ? 1f - (float) Math.pow(1 - age / 250.0, 3) : 1f;

            String title = card.count > 1 ? card.title + "  x" + card.count : card.title;
            int textX = card.icon.isEmpty() ? 12 : 32;
            int w = Math.max(150, textX + Math.max(mc.font.width(title),
                    Math.max(mc.font.width(card.detail), mc.font.width(card.soundId))) + 10);
            int x = 6 - Math.round((1f - slide) * (w + 10));

            // card body: violet sky gradient with an accent bar
            g.fillGradient(x, y, x + w, y + h, alpha(0xD0140A36, fade), alpha(0xD02A0F55, fade));
            g.fill(x, y, x + 3, y + h, alpha(card.accent, fade));
            g.fill(x + 3, y, x + w, y + 1, alpha(0x66B58CFF, fade));
            g.fill(x + 3, y + h - 1, x + w, y + h, alpha(0x66B58CFF, fade));
            g.fill(x + w - 1, y, x + w, y + h, alpha(0x66B58CFF, fade));

            if (!card.icon.isEmpty() && fade > 0.5f) g.renderItem(card.icon, x + 10, y + 9);

            g.drawString(mc.font, title, x + textX, y + 5, alpha(0xFFFFFFFF, fade));
            g.drawString(mc.font, card.detail, x + textX, y + 15, alpha(0xFFE6D6FF, fade));
            g.drawString(mc.font, card.soundId, x + textX, y + 24, alpha(0xFFA896D8, fade));
            y += h + 4;
        }
    }
}
