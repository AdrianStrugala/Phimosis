package com.tensura.engine;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket;
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

final class CobblemonPsychicVfx {
    private static final ResourceLocation ACTOR_EFFECT =
            ResourceLocation.fromNamespaceAndPath("cobblemon", "psychic_actor");
    private static final ResourceLocation TARGET_EFFECT =
            ResourceLocation.fromNamespaceAndPath("cobblemon", "psychic_target");
    private static final ResourceLocation IMPACT_EFFECT =
            ResourceLocation.fromNamespaceAndPath("cobblemon", "psychic_impact");
    private static final double VIEW_DISTANCE_SQR = 64.0 * 64.0;

    private CobblemonPsychicVfx() {}

    static boolean isPsychic(SpellDefinition definition) {
        return "psychic_crush".equals(definition.visual.impact);
    }

    static void sendHit(LivingEntity caster, LivingEntity target) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        List<ServerPlayer> players = nearbyPlayers(level, target.position());
        if (caster instanceof PokemonEntity) {
            new SpawnSnowstormEntityParticlePacket(
                    ACTOR_EFFECT, caster.getId(), List.of("root"), null, null)
                    .sendToPlayers(players);
        } else {
            sendAtPosition(players, ACTOR_EFFECT, caster.getBoundingBox().getCenter());
        }

        Vec3 targetPosition = target.getBoundingBox().getCenter();
        sendAtPosition(players, TARGET_EFFECT, targetPosition);
        sendAtPosition(players, IMPACT_EFFECT, targetPosition);
    }

    private static void sendAtPosition(List<ServerPlayer> players,
                                       ResourceLocation effect, Vec3 position) {
        new SpawnSnowstormParticlePacket(effect, position).sendToPlayers(players);
    }

    private static List<ServerPlayer> nearbyPlayers(ServerLevel level, Vec3 position) {
        return level.players().stream()
                .filter(player -> player.distanceToSqr(position) <= VIEW_DISTANCE_SQR)
                .toList();
    }
}