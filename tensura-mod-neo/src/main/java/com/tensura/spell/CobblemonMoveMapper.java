package com.tensura.spell;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.TensuraMod;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps Cobblemon move names → tensura spell ResourceLocations.
 *
 * Priority:
 *  1. NAME_MAP  — exact name match (covers 400+ moves)
 *  2. Status moves not in NAME_MAP → skipped (empty)
 *  3. Type fallback — for damage moves not explicitly mapped
 */
public class CobblemonMoveMapper {

    private static final Map<String, ResourceLocation> NAME_MAP = new HashMap<>();

    static {
        // ═══════════════════════════════════════════════════════════════════
        // GRASS
        // ═══════════════════════════════════════════════════════════════════
        n("vine_whip",          "vine_whip");
        n("razor_leaf",         "razor_leaf");
        n("solar_beam",         "solar_beam");
        n("solarbeam",          "solar_beam");
        n("solar_blade",        "solar_beam");
        n("leaf_blade",         "leaf_blade");
        n("leaf_storm",         "solar_beam");
        n("energy_ball",        "energy_ball");
        n("petal_dance",        "petal_blizzard");
        n("petal_blizzard",     "petal_blizzard");
        n("spore",              "leech_seed");
        n("leech_seed",         "leech_seed");
        n("absorb",             "leech_seed");
        n("mega_drain",         "leech_seed");
        n("giga_drain",         "leech_seed");
        n("seed_bomb",          "razor_leaf");
        n("seed_flare",         "solar_beam");
        n("bullet_seed",        "razor_leaf");
        n("power_whip",         "leaf_blade");
        n("wood_hammer",        "leaf_blade");
        n("frenzy_plant",       "solar_beam");
        n("grass_knot",         "vine_whip");
        n("magical_leaf",       "razor_leaf");
        n("needle_arm",         "razor_leaf");
        n("cotton_spore",       "leech_seed");
        n("razor_wind",         "razor_leaf");
        n("cut",                "razor_leaf");
        n("fury_cutter",        "razor_leaf");
        n("slash",              "leaf_blade");
        n("x_scissor",          "leaf_blade");
        n("trailblaze",         "vine_whip");
        n("snap_trap",          "vine_whip");
        n("trop_kick",          "vine_whip");
        n("branch_poke",        "vine_whip");
        n("forest_s_curse",     "energy_ball");
        n("grassy_glide",       "vine_whip");
        n("drum_beating",       "vine_whip");

        // ═══════════════════════════════════════════════════════════════════
        // FIRE
        // ═══════════════════════════════════════════════════════════════════
        n("ember",              "ember");
        n("flamethrower",       "flamethrower");
        n("fire_blast",         "fire_blast");
        n("overheat",           "overheat");
        n("heat_wave",          "overheat");
        n("lava_plume",         "fire_blast");
        n("eruption",           "fire_blast");
        n("inferno",            "overheat");
        n("sacred_fire",        "sacred_fire");
        n("v_create",           "sacred_fire");
        n("will_o_wisp",        "will_o_wisp");
        n("will-o-wisp",        "will_o_wisp");
        n("fire_spin",          "flamethrower");
        n("flame_wheel",        "flamethrower");
        n("blaze_kick",         "flamethrower");
        n("fire_punch",         "ember");
        n("fire_fang",          "ember");
        n("flame_charge",       "ember");
        n("incinerate",         "flamethrower");
        n("mystical_fire",      "flamethrower");
        n("burn_up",            "overheat");
        n("scorching_sands",    "fire_blast");
        n("fire_lash",          "flamethrower");
        n("pyro_ball",          "fire_blast");
        n("shell_trap",         "fire_blast");
        n("searing_shot",       "fire_blast");
        n("fiery_dance",        "flamethrower");
        n("fusion_flare",       "sacred_fire");
        n("fiery_wrath",        "fire_blast");
        n("torch_song",         "flamethrower");
        n("bitter_blade",       "sacred_fire");
        n("armor_cannon",       "fire_blast");

        // ═══════════════════════════════════════════════════════════════════
        // WATER
        // ═══════════════════════════════════════════════════════════════════
        n("water_gun",          "water_gun");
        n("bubble",             "water_gun");
        n("bubble_beam",        "bubble_beam");
        n("bubblebeam",         "bubble_beam");
        n("surf",               "surf");
        n("waterfall",          "surf");
        n("aqua_tail",          "surf");
        n("liquidation",        "surf");
        n("hydro_pump",         "hydro_pump");
        n("hydro_cannon",       "hydro_pump");
        n("origin_pulse",       "hydro_pump");
        n("steam_eruption",     "hydro_pump");
        n("scald",              "scald");
        n("water_pulse",        "water_pulse");
        n("water_spout",        "hydro_pump");
        n("clamp",              "water_pulse");
        n("whirlpool",          "whirlpool");
        n("aqua_ring",          "leech_seed");
        n("brine",              "scald");
        n("dive",               "surf");
        n("rain_dance",         "surf");
        n("soak",               "water_gun");
        n("sparkling_aria",     "bubble_beam");
        n("wave_crash",         "surf");
        n("flip_turn",          "surf");
        n("aqua_jet",           "aqua_jet");
        n("crabhammer",         "surf");
        n("muddy_water",        "surf");
        n("water_shuriken",     "water_gun");
        n("snipe_shot",         "hydro_pump");
        n("surging_strikes",    "water_gun");
        n("ivy_cudgel",         "surf");
        n("water_pledge",       "water_pulse");

        // ═══════════════════════════════════════════════════════════════════
        // ELECTRIC
        // ═══════════════════════════════════════════════════════════════════
        n("thundershock",       "thundershock");
        n("thunder_shock",      "thundershock");
        n("thunderbolt",        "thunderbolt");
        n("thunder",            "thunder");
        n("discharge",          "discharge");
        n("volt_tackle",        "volt_tackle");
        n("wild_charge",        "thunderbolt");
        n("spark",              "thundershock");
        n("charge_beam",        "thunderbolt");
        n("electro_ball",       "electro_ball");
        n("zap_cannon",         "thunder");
        n("thunder_punch",      "thundershock");
        n("thunder_fang",       "thundershock");
        n("bolt_strike",        "volt_tackle");
        n("fusion_bolt",        "thunder");
        n("volt_switch",        "thunderbolt");
        n("nuzzle",             "thundershock");
        n("parabolic_charge",   "discharge");
        n("electroweb",         "thunderbolt");
        n("magnetic_flux",      "discharge");
        n("eerie_impulse",      "discharge");
        n("plasma_fists",       "volt_tackle");
        n("overdrive",          "thunder");
        n("rising_voltage",     "thunder");
        n("thunderclap",        "thunderbolt");
        n("tera_blast",         "thunder");
        n("tera_starstorm",     "thunder");

        // ═══════════════════════════════════════════════════════════════════
        // ICE
        // ═══════════════════════════════════════════════════════════════════
        n("powder_snow",        "powder_snow");
        n("ice_shard",          "ice_shard");
        n("ice_beam",           "ice_beam");
        n("blizzard",           "blizzard");
        n("ice_punch",          "ice_shard");
        n("ice_fang",           "ice_shard");
        n("icicle_crash",       "ice_beam");
        n("icicle_spear",       "ice_shard");
        n("freeze_dry",         "blizzard");
        n("glaciate",           "frost_nova");
        n("aurora_beam",        "ice_beam");
        n("avalanche",          "frost_nova");
        n("icy_wind",           "powder_snow");
        n("frost_breath",       "ice_shard");
        n("mist",               "powder_snow");
        n("ice_spinner",        "ice_shard");
        n("freezing_glare",     "ice_beam");
        n("triple_axel",        "ice_shard");
        n("ceaseless_edge",     "ice_shard");
        n("chilly_reception",   "powder_snow");
        n("glacial_lance",      "blizzard");
        n("aurora_veil",        "aurora_veil");

        // ═══════════════════════════════════════════════════════════════════
        // PSYCHIC
        // ═══════════════════════════════════════════════════════════════════
        n("confusion",          "confusion");
        n("psybeam",            "psybeam");
        n("psychic",            "psychic");
        n("psycho_cut",         "psychic");
        n("zen_headbutt",       "confusion");
        n("extrasensory",       "psybeam");
        n("stored_power",       "psychic_blast");
        n("prismatic_laser",    "psychic_blast");
        n("psycho_boost",       "psychic_blast");
        n("luster_purge",       "psychic_blast");
        n("mist_ball",          "psybeam");
        n("heart_stamp",        "confusion");
        n("future_sight",       "future_sight");
        n("dream_eater",        "hex");
        n("psyshock",           "psychic");
        n("psystrike",          "psychic_blast");
        n("hyperspace_hole",    "psychic_blast");
        n("photon_geyser",      "psychic_blast");
        n("light_that_burns_the_sky", "psychic_blast");
        n("expanding_force",    "psychic_blast");
        n("twin_beam",          "psybeam");
        n("esper_wing",         "psybeam");
        n("lumina_crash",       "psychic");
        n("make_it_rain",       "psychic");
        n("hyper_space_fury",   "psychic_blast");

        // ═══════════════════════════════════════════════════════════════════
        // GHOST / DARK
        // ═══════════════════════════════════════════════════════════════════
        n("night_shade",        "night_shade");
        n("shadow_ball",        "shadow_ball");
        n("dark_pulse",         "dark_pulse");
        n("hex",                "hex");
        n("foul_play",          "foul_play");
        n("shadow_claw",        "shadow_ball");
        n("phantom_force",      "shadow_ball");
        n("shadow_force",       "shadow_ball");
        n("spectral_thief",     "foul_play");
        n("moongeist_beam",     "shadow_ball");
        n("shadow_sneak",       "night_shade");
        n("shadow_punch",       "night_shade");
        n("night_daze",         "dark_pulse");
        n("lick",               "night_shade");
        n("bite",               "dark_pulse");
        n("crunch",             "dark_pulse");
        n("thief",              "foul_play");
        n("sucker_punch",       "sucker_punch");
        n("assurance",          "hex");
        n("feint_attack",       "night_shade");
        n("pursuit",            "dark_pulse");
        n("darkest_lariat",     "foul_play");
        n("knock_off",          "dark_pulse");
        n("payback",            "dark_pulse");
        n("punishment",         "foul_play");
        n("fiery_wrath",        "dark_pulse");
        n("wicked_blow",        "dark_pulse");
        n("axe_kick",           "close_combat");
        n("menacing_moonraze_maelstrom", "shadow_ball");
        n("infernal_parade",    "hex");
        n("population_bomb",    "night_shade");
        n("last_respects",      "shadow_ball");

        // ═══════════════════════════════════════════════════════════════════
        // DRAGON
        // ═══════════════════════════════════════════════════════════════════
        n("dragon_breath",      "dragon_breath");
        n("dragonbreath",       "dragon_breath");
        n("dragon_pulse",       "dragon_pulse");
        n("draco_meteor",       "draco_meteor");
        n("outrage",            "outrage");
        n("dragon_claw",        "dragon_pulse");
        n("spacial_rend",       "draco_meteor");
        n("roar_of_time",       "draco_meteor");
        n("dragon_rush",        "dragon_pulse");
        n("twister",            "gust");
        n("dragon_tail",        "dragon_breath");
        n("dragon_darts",       "dragon_pulse");
        n("breaking_swipe",     "dragon_breath");
        n("clangorous_soul",    "dragon_pulse");
        n("clanging_scales",    "dragon_pulse");
        n("devastating_drake",  "draco_meteor");
        n("core_enforcer",      "dragon_pulse");
        n("eternabeam",         "draco_meteor");
        n("scale_shot",         "dragon_pulse");
        n("dual_wingbeat",      "aerial_ace");
        n("jungle_healing",     "recover");

        // ═══════════════════════════════════════════════════════════════════
        // POISON
        // ═══════════════════════════════════════════════════════════════════
        n("poison_sting",       "poison_sting");
        n("sludge",             "poison_strike");
        n("sludge_bomb",        "sludge_bomb");
        n("sludge_wave",        "sludge_bomb");
        n("toxic",              "toxic");
        n("toxic_spikes",       "toxic");
        n("venoshock",          "sludge_bomb");
        n("acid",               "poison_sting");
        n("smog",               "poison_sting");
        n("cross_poison",       "poison_strike");
        n("gunk_shot",          "sludge_bomb");
        n("poison_jab",         "poison_strike");
        n("poison_fang",        "poison_sting");
        n("twineedle",          "poison_sting");
        n("barb_barrage",       "sludge_bomb");
        n("acid_spray",         "sludge_bomb");
        n("clear_smog",         "poison_sting");
        n("baneful_bunker",     "toxic");
        n("belch",              "sludge_bomb");
        n("coil",               "poison_strike");
        n("mortal_spin",        "poison_strike");
        n("noxious_torque",     "sludge_bomb");
        n("double_shock",       "thunderbolt"); // Pawmi move, actually Electric

        // ═══════════════════════════════════════════════════════════════════
        // ROCK / GROUND
        // ═══════════════════════════════════════════════════════════════════
        n("rock_throw",         "rock_throw");
        n("rock_slide",         "rock_slide");
        n("stone_edge",         "stone_edge");
        n("rock_blast",         "rock_throw");
        n("stealth_rock",       "rock_throw");
        n("rock_wrecker",       "stone_edge");
        n("ancient_power",      "rock_slide");
        n("power_gem",          "rock_slide");
        n("smack_down",         "rock_throw");
        n("accelerock",         "rock_throw");
        n("tar_shot",           "rock_throw");
        n("meteor_beam",        "stone_edge");
        n("diamond_storm",      "stone_edge");
        n("earthquake",         "earthquake");
        n("magnitude",          "earthquake");
        n("bulldoze",           "earthquake");
        n("earth_power",        "earthquake");
        n("fissure",            "earthquake");
        n("dig",                "earthquake");
        n("mud_shot",           "rock_throw");
        n("sand_tomb",          "rock_slide");
        n("bone_club",          "rock_throw");
        n("bonemerang",         "rock_throw");
        n("bone_rush",          "rock_throw");
        n("shore_up",           "recover");
        n("poltergeist",        "shadow_ball");
        n("sandsear_storm",     "earthquake");
        n("high_horsepower",    "earthquake");
        n("stomping_tantrum",   "earthquake");
        n("tectonic_rage",      "earthquake");

        // ═══════════════════════════════════════════════════════════════════
        // FIGHTING
        // ═══════════════════════════════════════════════════════════════════
        n("mach_punch",         "mach_punch");
        n("close_combat",       "close_combat");
        n("focus_blast",        "focus_blast");
        n("dynamic_punch",      "close_combat");
        n("cross_chop",         "close_combat");
        n("aura_sphere",        "focus_blast");
        n("counter",            "close_combat");
        n("superpower",         "close_combat");
        n("drain_punch",        "leech_seed");
        n("sky_uppercut",       "mach_punch");
        n("brick_break",        "close_combat");
        n("submission",         "close_combat");
        n("low_kick",           "mach_punch");
        n("karate_chop",        "mach_punch");
        n("seismic_toss",       "seismic_slam");
        n("hi_jump_kick",       "close_combat");
        n("high_jump_kick",     "close_combat");
        n("vacuum_wave",        "mach_punch");
        n("final_gambit",       "close_combat");
        n("vital_throw",        "close_combat");
        n("reversal",           "close_combat");
        n("circle_throw",       "seismic_slam");
        n("storm_throw",        "close_combat");
        n("sacred_sword",       "close_combat");
        n("secret_sword",       "focus_blast");
        n("triple_kick",        "mach_punch");
        n("low_sweep",          "mach_punch");
        n("flying_press",       "close_combat");
        n("mat_block",          "close_combat");
        n("all_out_pummeling",  "close_combat");
        n("malicious_moonsault","close_combat");
        n("wicked_torque",      "close_combat");

        // ═══════════════════════════════════════════════════════════════════
        // FLYING
        // ═══════════════════════════════════════════════════════════════════
        n("gust",               "gust");
        n("wing_attack",        "aerial_ace");
        n("aerial_ace",         "aerial_ace");
        n("air_slash",          "aerial_ace");
        n("brave_bird",         "close_combat");
        n("hurricane",          "aerial_strike");
        n("drill_peck",         "aerial_ace");
        n("sky_attack",         "aerial_strike");
        n("fly",                "gust");
        n("peck",               "gust");
        n("pluck",              "aerial_ace");
        n("bounce",             "aerial_ace");
        n("feather_dance",      "gust");
        n("mirror_move",        "aerial_ace");
        n("aerial_ace",         "aerial_ace");
        n("oblivion_wing",      "aerial_ace");
        n("supersonic_skystrike", "aerial_strike");
        n("floaty_fall",        "aerial_ace");
        n("dual_wingbeat",      "aerial_ace");
        n("bleakwind_storm",    "aerial_strike");
        n("victory_dance",      "future_sight");

        // ═══════════════════════════════════════════════════════════════════
        // BUG
        // ═══════════════════════════════════════════════════════════════════
        n("bug_buzz",           "aerial_strike");
        n("x_scissor",          "aerial_ace");
        n("signal_beam",        "psybeam");
        n("leech_life",         "leech_seed");
        n("silver_wind",        "aerial_strike");
        n("quiver_dance",       "future_sight");
        n("attack_order",       "aerial_ace");
        n("pin_missile",        "aerial_ace");
        n("fell_stinger",       "poison_sting");
        n("lunge",              "aerial_ace");
        n("first_impression",   "razor_leaf");
        n("pollen_puff",        "energy_ball");
        n("struggle_bug",       "aerial_strike");
        n("infestation",        "poison_sting");
        n("bug_bite",           "aerial_ace");
        n("skitter_smack",      "aerial_ace");
        n("silk_trap",          "vine_whip");

        // ═══════════════════════════════════════════════════════════════════
        // STEEL
        // ═══════════════════════════════════════════════════════════════════
        n("iron_tail",          "iron_tail");
        n("flash_cannon",       "flash_cannon");
        n("meteor_mash",        "iron_tail");
        n("iron_head",          "iron_tail");
        n("steel_wing",         "iron_strike");
        n("bullet_punch",       "mach_punch");
        n("gyro_ball",          "iron_tail");
        n("doom_desire",        "draco_meteor");
        n("sunsteel_strike",    "iron_strike");
        n("smart_strike",       "iron_tail");
        n("iron_defense",       "iron_defense");
        n("shift_gear",         "iron_strike");
        n("magnet_bomb",        "iron_tail");
        n("mirror_shot",        "flash_cannon");
        n("steel_beam",         "flash_cannon");
        n("anchor_shot",        "iron_tail");
        n("double_iron_bash",   "iron_tail");
        n("steel_roller",       "iron_tail");
        n("behemoth_bash",      "close_combat");
        n("behemoth_blade",     "iron_strike");
        n("make_it_rain",       "flash_cannon");

        // ═══════════════════════════════════════════════════════════════════
        // FAIRY
        // ═══════════════════════════════════════════════════════════════════
        n("moonblast",          "moonblast");
        n("dazzling_gleam",     "dazzling_gleam");
        n("fairy_wind",         "gust");
        n("play_rough",         "aerial_ace");
        n("disarming_voice",    "confusion");
        n("draining_kiss",      "leech_seed");
        n("moongeist_beam",     "moonblast");
        n("twinkle_tackle",     "moonblast");
        n("misty_explosion",    "dazzling_gleam");
        n("sparkly_swirl",      "dazzling_gleam");
        n("spirit_break",       "moonblast");
        n("strange_steam",      "dazzling_gleam");
        n("baby_doll_eyes",     "confusion");
        n("aromatic_mist",      "future_sight");
        n("charm",              "confusion");
        n("light_of_ruin",      "moonblast");
        n("fleur_cannon",       "moonblast");
        n("decorate",           "future_sight");
        n("misty_terrain",      "dazzling_gleam");

        // ═══════════════════════════════════════════════════════════════════
        // NORMAL (physical + special)
        // ═══════════════════════════════════════════════════════════════════
        n("tackle",             "tackle");
        n("pound",              "tackle");
        n("scratch",            "tackle");
        n("slam",               "tackle");
        n("stomp",              "seismic_slam");
        n("headbutt",           "tackle");
        n("body_slam",          "seismic_slam");
        n("strength",           "close_combat");
        n("hyper_beam",         "hyper_beam");
        n("giga_impact",        "hyper_beam");
        n("double_edge",        "close_combat");
        n("take_down",          "tackle");
        n("quick_attack",       "quick_attack");
        n("extreme_speed",      "mach_punch");
        n("mega_punch",         "close_combat");
        n("return",             "tackle");
        n("frustration",        "tackle");
        n("skull_bash",         "close_combat");
        n("double_slap",        "tackle");
        n("comet_punch",        "tackle");
        n("fury_swipes",        "tackle");
        n("fury_attack",        "tackle");
        n("thrash",             "outrage");
        n("last_resort",        "close_combat");
        n("hyper_fang",         "close_combat");
        n("swift",              "aerial_ace");
        n("tri_attack",         "tri_attack");
        n("explosion",          "explosion");
        n("self_destruct",      "explosion");
        n("egg_bomb",           "fire_blast");
        n("boomburst",          "aerial_strike");
        n("hyper_voice",        "aerial_strike");
        n("echoed_voice",       "confusion");
        n("round",              "confusion");
        n("work_up",            "future_sight");
        n("razor_wind",         "aerial_ace");
        n("wrap",               "vine_whip");
        n("bind",               "vine_whip");
        n("constrict",          "vine_whip");
        n("vice_grip",          "iron_tail");
        n("guillotine",         "stone_edge");
        n("horn_attack",        "tackle");
        n("horn_drill",         "stone_edge");
        n("mega_kick",          "close_combat");
        n("jump_kick",          "close_combat");
        n("rolling_kick",       "mach_punch");
        n("spin_out",           "aerial_ace");
        n("facade",             "tackle");
        n("secret_power",       "tackle");
        n("retaliate",          "close_combat");
        n("body_press",         "close_combat");
        n("scale_shot",         "aerial_ace");
        n("population_bomb",    "tackle");

        // ═══════════════════════════════════════════════════════════════════
        // SPECIAL STATUS → these produce utility spells
        // ═══════════════════════════════════════════════════════════════════
        n("rest",               "rest");
        n("recover",            "recover");
        n("morning_sun",        "recover");
        n("synthesis",          "recover");
        n("moonlight",          "recover");
        n("roost",              "recover");
        n("slack_off",          "recover");
        n("soft_boiled",        "recover");
        n("milk_drink",         "recover");
        n("wish",               "recover");
        n("heal_pulse",         "recover");
        n("oblivion_wing",      "recover");
        n("parabolic_charge",   "recover");
        n("strength_sap",       "leech_seed");
    }

    private static void n(String moveName, String spellPath) {
        NAME_MAP.put(moveName, ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, spellPath));
    }

    /**
     * Returns all spells for the given Pokemon's current moveset (used by CompanionSpellGoal).
     */
    public static List<ResourceLocation> getSpellsForPokemon(PokemonEntity pokemon) {
        List<ResourceLocation> result = new ArrayList<>();
        for (Move move : pokemon.getPokemon().getMoveSet().getMoves()) {
            if (move == null) continue;
            toSpell(move).ifPresent(result::add);
        }
        return result;
    }

    /**
     * @param move the Cobblemon Move object
     * @return matching tensura spell, or empty if the move should be skipped
     */
    public static Optional<ResourceLocation> toSpell(Move move) {
        String name = move.getName().toLowerCase()
                .replace(" ", "_")
                .replace("-", "_")
                .replace("'", "");

        // 1. Exact name match
        ResourceLocation byName = NAME_MAP.get(name);
        if (byName != null) return Optional.of(byName);

        // 2. Status moves not in NAME_MAP → skip
        if ("status".equalsIgnoreCase(move.getDamageCategory().getName())) {
            return Optional.empty();
        }

        // 3. Type fallback for unmapped damage moves
        return typeFallback(move);
    }

    private static Optional<ResourceLocation> typeFallback(Move move) {
        String type  = move.getType().getName().toLowerCase();
        double power = move.getPower();

        String spellPath = switch (type) {
            case "electric"           -> power >= 100 ? "thunder"        : "thunderbolt";
            case "fire"               -> power >= 100 ? "fire_blast"     : "flamethrower";
            case "water"              -> power >= 100 ? "hydro_pump"     : "water_pulse";
            case "ice"                -> power >= 100 ? "blizzard"       : "ice_beam";
            case "ghost", "dark"      -> power >= 100 ? "dark_pulse"     : "shadow_ball";
            case "psychic"            -> power >= 100 ? "psychic_blast"  : "psychic";
            case "dragon"             -> power >= 100 ? "draco_meteor"   : "dragon_pulse";
            case "poison"             -> power >= 80  ? "sludge_bomb"    : "poison_sting";
            case "grass", "fairy"     -> power >= 80  ? "solar_beam"     : "energy_ball";
            case "steel", "rock"      -> power >= 80  ? "stone_edge"     : "iron_strike";
            case "ground", "fighting" -> power >= 100 ? "earthquake"     : "seismic_slam";
            case "flying", "bug"      -> power >= 80  ? "aerial_strike"  : "aerial_ace";
            default                   -> power >= 100 ? "hyper_beam"     : "tackle";
        };

        return Optional.ofNullable(spellPath == null ? null : ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, spellPath));
    }
}
