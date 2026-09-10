package com.tensura.engine;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.event.SpellMovementController;
import com.tensura.event.SpellCastController;
import com.tensura.event.SpellRuntimeController;
import com.tensura.network.CooldownSyncPacket;
import com.tensura.network.SpellVfxDispatcher;
import com.tensura.registry.TensuraMobEffects;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SpellExecutor {

    // cooldownMap: playerUUID -> (spellId -> gameTime when ready)
    private static final Map<UUID, Map<ResourceLocation, Long>> cooldowns = new HashMap<>();
    private static final Map<UUID, Map<ResourceLocation, Long>> companionCooldowns = new HashMap<>();
    private static final Map<UUID, Map<ResourceLocation, ChargeState>> charges = new HashMap<>();
    private static final Map<UUID, ResourceLocation> heldChannels = new HashMap<>();
    private static final Map<UUID, ActiveCharge> activeCharges = new HashMap<>();

    public static void clearPlayerState(UUID playerId) {
        cooldowns.remove(playerId);
        charges.remove(playerId);
        SpellImpactApplier.clearPlayerState(playerId);
        heldChannels.remove(playerId);
        activeCharges.remove(playerId);
    }

    public static void clearAllState() {
        cooldowns.clear();
        companionCooldowns.clear();
        charges.clear();
        SpellImpactApplier.clearAllState();
        heldChannels.clear();
        activeCharges.clear();
    }

    public static boolean cast(ServerPlayer caster, ResourceLocation spellId) {
        return cast(caster, spellId, false, false);
    }

    public static boolean castPrepared(ServerPlayer caster, ResourceLocation spellId) {
        return cast(caster, spellId, true, false);
    }

    public static boolean castHeldChannel(ServerPlayer caster, ResourceLocation spellId) {
        return cast(caster, spellId, true, true);
    }

    public static void finishHeldChannel(ServerPlayer caster, ResourceLocation spellId) {
        if (!heldChannels.remove(caster.getUUID(), spellId)) return;
        SpellDefinition definition = SpellRegistry.get(spellId).orElse(null);
        int cooldownTicks = definition == null ? 0 : Math.max(0, definition.cooldown_ticks);
        if (cooldownTicks > 0) {
            long now = caster.level().getGameTime();
            cooldowns.computeIfAbsent(caster.getUUID(), ignored -> new HashMap<>())
                    .put(spellId, now + cooldownTicks);
        }
        PacketDistributor.sendToPlayer(caster, new CooldownSyncPacket(spellId, cooldownTicks));
    }

        public static boolean castChargedProjectile(ServerPlayer caster) {
        ActiveCharge charge = activeCharges.remove(caster.getUUID());
        if (charge == null) return false;
        ResourceLocation spellId = charge.spellId;
        SpellDefinition definition = SpellRegistry.get(spellId).orElse(null);
        if (definition == null || !definition.delivery.charge_release
            || caster.hasEffect(TensuraMobEffects.ASLEEP)
            || caster.hasEffect(TensuraMobEffects.FROZEN)
            || caster.hasEffect(TensuraMobEffects.EXHAUSTED)) return false;

        int minimumChargeTicks = Math.max(1, definition.delivery.minimum_charge_ticks);
        int maximumChargeTicks = Math.max(
            minimumChargeTicks, definition.delivery.maximum_charge_ticks);
        int chargedTicks = (int) Math.min(Integer.MAX_VALUE,
            Math.max(0L, caster.level().getGameTime() - charge.startedAt));
        if (chargedTicks < minimumChargeTicks) return false;

        long now = caster.level().getGameTime();
        Map<ResourceLocation, Long> playerCooldowns = cooldowns.computeIfAbsent(
            caster.getUUID(), ignored -> new HashMap<>());
        if (now < playerCooldowns.getOrDefault(spellId, 0L)) return false;

        double progress = maximumChargeTicks <= minimumChargeTicks ? 1.0
            : Math.max(0.0, Math.min(1.0,
                (double) (chargedTicks - minimumChargeTicks)
                    / (maximumChargeTicks - minimumChargeTicks)));
        double damageScale = 0.65 + 0.35 * progress;
        double radiusScale = 0.75 + 0.50 * progress;
        SpellProjectileDelivery.castChargedProjectile(
            caster, definition, spellId, damageScale, radiusScale);
        playerCooldowns.put(spellId, now + definition.cooldown_ticks);
        PacketDistributor.sendToPlayer(
            caster, new CooldownSyncPacket(spellId, definition.cooldown_ticks));
        caster.swing(InteractionHand.MAIN_HAND, true);
        SpellFeedback.playTravelSound(caster, definition);
        return true;
    }

    public static boolean beginCharge(ServerPlayer caster, ResourceLocation spellId) {
        SpellDefinition definition = SpellRegistry.get(spellId).orElse(null);
        if (definition == null || !definition.delivery.charge_release
                || SpellCastController.isCasting(caster)
                || caster.hasEffect(TensuraMobEffects.ASLEEP)
                || caster.hasEffect(TensuraMobEffects.FROZEN)
                || caster.hasEffect(TensuraMobEffects.EXHAUSTED)
                || activeCharges.containsKey(caster.getUUID())
                || caster.level().getGameTime() < cooldowns
                    .getOrDefault(caster.getUUID(), Map.of())
                    .getOrDefault(spellId, 0L)) return false;
        activeCharges.put(caster.getUUID(), new ActiveCharge(
            spellId, caster.level().getGameTime()));
        SpellFeedback.sendCastVfx(caster, definition);
        SpellFeedback.playCastSound(caster, definition);
        return true;
    }

    public static boolean isCharging(UUID casterId) {
        return activeCharges.containsKey(casterId);
    }

    public static boolean interruptCharge(UUID casterId) {
        return activeCharges.remove(casterId) != null;
    }

    private static boolean cast(ServerPlayer caster, ResourceLocation spellId,
                                boolean skipCastTime, boolean deferCooldown) {
        if (caster.hasEffect(TensuraMobEffects.ASLEEP)
            || caster.hasEffect(TensuraMobEffects.FROZEN)
            || caster.hasEffect(TensuraMobEffects.EXHAUSTED)) {
            caster.sendSystemMessage(Component.literal("§7[You cannot cast right now]"));
            return false;
        }
        if (SpellCastController.isCasting(caster)) {
            caster.sendSystemMessage(Component.literal("§7[Already casting]"));
            return false;
        }
        if (deferCooldown && heldChannels.containsKey(caster.getUUID())) return false;

        SpellDefinition def = SpellRegistry.get(spellId).orElse(null);
        if (def == null) {
            caster.sendSystemMessage(Component.literal("Unknown spell: " + spellId));
            return false;
        }
        if ("orbit_release".equals(def.delivery.type)
                && SpellRuntimeController.releaseOrbit(caster)) {
            caster.swing(InteractionHand.MAIN_HAND, true);
            SpellFeedback.playTravelSound(caster, def);
            return true;
        }
        if ("phase_movement".equals(def.delivery.type)
                && SpellMovementController.releasePhaseMovement(caster)) {
            caster.swing(InteractionHand.MAIN_HAND, true);
            SpellFeedback.playTravelSound(caster, def);
            return true;
        }

        long now = caster.level().getGameTime();
        Map<ResourceLocation, Long> playerCooldowns = cooldowns.computeIfAbsent(caster.getUUID(), k -> new HashMap<>());
        ChargeState chargeState = null;
        if (def.charges > 1) {
            int recoveryTicks = def.charge_recovery_ticks > 0
                    ? def.charge_recovery_ticks : def.cooldown_ticks;
            Map<ResourceLocation, ChargeState> playerCharges = charges.computeIfAbsent(
                    caster.getUUID(), ignored -> new HashMap<>());
            chargeState = playerCharges.computeIfAbsent(spellId,
                    ignored -> new ChargeState(def.charges));
            chargeState.refresh(now, def.charges, recoveryTicks);
            if (chargeState.available <= 0) {
                long remaining = Math.max(1, chargeState.nextChargeAt - now) / 20 + 1;
                caster.sendSystemMessage(Component.literal("§7[Next charge: " + remaining + "s]"));
                return false;
            }
        } else {
            long ready = playerCooldowns.getOrDefault(spellId, 0L);
            if (now < ready) {
                long remaining = (ready - now) / 20 + 1;
                caster.sendSystemMessage(Component.literal("§7[Cooldown: " + remaining + "s]"));
                return false;
            }
        }
        boolean started = def.cast_time_ticks > 0 && !skipCastTime
                ? SpellCastController.startCast(caster, spellId, def)
                : executeDelivery(caster, spellId, def);
        if (!started) return false;

        caster.swing(InteractionHand.MAIN_HAND, true);
        SpellFeedback.sendCastVfx(caster, def);
        SpellFeedback.playCastSound(caster, def);
        if (deferCooldown) {
            heldChannels.put(caster.getUUID(), spellId);
        } else if (chargeState != null) {
            int recoveryTicks = def.charge_recovery_ticks > 0
                ? def.charge_recovery_ticks : def.cooldown_ticks;
            chargeState.consume(now, def.charges, recoveryTicks);
            int visibleCooldown = chargeState.available > 0
                ? 0 : (int) Math.max(1, chargeState.nextChargeAt - now);
            PacketDistributor.sendToPlayer(caster, new CooldownSyncPacket(spellId, visibleCooldown));
        } else {
            playerCooldowns.put(spellId, now + def.cooldown_ticks);
            PacketDistributor.sendToPlayer(caster, new CooldownSyncPacket(spellId, def.cooldown_ticks));
        }

        return true;
    }

    public static boolean executeDelivery(ServerPlayer caster, ResourceLocation spellId, SpellDefinition def) {
        boolean started = switch (def.delivery.type) {
            case "dash" -> SpellMovementController.startDash(caster, def);
            case "dash_combo" -> SpellRuntimeController.startDashCombo(
                caster, caster, SpellTargetResolver.rayCast(
                    caster, def.targeting.range), def);
            case "vortex" -> castVortex(caster, def);
            case "delayed" -> castDelayed(caster, def);
            case "delayed_area" -> castDelayedArea(caster, def);
            case "moving_zone" -> castMovingZone(caster, def);
            case "contact_aura" -> SpellRuntimeController.startContactAura(caster, caster, def);
            case "orbit_release" -> SpellRuntimeController.startOrbit(caster, caster, def);
            case "phase_movement" -> SpellMovementController.startPhaseMovement(caster, def);
            case "pulse_ring" -> SpellRuntimeController.startPulseRing(caster, caster, def);
            case "protective_aura" -> SpellRuntimeController.startProtectiveAura(caster, caster, def);
                case "zone" -> SpellRuntimeController.startZone(
                    caster, caster, def, caster.position());
            case "counter" -> SpellRuntimeController.startCounter(caster, def);
            case "channel_beam" -> SpellCastController.startChannelBeam(caster, def);
                case "channel_cone" -> SpellCastController.startChannelCone(caster, caster, def);
                case "wave" -> castWave(caster, caster, def);
                case "trap" -> castTrap(caster, caster, def,
                    SpellTargetResolver.resolveAimPosition(caster, def.targeting.range));
                case "melee_combo" -> castMeleeCombo(caster, caster,
                    SpellTargetResolver.rayCast(caster, def.targeting.range), def);
                case "teleport_strike" -> castTeleportStrike(caster, caster,
                    SpellTargetResolver.rayCast(caster, def.targeting.range), def);
                case "ricochet_beam" -> SpellBeamDelivery.castRicochetBeam(
                    caster, caster, null, def);
                case "arc_strike" -> SpellBeamDelivery.castArcStrike(
                    caster, caster, null, def);
                case "grab" -> { castStandard(caster, def); yield true; }
            case "beam" -> {
                SpellBeamDelivery.castRuntimeBeam(caster, caster, null, def);
                yield true;
            }
            case "meteor" -> {
                SpellProjectileDelivery.castMeteor(caster, spellId, def);
                yield true;
            }
            case "cloud" -> {
                SpellProjectileDelivery.castCloud(caster, def);
                yield true;
            }
            case "projectile" -> {
                SpellProjectileDelivery.castProjectile(caster, def, spellId);
                yield true;
            }
            case "instant", "self" -> { castStandard(caster, def); yield true; }
            default -> { castStandard(caster, def); yield true; }
        };
        if (started) SpellFeedback.playTravelSound(caster, def);
        return started;
    }

    /**
     * Called by CompanionSpellGoal. Uses companion's position as origin,
     * directly hits the known target — no raycast needed.
     * Cooldown keyed on owner UUID with a companion-specific suffix to not share with player's own spells.
     */
        public static boolean isCompanionSpellReady(PokemonEntity companion,
                            ResourceLocation spellId) {
        UUID pokemonId = companion.getPokemon().getUuid();
        long readyAt = companionCooldowns.getOrDefault(pokemonId, Map.of())
            .getOrDefault(spellId, 0L);
        return companion.level().getGameTime() >= readyAt;
        }

        public static boolean castAsCompanion(ServerPlayer owner, PokemonEntity companion,
                           ResourceLocation spellId, SpellDefinition def,
                           LivingEntity target) {
        if (!(companion.level() instanceof ServerLevel serverLevel)) return false;
        if (companion.isBattling() || companion.isVehicle()) return false;
        if (companion.hasEffect(TensuraMobEffects.ASLEEP)
                || companion.hasEffect(TensuraMobEffects.FROZEN)
            || companion.hasEffect(TensuraMobEffects.EXHAUSTED)) return false;
        if (!"self".equals(def.targeting.type)
            && !SpellTargetingRules.canCompanionTarget(owner, companion, target)) return false;

        UUID pokemonId = companion.getPokemon().getUuid();
        long now = companion.level().getGameTime();
        Map<ResourceLocation, Long> pokemonCooldowns = companionCooldowns.computeIfAbsent(
            pokemonId, ignored -> new HashMap<>());
        if (now < pokemonCooldowns.getOrDefault(spellId, 0L)) return false;

        boolean started = def.cast_time_ticks > 0 || def.delivery.charge_release
                ? SpellCastController.startCompanionCast(owner, companion, target, spellId, def)
                : executeCompanionDelivery(owner, companion, target, spellId, def);
        if (!started) return false;

        pokemonCooldowns.put(spellId, now + Math.max(0, def.cooldown_ticks));
        SpellFeedback.sendCastVfx(companion, def);
        serverLevel.sendParticles(SpellFeedback.schoolParticle(def.school),
                companion.getX(), companion.getY() + 1, companion.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
        SpellFeedback.playCastSound(companion, def);
        return true;
    }

    public static boolean executeCompanionDelivery(ServerPlayer owner, PokemonEntity companion,
                                                    LivingEntity target, ResourceLocation spellId,
                                                    SpellDefinition def) {
        if (!companion.isAlive() || !target.isAlive()) return false;
        if (!"self".equals(def.targeting.type)
            && !SpellTargetingRules.canHarm(owner, companion, target)) return false;
        boolean started = switch (def.delivery.type) {
            case "dash" -> SpellMovementController.startDash(owner, companion, target, def);
            case "dash_combo" -> SpellRuntimeController.startDashCombo(
                    owner, companion, target, def);
            case "vortex" -> SpellRuntimeController.startVortex(owner, companion, def, target.position());
            case "delayed" -> SpellRuntimeController.startDelayed(owner, companion, target, def);
            case "delayed_area" -> SpellRuntimeController.startDelayedArea(
                    owner, companion, def, target.position());
            case "moving_zone" -> startCompanionMovingZone(owner, companion, target, def);
                case "contact_aura" -> SpellRuntimeController.startContactAura(
                    owner, companion, def);
                    case "orbit_release" -> SpellRuntimeController.startOrbit(owner, companion, def);
                        case "phase_movement" -> SpellMovementController.startPhaseMovement(
                            owner, companion, target, def);
                            case "pulse_ring" -> SpellRuntimeController.startPulseRing(owner, companion, def);
            case "protective_aura" -> SpellRuntimeController.startProtectiveAura(
                    owner, companion, def);
                case "zone" -> SpellRuntimeController.startZone(
                    owner, companion, def, companion.position());
            case "counter" -> SpellRuntimeController.startCounter(owner, companion, def);
            case "channel_beam" -> SpellCastController.startCompanionChannelBeam(
                    owner, companion, target, def);
                case "channel_cone" -> SpellCastController.startCompanionChannelCone(
                    owner, companion, target, def);
                case "wave" -> castWave(owner, companion, target, def);
                case "trap" -> castTrap(owner, companion, def, target.position());
                case "melee_combo" -> castMeleeCombo(owner, companion, target, def);
                case "teleport_strike" -> castTeleportStrike(owner, companion, target, def);
                case "ricochet_beam" -> SpellBeamDelivery.castRicochetBeam(
                    owner, companion, target, def);
                case "arc_strike" -> SpellBeamDelivery.castArcStrike(
                    owner, companion, target, def);
                case "grab" -> {
                    applyImpacts(owner, companion, target, def);
                    yield true;
                }
            case "beam" -> {
                SpellBeamDelivery.castRuntimeBeam(owner, companion, target, def);
                yield true;
            }
            case "meteor" -> {
                SpellProjectileDelivery.castMeteorAt(
                        owner, companion, spellId, def, target.position());
                yield true;
            }
            case "cloud" -> {
                SpellProjectileDelivery.castCompanionCloud(
                        owner, companion, target.position(), def);
                yield true;
            }
            case "projectile" -> {
                SpellProjectileDelivery.castCompanionProjectile(
                        owner, companion, target, def, spellId);
                yield true;
            }
            case "instant", "self" -> castCompanionStandard(owner, companion, target, def);
            default -> {
                LivingEntity impactTarget = "self".equals(def.targeting.type) ? companion : target;
                applyImpacts(owner, companion, impactTarget, def);
                yield true;
            }
        };
        if (started) SpellFeedback.playTravelSound(companion, def);
        return started;
    }

    // ── Standard (aim / area / self + instant) ───────────────────────────────

    private static void castStandard(ServerPlayer caster, SpellDefinition def) {
        List<LivingEntity> targets = SpellTargetResolver.resolveTargets(caster, def);

        if (!targets.isEmpty()) {
            if (CobblemonPsychicVfx.isPsychic(def)) {
                CobblemonPsychicVfx.sendHit(caster, targets.get(0));
            } else {
                SpellFeedback.applySchoolVisual(caster, def.school, targets.get(0).position());
            }
            if ("vine_tether".equals(def.visual.trail)) {
                SpellFeedback.drawParticleLine(caster, targets.get(0), ParticleTypes.COMPOSTER);
            }
        } else if ("self".equals(def.targeting.type)) {
            SpellFeedback.applySchoolVisualSelf(caster, def.school);
        }

        if ("self".equals(def.targeting.type)) {
            applyImpacts(caster, caster, def);
        } else {
            LivingEntity casterImpactContext = targets.isEmpty() ? caster : targets.get(0);
            applyImpacts(caster, caster, casterImpactContext, def, true, true);
            for (LivingEntity target : targets) {
                applyImpacts(caster, caster, target, def, true, false);
            }
        }
    }

    // ── Beam: raycast hitting ALL entities in a line ─────────────────────────

    public static void sendRuntimeBeamVfx(ServerPlayer owner, LivingEntity effectCaster,
                                          LivingEntity lockedTarget, SpellDefinition def) {
        SpellBeamDelivery.sendRuntimeBeamVfx(owner, effectCaster, lockedTarget, def);
    }

    public static void castRuntimeBeam(ServerPlayer owner, LivingEntity effectCaster,
                                       LivingEntity lockedTarget, SpellDefinition def) {
        SpellBeamDelivery.castRuntimeBeam(owner, effectCaster, lockedTarget, def);
    }

    public static void castRuntimeBeam(ServerPlayer owner, LivingEntity effectCaster,
                                       LivingEntity lockedTarget, SpellDefinition def,
                                       int pulseIndex, int pulseCount) {
        SpellBeamDelivery.castRuntimeBeam(
                owner, effectCaster, lockedTarget, def, pulseIndex, pulseCount);
    }

    public static void castRuntimeCone(ServerPlayer owner, LivingEntity effectCaster,
                                       SpellDefinition def) {
        SpellBeamDelivery.castRuntimeCone(owner, effectCaster, def);
    }

    public static void castRuntimeCone(ServerPlayer owner, LivingEntity effectCaster,
                                       LivingEntity lockedTarget, SpellDefinition def) {
        SpellBeamDelivery.castRuntimeCone(owner, effectCaster, lockedTarget, def);
    }

    public static void castRuntimeCone(ServerPlayer owner, LivingEntity effectCaster,
                                       LivingEntity lockedTarget, SpellDefinition def,
                                       int pulseIndex, int pulseCount) {
        SpellBeamDelivery.castRuntimeCone(
                owner, effectCaster, lockedTarget, def, pulseIndex, pulseCount);
    }

        private static boolean castWave(ServerPlayer owner, LivingEntity effectCaster,
                        SpellDefinition def) {
        Vec3 look = effectCaster.getLookAngle();
        Vec3 direction = new Vec3(look.x, 0.0, look.z);
        if (direction.lengthSqr() < 1.0E-6) return false;
        return SpellRuntimeController.startWave(owner, effectCaster, def,
            effectCaster.position().add(direction.normalize().scale(1.5)),
            direction.normalize());
        }

        private static boolean castWave(ServerPlayer owner, LivingEntity effectCaster,
                        LivingEntity target, SpellDefinition def) {
        Vec3 direction = target.position().subtract(effectCaster.position());
        direction = new Vec3(direction.x, 0.0, direction.z);
        if (direction.lengthSqr() < 1.0E-6) return false;
        return SpellRuntimeController.startWave(owner, effectCaster, def,
            effectCaster.position().add(direction.normalize().scale(1.5)),
            direction.normalize());
        }

        private static boolean castTrap(ServerPlayer owner, LivingEntity effectCaster,
                        SpellDefinition def, Vec3 center) {
        Vec3 offset = center.subtract(effectCaster.position());
        Vec3 direction = new Vec3(offset.x, 0.0, offset.z);
        if (direction.lengthSqr() < 1.0E-6) direction = new Vec3(0.0, 0.0, 1.0);
        return SpellRuntimeController.startTrap(owner, effectCaster, def, center,
            direction.normalize());
        }

    private static boolean castCompanionStandard(ServerPlayer owner, PokemonEntity companion,
                                                 LivingEntity target, SpellDefinition def) {
        if ("self".equals(def.targeting.type)) {
            applyImpacts(owner, companion, companion, def);
            return true;
        }
        if (!"area".equals(def.targeting.type)) {
            if (CobblemonPsychicVfx.isPsychic(def)) {
                CobblemonPsychicVfx.sendHit(companion, target);
            }
            applyImpacts(owner, companion, target, def);
            return true;
        }

        double range = Math.max(0.0, def.targeting.range);
        List<LivingEntity> targets = companion.level().getEntitiesOfClass(
                LivingEntity.class, companion.getBoundingBox().inflate(range),
                candidate -> companion.distanceTo(candidate) <= range
                        && SpellTargetingRules.canHarm(owner, companion, candidate));
        targets.sort((left, right) -> Double.compare(
                left.distanceToSqr(companion), right.distanceToSqr(companion)));
        if (def.targeting.max_targets > 0 && targets.size() > def.targeting.max_targets) {
            targets = targets.subList(0, def.targeting.max_targets);
        }

        LivingEntity casterImpactContext = targets.isEmpty() ? companion : targets.get(0);
        applyImpacts(owner, companion, casterImpactContext, def, true, true);
        for (LivingEntity areaTarget : targets) {
            applyImpacts(owner, companion, areaTarget, def, true, false);
        }
        return true;
    }

        private static boolean castMeleeCombo(ServerPlayer owner, LivingEntity effectCaster,
                          LivingEntity target, SpellDefinition def) {
        if (target == null || !SpellTargetingRules.canHarm(owner, effectCaster, target)) {
            return false;
        }
        return SpellRuntimeController.startMeleeCombo(owner, effectCaster, target, def);
        }

        private static boolean castTeleportStrike(ServerPlayer owner, LivingEntity effectCaster,
                                                  LivingEntity target, SpellDefinition def) {
        if (target == null || !SpellTargetingRules.canHarm(owner, effectCaster, target)) {
            return false;
        }
        if (def.delivery.delay_ticks > 0) {
            return SpellRuntimeController.startDelayedTeleportStrike(
                    owner, effectCaster, target, def);
        }
        return castRuntimeTeleportStrike(owner, effectCaster, target, def);
        }

    public static boolean castRuntimeTeleportStrike(ServerPlayer owner,
                                                     LivingEntity effectCaster,
                                                     LivingEntity target,
                                                     SpellDefinition def) {
        if (!(effectCaster.level() instanceof ServerLevel level) || target == null
            || !SpellTargetingRules.canHarm(owner, effectCaster, target)) return false;
        Vec3 oldPosition = effectCaster.position();
        Vec3 destination = findTeleportDestination(effectCaster, target);
        if (destination == null) return false;

        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
            oldPosition.x, oldPosition.y + 1.0, oldPosition.z,
            24, 0.35, 0.7, 0.35, 0.05);
        effectCaster.teleportTo(destination.x, destination.y, destination.z);
        effectCaster.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
            target.getBoundingBox().getCenter());
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
            destination.x, destination.y + 1.0, destination.z,
            32, 0.4, 0.8, 0.4, 0.08);
        SpellVfxDispatcher.send(level, "impact", def.visual.impact, def.school,
            target.getBoundingBox().getCenter(), target.getBoundingBox().getCenter(),
            Math.max(1.0, def.targeting.width), 10, effectCaster, false);
        applyImpacts(owner, effectCaster, target, def);
        return true;
    }

        private static Vec3 findTeleportDestination(LivingEntity effectCaster, LivingEntity target) {
        Vec3 targetFacing = new Vec3(target.getLookAngle().x, 0.0, target.getLookAngle().z);
        if (targetFacing.lengthSqr() < 1.0E-6) {
            targetFacing = target.position().subtract(effectCaster.position());
        }
        targetFacing = new Vec3(targetFacing.x, 0.0, targetFacing.z).normalize();
        Vec3 right = new Vec3(-targetFacing.z, 0.0, targetFacing.x);
        Vec3[] candidates = {
            target.position().subtract(targetFacing.scale(1.5)),
            target.position().subtract(targetFacing.scale(1.2)).add(right.scale(1.0)),
            target.position().subtract(targetFacing.scale(1.2)).subtract(right.scale(1.0)),
            target.position().add(targetFacing.scale(1.5))
        };
        for (Vec3 candidate : candidates) {
            AABB movedBox = effectCaster.getBoundingBox().move(candidate.subtract(effectCaster.position()));
            if (effectCaster.level().noCollision(effectCaster, movedBox)) return candidate;
        }
        return null;
        }

    private static boolean castVortex(ServerPlayer caster, SpellDefinition def) {
        Vec3 center = SpellTargetResolver.resolveAimPosition(caster, def.targeting.range);
        return SpellRuntimeController.startVortex(caster, def, center);
    }

    private static boolean castDelayed(ServerPlayer caster, SpellDefinition def) {
        LivingEntity target = SpellTargetResolver.rayCast(caster, def.targeting.range);
        return target != null && SpellRuntimeController.startDelayed(caster, target, def);
    }

    private static boolean castDelayedArea(ServerPlayer caster, SpellDefinition def) {
        Vec3 center = SpellTargetResolver.resolveAimPosition(caster, def.targeting.range);
        return SpellRuntimeController.startDelayedArea(caster, caster, def, center);
    }

    private static boolean castMovingZone(ServerPlayer caster, SpellDefinition def) {
        Vec3 direction = caster.getLookAngle();
        Vec3 center = def.targeting.range <= 0.0
                ? caster.position()
                : caster.position().add(direction.normalize().scale(2.0));
        return SpellRuntimeController.startMovingZone(caster, caster, def, center, direction);
    }

    private static boolean startCompanionMovingZone(ServerPlayer owner, PokemonEntity companion,
                                                    LivingEntity target, SpellDefinition def) {
        Vec3 direction = target.position().subtract(companion.position());
        Vec3 center = def.targeting.range <= 0.0
            ? companion.position()
            : companion.position().add(direction.normalize().scale(2.0));
        return SpellRuntimeController.startMovingZone(owner, companion, def, center, direction);
    }

    public static void playLoopSound(LivingEntity source, SpellDefinition def) {
        SpellFeedback.playLoopSound(source, def);
    }

    private static class ChargeState {
        private int available;
        private int maximum;
        private long nextChargeAt;

        private ChargeState(int maximum) {
            this.available = maximum;
            this.maximum = maximum;
        }

        private void refresh(long now, int configuredMaximum, int recoveryTicks) {
            if (maximum != configuredMaximum) {
                maximum = configuredMaximum;
                available = Math.min(available, maximum);
            }
            while (available < maximum && now >= nextChargeAt) {
                available++;
                nextChargeAt += recoveryTicks;
            }
        }

        private void consume(long now, int configuredMaximum, int recoveryTicks) {
            if (available == configuredMaximum) nextChargeAt = now + recoveryTicks;
            available--;
        }
    }

    private record ActiveCharge(ResourceLocation spellId, long startedAt) {}

    // Public impact methods remain as a stable facade for controllers and projectiles.
    public static void applyImpacts(ServerPlayer caster, LivingEntity target,
                                    SpellDefinition definition) {
        SpellImpactApplier.applyImpacts(caster, target, definition);
    }

    public static void applyImpacts(ServerPlayer caster, LivingEntity target,
                                    SpellDefinition definition, boolean finalProjectile) {
        SpellImpactApplier.applyImpacts(caster, target, definition, finalProjectile);
    }

    public static void applyProjectileSplash(ServerPlayer owner, LivingEntity effectCaster,
                                             LivingEntity directTarget,
                                             SpellDefinition definition) {
        SpellImpactApplier.applyProjectileSplash(owner, effectCaster, directTarget, definition);
    }

    public static void applyProjectileSplash(ServerPlayer owner, LivingEntity effectCaster,
                                             LivingEntity directTarget,
                                             SpellDefinition definition,
                                             double damageScale, double radiusScale) {
        SpellImpactApplier.applyProjectileSplash(
                owner, effectCaster, directTarget, definition, damageScale, radiusScale);
    }

    public static void applyProjectileSplashAt(ServerPlayer owner, LivingEntity effectCaster,
                                               Vec3 position,
                                               SpellDefinition definition,
                                               double damageScale,
                                               double radiusScale) {
        SpellImpactApplier.applyProjectileSplashAt(
                owner, effectCaster, position, definition, damageScale, radiusScale);
    }

    public static void applyImpacts(ServerPlayer owner, LivingEntity effectCaster,
                                    LivingEntity target, SpellDefinition definition) {
        SpellImpactApplier.applyImpacts(owner, effectCaster, target, definition);
    }

    public static void applyImpacts(ServerPlayer owner, LivingEntity effectCaster,
                                    LivingEntity target, SpellDefinition definition,
                                    boolean finalProjectile) {
        SpellImpactApplier.applyImpacts(
                owner, effectCaster, target, definition, finalProjectile);
    }

        public static void applyCasterImpacts(ServerPlayer owner, LivingEntity effectCaster,
                          SpellDefinition definition) {
        SpellImpactApplier.applyImpacts(
            owner, effectCaster, effectCaster, definition, true, true, false);
        }

        public static void applyTargetImpacts(ServerPlayer owner, LivingEntity effectCaster,
                          LivingEntity target, SpellDefinition definition) {
        SpellImpactApplier.applyImpacts(
            owner, effectCaster, target, definition, true, false, true);
        }

    public static void applyTargetImpacts(ServerPlayer owner, LivingEntity effectCaster,
                                          LivingEntity target, SpellDefinition definition,
                                          double damageScale) {
        applyTargetImpacts(owner, effectCaster, target, definition, true, damageScale);
        }

        public static void applyTargetImpacts(ServerPlayer owner, LivingEntity effectCaster,
                          LivingEntity target, SpellDefinition definition,
                          boolean finalProjectile, double damageScale) {
        SpellImpactApplier.applyImpacts(owner, effectCaster, target, definition,
            finalProjectile, false, true, 1.0, damageScale);
    }

    private static void applyImpacts(ServerPlayer owner, LivingEntity effectCaster,
                                     LivingEntity target, SpellDefinition definition,
                                     boolean finalProjectile, Boolean casterOnly) {
        SpellImpactApplier.applyImpacts(
                owner, effectCaster, target, definition, finalProjectile, casterOnly);
    }

    private static void applyImpacts(ServerPlayer owner, LivingEntity effectCaster,
                                     LivingEntity target, SpellDefinition definition,
                                     boolean finalProjectile, Boolean casterOnly,
                                     boolean playFeedback) {
        SpellImpactApplier.applyImpacts(owner, effectCaster, target, definition,
                finalProjectile, casterOnly, playFeedback);
    }
}
