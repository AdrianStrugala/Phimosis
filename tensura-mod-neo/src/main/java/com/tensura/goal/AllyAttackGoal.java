package com.tensura.goal;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;

/**
 * Makes the companion attack whoever its mob.getTarget() is.
 * Target is set externally by CombatCompanionEvents (owner attacks X, or X attacks owner).
 * Filters: never attacks owner, never attacks other companions.
 */
public class AllyAttackGoal extends MeleeAttackGoal {

    private final Player owner;

    public AllyAttackGoal(PathfinderMob mob, Player owner) {
        super(mob, 1.3, true);
        this.owner = owner;
    }

    @Override
    public boolean canUse() {
        if (mob.isVehicle()) return false;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (target == owner) return false;
        if (target.getTags().contains("tensura:combat_companion")) return false;
        return true;
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target) {
        try {
            super.checkAndPerformAttack(target);
        } catch (IllegalArgumentException ignored) {
            // PokemonEntity lacks ATTACK_DAMAGE attribute — Cobblemon handles its own combat
        }
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (target == owner) return false;
        if (target.getTags().contains("tensura:combat_companion")) return false;
        return true;
    }
}
