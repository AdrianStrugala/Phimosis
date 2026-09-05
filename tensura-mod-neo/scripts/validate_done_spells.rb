#!/usr/bin/env ruby

require "digest"
require "json"

ROOT = File.expand_path("..", __dir__)
JAVA_ICON_FILE = File.join(ROOT, "src/main/java/com/tensura/item/SpellItem.java")
MAPPER_FILE = File.join(ROOT, "src/main/java/com/tensura/spell/CobblemonMoveMapper.java")
EXECUTOR_FILE = File.join(ROOT, "src/main/java/com/tensura/engine/SpellExecutor.java")
MOVEMENT_FILE = File.join(ROOT, "src/main/java/com/tensura/event/SpellMovementController.java")
PROJECTILE_FILE = File.join(ROOT, "src/main/java/com/tensura/entity/SpellProjectile.java")
RUNTIME_FILE = File.join(ROOT, "src/main/java/com/tensura/event/SpellRuntimeController.java")
VFX_FILE = File.join(ROOT, "src/main/java/com/tensura/client/ProgrammaticSpellFx.java")
SPELL_DIR = File.join(ROOT, "src/main/resources/data/tensura/spells")
MODEL_DIR = File.join(ROOT, "src/main/resources/assets/tensura/models/item")
TEXTURE_DIR = File.join(ROOT, "src/main/resources/assets/tensura/textures/item/spell")
DEVOUR_DIR = File.join(ROOT,
  "src/main/resources/data/tensura/puffish_skills/categories/devour")

def fail_validation(message)
  warn "Validation failed: #{message}"
  exit 1
end

java = File.read(JAVA_ICON_FILE)
icon_block = java[/CUSTOM_ICON_ORDER = java\.util\.List\.of\((.*?)\n    \);/m, 1]
fail_validation("CUSTOM_ICON_ORDER not found") unless icon_block
spells = icon_block.scan(/"([a-z0-9_]+)"/).flatten
fail_validation("expected 50 custom icon spells, got #{spells.size}") unless spells.size == 50
fail_validation("duplicate custom icon spell") unless spells.uniq.size == spells.size

mapper = File.read(MAPPER_FILE)
executor = File.read(EXECUTOR_FILE)
movement = File.read(MOVEMENT_FILE)
projectile = File.read(PROJECTILE_FILE)
runtime = File.read(RUNTIME_FILE)
vfx = File.read(VFX_FILE)
definitions = JSON.parse(File.read(File.join(DEVOUR_DIR, "definitions.json")))
skills = JSON.parse(File.read(File.join(DEVOUR_DIR, "skills.json")))
connections = JSON.parse(File.read(File.join(DEVOUR_DIR, "connections.json")))
casts = {}
cast_profiles = {}
texture_hashes = {}

vfx.scan(/case ((?:"[^"]+"(?:,\s*)?)+) -> new CastProfile\(CastFamily\.([A-Z]+),\s*(\d+)\);/) do |styles, family, variant|
  styles.scan(/"([^"]+)"/).flatten.each do |style|
    fail_validation("duplicate VFX profile for #{style}") if cast_profiles.key?(style)
    cast_profiles[style] = [family, variant.to_i]
  end
end

spells.each do |spell|
  definition_path = File.join(SPELL_DIR, "#{spell}.json")
  fail_validation("missing definition for #{spell}") unless File.exist?(definition_path)
  definition = JSON.parse(File.read(definition_path))

  fail_validation("#{spell} has no pokemon_type") if definition["pokemon_type"].to_s.empty?
  fail_validation("#{spell} has no explicit power") unless definition.key?("power")
  fail_validation("#{spell} has no delivery") if definition.dig("delivery", "type").to_s.empty?
  controller_delivery = definition.dig("delivery", "type") == "trap"
  fail_validation("#{spell} has no impact or controller mechanic") if
    Array(definition["impact"]).empty? && !controller_delivery
  fail_validation("#{spell} has no sound") unless definition.fetch("sound", {}).values.any? do |value|
    value.is_a?(String) && !value.empty?
  end

  visual = definition.fetch("visual", {})
  cast = visual["cast_animation"].to_s
  fail_validation("#{spell} uses placeholder cast_point") if cast.empty? || cast == "cast_point"
  fail_validation("#{spell} shares cast #{cast} with #{casts[cast]}") if casts.key?(cast)
  casts[cast] = spell
  fail_validation("#{spell} cast #{cast} has no explicit geometry profile") unless
    cast_profiles.key?(cast)
  populated_vfx = visual.values.count { |value| value.is_a?(String) && !value.empty? }
  fail_validation("#{spell} has fewer than three VFX phases") if populated_vfx < 3

  self_mapping = /n\("#{Regexp.escape(spell)}",\s*"#{Regexp.escape(spell)}"\);/
  fail_validation("#{spell} has no direct Cobblemon mapping") unless mapper.match?(self_mapping)

  owned_id = "#{spell}_owned"
  fail_validation("#{spell} has no Devour owned node") unless definitions.key?(owned_id)
  fail_validation("#{spell} has no Devour dispenser") unless definitions.key?(spell)
  expected_icon = "tensura:spell_icon_#{spell}"
  actual_icon = definitions.dig(owned_id, "icon", "data", "item")
  fail_validation("#{spell} Devour icon is #{actual_icon}") unless actual_icon == expected_icon
  fail_validation("#{spell} Devour pair is not overlaid") unless
    skills.dig(owned_id, "x") == skills.dig(spell, "x") &&
    skills.dig(owned_id, "y") == skills.dig(spell, "y")
  fail_validation("#{spell} owned-to-dispenser edge is missing") unless
    connections.include?([owned_id, spell])

  spell_model = File.join(MODEL_DIR, "spell_#{spell}.json")
  icon_model = File.join(MODEL_DIR, "spell_icon_#{spell}.json")
  texture = File.join(TEXTURE_DIR, "#{spell}.png")
  fail_validation("#{spell} item model is missing") unless File.exist?(spell_model)
  fail_validation("#{spell} tree icon model is missing") unless File.exist?(icon_model)
  fail_validation("#{spell} texture is missing") unless File.exist?(texture)

  png = File.binread(texture)
  valid_png = png.start_with?("\x89PNG\r\n\x1a\n".b) &&
    png.byteslice(16, 8).unpack("NN") == [32, 32]
  fail_validation("#{spell} texture is not a 32x32 PNG") unless valid_png
  hash = Digest::SHA256.hexdigest(png)
  fail_validation("#{spell} duplicates texture for #{texture_hashes[hash]}") if texture_hashes.key?(hash)
  texture_hashes[hash] = spell
end

profile_signatures = casts.keys.to_h { |cast| [cast, cast_profiles.fetch(cast)] }
duplicate_profiles = profile_signatures.group_by { |_cast, profile| profile }
  .select { |_profile, entries| entries.size > 1 }
fail_validation("completed casts share geometry profiles: #{duplicate_profiles}") unless
  duplicate_profiles.empty?
families = profile_signatures.values.map(&:first).uniq
fail_validation("expected 8 cast geometry families, got #{families.size}") unless families.size == 8

spell_definition = ->(name) { JSON.parse(File.read(File.join(SPELL_DIR, "#{name}.json"))) }
impacts = ->(name) { Array(spell_definition.call(name)["impact"]) }

u_turn = spell_definition.call("u_turn")
fail_validation("U-turn is not configured to return") unless
  u_turn.dig("delivery", "return_to_origin") && movement.include?("beginReturn(dash)")
fail_validation("grouped projectiles do not reset target i-frames") unless
  projectile.include?("target.invulnerableTime = 0")

close_combat_penalty = impacts.call("close_combat").find do |impact|
  impact["type"] == "expose" && impact["recipient"] == "caster"
end
fail_validation("Close Combat lacks a final-hit self penalty") unless
  close_combat_penalty&.fetch("final_hit_only", false)
fail_validation("X-Scissor inherits a caster penalty") if
  impacts.call("x_scissor").any? { |impact| impact["recipient"] == "caster" }

toxic_spikes = spell_definition.call("toxic_spikes")
fail_validation("Toxic Spikes duplicates controller escalation") unless
  toxic_spikes.dig("delivery", "type") == "trap" && impacts.call("toxic_spikes").empty? &&
    runtime.include?("MobEffects.POISON") && runtime.include?("TensuraMobEffects.TOXIC")

moonblast = spell_definition.call("moonblast")
fail_validation("Moonblast lacks projectile AoE") unless
  moonblast.dig("delivery", "type") == "projectile" &&
    moonblast.dig("targeting", "radius").to_f > 0.0 &&
    moonblast.dig("targeting", "max_targets").to_i > 1 &&
    projectile.include?("applyProjectileSplash") && executor.include?("entity != directTarget") &&
    executor.include?("applyImpacts(owner, effectCaster, directTarget, def, true, false)")

dazzling_cleanse = impacts.call("dazzling_gleam").find do |impact|
  impact["type"] == "cleanse_one" && impact["recipient"] == "caster"
end
fail_validation("Dazzling Gleam lacks its targeted self-cleanse") unless
  dazzling_cleanse&.fetch("effects", nil) == ["minecraft:poison", "tensura:frozen"] &&
    executor.include?("casterImpactContext")

%w[bug_buzz hyper_voice].each do |spell|
  fail_validation("#{spell} lacks cast interruption") unless
    impacts.call(spell).any? { |impact| impact["type"] == "interrupt_cast" }
end
fail_validation("interrupt impact is not connected to pending casts") unless
  executor.include?("interruptPendingCast") && runtime.include?("PENDING_CASTS.remove(target.getUUID())")

draining_heal = impacts.call("draining_kiss").find do |impact|
  impact["type"] == "heal_damage_fraction" && impact["recipient"] == "caster"
end
fail_validation("Draining Kiss does not heal 75% of actual damage") unless
  draining_heal&.fetch("amount", nil) == 0.75 && executor.include?("healthBefore - target.getHealth()")

ray_count = connections.count { |edge| edge.first == "devour_core" }
fail_validation("expected 18 Devour rays, got #{ray_count}") unless ray_count == 18

puts "Done spells: #{spells.size}"
puts "Unique casts: #{casts.size}"
puts "Unique cast geometry profiles: #{profile_signatures.values.uniq.size}"
puts "Cast geometry families: #{families.size}"
puts "Unique 32x32 icons: #{texture_hashes.size}"
puts "Devour rays: #{ray_count}"
puts "All done-done checks passed."