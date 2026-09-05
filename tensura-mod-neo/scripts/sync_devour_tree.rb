#!/usr/bin/env ruby

require "json"

ROOT = File.expand_path("..", __dir__)
DEVOUR_DIR = File.join(ROOT,
  "src/main/resources/data/tensura/puffish_skills/categories/devour")

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

def read_json(name)
  JSON.parse(File.read(File.join(DEVOUR_DIR, name)))
end

def write_json(name, value)
  File.write(File.join(DEVOUR_DIR, name), JSON.pretty_generate(value) + "\n")
end

def title_for(spell)
  spell.split("_").map(&:capitalize).join(" ")
end

definitions = read_json("definitions.json")
ordered_definitions = { "devour_core" => definitions.fetch("devour_core") }

RAYS.each do |type, spells|
  spells.each do |spell|
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