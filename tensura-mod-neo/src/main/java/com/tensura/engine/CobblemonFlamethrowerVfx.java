package com.tensura.engine;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CobblemonFlamethrowerVfx {
    private static final ResourceLocation ACTOR_EFFECT =
            ResourceLocation.fromNamespaceAndPath("cobblemon", "flamethrower_actor");
    private static final ResourceLocation HIT_EFFECT =
            ResourceLocation.fromNamespaceAndPath("cobblemon", "flamethrower_targetburst");
    private static final int EFFECT_INTERVAL_TICKS = 20;
    private static final double VIEW_DISTANCE_SQR = 64.0 * 64.0;
    private static final Map<UUID, Long> LAST_ACTOR_EFFECT = new HashMap<>();
    private static final Map<HitKey, Long> LAST_HIT_EFFECT = new HashMap<>();

    private CobblemonFlamethrowerVfx() {}

    static boolean isFlamethrower(SpellDefinition definition) {
        return "flame_stream".equals(definition.visual.trail);
    }

    static void sendStart(LivingEntity caster, LivingEntity target) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        if (caster instanceof PokemonEntity) {
            sendActorIfDue(level, caster, target);
            return;
        }

        Vec3 position = playerStreamOrigin(caster, caster.getLookAngle());
        sendAtPosition(level, HIT_EFFECT, position);
    }

    static Vec3 playerStreamOrigin(LivingEntity caster, Vec3 direction) {
        Vec3 forward = direction.lengthSqr() > 1.0E-6
                ? direction.normalize() : caster.getLookAngle().normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        if (right.lengthSqr() > 1.0E-6) right = right.normalize();
        double handSide = caster.getMainArm() == HumanoidArm.RIGHT ? 0.22 : -0.22;
        return caster.getEyePosition()
                .add(forward.scale(0.7))
                .add(right.scale(handSide))
                .add(0.0, -0.3, 0.0);
    }

    static void sendActorIfDue(ServerLevel level, LivingEntity caster, LivingEntity target) {
        if (!(caster instanceof PokemonEntity) || target == null) return;
        long now = level.getGameTime();
        pruneExpiredEntries(now);
        if (now - LAST_ACTOR_EFFECT.getOrDefault(caster.getUUID(), Long.MIN_VALUE / 2)
                < EFFECT_INTERVAL_TICKS) return;
        LAST_ACTOR_EFFECT.put(caster.getUUID(), now);

        SpawnSnowstormEntityParticlePacket packet =
                new SpawnSnowstormEntityParticlePacket(
                        ACTOR_EFFECT, caster.getId(), List.of("special", "target"),
                        target.getId(), List.of("middle"));
        packet.sendToPlayers(nearbyPlayers(level, caster.position()));
    }

    static void sendHitIfDue(ServerLevel level, LivingEntity caster, LivingEntity target) {
        long now = level.getGameTime();
        pruneExpiredEntries(now);
        HitKey hitKey = new HitKey(caster.getUUID(), target.getUUID());
        if (now - LAST_HIT_EFFECT.getOrDefault(hitKey, Long.MIN_VALUE / 2)
                < EFFECT_INTERVAL_TICKS) return;
        LAST_HIT_EFFECT.put(hitKey, now);
        sendAtPosition(level, HIT_EFFECT, target.getBoundingBox().getCenter());
    }

    static void clear() {
        LAST_ACTOR_EFFECT.clear();
        LAST_HIT_EFFECT.clear();
    }

    private static void sendAtPosition(ServerLevel level, ResourceLocation effect, Vec3 position) {
        SpawnSnowstormParticlePacket packet =
                new SpawnSnowstormParticlePacket(effect, position);
        packet.sendToPlayers(nearbyPlayers(level, position));
    }

    private static List<net.minecraft.server.level.ServerPlayer> nearbyPlayers(
            ServerLevel level, Vec3 position) {
        return level.players().stream()
                .filter(player -> player.distanceToSqr(position) <= VIEW_DISTANCE_SQR)
                .toList();
    }

    private static void pruneExpiredEntries(long now) {
        LAST_ACTOR_EFFECT.values().removeIf(
                lastSent -> now - lastSent >= EFFECT_INTERVAL_TICKS);
        LAST_HIT_EFFECT.values().removeIf(
                lastSent -> now - lastSent >= EFFECT_INTERVAL_TICKS);
    }

    private record HitKey(UUID casterId, UUID targetId) {}
}