package com.texasoveja.villarium.common.entity.ai;

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
        // Solo funciona si el aldeano tiene la etiqueta activada
        if (!this.villager.getTags().contains("villarium:is_following")) {
            return false;
        }
        // Busca el jugador más cercano
        this.player = this.villager.level().getNearestPlayer(this.villager, 10.0D);
        return this.player != null && this.villager.distanceToSqr(this.player) > (double)(startDistance * startDistance);
    }

    @Override
    public boolean canContinueToUse() {
        return this.player != null && !this.villager.getNavigation().isDone() && this.villager.distanceToSqr(this.player) > (double)(stopDistance * stopDistance) && this.villager.getTags().contains("villarium:is_following");
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        this.player = null;
        this.villager.getNavigation().stop();
    }

    @Override
    public void tick() {
        this.villager.getLookControl().setLookAt(this.player, 10.0F, (float)this.villager.getMaxHeadXRot());
        this.villager.getNavigation().moveTo(this.player, this.speedModifier);
    }
}