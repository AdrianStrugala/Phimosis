package com.tensura.spell;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.TensuraMod;
import com.tensura.engine.SpellRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Maps supported Cobblemon moves to same-named Tensura spell definitions. */
public final class CobblemonMoveMapper {

    private CobblemonMoveMapper() {}

    public static List<ResourceLocation> getSpellsForPokemon(PokemonEntity pokemon) {
        List<ResourceLocation> result = new ArrayList<>();
        for (Move move : pokemon.getPokemon().getMoveSet().getMoves()) {
            if (move == null) continue;
            toSpell(move).ifPresent(result::add);
        }
        return result;
    }

    public static Optional<ResourceLocation> toSpell(Move move) {
        String name = move.getName().toLowerCase(Locale.ROOT)
                .replace(" ", "_")
                .replace("-", "_")
                .replace("'", "");
        ResourceLocation spellId = ResourceLocation.tryParse(TensuraMod.MOD_ID + ":" + name);
        return spellId != null && SpellRegistry.get(spellId).isPresent()
                ? Optional.of(spellId)
                : Optional.empty();
    }
}
