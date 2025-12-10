package com.texasoveja.villarium.client;

import com.texasoveja.villarium.Villarium;
import com.texasoveja.villarium.network.FollowPacket;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MerchantMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Villarium.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onInitScreen(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof MerchantScreen merchantScreen) {
            MerchantMenu menu = merchantScreen.getMenu();

            // Verificamos el nivel usando el menú (Nivel 3 = Oficial/Journeyman)
            if (menu.getTraderLevel() >= 3) {
                int x = merchantScreen.width / 2 + 85;
                int y = merchantScreen.height / 2 - 50;

                event.addListener(Button.builder(Component.literal("Seguir"), button -> {
                            // Enviamos el ID del contenedor, el servidor sabrá qué aldeano es
                            PacketDistributor.sendToServer(new FollowPacket(menu.containerId));
                        })
                        .pos(x, y)
                        .size(50, 20)
                        .build());
            }
        }
    }
}