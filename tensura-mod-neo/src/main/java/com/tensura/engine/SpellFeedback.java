package com.tensura.engine;

import com.tensura.entity.SpellProjectile;
import com.tensura.network.SpellVfxDispatcher;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

final class SpellFeedback {

    private SpellFeedback() {}

    static void sendProjectileVfx(ServerLevel level, SpellProjectile projectile,
                                  SpellDefinition definition, Vec3 direction) {
        sendProjectileVfx(level, projectile, definition, direction, 1.0);
    }

    static void sendProjectileVfx(ServerLevel level, SpellProjectile projectile,
                                  SpellDefinition definition, Vec3 direction,
                                  double visualScale) {
        Vec3 origin = projectile.position();
        Vec3 target = origin.add(direction.normalize().scale(definition.targeting.range));
        SpellVfxDispatcher.send(level, "projectile", definition.visual.projectile,
                definition.school, origin, target, visualScale,
                definition.delivery.duration_ticks, projectile, false);
        SpellVfxDispatcher.send(level, "projectile", definition.visual.trail,
                definition.school, origin, target, visualScale,
                definition.delivery.duration_ticks, projectile, false);
    }

    static void sendCastVfx(LivingEntity effectCaster, SpellDefinition definition) {
        if (!(effectCaster.level() instanceof ServerLevel level)) return;
                CobblemonUltimateVfx.sendCastEffects(effectCaster, definition);
        SpellVfxDispatcher.send(level, "attachment", definition.visual.cast_animation,
                definition.school, effectCaster.position(), effectCaster.position(),
                1.0, definition.cast_time_ticks, effectCaster, true);
        if (definition.cast_time_ticks > 0
                && ("projectile".equals(definition.delivery.type)
                    || "beam".equals(definition.delivery.type)
                    || "channel_beam".equals(definition.delivery.type))) {
            SpellVfxDispatcher.send(level, "telegraph", definition.visual.telegraph,
                    definition.school, effectCaster.position(), effectCaster.position(),
                    1.25, definition.cast_time_ticks, effectCaster, true);
        }
    }

    static void applySchoolVisual(ServerPlayer caster, String school, Vec3 position) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        switch (school) {
            case "lightning" -> {
                LightningBolt bolt = new LightningBolt(
                        net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, level);
                bolt.moveTo(position.x, position.y, position.z);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            case "fire" -> level.sendParticles(ParticleTypes.FLAME,
                    position.x, position.y + 1, position.z, 50, 0.4, 0.6, 0.4, 0.05);
            case "water" -> level.sendParticles(ParticleTypes.DRIPPING_WATER,
                    position.x, position.y + 1, position.z, 50, 0.4, 0.6, 0.4, 0.05);
            case "ice" -> level.sendParticles(ParticleTypes.SNOWFLAKE,
                    position.x, position.y + 1, position.z, 50, 0.4, 0.6, 0.4, 0.02);
            case "shadow" -> level.sendParticles(ParticleTypes.PORTAL,
                    position.x, position.y + 1, position.z, 50, 0.4, 0.6, 0.4, 0.05);
            case "psychic" -> level.sendParticles(ParticleTypes.ENCHANT,
                    position.x, position.y + 1, position.z, 50, 0.4, 0.6, 0.4, 0.05);
            case "dragon" -> level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    position.x, position.y + 1, position.z, 50, 0.4, 0.6, 0.4, 0.05);
            case "nature" -> level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    position.x, position.y + 1, position.z, 30, 0.4, 0.6, 0.4, 0.05);
            case "poison" -> level.sendParticles(ParticleTypes.WITCH,
                    position.x, position.y + 1, position.z, 40, 0.4, 0.6, 0.4, 0.05);
            case "earth" -> level.sendParticles(ParticleTypes.EXPLOSION,
                    position.x, position.y + 1, position.z, 8, 0.4, 0.4, 0.4, 0.1);
            case "wind" -> level.sendParticles(ParticleTypes.CLOUD,
                    position.x, position.y + 1, position.z, 30, 0.4, 0.6, 0.4, 0.1);
            case "fairy" -> level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    position.x, position.y + 1, position.z, 30, 0.4, 0.6, 0.4, 0.05);
            case "steel" -> level.sendParticles(ParticleTypes.CRIT,
                    position.x, position.y + 1, position.z, 40, 0.4, 0.6, 0.4, 0.1);
            default -> level.sendParticles(ParticleTypes.CRIT,
                    position.x, position.y + 1, position.z, 20, 0.3, 0.5, 0.3, 0.05);
        }
    }

    static void applySchoolVisualSelf(ServerPlayer caster, String school) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        Vec3 position = caster.position();
        level.sendParticles(schoolParticle(school),
                position.x, position.y + 1, position.z, 20, 0.5, 0.5, 0.5, 0.05);
    }

    static void drawParticleLine(ServerPlayer caster, LivingEntity target,
                                 SimpleParticleType particle) {
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

    static void playCastSound(LivingEntity caster, SpellDefinition definition) {
        playSound(caster, definition.sound.cast, 1.0F);
    }

    static void playTravelSound(LivingEntity source, SpellDefinition definition) {
        playSound(source, definition.sound.travel, 0.9F);
    }

    static void playLoopSound(LivingEntity source, SpellDefinition definition) {
        playSound(source, definition.sound.loop, 0.8F);
    }

    static SimpleParticleType schoolParticle(String school) {
        return switch (school) {
            case "lightning" -> ParticleTypes.ELECTRIC_SPARK;
            case "fire" -> ParticleTypes.FLAME;
            case "water" -> ParticleTypes.DRIPPING_WATER;
            case "ice" -> ParticleTypes.SNOWFLAKE;
            case "shadow" -> ParticleTypes.PORTAL;
            case "psychic" -> ParticleTypes.ENCHANT;
            case "dragon" -> ParticleTypes.DRAGON_BREATH;
            case "nature" -> ParticleTypes.COMPOSTER;
            case "poison" -> ParticleTypes.WITCH;
            case "earth" -> ParticleTypes.EXPLOSION;
            case "wind" -> ParticleTypes.CLOUD;
            case "fairy" -> ParticleTypes.TOTEM_OF_UNDYING;
            default -> ParticleTypes.CRIT;
        };
    }

    private static void playSound(LivingEntity source, String soundId, float volume) {
        if (soundId == null || soundId.isBlank()) return;
        BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.parse(soundId))
                .ifPresent(sound -> source.level().playSound(null,
                        source.getX(), source.getY(), source.getZ(),
                        sound, SoundSource.PLAYERS, volume, 1.0F));
    }
}