package com.tttpog.stormfront;

import com.tttpog.stormfront.client.ClientStorms;
import com.tttpog.stormfront.storm.StormData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StormfrontMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, SyncPacket.class, SyncPacket::encode, SyncPacket::decode, SyncPacket::handle);
    }

    public static void sendTo(ServerPlayer player, List<StormData> storms) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncPacket(storms));
    }

    public static final class SyncPacket {
        public final List<StormData> storms;

        public SyncPacket(List<StormData> storms) {
            this.storms = storms;
        }

        public static void encode(SyncPacket p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.storms.size());
            for (StormData s : p.storms) s.write(buf);
        }

        public static SyncPacket decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<StormData> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(StormData.read(buf));
            return new SyncPacket(list);
        }

        public static void handle(SyncPacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientStorms.receive(p.storms)));
            ctx.get().setPacketHandled(true);
        }
    }

    private Net() {}
}
