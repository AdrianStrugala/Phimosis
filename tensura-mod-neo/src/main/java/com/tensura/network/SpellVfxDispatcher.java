package com.tensura.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SpellVfxDispatcher {
    private static final double TRACKING_RANGE_SQR = 128.0 * 128.0;

    private SpellVfxDispatcher() {
    }

    public static void send(ServerLevel level, String shape, String style, String school,
                            Vec3 origin, Vec3 target, double radius, int durationTicks,
                            Entity source, boolean friendly) {
        if (style == null || style.isBlank()) {
            return;
        }

        SpellVfxPacket packet = new SpellVfxPacket(
                shape, style, school, origin, target,
                (float) Math.max(0.0, radius), Math.max(0, durationTicks),
                source == null ? -1 : source.getId(), friendly);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(origin) <= TRACKING_RANGE_SQR) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }
}