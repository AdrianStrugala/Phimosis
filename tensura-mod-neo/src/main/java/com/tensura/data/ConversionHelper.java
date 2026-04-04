package com.tensura.data;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenSkillHandler;
import com.tensura.event.ColonyStartupEvents;
import net.minecraft.core.RegistryAccess;
import org.jetbrains.annotations.Nullable;

/**
 * Shared logic for converting a MineColonies citizen back to a Cobblemon Pokemon.
 *
 * Two cases:
 *  A) Enrolled citizen — restores the original Pokemon from saved NBT and applies
 *     any skill progression gained during work as EVs (overflow → IVs).
 *  B) Non-enrolled citizen — creates a fresh Pokemon of the mapped species with
 *     IVs derived from current citizen skill levels.
 */
public class ConversionHelper {

    private static final String SPECIES_TAG_PREFIX = "tensura:species:";

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Builds the recalled Pokemon for the given citizen.
     *
     * @return ready-to-add Pokemon, or {@code null} if no species could be determined.
     */
    @Nullable
    public static Pokemon buildRecalledPokemon(
            int citizenId,
            AbstractEntityCitizen citizen,
            ICitizenSkillHandler skills,
            DynamicCitizenSpeciesData data,
            RegistryAccess registryAccess) {

        if (data.contains(citizenId)) {
            // Case A: enrolled
            Pokemon pokemon = new Pokemon();
            pokemon.loadFromNBT(registryAccess, data.pokemonNbt.get(citizenId));
            applySkillProgression(pokemon, skills);
            return pokemon;
        } else {
            // Case B: non-enrolled
            String species = resolveSpecies(citizenId, citizen);
            if (species == null) return null;
            String name = citizen.getName().getString();
            boolean isFemale = citizen.isFemale();
            return buildFreshPokemon(species, skills, name, isFemale);
        }
    }

    // ── Case A: apply citizen skill progression to existing Pokemon ───────────

    private static void applySkillProgression(Pokemon pokemon, ICitizenSkillHandler skills) {
        var baseStats = pokemon.getSpecies().getBaseStats();
        applyStatProgression(pokemon, Stats.HP,              skills, Skill.Stamina,   baseStats.getOrDefault(Stats.HP, 45));
        applyStatProgression(pokemon, Stats.ATTACK,          skills, Skill.Strength,  baseStats.getOrDefault(Stats.ATTACK, 45));
        applyStatProgression(pokemon, Stats.DEFENCE,         skills, Skill.Athletics, baseStats.getOrDefault(Stats.DEFENCE, 45));
        applyStatProgression(pokemon, Stats.SPECIAL_ATTACK,  skills, Skill.Mana,      baseStats.getOrDefault(Stats.SPECIAL_ATTACK, 45));
        applyStatProgression(pokemon, Stats.SPECIAL_DEFENCE, skills, Skill.Knowledge, baseStats.getOrDefault(Stats.SPECIAL_DEFENCE, 45));
        applyStatProgression(pokemon, Stats.SPEED,           skills, Skill.Agility,   baseStats.getOrDefault(Stats.SPEED, 45));

        int newLevel = Math.max(pokemon.getLevel(), averageSkillLevel(skills));
        pokemon.setLevel(Math.min(100, newLevel));
    }

    /**
     * Computes the gain between the original skill level (set at enrollment from base stat)
     * and the current level, then applies it as EVs. Any amount that would exceed the 252
     * per-stat EV cap overflows into IVs (scaled to 0–31).
     */
    private static void applyStatProgression(
            Pokemon pokemon, Stat stat,
            ICitizenSkillHandler skills, Skill skill, int baseStat) {

        int origLevel = Math.max(1, baseStat * 10 / 255); // level assigned at enrollment
        int currLevel = skills.getLevel(skill);
        int delta = Math.max(0, currLevel - origLevel);
        if (delta == 0) return;

        int evGain    = delta * 255 / 10;
        int currentEV = pokemon.getEvs().getOrDefault(stat);
        int newEV     = Math.min(252, currentEV + evGain);
        pokemon.getEvs().set(stat, newEV);

        int overflow = (currentEV + evGain) - newEV;
        if (overflow > 0) {
            int ivGain    = overflow * 31 / 255;
            int currentIV = pokemon.getIvs().getOrDefault(stat);
            pokemon.getIvs().set(stat, Math.min(31, currentIV + ivGain));
        }
    }

    // ── Case B: create a fresh Pokemon for a never-enrolled citizen ───────────

    private static Pokemon buildFreshPokemon(String speciesName, ICitizenSkillHandler skills, String nickname, boolean isFemale) {
        var speciesObj = PokemonSpecies.INSTANCE.getByName(speciesName);
        if (speciesObj == null) return null;

        Pokemon pokemon = new Pokemon();
        pokemon.setSpecies(speciesObj);
        pokemon.setNickname(net.minecraft.network.chat.Component.literal(nickname));
        pokemon.setGender(isFemale ? com.cobblemon.mod.common.pokemon.Gender.FEMALE : com.cobblemon.mod.common.pokemon.Gender.MALE);

        // Level mirrors the average skill level (citizen skills range 0–100)
        int avgLevel = averageSkillLevel(skills);
        pokemon.setLevel(Math.max(1, Math.min(100, avgLevel)));

        // IVs (0–31) scaled from skill level (0–100)
        pokemon.getIvs().set(Stats.HP,              skillToIV(skills, Skill.Stamina));
        pokemon.getIvs().set(Stats.ATTACK,          skillToIV(skills, Skill.Strength));
        pokemon.getIvs().set(Stats.DEFENCE,         skillToIV(skills, Skill.Athletics));
        pokemon.getIvs().set(Stats.SPECIAL_ATTACK,  skillToIV(skills, Skill.Mana));
        pokemon.getIvs().set(Stats.SPECIAL_DEFENCE, skillToIV(skills, Skill.Knowledge));
        pokemon.getIvs().set(Stats.SPEED,           skillToIV(skills, Skill.Agility));

        // EVs (0–252) scaled from skill level (0–100)
        pokemon.getEvs().set(Stats.HP,              skillToEV(skills, Skill.Stamina));
        pokemon.getEvs().set(Stats.ATTACK,          skillToEV(skills, Skill.Strength));
        pokemon.getEvs().set(Stats.DEFENCE,         skillToEV(skills, Skill.Athletics));
        pokemon.getEvs().set(Stats.SPECIAL_ATTACK,  skillToEV(skills, Skill.Mana));
        pokemon.getEvs().set(Stats.SPECIAL_DEFENCE, skillToEV(skills, Skill.Knowledge));
        pokemon.getEvs().set(Stats.SPEED,           skillToEV(skills, Skill.Agility));

        return pokemon;
    }

    // ── Species resolution for non-enrolled citizens ──────────────────────────

    /**
     * Looks up the Pokemon species for a citizen not present in {@link DynamicCitizenSpeciesData}.
     * Priority: entity tag ({@code tensura:species:*}) → hardcoded map in ColonyStartupEvents.
     */
    @Nullable
    public static String resolveSpecies(int citizenId, AbstractEntityCitizen citizen) {
        for (String tag : citizen.getTags()) {
            if (tag.startsWith(SPECIES_TAG_PREFIX)) {
                return tag.substring(SPECIES_TAG_PREFIX.length());
            }
        }
        return ColonyStartupEvents.getHardcodedSpeciesMap().get(citizenId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int skillToIV(ICitizenSkillHandler skills, Skill skill) {
        return Math.min(31, skills.getLevel(skill) * 31 / 100);
    }

    private static int skillToEV(ICitizenSkillHandler skills, Skill skill) {
        return Math.min(252, skills.getLevel(skill) * 252 / 100);
    }

    private static int averageSkillLevel(ICitizenSkillHandler skills) {
        int sum = skills.getLevel(Skill.Stamina)
                + skills.getLevel(Skill.Strength)
                + skills.getLevel(Skill.Athletics)
                + skills.getLevel(Skill.Mana)
                + skills.getLevel(Skill.Knowledge)
                + skills.getLevel(Skill.Agility);
        return sum / 6;
    }
}
