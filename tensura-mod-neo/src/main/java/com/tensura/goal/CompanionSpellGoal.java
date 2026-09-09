package com.tensura.goal;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.engine.SpellDefinition;
import com.tensura.engine.SpellExecutor;
import com.tensura.engine.SpellRegistry;
import com.tensura.engine.SpellTargetingRules;
import com.tensura.event.SpellCastController;
import com.tensura.spell.CobblemonMoveMapper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Periodic spell casting by a companion Pokemon.
 * Picks a random spell from its list and fires it at the current target.
 * Cools down per-spell via SpellExecutor's own cooldown map keyed by owner UUID.
 */
public class CompanionSpellGoal extends Goal {

    private static final int DECISION_INTERVAL = 10;
    private static final int MAX_OWNER_DISTANCE = 24;
    private static final double CLOSE_RANGE = 4.0;

    private final PokemonEntity companion;
    private final UUID ownerId;
    private long nextDecisionAt;
    private ResourceLocation lastSpell;

    public CompanionSpellGoal(PokemonEntity companion, ServerPlayer owner) {
        this.companion = companion;
        this.ownerId = owner.getUUID();
        setFlags(EnumSet.noneOf(Flag.class)); // doesn't block movement
    }

    /**
     * Resolved per call rather than held: a ServerPlayer instance is replaced on respawn and on
     * dimension change, and a stale one never reports alive again, which would silence the goal
     * for the rest of the companion's life. Null also covers "owner is in another level".
     */
    private ServerPlayer owner() {
        return companion.level().getPlayerByUUID(ownerId) instanceof ServerPlayer owner
                && owner.isAlive() ? owner : null;
    }

    @Override
    public boolean canUse() {
        if (companion.isVehicle() || companion.isBattling()) return false;
        if (SpellCastController.isCompanionBusy(companion.getUUID())) return false;
        ServerPlayer owner = owner();
        if (owner == null) return false;
        if (companion.distanceToSqr(owner) > MAX_OWNER_DISTANCE * MAX_OWNER_DISTANCE) return false;
        LivingEntity target = companion.getTarget();
        return target != null && target.isAlive()
            && SpellTargetingRules.canCompanionTarget(owner, companion, target)
            && companion.level().getGameTime() >= nextDecisionAt;
    }

    @Override
    public void start() {
        nextDecisionAt = companion.level().getGameTime() + DECISION_INTERVAL;
        ServerPlayer owner = owner();
        if (owner == null) return;
        LivingEntity target = companion.getTarget();
        if (target == null || !SpellTargetingRules.canCompanionTarget(owner, companion, target)) return;

        // Deduplicated: a moveset can name the same spell twice, and duplicates would both
        // survive the size() > 1 gate below and leave removeIf with an empty list.
        List<Candidate> candidates = new ArrayList<>();
        Set<ResourceLocation> seen = new HashSet<>();
        for (ResourceLocation spellId : CobblemonMoveMapper.getSpellsForPokemon(companion)) {
            if (!seen.add(spellId)) continue;
            SpellDefinition definition = SpellRegistry.get(spellId).orElse(null);
            if (definition != null && SpellExecutor.isCompanionSpellReady(companion, spellId)
                    && canUseSpell(definition, target)) {
                candidates.add(new Candidate(spellId, definition));
            }
        }

        if (candidates.size() > 1 && lastSpell != null) {
            candidates.removeIf(candidate -> candidate.id.equals(lastSpell));
        }
        if (candidates.isEmpty()) return;
        preferRangeAppropriateSpells(candidates, target);
        Candidate selected = candidates.get(companion.getRandom().nextInt(candidates.size()));

        // Temporarily face target so aim-type targeting works
        companion.getLookControl().setLookAt(target, 30f, 30f);

        if (SpellExecutor.castAsCompanion(owner, companion, selected.id,
                selected.definition, target)) {
            lastSpell = selected.id;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return false; // one-shot per canUse cycle
    }

    private boolean canUseSpell(SpellDefinition definition, LivingEntity target) {
        double healthRatio = companion.getHealth() / companion.getMaxHealth();
        if (isHealingSpell(definition) && healthRatio >= 0.6) return false;

        double distanceSqr = companion.distanceToSqr(target);
        if ("self".equals(definition.targeting.type)) {
            if ("counter".equals(definition.delivery.type)) {
                return distanceSqr <= definition.targeting.range * definition.targeting.range;
            }
            if (isOffensiveSelfArea(definition)) {
                double radius = Math.max(definition.targeting.radius,
                        definition.targeting.range);
                return radius > 0.0 && distanceSqr <= radius * radius;
            }
            return true;
        }

        if (distanceSqr > definition.targeting.range * definition.targeting.range) return false;
        return !requiresLineOfSight(definition) || companion.hasLineOfSight(target);
    }

    private static boolean isHealingSpell(SpellDefinition definition) {
        boolean heals = definition.impact.stream().anyMatch(impact ->
                "heal".equals(impact.type)
                        || "heal_fraction".equals(impact.type)
                        || "heal_damage_fraction".equals(impact.type)
                        || "full_heal".equals(impact.type));
        boolean dealsDamage = definition.impact.stream().anyMatch(impact ->
            "damage".equals(impact.type)
                || "speed_scaled_damage".equals(impact.type)
                || "health_scaled_damage".equals(impact.type)
                || "status_scaled_damage".equals(impact.type)
                || "target_attack_scaled_damage".equals(impact.type)
                || "chain_damage".equals(impact.type));
        return heals && !dealsDamage;
    }

    private static boolean isOffensiveSelfArea(SpellDefinition definition) {
        if ("protective_aura".equals(definition.delivery.type)
                || "self".equals(definition.delivery.type)) return false;
        return definition.targeting.radius > 0.0;
    }

    private static boolean requiresLineOfSight(SpellDefinition definition) {
        return "aim".equals(definition.targeting.type)
                || "beam".equals(definition.targeting.type)
                || "projectile".equals(definition.delivery.type)
                || "beam".equals(definition.delivery.type)
                || "channel_beam".equals(definition.delivery.type);
    }

    private void preferRangeAppropriateSpells(List<Candidate> candidates,
                                               LivingEntity target) {
        boolean close = companion.distanceToSqr(target) <= CLOSE_RANGE * CLOSE_RANGE;
        List<Candidate> preferred = candidates.stream()
                .filter(candidate -> close == isCloseRange(candidate.definition))
                .toList();
        if (!preferred.isEmpty()) {
            candidates.clear();
            candidates.addAll(preferred);
        }
    }

    private static boolean isCloseRange(SpellDefinition definition) {
        return switch (definition.delivery.type) {
            case "dash", "dash_combo", "melee_combo", "teleport_strike", "grab",
                    "arc_strike" -> true;
            default -> definition.targeting.range > 0.0
                    && definition.targeting.range <= CLOSE_RANGE;
        };
    }

    private record Candidate(ResourceLocation id, SpellDefinition definition) {
    }
}
