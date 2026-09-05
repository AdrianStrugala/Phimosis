#!/usr/bin/env ruby

require "digest"
require "json"

ROOT = File.expand_path("..", __dir__)
JAVA_ICON_FILE = File.join(ROOT, "src/main/java/com/tensura/item/SpellItem.java")
MAPPER_FILE = File.join(ROOT, "src/main/java/com/tensura/spell/CobblemonMoveMapper.java")
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
definitions = JSON.parse(File.read(File.join(DEVOUR_DIR, "definitions.json")))
skills = JSON.parse(File.read(File.join(DEVOUR_DIR, "skills.json")))
connections = JSON.parse(File.read(File.join(DEVOUR_DIR, "connections.json")))
casts = {}
texture_hashes = {}

spells.each do |spell|
  definition_path = File.join(SPELL_DIR, "#{spell}.json")
  fail_validation("missing definition for #{spell}") unless File.exist?(definition_path)
  definition = JSON.parse(File.read(definition_path))

  fail_validation("#{spell} has no pokemon_type") if definition["pokemon_type"].to_s.empty?
  fail_validation("#{spell} has no explicit power") unless definition.key?("power")
  fail_validation("#{spell} has no delivery") if definition.dig("delivery", "type").to_s.empty?
  fail_validation("#{spell} has no impact") if Array(definition["impact"]).empty?
  fail_validation("#{spell} has no sound") unless definition.fetch("sound", {}).values.any? do |value|
    value.is_a?(String) && !value.empty?
  end

  visual = definition.fetch("visual", {})
  cast = visual["cast_animation"].to_s
  fail_validation("#{spell} uses placeholder cast_point") if cast.empty? || cast == "cast_point"
  fail_validation("#{spell} shares cast #{cast} with #{casts[cast]}") if casts.key?(cast)
  casts[cast] = spell
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

ray_count = connections.count { |edge| edge.first == "devour_core" }
fail_validation("expected 18 Devour rays, got #{ray_count}") unless ray_count == 18

puts "Done spells: #{spells.size}"
puts "Unique casts: #{casts.size}"
puts "Unique 32x32 icons: #{texture_hashes.size}"
puts "Devour rays: #{ray_count}"
puts "All done-done checks passed."