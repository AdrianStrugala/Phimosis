#!/usr/bin/env ruby

require "json"

ROOT = File.expand_path("..", __dir__)
DEVOUR_DIR = File.join(ROOT,
  "src/main/resources/data/tensura/puffish_skills/categories/devour")
SPELL_DIR = File.join(ROOT, "src/main/resources/data/tensura/spells")

RAYS = {
  normal: %w[tackle quick_attack swift tri_attack recover hyper_voice hyper_beam explosion],
  fire: %w[ember will_o_wisp flamethrower fire_spin fire_blast overheat sacred_fire],
  water: %w[water_gun aqua_jet bubble_beam water_pulse whirlpool surf hydro_pump scald],
  electric: %w[thundershock electro_ball thunderbolt discharge thunder volt_tackle],
  grass: %w[vine_whip leech_seed razor_leaf leaf_blade energy_ball petal_blizzard solar_beam nature_burst],
  ice: %w[powder_snow ice_shard ice_beam frost_nova aurora_veil blizzard],
  fighting: %w[mach_punch close_combat focus_blast seismic_slam],
  poison: %w[poison_sting poison_strike toxic_spikes sludge_bomb toxic],
  ground: %w[mud_shot bulldoze dig earth_power earthquake],
  flying: %w[gust air_cutter aerial_ace tailwind hurricane aerial_strike],
  psychic: %w[confusion psybeam psychic rest psychic_blast future_sight],
  bug: %w[string_shot pin_missile u_turn x_scissor bug_buzz],
  rock: %w[rock_throw rock_slide stone_edge],
  ghost: %w[night_shade shadow_sneak shadow_ball hex],
  dragon: %w[dragon_breath dragon_pulse draco_meteor outrage],
  dark: %w[dark_pulse sucker_punch foul_play],
  steel: %w[iron_tail iron_defense iron_strike flash_cannon],
  fairy: %w[fairy_wind draining_kiss charm dazzling_gleam moonblast]
}.freeze

CUSTOM_ICONS = %w[
  flamethrower surf toxic_spikes close_combat shadow_sneak psybeam volt_tackle
  fire_spin rock_slide recover dark_pulse aqua_jet aurora_veil blizzard
  draco_meteor electro_ball ember future_sight hydro_pump ice_beam iron_defense
  quick_attack rest string_shot sucker_punch thunder tri_attack vine_whip
  whirlpool pin_missile u_turn x_scissor bug_buzz mud_shot bulldoze dig
  earth_power earthquake fairy_wind draining_kiss charm dazzling_gleam moonblast
  gust air_cutter aerial_ace tailwind hurricane swift hyper_voice
].freeze

DELIVERY_NAMES = {
  "beam" => "beam", "channel_beam" => "channeled beam",
  "channel_cone" => "channeled cone", "cloud" => "lingering cloud",
  "counter" => "counter stance", "dash" => "dash",
  "delayed" => "delayed strike", "delayed_area" => "delayed area",
  "explosion" => "caster-centered explosion", "instant" => "instant cast",
  "melee_combo" => "melee combo", "meteor" => "meteor strike",
  "moving_zone" => "moving zone", "projectile" => "projectile",
  "protective_aura" => "protective aura", "ricochet_beam" => "ricochet beam",
  "self" => "self cast", "teleport_strike" => "teleport strike",
  "trap" => "placed trap", "vortex" => "vortex", "wave" => "traveling wave"
}.freeze

EFFECT_NAMES = {
  "minecraft:darkness" => "Darkness", "minecraft:nausea" => "Nausea",
  "minecraft:poison" => "Poison", "minecraft:slowness" => "Slowness",
  "minecraft:speed" => "Speed", "minecraft:weakness" => "Weakness",
  "minecraft:wither" => "Wither", "tensura:asleep" => "Sleep",
  "tensura:frozen" => "Chill", "tensura:paralyzed" => "Paralysis"
}.freeze

def read_json(name)
  JSON.parse(File.read(File.join(DEVOUR_DIR, name)))
end

def write_json(name, value)
  File.write(File.join(DEVOUR_DIR, name), JSON.pretty_generate(value) + "\n")
end

def title_for(spell)
  spell.split("_").map(&:capitalize).join(" ")
end

def number(value)
  numeric = value.to_f
  numeric == numeric.to_i ? numeric.to_i.to_s : format("%.1f", numeric)
end

def seconds(ticks)
  value = ticks.to_f / 20.0
  "#{number(value)}s"
end

def effect_name(effect_id)
  EFFECT_NAMES.fetch(effect_id) do
    effect_id.to_s.split(":").last.to_s.split("_").map(&:capitalize).join(" ")
  end
end

def effect_level(amplifier)
  level = amplifier.to_i + 1
  level > 1 ? " #{level}" : ""
end

def chance_suffix(chance)
  value = chance.nil? ? 1.0 : chance.to_f
  value < 1.0 ? ", #{(value * 100).round}% chance" : ""
end

def recipient_suffix(impact)
  impact["recipient"] == "caster" ? " on self" : ""
end

def impact_text(definition, impact)
  suffix = recipient_suffix(impact)
  case impact["type"]
  when "damage"
    power = definition.fetch("power", -1).to_f
    return "Weapon-scaled damage#{suffix}" if power < 0
    damage = power * impact.fetch("damage_multiplier", 1).to_f
    delivery = definition.fetch("delivery", {})
    repeated = delivery["projectile_count"].to_i > 1 || delivery["combo_hits"].to_i > 1 ||
      %w[channel_beam channel_cone].include?(delivery["type"])
    "#{number(damage)} damage#{repeated ? " per hit" : ""}#{suffix}"
  when "speed_scaled_damage"
    "Speed-scaled damage up to #{number(impact.fetch("amount", definition["power"]))}"
  when "status_effect"
    name = effect_name(impact["effect"])
    "#{name}#{effect_level(impact["amplifier"])} for #{seconds(impact["duration"])}#{chance_suffix(impact["chance"])}#{suffix}"
  when "fire"
    "Burns for #{number(impact["seconds"])}s"
  when "knockback" then "Knockback"
  when "pull" then "Pulls targets"
  when "heal" then "Heals #{number(impact["amount"])} HP#{suffix}"
  when "heal_fraction" then "Heals #{(impact["amount"].to_f * 100).round}% max HP#{suffix}"
  when "heal_damage_fraction" then "Heals #{(impact["amount"].to_f * 100).round}% of damage dealt#{suffix}"
  when "full_heal" then "Fully heals#{suffix}"
  when "recoil" then "#{number(impact["amount"])} recoil damage"
  when "cleanse" then "Removes all harmful effects#{suffix}"
  when "cleanse_one"
    names = Array(impact["effects"]).map { |effect| effect_name(effect) }.join(" or ")
    "Removes one #{names} effect#{suffix}"
  when "interrupt_cast" then "Interrupts casting"
  when "wet" then "Applies Wet for #{seconds(impact["duration"])}"
  when "freeze_if_wet" then "Chills Wet targets for #{seconds(impact["duration"])}"
  when "paralyze_if_wet" then "Paralyzes Wet targets for #{seconds(impact["duration"])}"
  when "damage_reduction" then "Reduces damage by #{(impact["reduction"].to_f * 100).round}%#{suffix}"
  when "expose" then "Exposed for #{seconds(impact["duration"])}#{suffix}"
  when "guard" then "Guards the next hit#{suffix}"
  when "tri_status" then "Randomly burns, paralyzes, or freezes"
  else title_for(impact["type"].to_s)
  end
end

def spell_details(definition)
  targeting = definition.fetch("targeting", {})
  delivery = definition.fetch("delivery", {})
  delivery_name = DELIVERY_NAMES.fetch(delivery["type"], title_for(delivery["type"].to_s))
  targeting_parts = [delivery_name.capitalize]
  targeting_parts << "#{number(targeting["range"])} block range" if targeting["range"].to_f > 0
  targeting_parts << "#{number(targeting["radius"])} block radius" if targeting["radius"].to_f > 0
  targeting_parts << "#{targeting["max_targets"]} targets" if targeting["max_targets"].to_i > 1
  targeting_parts << "1 target" if targeting["max_targets"].to_i == 1
  targeting_parts << "#{delivery["projectile_count"]} projectiles" if delivery["projectile_count"].to_i > 1
  targeting_parts << "#{delivery["combo_hits"]} hits" if delivery["combo_hits"].to_i > 1
  if %w[channel_beam channel_cone].include?(delivery["type"]) &&
      delivery["tick_interval_ticks"].to_i > 0
    pulses = (delivery["duration_ticks"].to_f / delivery["tick_interval_ticks"]).ceil
    targeting_parts << "#{pulses} pulses"
  end
  targeting_parts << "#{seconds(delivery["duration_ticks"])} duration" if delivery["duration_ticks"].to_i > 0

  effects = Array(definition["impact"]).map { |impact| impact_text(definition, impact) }
  effects << "Poison on first trigger; Toxic on repeat" if delivery["type"] == "trap"

  timing = ["#{seconds(definition["cooldown_ticks"])} cooldown"]
  timing << "#{seconds(definition["cast_time_ticks"])} cast" if definition["cast_time_ticks"].to_i > 0
  timing << "#{definition["charges"]} charges" if definition["charges"].to_i > 1
  if definition["charge_recovery_ticks"].to_i > 0
    timing << "#{seconds(definition["charge_recovery_ticks"])} charge recovery"
  end

  "#{targeting_parts.join(", ")}. #{effects.join("; ")}. #{timing.join(", ")}."
end

definitions = read_json("definitions.json")
ordered_definitions = { "devour_core" => definitions.fetch("devour_core") }

RAYS.each do |type, spells|
  spells.each do |spell|
    spell_definition = JSON.parse(File.read(File.join(SPELL_DIR, "#{spell}.json")))
    details = spell_details(spell_definition)
    owned_id = "#{spell}_owned"
    owned = definitions.fetch(owned_id) do
      {
        "title" => title_for(spell),
        "description" => "Absorbed. An absorbed #{type.to_s.capitalize}-type Pokemon ability.",
        "icon" => { "type" => "item", "data" => { "item" => "minecraft:nether_star" } },
        "size" => 1.3,
        "rewards" => [],
        "cost" => 1
      }
    end
    if CUSTOM_ICONS.include?(spell)
      owned["icon"] = {
        "type" => "item",
        "data" => { "item" => "tensura:spell_icon_#{spell}" }
      }
    end
    category = spell_definition["category"].to_s
    category_text = category.empty? ? "spell" : "#{category} spell"
    owned["description"] =
      "Absorbed #{type.to_s.capitalize}-type #{category_text}. #{details}"
    ordered_definitions[owned_id] = owned

    ordered_definitions[spell] = definitions.fetch(spell) do
      {
        "title" => "#{title_for(spell)} Dispenser",
        "description" => "Dispenses another copy of the absorbed #{title_for(spell)} spell.",
        "icon" => { "type" => "item", "data" => { "item" => "minecraft:dispenser" } },
        "size" => 0.7,
        "rewards" => [
          {
            "type" => "puffish_skills:command",
            "data" => { "command" => "tensura devour_recover @s #{spell}" }
          }
        ],
        "cost" => 0
      }
    end
    ordered_definitions[spell]["description"] =
      "Dispenses another copy of #{title_for(spell)}. #{details}"
  end
end

skills = {
  "devour_core" => {
    "x" => 0,
    "y" => 0,
    "definition" => "devour_core",
    "root" => true
  }
}
connections = []

RAYS.values.each_with_index do |spells, ray_index|
  angle = (-90 + ray_index * 20) * Math::PI / 180.0
  previous_owned = nil
  spells.each_with_index do |spell, spell_index|
    radius = 96 + spell_index * 54
    x = (Math.cos(angle) * radius).round
    y = (Math.sin(angle) * radius).round
    owned_id = "#{spell}_owned"
    skills[owned_id] = { "x" => x, "y" => y, "definition" => owned_id }
    skills[spell] = { "x" => x, "y" => y, "definition" => spell }

    connections << [previous_owned || "devour_core", owned_id]
    connections << [owned_id, spell]
    previous_owned = owned_id
  end
end

write_json("definitions.json", ordered_definitions)
write_json("skills.json", skills)
write_json("connections.json", connections)

puts "Synced #{RAYS.size} rays and #{RAYS.values.flatten.size} Devour spell pairs."