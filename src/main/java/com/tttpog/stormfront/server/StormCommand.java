package com.tttpog.stormfront.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tttpog.stormfront.storm.StormData;
import com.tttpog.stormfront.storm.StormType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * /stormfront spawn <lp|classic|hp> [here]   - spawn a mature storm
 * /stormfront tornado [ef 0-5]                - force a tornado on the nearest storm
 * /stormfront list | clear
 */
@Mod.EventBusSubscriber(modid = "stormfront", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StormCommand {
    private StormCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("stormfront").requires(src -> src.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("lp", "classic", "hp"), b))
                                .executes(c -> spawn(c.getSource(), StringArgumentType.getString(c, "type"), false))
                                .then(Commands.literal("here")
                                        .executes(c -> spawn(c.getSource(), StringArgumentType.getString(c, "type"), true)))))
                .then(Commands.literal("tornado")
                        .executes(c -> tornado(c.getSource(), -1))
                        .then(Commands.argument("ef", IntegerArgumentType.integer(0, 5))
                                .executes(c -> tornado(c.getSource(), IntegerArgumentType.getInteger(c, "ef")))))
                .then(Commands.literal("clear").executes(c -> {
                    StormManager.STORMS.clear();
                    c.getSource().sendSuccess(() -> Component.literal("All storms cleared."), true);
                    return 1;
                }))
                .then(Commands.literal("list").executes(c -> list(c.getSource()))));
    }

    private static int spawn(CommandSourceStack src, String name, boolean here) {
        StormType t = StormType.byName(name);
        if (t == null) {
            src.sendFailure(Component.literal("Unknown storm type. Use lp, classic or hp."));
            return 0;
        }
        try {
            ServerPlayer p = src.getPlayerOrException();
            ServerLevel level = p.serverLevel();
            Vec3 look = p.getLookAngle();
            double lx = look.x, lz = look.z;
            double len = Math.sqrt(lx * lx + lz * lz);
            if (len < 0.01) {
                lx = 1;
                lz = 0;
                len = 1;
            }
            lx /= len;
            lz /= len;
            double dist = here ? 380 : 1100;
            double sx = p.getX() + lx * dist, sz = p.getZ() + lz * dist;
            // heads straight toward the player
            StormData s = StormManager.spawn(level, t, sx, sz, p.getX(), p.getZ(), 40, true);
            if (here) s.speed *= 0.6f;
            src.sendSuccess(() -> Component.literal("Spawned a " + t.label + " supercell " + (int) dist
                    + " blocks away, heading toward you."), true);
            return 1;
        } catch (Exception ex) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
    }

    private static int tornado(CommandSourceStack src, int ef) {
        try {
            ServerPlayer p = src.getPlayerOrException();
            StormData s = StormManager.nearest(p.getX(), p.getZ());
            if (s == null) {
                src.sendFailure(Component.literal("No storms. Use /stormfront spawn first."));
                return 0;
            }
            if (s.tornado) {
                src.sendFailure(Component.literal("That storm already has a tornado."));
                return 0;
            }
            StormManager.startTornado(p.serverLevel(), s, p.serverLevel().getRandom(), ef);
            src.sendSuccess(() -> Component.literal("Tornado started (EF" + s.torEf + ")."), true);
            return 1;
        } catch (Exception ex) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
    }

    private static int list(CommandSourceStack src) {
        if (StormManager.STORMS.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No active storms."), false);
            return 0;
        }
        for (StormData s : StormManager.STORMS) {
            String line = "#" + s.id + " " + s.type.label + " @ " + (int) s.x + ", " + (int) s.z
                    + "  strength " + (int) (s.intensity * 100) + "%  hail " + String.format("%.1f", s.hail) + "in"
                    + (s.tornado ? "  TORNADO EF" + s.torEf : "");
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return StormManager.STORMS.size();
    }
}
