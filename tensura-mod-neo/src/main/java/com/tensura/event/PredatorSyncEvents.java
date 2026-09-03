package com.tensura.event;

import com.tensura.data.PredatorAbsorption;
import com.tensura.data.PredatorData;
import com.tensura.engine.SpellRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Brings the puffish devour tree in line with PredatorData.absorbed on login.
 *
 * Unconditional: every known spell gets either an unlock or a lock. Both puffish
 * commands are idempotent, so re-sending the current state costs nothing but a few
 * dozen command dispatches once per login. That is deliberately simpler than reading
 * the tree's state back out of puffish, and it self-heals nodes a player clicked by
 * hand as well as any unlock command that failed at kill time.
 *
 * Deferred by one server task so puffish has finished setting up the player's
 * category data before the commands run.
 */
public class PredatorSyncEvents {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Set<ResourceLocation> absorbed = new HashSet<>(PredatorData.getAbsorbed(player));
        Set<ResourceLocation> known = new HashSet<>(SpellRegistry.all().keySet());

        player.getServer().execute(() -> {
            if (player.hasDisconnected()) return;
            for (ResourceLocation spellId : known) {
                if (absorbed.contains(spellId)) {
                    PredatorAbsorption.unlockNode(player, spellId);
                } else {
                    PredatorAbsorption.lockNode(player, spellId);
                }
            }
        });
    }
}
