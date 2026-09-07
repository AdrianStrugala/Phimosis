#!/usr/bin/env ruby

require "json"

ROOT = File.expand_path("..", __dir__)
DEVOUR_DIR = File.join(ROOT,
  "src/main/resources/data/tensura/puffish_skills/categories/devour")
SPELL_DIR = File.join(ROOT, "src/main/resources/data/tensura/spells")
SPELL_ITEM_FILE = File.join(ROOT, "src/main/java/com/tensura/item/SpellItem.java")

spell_item = File.read(SPELL_ITEM_FILE)
icon_block = spell_item[/CUSTOM_ICON_ORDER = java\.util\.List\.of\((.*?)\n    \);/m, 1]
abort "CUSTOM_ICON_ORDER not found" unless icon_block
CUSTOM_ICONS = icon_block.scan(/"([a-z0-9_]+)"/).flatten.freeze

RAYS = {
  normal: %w[tackle quick_attack swift tri_attack recover hyper_voice hyper_beam],
  fire: %w[ember flame_charge flamethrower fire_spin fire_blast fire_punch overheat],
  water: %w[water_gun aqua_jet bubble_beam whirlpool surf hydro_pump],
  electric: %w[thunder_shock spark electro_ball discharge thunder_punch thunder volt_tackle],
  grass: %w[vine_whip leech_seed razor_leaf leaf_blade giga_drain petal_blizzard solar_beam],
  ice: %w[powder_snow ice_shard icy_wind ice_beam ice_punch aurora_veil blizzard],
  fighting: %w[mach_punch force_palm drain_punch seismic_toss close_combat focus_blast],
  poison: %w[poison_sting acid_spray venoshock toxic_spikes toxic],
  ground: %w[mud_shot bulldoze dig earth_power earthquake],
  flying: %w[gust air_cutter aerial_ace tailwind hurricane aerial_strike],
  psychic: %w[confusion psybeam psycho_cut psychic rest psychic_blast future_sight],
  bug: %w[string_shot pin_missile u_turn x_scissor bug_buzz],
  rock: %w[rock_throw smack_down rock_slide stone_edge],
  ghost: %w[lick night_shade shadow_claw shadow_sneak shadow_ball hex],
  dragon: %w[dragon_breath dragon_claw dragon_tail dragon_pulse draco_meteor outrage],
  dark: %w[bite crunch snarl dark_pulse sucker_punch foul_play],
  steel: %w[bullet_punch metal_claw iron_head iron_defense flash_cannon],
  fairy: %w[fairy_wind draining_kiss charm dazzling_gleam moonblast]
}.freeze

EFFECT_NAMES = {
  "minecraft:darkness" => "blinds", "minecraft:nausea" => "disorients",
  "minecraft:poison" => "poisons", "minecraft:slowness" => "slows",
  "minecraft:speed" => "hastens", "minecraft:weakness" => "weakens",
  "minecraft:wither" => "withers", "tensura:asleep" => "puts enemies to sleep",
  "tensura:frozen" => "chills", "tensura:paralyzed" => "paralyzes"
}.freeze

DESCRIPTION_OVERRIDES = {
  "toxic_spikes" => "Scatter poisonous spikes across the ground. The first trigger poisons; repeated triggers turn the poison Toxic.",
  "u_turn" => "Dash through an enemy with Bug energy, then return to where you started.",
  "future_sight" => "Mark an area with Psychic energy. After a short delay, it erupts and catches anyone still inside.",
  "sucker_punch" => "Prepare a Dark counter. If an enemy attacks, blink behind them and strike first.",
  "rest" => "Fall asleep to fully restore your health and clear harmful effects.",
  "recover" => "Focus your energy to restore a large amount of health.",
  "aurora_veil" => "Raise an icy aurora around you that protects you and nearby allies from damage.",
  "tailwind" => "Summon a guiding wind that makes you and nearby allies move faster.",
  "tri_attack" => "Fire three homing blasts that may burn, chill, or paralyze their target.",
  "ember" => "Flick a small ember at an enemy and set them ablaze.",
  "electro_ball" => "Launch a crackling Electric orb that hits harder when you outpace your target.",
  "flamethrower" => "Hold to breathe a stream of fire. It starts recharging when you release it.",
  "hydro_pump" => "Hold to fire a powerful Water stream that repeatedly pushes enemies back. It starts recharging when released.",
  "ice_shard" => "Hurl a razor-sharp shard of Ice from long range that may slow enemies.",
  "rock_throw" => "Hurl a heavy rock at an enemy from long range.",
  "bubble_beam" => "Launch a stream of bubbles from long range that may slow enemies.",
  "solar_beam" => "Launch a devastating blast of Nature energy from long range.",
  "dragon_pulse" => "Launch a concentrated blast of Dragon energy from long range.",
  "ice_beam" => "Hold to channel a piercing Ice beam. Soaked enemies freeze on hit, and recharging starts when released.",
  "psybeam" => "Hold to channel a disorienting Psychic beam. It starts recharging when you release it.",
  "dragon_breath" => "Hold to breathe Dragon energy indefinitely without recharging. Your movement is heavily slowed while channeling.",
  "moonblast" => "Launch a Fairy blast that explodes across an area and weakens enemies. Takes a short moment to cast.",
  "dazzling_gleam" => "Release a wide burst of Fairy light around you. Also clears Poison or Chill from you.",
  "draining_kiss" => "Send a homing Fairy kiss at an enemy and restore health from the damage dealt.",
  "acid_spray" => "Spray a forward cone of acid that poisons enemies and leaves them Exposed.",
  "bite" => "Lunge into one enemy. Striking from behind briefly staggers them.",
  "bullet_punch" => "Flash forward with a Steel punch that ignores two points of armor.",
  "crunch" => "Lunge with crushing Dark jaws that sometimes leave the target Exposed.",
  "dragon_claw" => "Sweep Dragon claws in front of you and launch struck enemies backward.",
  "dragon_tail" => "Swing a wide Dragon tail that knocks enemies away and interrupts casting.",
  "drain_punch" => "Punch a nearby enemy and restore health equal to two fifths of the damage dealt.",
  "fire_punch" => "Swing a fiery fist in front of you and set struck enemies ablaze.",
  "flame_charge" => "Dash forward in flame. Hitting an enemy briefly increases your movement speed.",
  "force_palm" => "Release a close-range palm strike that knocks enemies back and paralyzes nearby targets.",
  "giga_drain" => "Briefly channel Nature energy from one enemy and heal for three fifths of the damage dealt.",
  "ice_punch" => "Swing an icy fist that heavily chills enemies and freezes soaked targets.",
  "icy_wind" => "Sweep freezing wind forward to heavily chill and push enemies back.",
  "iron_head" => "Charge one enemy under a Steel shell and stagger them on impact.",
  "lick" => "Sweep a spectral tongue at close range to paralyze and disorient enemies.",
  "metal_claw" => "Strike twice with Steel claws. Landing both hits grants four Guard.",
  "psycho_cut" => "Cut the area ahead with Psychic energy that ignores two points of armor.",
  "seismic_toss" => "Grab a nearby enemy and throw it toward your aim. Bosses are staggered instead.",
  "shadow_claw" => "Rend the area ahead with spectral claws that sometimes leave enemies Exposed.",
  "smack_down" => "Hurl a homing rock that drags its target down and briefly blocks flight.",
  "snarl" => "Unleash a Dark sound wave that briefly reduces enemy special spell damage.",
  "spark" => "Dash with Electric energy. A soaked target conducts damage to another nearby enemy.",
  "thunder_punch" => "Swing an Electric fist that paralyzes soaked enemies without fail.",
  "thunder_shock" => "Fire a quick Electric bolt that can leap to one nearby soaked enemy.",
  "venoshock" => "Launch a Poison orb that explodes and deals double damage to poisoned or Toxic enemies."
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

def impact_text(impact)
  self_effect = impact["recipient"] == "caster"
  case impact["type"]
  when "damage" then nil
  when "speed_scaled_damage" then "Deals more damage when you outpace your target"
  when "status_effect"
    verb = EFFECT_NAMES.fetch(impact["effect"], "afflicts")
    subject = self_effect ? "you" : "enemies"
    impact.fetch("chance", 1).to_f < 1 ? "sometimes #{verb} #{subject}" : "#{verb} #{subject}"
  when "fire"
    impact.fetch("chance", 1).to_f < 1 ? "sometimes sets enemies ablaze" : "sets enemies ablaze"
  when "knockback" then "knocks enemies back"
  when "pull" then "pulls enemies inward"
  when "heal", "heal_fraction" then "restores your health"
  when "heal_damage_fraction" then "restores health from damage dealt"
  when "full_heal" then "fully restores your health"
  when "recoil" then "deals recoil damage to you"
  when "cleanse" then "clears harmful effects from you"
  when "cleanse_one"
    "clears Poison or Chill from you"
  when "interrupt_cast" then "interrupts casting"
  when "wet" then "soaks enemies"
  when "freeze_if_wet" then "chills soaked enemies"
  when "paralyze_if_wet" then "paralyzes soaked enemies"
  when "damage_reduction" then "reduces incoming damage"
  when "expose"
    effect = self_effect ? "leaves you Exposed afterward" : "leaves enemies Exposed"
    impact.fetch("chance", 1).to_f < 1 ? "sometimes #{effect}" : effect
  when "guard" then "blocks the next hit"
  when "tri_status" then "may burn, chill, or paralyze"
  end
end

def range_phrase(definition)
  targeting = definition.fetch("targeting", {})
  range = targeting["range"].to_f
  return "" if range <= 0
  return " at close range" if range <= 7
  return " at medium range" if range <= 15

  " from long range"
end

def with_article(type, noun)
  article = type.match?(/\A[aeiou]/i) ? "an" : "a"
  "#{article} #{type} #{noun}"
end

def spell_action(definition)
  delivery = definition.fetch("delivery", {})
  type = title_for(definition.fetch("pokemon_type", definition["school"]).to_s)
  range = range_phrase(definition)
  area = definition.dig("targeting", "radius").to_f > 1.5
  many = delivery["projectile_count"].to_i > 1
  case delivery["type"]
  when "projectile"
    return "Launch a volley of #{type} projectiles#{range}" if many
    return "Launch #{with_article(type, "blast")}#{range} that explodes on impact" if area
    "Launch #{with_article(type, "projectile")}#{range}"
  when "beam" then "Unleash a piercing #{type} beam#{range}"
  when "channel_beam" then "Channel a sustained #{type} beam#{range}"
  when "channel_cone" then "Channel a wide cone of #{type} energy#{range}"
  when "cloud" then "Create a lingering cloud of #{type} energy#{range}"
  when "counter" then "Take a counter stance and punish the next attacker"
  when "dash" then "Dash forward in a burst of #{type} energy#{range}"
  when "delayed" then "Mark an enemy for a delayed #{type} strike#{range}"
  when "delayed_area" then "Mark an area for a delayed #{type} blast#{range}"
  when "melee_combo" then "Rush an enemy with a rapid #{type} combo#{range}"
  when "meteor" then "Call down a barrage of #{type} strikes#{range}"
  when "moving_zone" then "Summon a roaming storm of #{type} energy"
  when "protective_aura" then "Wrap yourself and nearby allies in #{with_article(type, "barrier")}"
  when "ricochet_beam" then "Fire #{with_article(type, "beam")} that leaps to another enemy#{range}"
  when "self" then "Focus #{type} energy within yourself"
  when "teleport_strike" then "Blink behind an enemy and strike with #{type} energy#{range}"
  when "trap" then "Scatter #{with_article(type, "trap")}#{range}"
  when "vortex" then "Create a swirling #{type} vortex#{range}"
  when "wave" then "Send a surging wave of #{type} energy forward"
  else
    definition.dig("targeting", "type") == "self" ?
      "Empower yourself with #{type} energy" : "Strike enemies with a burst of #{type} energy#{range}"
  end
end

def spell_description(spell, definition)
  return DESCRIPTION_OVERRIDES[spell] if DESCRIPTION_OVERRIDES.key?(spell)

  sentences = ["#{spell_action(definition)}."]
  effects = Array(definition["impact"]).map { |impact| impact_text(impact) }.compact.uniq
  unless effects.empty?
    effect_sentence = if effects.size == 1
      effects.first
    elsif effects.size == 2
      effects.join(" and ")
    else
      "#{effects[0...-1].join(", ")}, and #{effects.last}"
    end
    effect_sentence = effect_sentence.sub(/\A./) { |character| character.upcase }
    sentences << "#{effect_sentence}."
  end
  sentences << "Takes a short moment to cast." if definition["cast_time_ticks"].to_i > 5
  sentences << "Can be used twice before recharging." if definition["charges"].to_i > 1
  sentences.join(" ")
end

definitions = read_json("definitions.json")
ordered_definitions = { "devour_core" => definitions.fetch("devour_core") }

RAYS.each do |type, spells|
  spells.each do |spell|
    spell_definition = JSON.parse(File.read(File.join(SPELL_DIR, "#{spell}.json")))
    description = spell_description(spell, spell_definition)
    owned_id = "#{spell}_owned"
    owned = definitions.fetch(owned_id) do
      {
        "title" => title_for(spell),
        "icon" => {
          "type" => "texture",
          "data" => { "texture" => "tensura:textures/gui/blank.png" }
        },
        "size" => 0.1,
        "rewards" => [],
        "cost" => 0,
        "frame" => {
          "type" => "texture",
          "data" => {
            "unlocked" => "tensura:textures/gui/blank.png",
            "available" => "tensura:textures/gui/blank.png",
            "locked" => "tensura:textures/gui/blank.png",
            "excluded" => "tensura:textures/gui/blank.png"
          }
        }
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
        "cost" => 0,
        "frame" => {
          "type" => "texture",
          "data" => {
            "locked" => "tensura:textures/gui/unowned.png",
            "available" => "tensura:textures/gui/owned.png",
            "unlocked" => "tensura:textures/gui/owned.png",
            "excluded" => "tensura:textures/gui/unowned.png"
          }
        }
      }
    end
    ordered_definitions[spell]["title"] = title_for(spell)
    ordered_definitions[spell]["description"] = description
    if CUSTOM_ICONS.include?(spell)
      ordered_definitions[spell]["icon"] = {
        "type" => "item",
        "data" => { "item" => "tensura:spell_icon_#{spell}" }
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
    skills[owned_id] = {
      "x" => x,
      "y" => y,
      "definition" => owned_id,
      "root" => true
    }
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