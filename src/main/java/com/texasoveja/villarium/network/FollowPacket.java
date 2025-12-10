package com.texasoveja.villarium.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FollowPacket(int villagerId) implements CustomPacketPayload {

    public static final Type<FollowPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("villarium", "follow_packet"));

    // Codec para 1.21
    public static final StreamCodec<FriendlyByteBuf, FollowPacket> STREAM_CODEC = StreamCodec.composite(
            StreamCodec.of(FriendlyByteBuf::writeInt, FriendlyByteBuf::readInt),
            FollowPacket::villagerId,
            FollowPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final FollowPacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                if (serverPlayer.containerMenu instanceof MerchantMenu menu) {
                    // Reflexión en el servidor para obtener el trader
                    try {
                        java.lang.reflect.Field traderField = MerchantMenu.class.getDeclaredField("trader");
                        traderField.setAccessible(true);
                        Object traderObj = traderField.get(menu);

                        if (traderObj instanceof Villager villager) {
                            if (villager.getTags().contains("villarium:is_following")) {
                                villager.removeTag("villarium:is_following");
                            } else {
                                villager.addTag("villarium:is_following");
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
}