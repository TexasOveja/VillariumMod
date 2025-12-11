package com.texasoveja.villarium.common.entity;

import com.texasoveja.villarium.common.entity.IVillagerFollow;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import java.util.EnumSet;

public class FollowPlayerGoal extends Goal {
    private final Villager villager;
    private Player player;
    private final double speedModifier;
    private final float stopDistance = 3.0F;
    private final float startDistance = 6.0F;

    public FollowPlayerGoal(Villager villager, double speed) {
        this.villager = villager;
        this.speedModifier = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 1. COMPROBACIÓN NUEVA: Usamos la interfaz y el dato sincronizado
        if (this.villager instanceof IVillagerFollow followVillager) {
            if (!followVillager.villarium$isFollowing()) {
                return false; // Si la variable es false, no hacemos nada
            }
        } else {
            return false; // Si no tiene la interfaz (algo raro), abortamos
        }

        // 2. LÓGICA DE BÚSQUEDA DEL JUGADOR
        this.player = this.villager.level().getNearestPlayer(this.villager, 10.0D);
        return this.player != null && this.villager.distanceToSqr(this.player) > (double)(startDistance * startDistance);
    }

    @Override
    public boolean canContinueToUse() {
        // Verificamos de nuevo la variable para detenernos si el jugador desactiva el botón mientras caminamos
        boolean isFollowing = (this.villager instanceof IVillagerFollow fv) && fv.villarium$isFollowing();

        return isFollowing && this.player != null && !this.villager.getNavigation().isDone() && this.villager.distanceToSqr(this.player) > (double)(stopDistance * stopDistance);
    }

    @Override
    public void start() {
        // Opcional: El aldeano puede hacer un sonido o mirarte al empezar
    }

    @Override
    public void stop() {
        this.player = null;
        this.villager.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.player != null) {
            this.villager.getLookControl().setLookAt(this.player, 10.0F, (float)this.villager.getMaxHeadXRot());
            this.villager.getNavigation().moveTo(this.player, this.speedModifier);
        }
    }
}