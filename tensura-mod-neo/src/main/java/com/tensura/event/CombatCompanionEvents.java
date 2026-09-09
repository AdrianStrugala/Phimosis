package com.tensura.event;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.TensuraMod;
import com.tensura.engine.SpellImpactApplier;
import com.tensura.engine.SpellTargetingRules;
import com.tensura.entity.SpellProjectile;
import com.tensura.goal.AllyFollowGoal;
import com.tensura.goal.CompanionSpellGoal;
import kotlin.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CombatCompanionEvents {

    private static final int OWNER_TARGET_PRIORITY = 1;
    private static final int COMPANION_DEFENSE_PRIORITY = 2;
    private static final int OWNER_DEFENSE_PRIORITY = 3;
    private static final int MAX_COMBAT_DISTANCE = 24;

    // ownerUUID → their active companion
    private static final Map<UUID, PokemonEntity> activeCompanions = new HashMap<>();
    private static final Map<UUID, Integer> targetPriorities = new HashMap<>();
    // Companions already torn down for the current not-ready stretch. Not-ready is a sustained
    // normal state (owner mounted, in a Cobblemon battle, out of range), so without this the
    // suspendCompanion sweep would run its entity query every tick for the whole stretch.
    private static final Set<UUID> suspendedCompanions = new HashSet<>();

    public static void registerCobblemonHooks() {
        // Called once from TensuraMod constructor (after mod init)
        CobblemonEvents.POKEMON_SENT_POST.subscribe(Priority.NORMAL, event -> {
            PokemonEntity pokemon = event.getPokemonEntity();
            if (pokemon == null) return Unit.INSTANCE;

            // Only attach AI when sent out by a player
            if (!(pokemon.getOwner() instanceof ServerPlayer owner)) return Unit.INSTANCE;

            // ConversionEvents (HIGH priority) already handled this as a village enrollment
            if (pokemon.getTags().contains("tensura:village_resident")) return Unit.INSTANCE;
                if (activeCompanions.get(owner.getUUID()) == pokemon
                    && pokemon.getTags().contains("tensura:combat_companion")) return Unit.INSTANCE;

            PokemonEntity previous = activeCompanions.get(owner.getUUID());
            if (previous != null && previous != pokemon) {
                detachCompanion(owner.getUUID(), previous);
            }

            attachCompanion(owner, pokemon);

            TensuraMod.LOGGER.debug("[Tensura] Companion AI attached to {} for {}",
                    pokemon.getPokemon().getSpecies().getName(), owner.getName().getString());
            return Unit.INSTANCE;
        });

        CobblemonEvents.POKEMON_RECALL_POST.subscribe(Priority.NORMAL, event -> {
            PokemonEntity oldEntity = event.getOldEntity();
            if (oldEntity == null) return Unit.INSTANCE;
            if (!(oldEntity.getOwner() instanceof ServerPlayer owner)) return Unit.INSTANCE;

            detachCompanion(owner.getUUID(), oldEntity);
            TensuraMod.LOGGER.debug("[Tensura] Companion recalled for {}", owner.getName().getString());
            return Unit.INSTANCE;
        });
    }

    // ── Owner attacks something → companion targets it too ───────────────────

    @SubscribeEvent
    public void onOwnerAttacks(LivingIncomingDamageEvent event) {
        if (SpellImpactApplier.isApplyingCompanionDamage()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        PokemonEntity companion = activeCompanions.get(player.getUUID());
        if (!isReady(companion, player)) return;

        LivingEntity attacked = event.getEntity();
        assignTarget(player, companion, attacked, OWNER_TARGET_PRIORITY);
    }

    // ── Something attacks the owner → companion retaliates ───────────────────

    @SubscribeEvent
    public void onOwnerHurt(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;

        if (event.getEntity() instanceof ServerPlayer owner) {
            PokemonEntity companion = activeCompanions.get(owner.getUUID());
            if (isReady(companion, owner)) {
                assignTarget(owner, companion, attacker, OWNER_DEFENSE_PRIORITY);
            }
            return;
        }

        if (event.getEntity() instanceof PokemonEntity companion
                && companion.getOwner() instanceof ServerPlayer owner
                && activeCompanions.get(owner.getUUID()) == companion
                && isReady(companion, owner)) {
            assignTarget(owner, companion, attacker, COMPANION_DEFENSE_PRIORITY);
        }
    }

    @SubscribeEvent
    public void onCompanionTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof PokemonEntity companion)
                || !companion.getTags().contains("tensura:combat_companion")) return;
        if (!(companion.getOwner() instanceof ServerPlayer owner)) return;

        PokemonEntity registered = activeCompanions.get(owner.getUUID());
        if (registered != companion) {
            // Tagged but unregistered means this instance was reloaded after its level unloaded:
            // the tag survives in NBT, the goals do not. Re-attach, unless the owner has sent out
            // a different companion since — then this one is an orphan and loses its tag.
            if (registered == null) {
                attachCompanion(owner, companion);
            } else {
                companion.removeTag("tensura:combat_companion");
            }
            return;
        }

        if (!isReady(companion, owner)) {
            if (suspendedCompanions.add(companion.getUUID())) {
                suspendCompanion(owner.getUUID(), companion);
            }
            return;
        }
        suspendedCompanions.remove(companion.getUUID());

        LivingEntity target = companion.getTarget();
        if (target == null || !isValidTarget(owner, companion, target)) {
            companion.setTarget(null);
            targetPriorities.remove(owner.getUUID());
            SpellCastController.clearCompanionState(companion.getUUID());
        }
    }

    @SubscribeEvent
    public void onCompanionDeath(LivingDeathEvent event) {
        // Owner death is deliberately not a teardown: isReady() already gates on owner.isAlive(),
        // so the companion suspends and resumes by itself once the owner respawns. Detaching here
        // would leave a still-deployed Pokemon untagged and unreachable.
        if (event.getEntity() instanceof PokemonEntity companion) {
            detachByEntity(companion);
        }
    }

    @SubscribeEvent
    public void onCompanionLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof PokemonEntity companion)) return;
        UUID ownerId = companion.getOwnerUUID();
        if (ownerId == null || activeCompanions.get(ownerId) != companion) return;
        // An unload is not a teardown. Drop the runtime state but keep the tag, so the reloaded
        // instance is recognised in onCompanionTick and gets its goals back — removing it here
        // would also strip it from the NBT this unload is about to write.
        suspendCompanion(ownerId, companion);
        suspendedCompanions.remove(companion.getUUID());
        activeCompanions.remove(ownerId, companion);
    }

    @SubscribeEvent
    public void onOwnerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        detachByOwner(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onOwnerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        detachByOwner(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        activeCompanions.clear();
        targetPriorities.clear();
        suspendedCompanions.clear();
    }

    private static boolean isReady(PokemonEntity companion, ServerPlayer owner) {
        return companion != null && companion.isAlive() && !companion.isBattling()
                && !companion.isVehicle() && owner.isAlive()
                && companion.level() == owner.level()
                && companion.distanceToSqr(owner) <= MAX_COMBAT_DISTANCE * MAX_COMBAT_DISTANCE;
            }

            private static boolean isValidTarget(ServerPlayer owner, PokemonEntity companion,
                             LivingEntity target) {
            return target.level() == owner.level()
                && target.distanceToSqr(owner) <= MAX_COMBAT_DISTANCE * MAX_COMBAT_DISTANCE
                && SpellTargetingRules.canCompanionTarget(owner, companion, target);
    }

    private static void assignTarget(ServerPlayer owner, PokemonEntity companion,
                                     LivingEntity target, int priority) {
        if (!isValidTarget(owner, companion, target)) return;
        int currentPriority = targetPriorities.getOrDefault(owner.getUUID(), 0);
        LivingEntity currentTarget = companion.getTarget();
        if (currentTarget != null && currentTarget.isAlive() && priority < currentPriority) return;
        companion.setTarget(target);
        targetPriorities.put(owner.getUUID(), priority);
    }

    private static void suspendCompanion(UUID ownerId, PokemonEntity companion) {
        companion.setTarget(null);
        targetPriorities.remove(ownerId);
        UUID companionId = companion.getUUID();
        SpellRuntimeController.clearCompanionState(companion);
        SpellMovementController.clearCompanionState(companion);
        if (companion.level() instanceof ServerLevel level) {
            level.getEntitiesOfClass(SpellProjectile.class,
                    companion.getBoundingBox().inflate(128.0),
                    projectile -> projectile.isCastBy(companionId))
                    .forEach(Entity::discard);
        }
    }

    private static void attachCompanion(ServerPlayer owner, PokemonEntity companion) {
        // Goals are not persisted, so an entity reloaded from NBT arrives with an empty
        // goalSelector and needs them added again.
        // (goalSelector/targetSelector public via accesstransformer.cfg)
        companion.goalSelector.addGoal(2, new AllyFollowGoal(companion, owner, 1.2, 3, 24));
        companion.goalSelector.addGoal(4, new CompanionSpellGoal(companion, owner));
        companion.addTag("tensura:combat_companion");
        activeCompanions.put(owner.getUUID(), companion);
        suspendedCompanions.remove(companion.getUUID());
    }

    private static void detachCompanion(UUID ownerId, PokemonEntity companion) {
        suspendCompanion(ownerId, companion);
        suspendedCompanions.remove(companion.getUUID());
        companion.ejectPassengers();
        companion.removeTag("tensura:combat_companion");
        activeCompanions.remove(ownerId, companion);
        MountEvents.onCompanionRecalled(ownerId, companion);
    }

    private static void detachByEntity(PokemonEntity companion) {
        UUID ownerId = companion.getOwnerUUID();
        if (ownerId != null && activeCompanions.get(ownerId) == companion) {
            detachCompanion(ownerId, companion);
        }
    }

    private static void detachByOwner(UUID ownerId) {
        PokemonEntity companion = activeCompanions.get(ownerId);
        if (companion != null) detachCompanion(ownerId, companion);
    }

    // ── Public accessor for other systems ────────────────────────────────────

    public static PokemonEntity getCompanion(UUID ownerUUID) {
        return activeCompanions.get(ownerUUID);
    }
}
