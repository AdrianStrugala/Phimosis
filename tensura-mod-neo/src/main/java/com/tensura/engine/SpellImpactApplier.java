package com.tensura.engine;

import com.tensura.event.SpellRuntimeController;
import com.tensura.network.SpellVfxDispatcher;
import com.tensura.registry.TensuraMobEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SpellImpactApplier {

    private static final Map<UUID, Map<String, Long>> IMPACT_SOUND_TIMES = new HashMap<>();
    private static final ResourceLocation ARMOR_PENETRATION_ID =
            ResourceLocation.fromNamespaceAndPath("tensura", "spell_armor_penetration");

    private SpellImpactApplier() {}

    static void clearPlayerState(UUID playerId) {
        IMPACT_SOUND_TIMES.remove(playerId);
    }

    static void clearAllState() {
        IMPACT_SOUND_TIMES.clear();
    }

    public static void applyImpacts(ServerPlayer caster, LivingEntity target,
                                    SpellDefinition definition) {
        applyImpacts(caster, caster, target, definition, true);
    }

    public static void applyImpacts(ServerPlayer caster, LivingEntity target,
                                    SpellDefinition definition, boolean finalProjectile) {
        applyImpacts(caster, caster, target, definition, finalProjectile);
    }

    public static void applyProjectileSplash(ServerPlayer owner, LivingEntity effectCaster,
                                             LivingEntity directTarget,
                                             SpellDefinition definition) {
        double radius = definition.targeting.radius;
        if (radius <= 0.0) {
            applyImpacts(owner, effectCaster, directTarget, definition);
            return;
        }

        int maxTargets = definition.targeting.max_targets > 0
                ? definition.targeting.max_targets : Integer.MAX_VALUE;
        List<LivingEntity> targets = directTarget.level().getEntitiesOfClass(
                LivingEntity.class, directTarget.getBoundingBox().inflate(radius),
                entity -> entity != directTarget
                        && SpellTargetingRules.canHarm(owner, effectCaster, entity));
        targets.sort((left, right) -> Double.compare(
                left.distanceToSqr(directTarget), right.distanceToSqr(directTarget)));

        applyImpacts(owner, effectCaster, directTarget, definition, true, true);
        applyImpacts(owner, effectCaster, directTarget, definition, true, false);
        int affected = 1;
        for (LivingEntity target : targets) {
            if (affected++ >= maxTargets) break;
            applyImpacts(owner, effectCaster, target, definition, true, false);
        }
    }

    public static void applyImpacts(ServerPlayer owner, LivingEntity effectCaster,
                                    LivingEntity target, SpellDefinition definition) {
        applyImpacts(owner, effectCaster, target, definition, true);
    }

    public static void applyImpacts(ServerPlayer owner, LivingEntity effectCaster,
                                    LivingEntity target, SpellDefinition definition,
                                    boolean finalProjectile) {
        applyImpacts(owner, effectCaster, target, definition, finalProjectile, null);
    }

    static void applyImpacts(ServerPlayer owner, LivingEntity effectCaster,
                             LivingEntity target, SpellDefinition definition,
                             boolean finalProjectile, Boolean casterOnly) {
        applyImpacts(owner, effectCaster, target, definition, finalProjectile, casterOnly, true);
    }

    static void applyImpacts(ServerPlayer owner, LivingEntity effectCaster,
                             LivingEntity target, SpellDefinition definition,
                             boolean finalProjectile, Boolean casterOnly,
                             boolean playFeedback) {
        boolean canHarm = SpellTargetingRules.canHarm(owner, effectCaster, target);
        float damageDealt = 0.0F;
        for (SpellDefinition.Impact impact : definition.impact) {
            if (impact.final_hit_only && !finalProjectile) continue;
            boolean casterRecipient = "caster".equals(impact.recipient);
            if (casterOnly != null && casterRecipient != casterOnly) continue;
            LivingEntity recipient = casterRecipient ? effectCaster : target;
            if (impact.max_distance > 0.0
                    && recipient != effectCaster
                    && recipient.distanceTo(effectCaster) > impact.max_distance) continue;
            switch (impact.type) {
                case "damage" -> {
                    if (!canHarm) continue;
                    double baseDamage = definition.power >= 0.0
                            ? definition.power
                            : owner.getAttackStrengthScale(0) * 6.0;
                    double conditionalMultiplier = hasAnyEffect(target, impact.effects)
                            ? impact.conditional_multiplier : 1.0;
                    float damage = (float) (applyExposedModifier(baseDamage, target, definition)
                            * impact.damage_multiplier * conditionalMultiplier);
                    float healthBefore = target.getHealth();
                    hurtWithSpellDamage(owner, effectCaster, target, definition,
                            Math.max(1, damage), impact.armor_penetration);
                    damageDealt += Math.max(0.0F, healthBefore - target.getHealth());
                }
                case "speed_scaled_damage" -> {
                    if (!canHarm) continue;
                    Vec3 casterMovement = effectCaster.getDeltaMovement();
                    Vec3 targetMovement = target.getDeltaMovement();
                    double casterSpeed = Math.sqrt(casterMovement.x * casterMovement.x
                            + casterMovement.z * casterMovement.z);
                    double targetSpeed = Math.sqrt(targetMovement.x * targetMovement.x
                            + targetMovement.z * targetMovement.z);
                    double advantage = Math.max(0.0,
                            Math.min(1.0, (casterSpeed - targetSpeed) / 0.35));
                    double maximumPower = Math.max(definition.power, impact.amount);
                    double scaledPower = definition.power
                            + (maximumPower - definition.power) * advantage;
                    float damage = (float) (applyExposedModifier(scaledPower, target, definition)
                            * impact.damage_multiplier);
                    float healthBefore = target.getHealth();
                    hurtWithSpellDamage(owner, effectCaster, target, definition,
                            Math.max(1, damage), impact.armor_penetration);
                    damageDealt += Math.max(0.0F, healthBefore - target.getHealth());
                }
                case "status_effect" -> {
                    if (Math.random() <= impact.chance && !impact.effect.isEmpty()) {
                        BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(impact.effect))
                                .filter(holder -> recipient == effectCaster || canHarm
                                        || holder.value().getCategory()
                                        != net.minecraft.world.effect.MobEffectCategory.HARMFUL)
                                .ifPresent(holder -> recipient.addEffect(new MobEffectInstance(holder,
                                        impact.duration, impact.amplifier, impact.ambient,
                                        impact.show_particles, impact.show_icon)));
                    }
                }
                case "fire" -> {
                    if (canHarm && target.getRandom().nextDouble() <= impact.chance) {
                        target.igniteForSeconds(impact.seconds);
                    }
                }
                case "knockback" -> {
                    if (!canHarm) continue;
                    Vec3 direction = target.position().subtract(effectCaster.position())
                            .normalize().scale(impact.strength);
                    target.setDeltaMovement(target.getDeltaMovement()
                            .add(direction.x, 0.4, direction.z));
                    target.hurtMarked = true;
                }
                case "pull" -> {
                    if (!canHarm) continue;
                    Vec3 delta = effectCaster.position().subtract(target.position());
                    if (delta.lengthSqr() > 1.0E-6) {
                        Vec3 pull = delta.normalize().scale(impact.strength);
                        target.setDeltaMovement(target.getDeltaMovement().add(pull.x, 0.15, pull.z));
                        target.hurtMarked = true;
                    }
                }
                case "stagger" -> {
                    if (!canHarm) continue;
                    target.setDeltaMovement(Vec3.ZERO);
                    target.hurtMarked = true;
                    SpellRuntimeController.interruptPendingCast(target);
                    BuiltInRegistries.MOB_EFFECT.getHolder(
                                    ResourceLocation.withDefaultNamespace("slowness"))
                            .ifPresent(holder -> target.addEffect(new MobEffectInstance(holder,
                                    Math.max(1, impact.duration), Math.max(1, impact.amplifier),
                                    false, impact.show_particles, impact.show_icon)));
                }
                case "rear_stagger" -> {
                    if (!canHarm) continue;
                    Vec3 facing = target.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
                    Vec3 toCaster = effectCaster.position().subtract(target.position())
                            .multiply(1.0, 0.0, 1.0).normalize();
                    if (facing.dot(toCaster) < -0.35) {
                        target.setDeltaMovement(Vec3.ZERO);
                        target.hurtMarked = true;
                        SpellRuntimeController.interruptPendingCast(target);
                    }
                }
                case "throw" -> {
                    if (!canHarm) continue;
                    if (target.getMaxHealth() >= 100.0F) {
                        target.setDeltaMovement(Vec3.ZERO);
                        SpellRuntimeController.interruptPendingCast(target);
                    } else {
                        Vec3 direction = effectCaster.getLookAngle().normalize();
                        target.setDeltaMovement(direction.x * impact.strength,
                                Math.max(0.35, impact.amount), direction.z * impact.strength);
                        target.hurtMarked = true;
                    }
                }
                case "ground" -> {
                    if (!canHarm) continue;
                    target.addEffect(new MobEffectInstance(TensuraMobEffects.GROUNDED,
                            Math.max(1, impact.duration), 0, false,
                            impact.show_particles, impact.show_icon));
                    Vec3 movement = target.getDeltaMovement();
                    target.setDeltaMovement(movement.x, Math.min(-0.8, movement.y), movement.z);
                    target.hurtMarked = true;
                }
                case "chain_damage" -> {
                    if (!canHarm || !(target.level() instanceof ServerLevel level)) continue;
                    if (!impact.effect.isEmpty() && !hasEffect(target, impact.effect)) continue;
                    double radius = impact.amount > 0.0 ? impact.amount : 6.0;
                    LivingEntity chainedTarget = level.getEntitiesOfClass(LivingEntity.class,
                                    target.getBoundingBox().inflate(radius),
                                    candidate -> candidate != target
                                            && SpellTargetingRules.canHarm(
                                                    owner, effectCaster, candidate)
                                            && hasAnyEffect(candidate, impact.effects))
                            .stream()
                            .min((left, right) -> Double.compare(
                                    left.distanceToSqr(target), right.distanceToSqr(target)))
                            .orElse(null);
                    if (chainedTarget == null) continue;
                    float chainedDamage = (float) Math.max(1.0,
                            definition.power * impact.damage_multiplier);
                    hurtWithSpellDamage(owner, effectCaster, chainedTarget, definition,
                            chainedDamage, impact.armor_penetration);
                    SpellVfxDispatcher.send(level, "beam", definition.visual.trail,
                            definition.school, target.getBoundingBox().getCenter(),
                            chainedTarget.getBoundingBox().getCenter(),
                            0.35, 4, effectCaster, false);
                }
                case "heal" -> recipient.heal((float) impact.amount);
                case "heal_damage_fraction" -> recipient.heal(
                        damageDealt * (float) Math.max(0.0, impact.amount));
                case "heal_fraction" -> recipient.heal((float) (recipient.getMaxHealth()
                        * Math.max(0.0, Math.min(1.0, impact.amount))));
                case "full_heal" -> recipient.setHealth(recipient.getMaxHealth());
                case "recoil" -> {
                    if (canHarm && impact.amount > 0.0) {
                        effectCaster.hurt(effectCaster.damageSources().generic(),
                                (float) impact.amount);
                    }
                }
                case "cleanse" -> {
                    List<Holder<MobEffect>> harmfulEffects = recipient.getActiveEffects().stream()
                            .filter(instance -> instance.getEffect().value().getCategory()
                                    .equals(net.minecraft.world.effect.MobEffectCategory.HARMFUL))
                            .map(MobEffectInstance::getEffect)
                            .toList();
                    harmfulEffects.forEach(recipient::removeEffect);
                }
                case "cleanse_one" -> {
                    for (String effectId : impact.effects) {
                        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT
                                .getHolder(ResourceLocation.parse(effectId)).orElse(null);
                        if (effect != null && recipient.removeEffect(effect)) break;
                    }
                }
                case "interrupt_cast" -> {
                    if (canHarm) SpellRuntimeController.interruptPendingCast(recipient);
                }
                case "wet" -> {
                    if (canHarm) {
                        recipient.addEffect(new MobEffectInstance(TensuraMobEffects.WET,
                                impact.duration, 0, false,
                                impact.show_particles, impact.show_icon));
                    }
                }
                case "freeze_if_wet" -> {
                    if (!canHarm) continue;
                    if (recipient.hasEffect(TensuraMobEffects.WET)) {
                        recipient.removeEffect(TensuraMobEffects.WET);
                        recipient.addEffect(new MobEffectInstance(TensuraMobEffects.FROZEN,
                                impact.duration, 0, false,
                                impact.show_particles, impact.show_icon));
                    } else {
                        BuiltInRegistries.MOB_EFFECT.getHolder(
                                        ResourceLocation.withDefaultNamespace("slowness"))
                                .ifPresent(holder -> recipient.addEffect(new MobEffectInstance(holder,
                                        impact.duration, Math.max(1, impact.amplifier))));
                    }
                }
                case "paralyze_if_wet" -> {
                    if (!canHarm) continue;
                    double chance = recipient.hasEffect(TensuraMobEffects.WET)
                            ? 1.0 : impact.chance;
                    if (recipient.getRandom().nextDouble() <= chance) {
                        recipient.addEffect(new MobEffectInstance(TensuraMobEffects.PARALYZED,
                                impact.duration, impact.amplifier, false,
                                impact.show_particles, impact.show_icon));
                    }
                }
                case "toxic" -> {
                    if (canHarm) {
                        recipient.addEffect(new MobEffectInstance(TensuraMobEffects.TOXIC,
                                impact.duration, impact.amplifier, false,
                                impact.show_particles, impact.show_icon));
                    }
                }
                case "expose" -> {
                    if ((canHarm || recipient == effectCaster)
                            && recipient.getRandom().nextDouble() <= impact.chance) {
                        recipient.addEffect(new MobEffectInstance(TensuraMobEffects.EXPOSED,
                                impact.duration, impact.amplifier, false,
                                impact.show_particles, impact.show_icon));
                    }
                }
                case "tri_status" -> {
                    if (!canHarm || !finalProjectile) continue;
                    switch (target.getRandom().nextInt(3)) {
                        case 0 -> target.igniteForSeconds(Math.max(1, impact.seconds));
                        case 1 -> BuiltInRegistries.MOB_EFFECT.getHolder(
                                        ResourceLocation.withDefaultNamespace("slowness"))
                                .ifPresent(holder -> target.addEffect(new MobEffectInstance(holder,
                                        impact.duration, Math.max(1, impact.amplifier))));
                        default -> target.addEffect(new MobEffectInstance(
                                TensuraMobEffects.PARALYZED, impact.duration, 0, false,
                                impact.show_particles, impact.show_icon));
                    }
                }
                case "guard" -> SpellRuntimeController.addGuard(
                        recipient, impact.amount, impact.duration);
            }
        }
        if (playFeedback && finalProjectile && target.level() instanceof ServerLevel level) {
            Vec3 impactPosition = target.getBoundingBox().getCenter();
            double radius = definition.targeting.radius > 0.0
                    ? definition.targeting.radius : 1.0;
            SpellVfxDispatcher.send(level, "impact", definition.visual.impact,
                    definition.school, impactPosition, impactPosition, radius, 0,
                    effectCaster, !SpellTargetingRules.canHarm(owner, effectCaster, target));
            if (!"moving_zone".equals(definition.delivery.type)
                    && !"protective_aura".equals(definition.delivery.type)) {
                SpellVfxDispatcher.send(level, "aftermath", definition.visual.aftermath,
                        definition.school, impactPosition, impactPosition, radius,
                        definition.delivery.duration_ticks, effectCaster,
                        !SpellTargetingRules.canHarm(owner, effectCaster, target));
            }
        }
        if (playFeedback) playImpactSound(owner, target, definition);
    }

    private static void hurtWithSpellDamage(ServerPlayer owner, LivingEntity effectCaster,
                                            LivingEntity target, SpellDefinition definition,
                                            float damage, double armorPenetration) {
        float adjustedDamage = damage;
        if ("special".equals(definition.category)
                && effectCaster.hasEffect(TensuraMobEffects.SPECIAL_WEAKENED)) {
            adjustedDamage *= 0.8F;
        }
        AttributeInstance armor = target.getAttribute(Attributes.ARMOR);
        if (armor == null || armorPenetration <= 0.0) {
            target.hurt(owner.damageSources().playerAttack(owner), adjustedDamage);
            return;
        }
        armor.removeModifier(ARMOR_PENETRATION_ID);
        armor.addTransientModifier(new AttributeModifier(ARMOR_PENETRATION_ID,
                -armorPenetration, AttributeModifier.Operation.ADD_VALUE));
        try {
            target.hurt(owner.damageSources().playerAttack(owner), adjustedDamage);
        } finally {
            armor.removeModifier(ARMOR_PENETRATION_ID);
        }
    }

    private static boolean hasAnyEffect(LivingEntity target, List<String> effectIds) {
        return effectIds.isEmpty() || effectIds.stream().anyMatch(id -> hasEffect(target, id));
    }

    private static boolean hasEffect(LivingEntity target, String effectId) {
        return BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(effectId))
                .map(target::hasEffect).orElse(false);
    }

    private static double applyExposedModifier(double damage, LivingEntity target,
                                               SpellDefinition definition) {
        MobEffectInstance exposed = target.getEffect(TensuraMobEffects.EXPOSED);
        if (exposed == null || !"physical".equals(definition.category)) return damage;
        return damage * (1.0 + 0.15 * (exposed.getAmplifier() + 1));
    }

    private static void playImpactSound(ServerPlayer caster, LivingEntity target,
                                        SpellDefinition definition) {
        if (definition.sound.impact == null || definition.sound.impact.isBlank()) return;
        if (definition.sound.loop != null && !definition.sound.loop.isBlank()) {
            long now = target.level().getGameTime();
            Map<String, Long> playerSounds = IMPACT_SOUND_TIMES.computeIfAbsent(
                    caster.getUUID(), ignored -> new HashMap<>());
            long lastPlayed = playerSounds.getOrDefault(
                    definition.sound.impact, Long.MIN_VALUE / 2);
            if (now - lastPlayed < 10) return;
            playerSounds.put(definition.sound.impact, now);
        }
        BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.parse(definition.sound.impact))
                .ifPresent(sound -> target.level().playSound(null,
                        target.getX(), target.getY(), target.getZ(), sound,
                        SoundSource.PLAYERS, 1.0f, 1.0f));
    }
}