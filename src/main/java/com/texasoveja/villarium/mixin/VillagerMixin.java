package com.texasoveja.villarium.mixin;

import com.texasoveja.villarium.common.entity.IVillagerFollow;
import com.texasoveja.villarium.common.entity.FollowPlayerGoal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager implements IVillagerFollow {

    // Identificador único para nuestro dato sincronizado (True/False)
    @Unique
    private static final EntityDataAccessor<Boolean> IS_FOLLOWING = SynchedEntityData.defineId(Villager.class, EntityDataSerializers.BOOLEAN);

    public VillagerMixin(EntityType<? extends AbstractVillager> entityType, net.minecraft.world.level.Level level) {
        super(entityType, level);
    }

    // 1. REGISTRAR EL DATO
    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    protected void villarium$defineData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(IS_FOLLOWING, false);
    }

    // 2. IMPLEMENTAR LA INTERFAZ (GET/SET)
    @Override
    public boolean villarium$isFollowing() {
        return this.entityData.get(IS_FOLLOWING);
    }

    @Override
    public void villarium$setFollowing(boolean following) {
        this.entityData.set(IS_FOLLOWING, following);
    }

    // 3. GUARDAR EN DISCO (Para que no se olvide al reiniciar el mundo)
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    public void villarium$addSaveData(CompoundTag compound, CallbackInfo ci) {
        compound.putBoolean("VillariumIsFollowing", this.villarium$isFollowing());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    public void villarium$readSaveData(CompoundTag compound, CallbackInfo ci) {
        this.villarium$setFollowing(compound.getBoolean("VillariumIsFollowing"));
    }

    // 4. INYECTAR LA IA (El comportamiento)
    @Inject(method = "<init>", at = @At("TAIL"))
    private void villarium$onInit(EntityType<? extends AbstractVillager> entityType, net.minecraft.world.level.Level level, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        // Prioridad 2 para que sea importante
        this.goalSelector.addGoal(2, new FollowPlayerGoal(self, 0.7D));
    }
}