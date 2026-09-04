package com.tensura.goal;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.engine.SpellDefinition;
import com.tensura.engine.SpellExecutor;
import com.tensura.engine.SpellRegistry;
import com.tensura.engine.SpellTargetingRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;

/**
 * Periodic spell casting by a companion Pokemon.
 * Picks a random spell from its list and fires it at the current target.
 * Cools down per-spell via SpellExecutor's own cooldown map keyed by owner UUID.
 */
public class CompanionSpellGoal extends Goal {

    private static final int CAST_INTERVAL_MIN = 40;  // ticks between cast attempts
    private static final int CAST_RANGE = 20;

    private final PokemonEntity companion;
    private final ServerPlayer owner;
    private final List<ResourceLocation> spells;
    private int cooldown = 60;

    public CompanionSpellGoal(PokemonEntity companion, ServerPlayer owner, List<ResourceLocation> spells) {
        this.companion = companion;
        this.owner = owner;
        this.spells = spells;
        setFlags(EnumSet.noneOf(Flag.class)); // doesn't block movement
    }

    @Override
    public boolean canUse() {
        if (companion.isVehicle()) return false;
        if (spells.isEmpty()) return false;
        LivingEntity target = companion.getTarget();
        return target != null && target.isAlive()
                && SpellTargetingRules.canHarm(owner, companion, target)
                && companion.distanceToSqr(target) <= CAST_RANGE * CAST_RANGE
                && cooldown <= 0;
    }

    @Override
    public void start() {
        LivingEntity target = companion.getTarget();
        if (target == null || spells.isEmpty()
                || !SpellTargetingRules.canHarm(owner, companion, target)) return;

        // Pick a random spell the companion knows
        ResourceLocation spellId = spells.get(companion.getRandom().nextInt(spells.size()));
        SpellDefinition def = SpellRegistry.get(spellId).orElse(null);
        if (def == null) {
            cooldown = CAST_INTERVAL_MIN;
            return;
        }

        // Temporarily face target so aim-type targeting works
        companion.getLookControl().setLookAt(target, 30f, 30f);

        // Cast via SpellExecutor using the owner's identity (so cooldowns, permissions, etc. apply)
        SpellExecutor.castAsCompanion(owner, companion, spellId, def, target);

        cooldown = CAST_INTERVAL_MIN + companion.getRandom().nextInt(40);
    }

    @Override
    public boolean canContinueToUse() {
        return false; // one-shot per canUse cycle
    }

    @Override
    public void tick() {
        if (cooldown > 0) cooldown--;
    }
}
