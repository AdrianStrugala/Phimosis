package com.tensura.engine;

import com.cobblemon.mod.common.entity.PosableEntity;
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

        // psychic_target is the only one of the three that must be entity-bound. Its disc radius
        // is "(q.entity_radius*0.95)/math.clamp(q.entity_scale*1.1-0.1,1,9)", and those entity
        // queries resolve to 0 when the storm has no entity - DiscParticleEmitterShape feeds the
        // result straight into Random.nextDouble(bound), which throws "bound must be finite and
        // positive" on the render thread and takes the whole client down. Cobblemon's handler
        // drops the packet unless the source is a PosableEntity, so a vanilla victim simply goes
        // without the body wrap rather than crashing.
        if (target instanceof PosableEntity) {
            new SpawnSnowstormEntityParticlePacket(
                    TARGET_EFFECT, target.getId(), List.of("root"), null, null)
                    .sendToPlayers(players);
        }
        sendAtPosition(players, IMPACT_EFFECT, target.getBoundingBox().getCenter());
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