package com.texasoveja.villarium.client;

import com.texasoveja.villarium.Villarium;
import com.texasoveja.villarium.common.entity.IVillagerFollow;
import com.texasoveja.villarium.network.FollowPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(modid = Villarium.MODID, value = Dist.CLIENT)
public class ClientEvents {

    private static Button followButton;

    @SubscribeEvent
    public static void onInitScreen(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof MerchantScreen merchantScreen) {
            MerchantMenu menu = merchantScreen.getMenu();

            // Cálculos básicos de la ventana
            // El ancho total de la textura del aldeano es 276 px
            int guiLeft = (merchantScreen.width - 276) / 2;
            int guiTop = (merchantScreen.height - 166) / 2;

            // --- NUEVAS COORDENADAS SEGÚN TU IMAGEN (4.PNG) ---

            // X: guiLeft + 276 es el borde exacto derecho.
            // Sumamos +4 para dejar un pequeño margen y que esté fuera.
            int x = guiLeft + 280;

            // Y: guiTop + 10 lo alinea con la parte superior (donde dice el nombre del aldeano)
            int y = guiTop + 10;

            // Creamos el botón
            followButton = Button.builder(Component.literal("Seguir"), button -> {
                        PacketDistributor.sendToServer(new FollowPacket(menu.containerId));

                        // Cambio visual inmediato al pulsar
                        if (button.getMessage().getString().equals("Seguir")) {
                            button.setMessage(Component.literal("Siguiendo"));
                        } else {
                            button.setMessage(Component.literal("Seguir"));
                        }
                    })
                    .pos(x, y)
                    .size(60, 20) // Un poco más ancho (60) para que se lea bien fuera
                    .build();

            followButton.active = false;
            event.addListener(followButton);
        }
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof MerchantScreen merchantScreen && followButton != null) {
            MerchantMenu menu = merchantScreen.getMenu();
            Player localPlayer = Minecraft.getInstance().player;

            if (localPlayer == null) return;

            // 1. NIVEL
            int level = menu.getTraderLevel();
            boolean isUnlocked = level >= 3;
            followButton.active = isUnlocked;

            if (!isUnlocked) {
                followButton.setMessage(Component.literal("Seguir"));
                return;
            }

            // 2. BUSCAR AL ALDEANO REAL
            Villager realVillager = findTradingVillager(localPlayer);

            if (realVillager instanceof IVillagerFollow followVillager) {
                boolean isFollowing = followVillager.villarium$isFollowing();

                String expectedText = isFollowing ? "Siguiendo" : "Seguir";
                if (!followButton.getMessage().getString().equals(expectedText)) {
                    followButton.setMessage(Component.literal(expectedText));
                }
            }
        }
    }

    private static Villager findTradingVillager(Player player) {
        List<Villager> villagers = player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(10));

        for (Villager v : villagers) {
            if (v.getTradingPlayer() == player) {
                return v;
            }
        }

        return villagers.stream()
                .min(Comparator.comparingDouble(v -> v.distanceToSqr(player)))
                .orElse(null);
    }
}