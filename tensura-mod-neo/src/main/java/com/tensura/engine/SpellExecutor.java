package com.tensura.engine;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.entity.SpellProjectile;
import com.tensura.event.SpellMovementController;
import com.tensura.event.SpellRuntimeController;
import com.tensura.network.CooldownSyncPacket;
import com.tensura.registry.TensuraMobEffects;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SpellExecutor {

    // cooldownMap: playerUUID -> (spellId -> gameTime when ready)
    private static final Map<UUID, Map<ResourceLocation, Long>> cooldowns = new HashMap<>();
    private static final Map<UUID, Map<ResourceLocation, ChargeState>> charges = new HashMap<>();

    public static void clearPlayerState(UUID playerId) {
        cooldowns.remove(playerId);
        charges.remove(playerId);
    }

    public static void clearAllState() {
        cooldowns.clear();
        charges.clear();
    }

    public static boolean cast(ServerPlayer caster, ResourceLocation spellId) {
        if (caster.hasEffect(TensuraMobEffects.ASLEEP)
            || caster.hasEffect(TensuraMobEffects.FROZEN)
            || caster.hasEffect(TensuraMobEffects.EXHAUSTED)) {
            caster.sendSystemMessage(Component.literal("§7[You cannot cast right now]"));
            return false;
        }
        if (SpellRuntimeController.isCasting(caster)) {
            caster.sendSystemMessage(Component.literal("§7[Already casting]"));
            return false;
        }

        SpellDefinition def = SpellRegistry.get(spellId).orElse(null);
        if (def == null) {
            caster.sendSystemMessage(Component.literal("Unknown spell: " + spellId));
            return false;
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
        boolean started = def.cast_time_ticks > 0
                ? SpellRuntimeController.startCast(caster, spellId, def)
                : executeDelivery(caster, spellId, def);
        if (!started) return false;

        caster.swing(InteractionHand.MAIN_HAND, true);
        playCastSound(caster, def);
        if (chargeState != null) {
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
        return switch (def.delivery.type) {
            case "dash" -> SpellMovementController.startDash(caster, def);
            case "vortex" -> castVortex(caster, def);
            case "delayed" -> castDelayed(caster, def);
            case "counter" -> SpellRuntimeController.startCounter(caster, def);
            case "beam" -> { castBeam(caster, def); yield true; }
            case "meteor" -> { castMeteor(caster, spellId, def); yield true; }
            case "cloud" -> { castCloud(caster, def); yield true; }
            case "projectile" -> { castProjectile(caster, def, spellId); yield true; }
            case "explosion" -> { castExplosion(caster, def); yield true; }
            case "instant", "self" -> { castStandard(caster, def); yield true; }
            default -> { castStandard(caster, def); yield true; }
        };
    }

    /**
     * Called by CompanionSpellGoal. Uses companion's position as origin,
     * directly hits the known target — no raycast needed.
     * Cooldown keyed on owner UUID with a companion-specific suffix to not share with player's own spells.
     */
    public static void castAsCompanion(ServerPlayer owner, PokemonEntity companion,
                                        ResourceLocation spellId, SpellDefinition def, LivingEntity target) {
        if (!(companion.level() instanceof ServerLevel serverLevel)) return;
        if (companion.hasEffect(TensuraMobEffects.ASLEEP)
                || companion.hasEffect(TensuraMobEffects.FROZEN)
                || companion.hasEffect(TensuraMobEffects.EXHAUSTED)) return;

        ResourceLocation companionSpellKey = ResourceLocation.fromNamespaceAndPath(
                spellId.getNamespace(), "companion_" + spellId.getPath());
        long now = companion.level().getGameTime();
        Map<ResourceLocation, Long> ownerCooldowns = cooldowns.computeIfAbsent(owner.getUUID(), k -> new HashMap<>());
        if (now < ownerCooldowns.getOrDefault(companionSpellKey, 0L)) return;

        boolean started = def.cast_time_ticks > 0
                ? SpellRuntimeController.startCompanionCast(owner, companion, target, spellId, def)
                : executeCompanionDelivery(owner, companion, target, spellId, def);
        if (!started) return;

        ownerCooldowns.put(companionSpellKey, now + def.cooldown_ticks);
        serverLevel.sendParticles(schoolParticle(def.school),
                companion.getX(), companion.getY() + 1, companion.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
        playCastSound(companion, def);
    }

    public static boolean executeCompanionDelivery(ServerPlayer owner, PokemonEntity companion,
                                                    LivingEntity target, ResourceLocation spellId,
                                                    SpellDefinition def) {
        if (!companion.isAlive() || !target.isAlive()) return false;
        return switch (def.delivery.type) {
            case "dash" -> SpellMovementController.startDash(owner, companion, target, def);
            case "vortex" -> SpellRuntimeController.startVortex(owner, companion, def, target.position());
            case "delayed" -> SpellRuntimeController.startDelayed(owner, companion, target, def);
            case "counter" -> SpellRuntimeController.startCounter(owner, companion, def);
            case "beam" -> { castCompanionBeam(owner, companion, target, def); yield true; }
            case "meteor" -> { castMeteorAt(owner, companion, spellId, def, target.position()); yield true; }
            case "cloud" -> { castCompanionCloud(owner, companion, target.position(), def); yield true; }
            case "projectile" -> { castCompanionProjectile(owner, companion, target, def, spellId); yield true; }
            case "explosion" -> { castCompanionExplosion(owner, companion, def); yield true; }
            case "instant", "self" -> {
                LivingEntity impactTarget = "self".equals(def.targeting.type) ? companion : target;
                applyImpacts(owner, companion, impactTarget, def);
                yield true;
            }
            default -> {
                LivingEntity impactTarget = "self".equals(def.targeting.type) ? companion : target;
                applyImpacts(owner, companion, impactTarget, def);
                yield true;
            }
        };
    }

    // ── Projectile: flying entity like ghast fireball ────────────────────────

    private static void castProjectile(ServerPlayer caster, SpellDefinition def, ResourceLocation spellId) {
        if (!(caster.level() instanceof ServerLevel serverLevel)) return;
        applySchoolVisualSelf(caster, def.school);
        int projectileCount = Math.max(1, def.delivery.projectile_count);
        UUID projectileGroup = projectileCount > 1
            ? SpellRuntimeController.createProjectileGroup(serverLevel, 100) : null;
        for (int index = 0; index < projectileCount; index++) {
            double centeredIndex = index - (projectileCount - 1) / 2.0;
            float angle = (float) Math.toRadians(centeredIndex * def.delivery.spread_degrees);
            Vec3 direction = caster.getLookAngle().yRot(angle);
            SpellProjectile projectile = SpellProjectile.create(
                    caster, caster, spellId, def, direction, index, projectileCount,
                    projectileGroup);
            serverLevel.addFreshEntity(projectile);
        }
    }

    private static void castCompanionProjectile(ServerPlayer owner, PokemonEntity companion,
                                                LivingEntity target, SpellDefinition def,
                                                ResourceLocation spellId) {
        if (!(companion.level() instanceof ServerLevel serverLevel)) return;
        Vec3 baseDirection = target.getBoundingBox().getCenter().subtract(companion.getEyePosition()).normalize();
        int projectileCount = Math.max(1, def.delivery.projectile_count);
        UUID projectileGroup = projectileCount > 1
            ? SpellRuntimeController.createProjectileGroup(serverLevel, 100) : null;
        for (int index = 0; index < projectileCount; index++) {
            double centeredIndex = index - (projectileCount - 1) / 2.0;
            float angle = (float) Math.toRadians(centeredIndex * def.delivery.spread_degrees);
            SpellProjectile projectile = SpellProjectile.create(owner, companion, spellId, def,
                    baseDirection.yRot(angle), index, projectileCount, projectileGroup);
            serverLevel.addFreshEntity(projectile);
        }
    }

    // ── Explosion: massive AoE burst centered on caster ──────────────────────

    private static void castExplosion(ServerPlayer caster, SpellDefinition def) {
        if (!(caster.level() instanceof ServerLevel serverLevel)) return;

        Vec3 pos = caster.position();
        double radius = def.targeting.range;

        // Visual: multiple explosion layers
        serverLevel.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 1, pos.z, 8, radius * 0.4, 0.5, radius * 0.4, 0.1);
        serverLevel.sendParticles(ParticleTypes.FLAME,     pos.x, pos.y + 1, pos.z, 80, radius * 0.5, 1.0, radius * 0.5, 0.15);
        serverLevel.sendParticles(ParticleTypes.LAVA,      pos.x, pos.y + 1, pos.z, 30, radius * 0.3, 0.5, radius * 0.3, 0.1);

        // Lightning strikes around caster for extra drama
        for (int i = 0; i < 4; i++) {
            double ox = (Math.random() - 0.5) * radius;
            double oz = (Math.random() - 0.5) * radius;
            LightningBolt bolt = new LightningBolt(net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, serverLevel);
            bolt.moveTo(pos.x + ox, pos.y, pos.z + oz);
            bolt.setVisualOnly(true);
            serverLevel.addFreshEntity(bolt);
        }

        // Hit all entities in radius
        AABB box = caster.getBoundingBox().inflate(radius);
        List<LivingEntity> targets = caster.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != caster && caster.distanceTo(e) <= radius);
        for (LivingEntity target : targets) {
            applyImpacts(caster, target, def);
        }
    }

    private static void castCompanionExplosion(ServerPlayer owner, PokemonEntity companion,
                                               SpellDefinition def) {
        if (!(companion.level() instanceof ServerLevel serverLevel)) return;
        Vec3 position = companion.position();
        double radius = def.targeting.range;
        serverLevel.sendParticles(ParticleTypes.EXPLOSION, position.x, position.y + 1, position.z,
                8, radius * 0.4, 0.5, radius * 0.4, 0.1);
        AABB area = companion.getBoundingBox().inflate(radius);
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != companion && entity != owner && entity.isAlive()
                        && companion.distanceTo(entity) <= radius);
        for (LivingEntity target : targets) {
            applyImpacts(owner, companion, target, def);
        }
    }

    // ── Standard (aim / area / self + instant) ───────────────────────────────

    private static void castStandard(ServerPlayer caster, SpellDefinition def) {
        List<LivingEntity> targets = resolveTargets(caster, def);

        if (!targets.isEmpty()) {
            applySchoolVisual(caster, def.school, targets.get(0).position());
            if ("vine_tether".equals(def.visual.trail)) {
                drawParticleLine(caster, targets.get(0), ParticleTypes.COMPOSTER);
            }
        } else if ("self".equals(def.targeting.type)) {
            applySchoolVisualSelf(caster, def.school);
        }

        if ("self".equals(def.targeting.type)) {
            applyImpacts(caster, caster, def);
        } else {
            for (LivingEntity target : targets) {
                applyImpacts(caster, target, def);
            }
        }
    }

    // ── Beam: raycast hitting ALL entities in a line ─────────────────────────

    private static void castBeam(ServerPlayer caster, SpellDefinition def) {
        if (!(caster.level() instanceof ServerLevel serverLevel)) return;

        Vec3 eye   = caster.getEyePosition();
        Vec3 look  = caster.getLookAngle().scale(def.targeting.range);
        Vec3 end   = eye.add(look);

        // Block clip
        HitResult blockHit = caster.level().clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster));
        Vec3 beamEnd = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : end;

        // Particles along the beam
        double dist = eye.distanceTo(beamEnd);
        Vec3 dir = beamEnd.subtract(eye).normalize();
        for (double d = 1.0; d < dist; d += 1.5) {
            Vec3 p = eye.add(dir.scale(d));
            serverLevel.sendParticles(schoolParticle(def.school), p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.0);
        }

        // Hit all entities along beam
        double width = Math.max(0.1, def.targeting.width);
        AABB box = new AABB(eye, beamEnd).inflate(width);
        List<LivingEntity> hit = caster.level().getEntitiesOfClass(LivingEntity.class, box,
            entity -> entity != caster && entity.getBoundingBox().inflate(width).clip(eye, beamEnd).isPresent());
        hit.sort((left, right) -> Double.compare(
            left.distanceToSqr(caster), right.distanceToSqr(caster)));
        if (def.targeting.max_targets > 0 && hit.size() > def.targeting.max_targets) {
            hit = hit.subList(0, def.targeting.max_targets);
        }
        for (LivingEntity t : hit) {
            applyImpacts(caster, t, def);
        }

        // Lightning visual at end for lightning school
        if ("lightning".equals(def.school)) {
            LightningBolt bolt = new LightningBolt(net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, serverLevel);
            bolt.moveTo(beamEnd.x, beamEnd.y, beamEnd.z);
            bolt.setVisualOnly(true);
            serverLevel.addFreshEntity(bolt);
        }
    }

    private static void castCompanionBeam(ServerPlayer owner, PokemonEntity companion,
                                          LivingEntity target, SpellDefinition def) {
        if (!(companion.level() instanceof ServerLevel serverLevel)) return;
        Vec3 start = companion.getEyePosition();
        Vec3 desiredEnd = target.getBoundingBox().getCenter();
        HitResult blockHit = companion.level().clip(new ClipContext(start, desiredEnd,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, companion));
        Vec3 end = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : desiredEnd;
        Vec3 delta = end.subtract(start);
        double distance = delta.length();
        if (distance <= 1.0E-6) return;
        Vec3 direction = delta.normalize();
        for (double offset = 0.5; offset < distance; offset += 1.0) {
            Vec3 position = start.add(direction.scale(offset));
            serverLevel.sendParticles(schoolParticle(def.school), position.x, position.y, position.z,
                    3, 0.1, 0.1, 0.1, 0.0);
        }

        double width = Math.max(0.1, def.targeting.width);
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class,
                new AABB(start, end).inflate(width),
                entity -> entity != companion && entity != owner
                        && entity.getBoundingBox().inflate(width).clip(start, end).isPresent());
        targets.sort((left, right) -> Double.compare(left.distanceToSqr(companion),
                right.distanceToSqr(companion)));
        if (def.targeting.max_targets > 0 && targets.size() > def.targeting.max_targets) {
            targets = targets.subList(0, def.targeting.max_targets);
        }
        for (LivingEntity beamTarget : targets) {
            applyImpacts(owner, companion, beamTarget, def);
        }
    }

    // ── Meteor: impacts from above at aimed location ─────────────────────────

    private static void castMeteor(ServerPlayer caster, ResourceLocation spellId, SpellDefinition def) {
        Vec3 center = resolveAimPosition(caster, def.targeting.range);
        castMeteorAt(caster, caster, spellId, def, center);
    }

    private static void castMeteorAt(ServerPlayer owner, LivingEntity effectCaster,
                                     ResourceLocation spellId, SpellDefinition def, Vec3 center) {
        if (!(effectCaster.level() instanceof ServerLevel serverLevel)) return;
        int projectileCount = Math.max(1, def.delivery.projectile_count);
        double spreadRadius = Math.max(1.0, def.targeting.radius * 0.65);
        UUID groupId = SpellRuntimeController.createMeteorGroup(owner, effectCaster, 100);

        serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, center.x, center.y + 0.1, center.z,
            25, spreadRadius, 0.05, spreadRadius, 0.01);
        for (int index = 0; index < projectileCount; index++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * spreadRadius * 2.0;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * spreadRadius * 2.0;
            Vec3 impactPosition = center.add(offsetX, 0.0, offsetZ);
            Vec3 spawnPosition = impactPosition.add(
                (serverLevel.random.nextDouble() - 0.5) * 4.0,
                15.0 + serverLevel.random.nextDouble() * 5.0,
                (serverLevel.random.nextDouble() - 0.5) * 4.0);
            SpellProjectile meteor = SpellProjectile.createMeteor(
                owner, effectCaster, spellId,
                def, spawnPosition, impactPosition, groupId, index, projectileCount);
            serverLevel.addFreshEntity(meteor);
        }
        if (def.delivery.recovery_ticks > 0) {
            effectCaster.addEffect(new MobEffectInstance(TensuraMobEffects.EXHAUSTED,
                    def.delivery.recovery_ticks, 0, false, true, true));
        }
    }

    // ── Cloud: lingering area at aimed position ───────────────────────────────

    private static void castCloud(ServerPlayer caster, SpellDefinition def) {
        if (!(caster.level() instanceof ServerLevel serverLevel)) return;

        LivingEntity aimTarget = rayCast(caster, def.targeting.range);
        Vec3 cloudPos = aimTarget != null ? aimTarget.position()
                : caster.position().add(caster.getLookAngle().scale(8));

        double radius = def.targeting.radius > 0 ? def.targeting.radius : 4.0;

        // Particle cloud effect (30 ticks spread)
        for (int i = 0; i < 40; i++) {
            double ox = (Math.random() - 0.5) * radius * 2;
            double oy = Math.random() * 2;
            double oz = (Math.random() - 0.5) * radius * 2;
            serverLevel.sendParticles(schoolParticle(def.school),
                    cloudPos.x + ox, cloudPos.y + oy, cloudPos.z + oz,
                    1, 0.0, 0.0, 0.0, 0.0);
        }

        // Immediate AoE in cloud area
        AABB box = new AABB(cloudPos, cloudPos).inflate(radius);
        List<LivingEntity> targets = caster.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != caster);
        for (LivingEntity t : targets) {
            applyImpacts(caster, t, def);
        }
    }

    private static void castCompanionCloud(ServerPlayer owner, PokemonEntity companion,
                                           Vec3 position, SpellDefinition def) {
        if (!(companion.level() instanceof ServerLevel serverLevel)) return;
        double radius = def.targeting.radius > 0 ? def.targeting.radius : 4.0;
        serverLevel.sendParticles(schoolParticle(def.school), position.x, position.y + 1, position.z,
                40, radius * 0.5, 1.0, radius * 0.5, 0.03);
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class,
                new AABB(position, position).inflate(radius),
                entity -> entity != companion && entity != owner && entity.isAlive());
        for (LivingEntity cloudTarget : targets) {
            applyImpacts(owner, companion, cloudTarget, def);
        }
    }

    private static boolean castVortex(ServerPlayer caster, SpellDefinition def) {
        Vec3 center = resolveAimPosition(caster, def.targeting.range);
        return SpellRuntimeController.startVortex(caster, def, center);
    }

    private static boolean castDelayed(ServerPlayer caster, SpellDefinition def) {
        LivingEntity target = rayCast(caster, def.targeting.range);
        return target != null && SpellRuntimeController.startDelayed(caster, target, def);
    }

    // ── Targeting helpers ────────────────────────────────────────────────────

    private static List<LivingEntity> resolveTargets(ServerPlayer caster, SpellDefinition def) {
        if ("self".equals(def.targeting.type)) return List.of(caster);
        if ("area".equals(def.targeting.type)) return aoeTargets(caster, def.targeting.range);
        // "aim" default
        LivingEntity hit = rayCast(caster, def.targeting.range);
        return hit != null ? List.of(hit) : List.of();
    }

    private static LivingEntity rayCast(Player player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().scale(range);
        Vec3 end  = eye.add(look);

        HitResult blockHit = player.level().clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 target = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : end;

        AABB box = player.getBoundingBox().expandTowards(look).inflate(1.0);
        LivingEntity closest = null;
        double closestDistance = range * range;
        for (Entity entity : player.level().getEntities(player, box)) {
            if (entity instanceof LivingEntity living && entity != player) {
                var intersection = entity.getBoundingBox().inflate(0.3).clip(eye, target);
                if (intersection.isPresent()) {
                    double distance = eye.distanceToSqr(intersection.get());
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closest = living;
                    }
                }
            }
        }
        return closest;
    }

    private static Vec3 resolveAimPosition(Player player, double range) {
        LivingEntity target = rayCast(player, range);
        if (target != null) return target.position();

        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        HitResult blockHit = player.level().clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : end;
    }

    private static List<LivingEntity> aoeTargets(Player caster, double range) {
        AABB box = caster.getBoundingBox().inflate(range);
        return caster.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != caster && caster.distanceTo(e) <= range);
    }

    // ── Visuals ──────────────────────────────────────────────────────────────

    private static void applySchoolVisual(ServerPlayer caster, String school, Vec3 pos) {
        if (!(caster.level() instanceof ServerLevel serverLevel)) return;
        switch (school) {
            case "lightning" -> {
                LightningBolt bolt = new LightningBolt(net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, serverLevel);
                bolt.moveTo(pos.x, pos.y, pos.z);
                bolt.setVisualOnly(true);
                serverLevel.addFreshEntity(bolt);
            }
            case "fire"    -> serverLevel.sendParticles(ParticleTypes.FLAME,    pos.x, pos.y + 1, pos.z, 50, 0.4, 0.6, 0.4, 0.05);
            case "water"   -> serverLevel.sendParticles(ParticleTypes.DRIPPING_WATER, pos.x, pos.y + 1, pos.z, 50, 0.4, 0.6, 0.4, 0.05);
            case "ice"     -> serverLevel.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 1, pos.z, 50, 0.4, 0.6, 0.4, 0.02);
            case "shadow"  -> serverLevel.sendParticles(ParticleTypes.PORTAL,   pos.x, pos.y + 1, pos.z, 50, 0.4, 0.6, 0.4, 0.05);
            case "psychic" -> serverLevel.sendParticles(ParticleTypes.ENCHANT,  pos.x, pos.y + 1, pos.z, 50, 0.4, 0.6, 0.4, 0.05);
            case "dragon"  -> serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y + 1, pos.z, 50, 0.4, 0.6, 0.4, 0.05);
            case "nature"  -> serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 1, pos.z, 30, 0.4, 0.6, 0.4, 0.05);
            case "poison"  -> serverLevel.sendParticles(ParticleTypes.WITCH,    pos.x, pos.y + 1, pos.z, 40, 0.4, 0.6, 0.4, 0.05);
            case "earth"   -> serverLevel.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 1, pos.z, 8, 0.4, 0.4, 0.4, 0.1);
            case "wind"    -> serverLevel.sendParticles(ParticleTypes.CLOUD,    pos.x, pos.y + 1, pos.z, 30, 0.4, 0.6, 0.4, 0.1);
            case "fairy"   -> serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1, pos.z, 30, 0.4, 0.6, 0.4, 0.05);
            case "steel"   -> serverLevel.sendParticles(ParticleTypes.CRIT,     pos.x, pos.y + 1, pos.z, 40, 0.4, 0.6, 0.4, 0.1);
            default        -> serverLevel.sendParticles(ParticleTypes.CRIT,     pos.x, pos.y + 1, pos.z, 20, 0.3, 0.5, 0.3, 0.05);
        }
    }

    private static void applySchoolVisualSelf(ServerPlayer caster, String school) {
        if (!(caster.level() instanceof ServerLevel serverLevel)) return;
        Vec3 pos = caster.position();
        serverLevel.sendParticles(schoolParticle(school), pos.x, pos.y + 1, pos.z, 20, 0.5, 0.5, 0.5, 0.05);
    }

    private static void drawParticleLine(ServerPlayer caster, LivingEntity target,
                                         net.minecraft.core.particles.SimpleParticleType particle) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        Vec3 start = caster.getEyePosition().subtract(0.0, 0.35, 0.0);
        Vec3 end = target.getBoundingBox().getCenter();
        Vec3 delta = end.subtract(start);
        int steps = Math.max(2, (int) Math.ceil(delta.length() * 3.0));
        for (int step = 0; step <= steps; step++) {
            Vec3 position = start.add(delta.scale((double) step / steps));
            level.sendParticles(particle, position.x, position.y, position.z,
                    1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    private static void playCastSound(LivingEntity caster, SpellDefinition def) {
        if (def.sound.cast == null || def.sound.cast.isBlank()) return;
        BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.parse(def.sound.cast))
                .ifPresent(sound -> caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                        sound, SoundSource.PLAYERS, 1.0f, 1.0f));
    }

    private static void playImpactSound(ServerPlayer caster, LivingEntity target, SpellDefinition def) {
        if (def.sound.impact == null || def.sound.impact.isBlank()) return;
        BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.parse(def.sound.impact))
                .ifPresent(sound -> target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        sound, SoundSource.PLAYERS, 1.0f, 1.0f));
    }

    private static net.minecraft.core.particles.SimpleParticleType schoolParticle(String school) {
        return switch (school) {
            case "lightning" -> ParticleTypes.ELECTRIC_SPARK;
            case "fire"      -> ParticleTypes.FLAME;
            case "water"     -> ParticleTypes.DRIPPING_WATER;
            case "ice"       -> ParticleTypes.SNOWFLAKE;
            case "shadow"    -> ParticleTypes.PORTAL;
            case "psychic"   -> ParticleTypes.ENCHANT;
            case "dragon"    -> ParticleTypes.DRAGON_BREATH;
            case "nature"    -> ParticleTypes.COMPOSTER;
            case "poison"    -> ParticleTypes.WITCH;
            case "earth"     -> ParticleTypes.EXPLOSION;
            case "wind"      -> ParticleTypes.CLOUD;
            case "fairy"     -> ParticleTypes.TOTEM_OF_UNDYING;
            default          -> ParticleTypes.CRIT;
        };
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

    // ── Impact application ───────────────────────────────────────────────────

    public static void applyImpacts(ServerPlayer caster, LivingEntity target, SpellDefinition def) {
        applyImpacts(caster, caster, target, def, true);
    }

    public static void applyImpacts(ServerPlayer caster, LivingEntity target,
                                    SpellDefinition def, boolean finalProjectile) {
        applyImpacts(caster, caster, target, def, finalProjectile);
    }

    public static void applyImpacts(ServerPlayer owner, LivingEntity effectCaster,
                                    LivingEntity target, SpellDefinition def) {
        applyImpacts(owner, effectCaster, target, def, true);
    }

    public static void applyImpacts(ServerPlayer owner, LivingEntity effectCaster,
                                    LivingEntity target, SpellDefinition def,
                                    boolean finalProjectile) {
        for (SpellDefinition.Impact impact : def.impact) {
            LivingEntity recipient = "caster".equals(impact.recipient) ? effectCaster : target;
            switch (impact.type) {
                case "damage" -> {
                    double baseDamage = def.power >= 0.0
                            ? def.power
                            : owner.getAttackStrengthScale(0) * 6.0;
                    float dmg = (float) (baseDamage * impact.damage_multiplier);
                    target.hurt(owner.damageSources().playerAttack(owner), Math.max(1, dmg));
                }
                case "status_effect" -> {
                    if (Math.random() <= impact.chance && !impact.effect.isEmpty()) {
                        BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(impact.effect))
                            .ifPresent(holder -> recipient.addEffect(new MobEffectInstance(holder,
                                    impact.duration, impact.amplifier, impact.ambient,
                                    impact.show_particles, impact.show_icon)));
                    }
                }
                case "fire"     -> target.igniteForSeconds(impact.seconds);
                case "knockback" -> {
                    Vec3 dir = target.position().subtract(effectCaster.position()).normalize().scale(impact.strength);
                    target.setDeltaMovement(target.getDeltaMovement().add(dir.x, 0.4, dir.z));
                    target.hurtMarked = true;
                }
                case "pull" -> {
                    Vec3 delta = effectCaster.position().subtract(target.position());
                    if (delta.lengthSqr() > 1.0E-6) {
                        Vec3 pull = delta.normalize().scale(impact.strength);
                        target.setDeltaMovement(target.getDeltaMovement().add(pull.x, 0.15, pull.z));
                        target.hurtMarked = true;
                    }
                }
                case "heal"     -> recipient.heal((float) impact.amount);
                case "full_heal" -> recipient.setHealth(recipient.getMaxHealth());
                case "cleanse" -> {
                    List<Holder<MobEffect>> harmfulEffects = recipient.getActiveEffects().stream()
                            .filter(instance -> instance.getEffect().value().getCategory().equals(net.minecraft.world.effect.MobEffectCategory.HARMFUL))
                            .map(MobEffectInstance::getEffect)
                            .toList();
                    harmfulEffects.forEach(recipient::removeEffect);
                }
                case "wet" -> recipient.addEffect(new MobEffectInstance(
                        TensuraMobEffects.WET, impact.duration, 0, false,
                        impact.show_particles, impact.show_icon));
                case "freeze_if_wet" -> {
                    if (recipient.hasEffect(TensuraMobEffects.WET)) {
                        recipient.removeEffect(TensuraMobEffects.WET);
                        recipient.addEffect(new MobEffectInstance(TensuraMobEffects.FROZEN,
                                impact.duration, 0, false, impact.show_particles, impact.show_icon));
                    } else {
                        BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.withDefaultNamespace("slowness"))
                                .ifPresent(holder -> recipient.addEffect(new MobEffectInstance(holder,
                                        impact.duration, Math.max(1, impact.amplifier))));
                    }
                }
                case "tri_status" -> {
                    if (!finalProjectile) continue;
                    switch (target.getRandom().nextInt(3)) {
                        case 0 -> target.igniteForSeconds(Math.max(1, impact.seconds));
                        case 1 -> BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.withDefaultNamespace("slowness"))
                                .ifPresent(holder -> target.addEffect(new MobEffectInstance(holder,
                                        impact.duration, Math.max(1, impact.amplifier))));
                        default -> target.addEffect(new MobEffectInstance(TensuraMobEffects.PARALYZED,
                                impact.duration, 0, false, impact.show_particles, impact.show_icon));
                    }
                }
                case "guard" -> SpellRuntimeController.addGuard(recipient, impact.amount, impact.duration);
            }
        }
        playImpactSound(owner, target, def);
    }
}
