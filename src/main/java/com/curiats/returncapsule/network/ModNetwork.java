package com.curiats.returncapsule.network;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.entity.TransportCapsuleEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkRegistry;
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
        CHANNEL.registerMessage(id++, RagdollEndMessage.class,
                RagdollEndMessage::encode, RagdollEndMessage::decode, RagdollEndMessage::handle);
        CHANNEL.registerMessage(id++, RagdollStartMessage.class,
                RagdollStartMessage::encode, RagdollStartMessage::decode, RagdollStartMessage::handle);
        CHANNEL.registerMessage(id++, ToggleAnchorGuardMessage.class,
                ToggleAnchorGuardMessage::encode, ToggleAnchorGuardMessage::decode,
                ToggleAnchorGuardMessage::handle);
    }

    public static void openTransportMenu(int entityId) {
        CHANNEL.sendToServer(new OpenTransportMenuMessage(entityId));
    }

    public static void toggleAnchorGuard(int slot) {
        CHANNEL.sendToServer(new ToggleAnchorGuardMessage(slot));
    }

    public static void sendRagdollStart(int entityId) {
        CHANNEL.sendToServer(new RagdollStartMessage(entityId));
    }

    public static void sendRagdollEnd(int entityId, Vec3 position) {
        CHANNEL.sendToServer(new RagdollEndMessage(entityId, position.x, position.y, position.z));
    }

    public record ToggleAnchorGuardMessage(int slot) {
        public static void encode(ToggleAnchorGuardMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.slot);
        }

        public static ToggleAnchorGuardMessage decode(FriendlyByteBuf buffer) {
            return new ToggleAnchorGuardMessage(buffer.readVarInt());
        }

        public static void handle(ToggleAnchorGuardMessage message,
                                  java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> supplier) {
            net.minecraftforge.network.NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || message.slot < 0 || message.slot >= player.containerMenu.slots.size()) {
                    return;
                }
                net.minecraft.world.item.ItemStack stack = player.containerMenu.getSlot(message.slot).getItem();
                if (!(stack.getItem() instanceof com.curiats.returncapsule.item.StandardStorageAnchorItem)) {
                    return;
                }
                com.curiats.returncapsule.item.StandardStorageAnchorItem.toggleGuard(stack);
                player.containerMenu.getSlot(message.slot).setChanged();
                player.containerMenu.broadcastChanges();
            });
            context.setPacketHandled(true);
        }
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

    private static net.minecraft.core.BlockPos findLanding(ServerPlayer player,
                                                          com.curiats.returncapsule.entity.humanoid.NeutralHumanoid humanoid,
                                                          Vec3 target) {
        net.minecraft.core.BlockPos origin = net.minecraft.core.BlockPos.containing(target.x, target.y, target.z);
        net.minecraft.world.level.Level level = player.level();
        if (com.curiats.returncapsule.entity.humanoid.HumanoidTraversal.canStandOn(level, origin)) {
            return origin;
        }
        for (int dy = 1; dy <= 3; dy++) {
            net.minecraft.core.BlockPos up = origin.above(dy);
            if (com.curiats.returncapsule.entity.humanoid.HumanoidTraversal.canStandOn(level, up)) {
                return up;
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                net.minecraft.core.BlockPos side = origin.offset(dx, 0, dz);
                if (com.curiats.returncapsule.entity.humanoid.HumanoidTraversal.canStandOn(level, side)) {
                    return side;
                }
            }
        }
        return humanoid.blockPosition();
    }

    public record RagdollEndMessage(int entityId, double x, double y, double z) {
        public static void encode(RagdollEndMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.entityId);
            buffer.writeDouble(message.x);
            buffer.writeDouble(message.y);
            buffer.writeDouble(message.z);
        }

        public static RagdollEndMessage decode(FriendlyByteBuf buffer) {
            return new RagdollEndMessage(buffer.readVarInt(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble());
        }

        public static void handle(RagdollEndMessage message,
                                  java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> supplier) {
            net.minecraftforge.network.NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;
                }
                if (player.level().getEntity(message.entityId)
                        instanceof com.curiats.returncapsule.entity.humanoid.NeutralHumanoid humanoid) {
                    Vec3 target = new Vec3(message.x, message.y, message.z);
                    if (humanoid.position().distanceToSqr(target) < 1024.0D) {
                        net.minecraft.core.BlockPos landing = findLanding(player, humanoid, target);
                        humanoid.moveTo(landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                                humanoid.getYRot(), humanoid.getXRot());
                        humanoid.setDeltaMovement(Vec3.ZERO);
                        humanoid.hasImpulse = false;
                    }
                }
            });
            context.setPacketHandled(true);
        }
    }

    public record RagdollStartMessage(int entityId) {
        public static void encode(RagdollStartMessage message, FriendlyByteBuf buffer) {
            buffer.writeVarInt(message.entityId);
        }

        public static RagdollStartMessage decode(FriendlyByteBuf buffer) {
            return new RagdollStartMessage(buffer.readVarInt());
        }

        public static void handle(RagdollStartMessage message,
                                  java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> supplier) {
            net.minecraftforge.network.NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;
                }
                if (player.level().getEntity(message.entityId)
                        instanceof com.curiats.returncapsule.entity.humanoid.NeutralHumanoid humanoid
                        && player.distanceToSqr(humanoid) < 4096.0D) {
                    humanoid.knockDownServer(100);
                }
            });
            context.setPacketHandled(true);
        }
    }

    private ModNetwork() {
    }
}
