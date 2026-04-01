package com.tensura.engine;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.entity.SpellProjectile;
import com.tensura.network.CooldownSyncPacket;
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
import net.minecraft.world.level.ClipContext;
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

    public static boolean cast(ServerPlayer caster, ResourceLocation spellId) {
        SpellDefinition def = SpellRegistry.get(spellId).orElse(null);
        if (def == null) {
            caster.sendSystemMessage(Component.literal("Unknown spell: " + spellId));
            return false;
        }

        // Cooldown check
        long now = caster.level().getGameTime();
        Map<ResourceLocation, Long> playerCooldowns = cooldowns.computeIfAbsent(caster.getUUID(), k -> new HashMap<>());
        long ready = playerCooldowns.getOrDefault(spellId, 0L);
        if (now < ready) {
            long remaining = (ready - now) / 20 + 1;
            caster.sendSystemMessage(Component.literal("§7[Cooldown: " + remaining + "s]"));
            return false;
        }
        playerCooldowns.put(spellId, now + def.cooldown_ticks);
        // Sync per-spell cooldown to client for visual bar
        PacketDistributor.sendToPlayer(caster, new CooldownSyncPacket(spellId, def.cooldown_ticks));

        // Resolve targets based on targeting type + delivery type
        switch (def.delivery.type) {
            case "beam"       -> castBeam(caster, def);
            case "meteor"     -> castMeteor(caster, def);
            case "cloud"      -> castCloud(caster, def);
            case "projectile" -> castProjectile(caster, def, spellId);
            case "explosion"  -> castExplosion(caster, def);
            default           -> castStandard(caster, def);
        }

        return true;
    }

    /**
     * Called by CompanionSpellGoal. Uses companion's position as origin,
     * directly hits the known target — no raycast needed.
     * Cooldown keyed on owner UUID with a companion-specific suffix to not share with player's own spells.
     */
    public static void castAsCompanion(ServerPlayer owner, PokemonEntity companion,
                                        ResourceLocation spellId, SpellDefinition def, LivingEntity target) {
        if (!(companion.level() instanceof ServerLevel serverLevel)) return;

        // Companion cooldown: separate key per companion species so each companion has own cooldowns
        ResourceLocation companionSpellKey = ResourceLocation.fromNamespaceAndPath(
                spellId.getNamespace(), "companion_" + spellId.getPath());
        long now = companion.level().getGameTime();
        Map<ResourceLocation, Long> ownerCooldowns = cooldowns.computeIfAbsent(owner.getUUID(), k -> new HashMap<>());
        if (now < ownerCooldowns.getOrDefault(companionSpellKey, 0L)) return;
        ownerCooldowns.put(companionSpellKey, now + def.cooldown_ticks);

        applySchoolVisualSelf(owner, def.school); // visual at companion position approximated via owner
        serverLevel.sendParticles(schoolParticle(def.school),
                companion.getX(), companion.getY() + 1, companion.getZ(), 12, 0.3, 0.3, 0.3, 0.05);

        // Direct impact on known target — no need for aim/area resolution
        applyImpacts(owner, target, def);
    }

    // ── Projectile: flying entity like ghast fireball ────────────────────────

    private static void castProjectile(ServerPlayer caster, SpellDefinition def, ResourceLocation spellId) {
        if (!(caster.level() instanceof ServerLevel serverLevel)) return;
        applySchoolVisualSelf(caster, def.school);
        SpellProjectile proj = SpellProjectile.create(caster, spellId, def);
        serverLevel.addFreshEntity(proj);
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

    // ── Standard (aim / area / self + instant) ───────────────────────────────

    private static void castStandard(ServerPlayer caster, SpellDefinition def) {
        List<LivingEntity> targets = resolveTargets(caster, def);

        if (!targets.isEmpty()) {
            applySchoolVisual(caster, def.school, targets.get(0).position());
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
        AABB box = new AABB(eye, beamEnd).inflate(1.0);
        List<LivingEntity> hit = caster.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != caster);
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

    // ── Meteor: impacts from above at aimed location ─────────────────────────

    private static void castMeteor(ServerPlayer caster, SpellDefinition def) {
        if (!(caster.level() instanceof ServerLevel serverLevel)) return;

        LivingEntity aimTarget = rayCast(caster, def.targeting.range);
        Vec3 impactPos = aimTarget != null ? aimTarget.position() : caster.position().add(caster.getLookAngle().scale(8));

        // Particles falling from above
        for (int i = 0; i < 20; i++) {
            double ox = (Math.random() - 0.5) * 2;
            double oz = (Math.random() - 0.5) * 2;
            serverLevel.sendParticles(schoolParticle(def.school),
                    impactPos.x + ox, impactPos.y + 15 + Math.random() * 5, impactPos.z + oz,
                    1, 0.2, 0.2, 0.2, 0.3);
        }

        // AoE at impact
        double radius = def.targeting.radius > 0 ? def.targeting.radius : 5.0;
        AABB box = new AABB(impactPos, impactPos).inflate(radius);
        List<LivingEntity> targets = caster.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != caster && caster.distanceTo(e) <= radius * 1.5);

        applySchoolVisual(caster, def.school, impactPos);
        for (LivingEntity t : targets) {
            applyImpacts(caster, t, def);
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
        for (Entity e : player.level().getEntities(player, box)) {
            if (e instanceof LivingEntity living && e != player) {
                if (e.getBoundingBox().inflate(0.3).clip(eye, target).isPresent()) {
                    return living;
                }
            }
        }
        return null;
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
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 1, pos.z, 20, 0.5, 0.5, 0.5, 0.05);
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

    // ── Impact application ───────────────────────────────────────────────────

    public static void applyImpacts(ServerPlayer caster, LivingEntity target, SpellDefinition def) {
        for (SpellDefinition.Impact impact : def.impact) {
            switch (impact.type) {
                case "damage" -> {
                    float dmg = (float)(caster.getAttackStrengthScale(0) * 6.0 * impact.damage_multiplier);
                    target.hurt(caster.damageSources().playerAttack(caster), Math.max(1, dmg));
                }
                case "status_effect" -> {
                    if (Math.random() <= impact.chance && !impact.effect.isEmpty()) {
                        BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(impact.effect))
                            .ifPresent(holder -> target.addEffect(new MobEffectInstance(holder, impact.duration, impact.amplifier)));
                    }
                }
                case "fire"     -> target.igniteForSeconds(impact.seconds);
                case "knockback" -> {
                    Vec3 dir = target.position().subtract(caster.position()).normalize().scale(impact.strength);
                    target.setDeltaMovement(target.getDeltaMovement().add(dir.x, 0.4, dir.z));
                }
                case "heal"     -> target.heal((float) impact.amount);
            }
        }
    }
}
