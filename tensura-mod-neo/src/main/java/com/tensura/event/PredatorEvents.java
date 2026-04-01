package com.tensura.event;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.network.OpenSkillSelectPacket;
import com.tensura.spell.CobblemonMoveMapper;
import com.tensura.spell.SkillEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Listens for Pokemon deaths and opens the skill selection GUI for the killer.
 * Skills shown = the actual moves that specific Pokemon knew (from its MoveSet).
 */
public class PredatorEvents {

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity.getType() != CobblemonEntities.POKEMON) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        PokemonEntity pokemonEntity = (PokemonEntity) entity;
        String speciesName = pokemonEntity.getPokemon().getSpecies().getName();
        ResourceLocation species = ResourceLocation.fromNamespaceAndPath("cobblemon", speciesName.toLowerCase());

        // XP cost = the defeated Pokemon's level
        int pokemonLevel = pokemonEntity.getPokemon().getLevel();

        // Build skill list from the Pokemon's actual current moves (deduplicated by spell ID)
        List<SkillEntry> skills = new ArrayList<>();
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (Move move : pokemonEntity.getPokemon().getMoveSet().getMoves()) {
            CobblemonMoveMapper.toSpell(move).ifPresent(spellId -> {
                if (seen.add(spellId)) {
                    skills.add(new SkillEntry(spellId, pokemonLevel));
                }
            });
        }

        if (skills.isEmpty()) return;

        OpenSkillSelectPacket.sendToPlayer(player, skills, speciesName, species);
    }
}
