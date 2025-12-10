package com.texasoveja.villarium.mixin;

import com.texasoveja.villarium.common.entity.ai.FollowPlayerGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager {

    public VillagerMixin(EntityType<? extends AbstractVillager> entityType, net.minecraft.world.level.Level level) {
        super(entityType, level);
    }

    @Shadow public abstract VillagerData getVillagerData();



    // --- 1. IA SEGUIR JUGADOR (Inyección en Constructor) ---
    // Usamos <init> para asegurarnos de que la meta se añada al crear el aldeano sin errores.
    @Inject(method = "<init>", at = @At("TAIL"))
    private void villarium$onInit(EntityType<? extends AbstractVillager> entityType, net.minecraft.world.level.Level level, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        System.out.println("VILLARIUM: Aldeano creado con IA modificada");
        // Añadimos la meta de seguir al jugador con prioridad 2
        // Nota: goalSelector es accesible porque AbstractVillager hereda de Mob
        this.goalSelector.addGoal(2, new FollowPlayerGoal(self, 0.7D));
    }

    // AÑADE ESTO NUEVO:
    // Cambiamos el límite de nivel de 5 a 6 para permitir que suba uno más.
    @ModifyConstant(method = "increaseMerchantCareer", constant = @Constant(intValue = 5))
    private int villarium$increaseMaxLevel(int constant) {
        return 6;
    }

    // --- 3. BONIFICACIÓN POR MESAS DE TRABAJO ---
    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void villarium$updatePricesBasedOnWorkstations(net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
        if (this.level().isClientSide) return;

        Villager villager = (Villager) (Object) this;
        BlockPos pos = villager.blockPosition();
        ServerLevel serverLevel = (ServerLevel) this.level();

        VillagerProfession profession = villager.getVillagerData().getProfession();

        // Buscamos mesas cercanas del mismo tipo usando heldJobSite (API correcta 1.21)
        long nearbyWorkstations = serverLevel.getPoiManager().getInRange(
                holder -> profession.heldJobSite().test(holder),
                pos,
                10,
                PoiManager.Occupancy.ANY
        ).limit(6).count();

        int bonusTables = Math.max(0, (int) nearbyWorkstations - 1);
        int finalBonus = Math.min(bonusTables, 5);

        if (finalBonus > 0) {
            for (MerchantOffer offer : this.getOffers()) {
                int currentDiscount = offer.getSpecialPriceDiff();
                // Aplicamos descuento: -2 esmeraldas por cada mesa extra
                offer.setSpecialPriceDiff(currentDiscount - (finalBonus * 2));
            }
        }
    }
}