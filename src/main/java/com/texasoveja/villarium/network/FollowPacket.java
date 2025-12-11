package com.texasoveja.villarium.network;

import com.texasoveja.villarium.common.entity.IVillagerFollow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FollowPacket(int containerId) implements CustomPacketPayload {

    public static final Type<FollowPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("villarium", "follow_packet"));

    public static final StreamCodec<FriendlyByteBuf, FollowPacket> STREAM_CODEC = StreamCodec.composite(
            StreamCodec.of(FriendlyByteBuf::writeInt, FriendlyByteBuf::readInt),
            FollowPacket::containerId,
            FollowPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final FollowPacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                // Verificamos que el contenedor coincida para seguridad
                if (serverPlayer.containerMenu.containerId == payload.containerId() &&
                        serverPlayer.containerMenu instanceof MerchantMenu menu) {

                    try {
                        // Accedemos al aldeano mediante reflexión
                        java.lang.reflect.Field traderField = MerchantMenu.class.getDeclaredField("trader");
                        traderField.setAccessible(true);
                        Object traderObj = traderField.get(menu);

                        // AQUÍ ESTÁ LA CLAVE: Usamos la interfaz para cambiar el valor sincronizado
                        if (traderObj instanceof IVillagerFollow followVillager) {
                            boolean currentState = followVillager.villarium$isFollowing();
                            followVillager.villarium$setFollowing(!currentState);
                            // System.out.println("VILLARIUM: Estado cambiado a " + !currentState); // Descomenta para depurar
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
}