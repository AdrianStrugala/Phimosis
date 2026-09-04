package com.tensura.event;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.config.TensuraConfig;
import com.tensura.data.PredatorAbsorption;
import com.tensura.data.PredatorData;
import com.tensura.spell.CobblemonMoveMapper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Killing a Pokemon rolls each of its mapped moves for a spell drop.
 * Moves the player has already absorbed are skipped without rolling, so a full
 * collection stops producing duplicates instead of cluttering the inventory.
 */
public class PredatorEvents {

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getType() != CobblemonEntities.POKEMON) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!(entity.level() instanceof ServerLevel level)) return;

        PokemonEntity pokemonEntity = (PokemonEntity) entity;
        double chance = TensuraConfig.DEVOUR_DROP_CHANCE.get();
        if (chance <= 0.0) return;

        Vec3 pos = entity.position();
        Set<ResourceLocation> rolled = new LinkedHashSet<>();

        for (Move move : pokemonEntity.getPokemon().getMoveSet().getMoves()) {
            Optional<ResourceLocation> mapped = CobblemonMoveMapper.toSpell(move);
            if (mapped.isEmpty()) continue;

            ResourceLocation spellId = mapped.get();
            // Two moves can map to the same spell — roll it once.
            if (!rolled.add(spellId)) continue;
            if (PredatorData.hasAbsorbed(player, spellId)) continue;
            if (level.getRandom().nextDouble() >= chance) continue;

            PredatorAbsorption.absorb(player, spellId, level, pos);
        }
    }
}
