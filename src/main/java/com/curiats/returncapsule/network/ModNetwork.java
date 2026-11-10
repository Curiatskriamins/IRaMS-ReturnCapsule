package com.curiats.returncapsule.network;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ReturnCapsuleMod.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenTransportMenuMessage.class,
                OpenTransportMenuMessage::encode, OpenTransportMenuMessage::decode,
                OpenTransportMenuMessage::handle);
    }

    public static void openTransportMenu(int entityId) {
        CHANNEL.sendToServer(new OpenTransportMenuMessage(entityId));
    }

    public record OpenTransportMenuMessage(int entityId) {
        public static void encode(OpenTransportMenuMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.entityId);
        }

        public static OpenTransportMenuMessage decode(FriendlyByteBuf buffer) {
            return new OpenTransportMenuMessage(buffer.readVarInt());
        }

        public static void handle(OpenTransportMenuMessage message,
                                  java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> supplier) {
            net.minecraftforge.network.NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null && player.level().getEntity(message.entityId) instanceof TransportCapsuleEntity capsule
                        && (capsule.hasPassenger(player) || player.distanceToSqr(capsule) < 64.0D)) {
                    player.openMenu(capsule);
                }
            });
            context.setPacketHandled(true);
        }
    }

    private ModNetwork() {
    }
}
