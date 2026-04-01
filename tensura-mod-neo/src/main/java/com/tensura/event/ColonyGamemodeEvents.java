package com.tensura.event;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gives Ehwar creative mode inside any colony, survival outside.
 */
public class ColonyGamemodeEvents {

    private static final String TARGET_PLAYER = "Ehwar";

    // UUID → was inside colony last tick
    private static final Map<UUID, Boolean> wasInColony = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getName().getString().equals(TARGET_PLAYER)) return;
        if (player.tickCount % 20 != 0) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        IColony colony = IColonyManager.getInstance().getIColony(level, player.blockPosition());
        boolean inColony = colony != null;
        boolean wasIn = wasInColony.getOrDefault(player.getUUID(), false);

        if (inColony && !wasIn) {
            player.setGameMode(GameType.CREATIVE);
        } else if (!inColony && wasIn) {
            player.setGameMode(GameType.SURVIVAL);
        }

        wasInColony.put(player.getUUID(), inColony);
    }
}
