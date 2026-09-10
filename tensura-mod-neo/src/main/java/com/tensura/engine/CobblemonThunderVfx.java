package com.tensura.engine;

import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class CobblemonThunderVfx {
    private static final ResourceLocation TELEGRAPH_EFFECT =
            ResourceLocation.fromNamespaceAndPath("cobblemon", "thunder_targetcloud");
    private static final ResourceLocation IMPACT_EFFECT =
            ResourceLocation.fromNamespaceAndPath("cobblemon", "thunder_target");
    private static final ResourceLocation BOOM_EFFECT =
            ResourceLocation.fromNamespaceAndPath("cobblemon", "thunder_targetboom");
    private static final double VIEW_DISTANCE_SQR = 96.0 * 96.0;

    private CobblemonThunderVfx() {}

    public static boolean isThunder(SpellDefinition definition) {
        return "lightning_column".equals(definition.visual.impact);
    }

    public static void sendTelegraph(ServerLevel level, Vec3 center) {
        send(level, TELEGRAPH_EFFECT, center);
    }

    public static void sendImpact(ServerLevel level, Vec3 center) {
        send(level, IMPACT_EFFECT, center);
        send(level, BOOM_EFFECT, center);
    }

    private static void send(ServerLevel level, ResourceLocation effect, Vec3 position) {
        List<ServerPlayer> players = level.players().stream()
                .filter(player -> player.distanceToSqr(position) <= VIEW_DISTANCE_SQR)
                .toList();
        new SpawnSnowstormParticlePacket(effect, position).sendToPlayers(players);
    }
}