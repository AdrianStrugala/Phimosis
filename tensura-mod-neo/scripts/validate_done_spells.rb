#!/usr/bin/env ruby

require "digest"
require "json"

ROOT = File.expand_path("..", __dir__)
JAVA_ICON_FILE = File.join(ROOT, "src/main/java/com/tensura/item/SpellItem.java")
SPELL_CASTING_FILE = File.join(ROOT, "src/main/java/com/tensura/item/SpellCasting.java")
MAPPER_FILE = File.join(ROOT, "src/main/java/com/tensura/spell/CobblemonMoveMapper.java")
EXECUTOR_FILE = File.join(ROOT, "src/main/java/com/tensura/engine/SpellExecutor.java")
IMPACT_APPLIER_FILE = File.join(ROOT, "src/main/java/com/tensura/engine/SpellImpactApplier.java")
FEEDBACK_FILE = File.join(ROOT, "src/main/java/com/tensura/engine/SpellFeedback.java")
PROJECTILE_DELIVERY_FILE = File.join(ROOT,
  "src/main/java/com/tensura/engine/SpellProjectileDelivery.java")
BEAM_DELIVERY_FILE = File.join(ROOT,
  "src/main/java/com/tensura/engine/SpellBeamDelivery.java")
MOVEMENT_FILE = File.join(ROOT, "src/main/java/com/tensura/event/SpellMovementController.java")
PROJECTILE_FILE = File.join(ROOT, "src/main/java/com/tensura/entity/SpellProjectile.java")
RUNTIME_FILE = File.join(ROOT, "src/main/java/com/tensura/event/SpellRuntimeController.java")
VFX_FILE = File.join(ROOT, "src/main/java/com/tensura/client/ProgrammaticSpellFx.java")
ALIASES_FILE = File.join(ROOT, "src/main/java/com/tensura/engine/SpellIdAliases.java")
STATUS_FILE = File.join(ROOT, "src/main/java/com/tensura/event/SpellStatusEvents.java")
DEVOUR_GENERATOR_FILE = File.join(ROOT, "scripts/sync_devour_tree.rb")
SPELL_DIR = File.join(ROOT, "src/main/resources/data/tensura/spells")
MODEL_DIR = File.join(ROOT, "src/main/resources/assets/tensura/models/item")
TEXTURE_DIR = File.join(ROOT, "src/main/resources/assets/tensura/textures/item/spell")
DEVOUR_DIR = File.join(ROOT,
  "src/main/resources/data/tensura/puffish_skills/categories/devour")

DELIVERY_VFX = {
  "projectile" => [%w[projectile], %w[trail], %w[impact]],
  "beam" => [%w[trail], %w[impact]],
  "channel_beam" => [%w[trail], %w[impact]],
  "channel_cone" => [%w[trail], %w[impact]],
  "dash" => [%w[trail], %w[impact]],
  "dash_combo" => [%w[trail], %w[impact]],
  "delayed" => [%w[telegraph], %w[impact]],
  "delayed_area" => [%w[telegraph], %w[impact], %w[aftermath]],
  "moving_zone" => [%w[aftermath], %w[impact]],
  "cloud" => [%w[aftermath], %w[impact]],
  "protective_aura" => [%w[telegraph], %w[aftermath]],
  "zone" => [%w[telegraph], %w[aftermath]],
  "counter" => [%w[telegraph], %w[impact]],
  "wave" => [%w[trail aftermath], %w[impact]],
  "trap" => [%w[telegraph], %w[aftermath]],
  "vortex" => [%w[telegraph], %w[aftermath]],
  "melee_combo" => [%w[impact], %w[aftermath]],
  "teleport_strike" => [%w[trail], %w[impact]],
  "ricochet_beam" => [%w[trail], %w[impact]],
  "arc_strike" => [%w[trail], %w[impact]],
  "grab" => [%w[trail], %w[impact]],
  "meteor" => [%w[projectile], %w[telegraph], %w[impact]],
  "instant" => [%w[impact]],
  "self" => [%w[impact], %w[aftermath]]
}.freeze

LOOP_DELIVERIES = %w[
  channel_beam channel_cone cloud moving_zone protective_aura vortex wave zone
].freeze

def fail_validation(message)
  warn "Validation failed: #{message}"
  exit 1
end

java = File.read(JAVA_ICON_FILE)
icon_block = java[/CUSTOM_ICON_ORDER = java\.util\.List\.of\((.*?)\n    \);/m, 1]
fail_validation("CUSTOM_ICON_ORDER not found") unless icon_block
spells = icon_block.scan(/"([a-z0-9_]+)"/).flatten
fail_validation("expected 110 custom icon spells, got #{spells.size}") unless spells.size == 110
fail_validation("duplicate custom icon spell") unless spells.uniq.size == spells.size

devour_generator = File.read(DEVOUR_GENERATOR_FILE)
ray_block = devour_generator[/RAYS = \{(.*?)\n\}\.freeze/m, 1]
fail_validation("Devour RAYS not found") unless ray_block
ray_spells = ray_block.scan(/\w+: %w\[([^\]]+)\]/).flatten.flat_map(&:split)
canonical_spells = ray_spells
fail_validation("expected 110 canonical spells, got #{canonical_spells.size}") unless
  canonical_spells.size == 110 && canonical_spells.uniq.size == 110
fail_validation("done-done roster differs from canonical roster") unless
  spells.sort == canonical_spells.sort

promoted_spells = %w[
  water_gun thunder_shock psychic confusion razor_leaf leaf_blade poison_sting
  rock_throw ice_shard fire_blast bubble_beam petal_blizzard solar_beam
  stone_edge discharge dragon_pulse bullet_punch mach_punch focus_blast shadow_ball
  acid_spray bite crunch dragon_claw dragon_tail drain_punch fire_punch flame_charge
  force_palm giga_drain ice_punch icy_wind iron_head lick metal_claw psycho_cut
  seismic_toss shadow_claw smack_down snarl spark thunder_punch venoshock
  tackle hyper_beam overheat leech_seed powder_snow toxic night_shade hex
  dragon_breath outrage foul_play flash_cannon dragon_rush phantom_force
  rock_tomb stealth_rock trick_room
]
missing_promotions = promoted_spells - spells
fail_validation("missing promoted spells: #{missing_promotions.join(', ')}") unless
  missing_promotions.empty?

mapper = File.read(MAPPER_FILE)
fail_validation("Cobblemon mapper does not use same-named spell IDs") unless
  mapper.include?('TensuraMod.MOD_ID + ":" + name')
fail_validation("Cobblemon mapper does not reject unsupported moves") unless
  mapper.include?("SpellRegistry.get(spellId).isPresent()")
fail_validation("Cobblemon mapper still contains custom aliases or type fallback") if
  mapper.include?("NAME_MAP") || mapper.include?("typeFallback") || mapper.match?(/\bn\("/)
spell_casting = File.read(SPELL_CASTING_FILE)
executor = [EXECUTOR_FILE, IMPACT_APPLIER_FILE, FEEDBACK_FILE,
  PROJECTILE_DELIVERY_FILE, BEAM_DELIVERY_FILE]
  .map { |file| File.read(file) }.join("\n")
movement = File.read(MOVEMENT_FILE)
projectile = File.read(PROJECTILE_FILE)
runtime = File.read(RUNTIME_FILE)
vfx = File.read(VFX_FILE)
aliases = File.read(ALIASES_FILE)
status_events = File.read(STATUS_FILE)
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
  delivery = definition.dig("delivery", "type").to_s
  fail_validation("#{spell} has no delivery") if delivery.empty?
  fail_validation("#{spell} has unsupported delivery #{delivery}") unless DELIVERY_VFX.key?(delivery)
  controller_delivery = delivery == "trap"
  fail_validation("#{spell} has no impact or controller mechanic") if
    Array(definition["impact"]).empty? && !controller_delivery
  sounds = definition.fetch("sound", {})
  sound_keys = sounds.select { |_key, value| value.is_a?(String) && !value.empty? }.keys
  fail_validation("#{spell} has no cast sound") unless sound_keys.include?("cast")
  unsupported_sounds = sound_keys - %w[cast travel impact loop]
  fail_validation("#{spell} has unsupported sounds: #{unsupported_sounds.join(', ')}") unless
    unsupported_sounds.empty?
  fail_validation("#{spell} declares a loop sound for non-looping #{delivery}") if
    sound_keys.include?("loop") && !LOOP_DELIVERIES.include?(delivery)

  visual = definition.fetch("visual", {})
  cast = visual["cast_animation"].to_s
  fail_validation("#{spell} uses placeholder cast_point") if cast.empty? || cast == "cast_point"
  fail_validation("#{spell} shares cast #{cast} with #{casts[cast]}") if casts.key?(cast)
  casts[cast] = spell
  fail_validation("#{spell} cast #{cast} has no explicit geometry profile") unless
    cast_profiles.key?(cast)
  DELIVERY_VFX.fetch(delivery).each do |alternatives|
    next if alternatives.any? { |phase| !visual[phase].to_s.empty? }

    fail_validation("#{spell} #{delivery} lacks reachable #{alternatives.join('/')} VFX")
  end

  owned_id = "#{spell}_owned"
  fail_validation("#{spell} has no Devour owned node") unless definitions.key?(owned_id)
  fail_validation("#{spell} has no Devour dispenser") unless definitions.key?(spell)
  expected_icon = "tensura:spell_icon_#{spell}"
  actual_icon = definitions.dig(spell, "icon", "data", "item")
  fail_validation("#{spell} Devour icon is #{actual_icon}") unless actual_icon == expected_icon
  fail_validation("#{spell} owned marker is visible") unless
    definitions.dig(owned_id, "icon", "data", "texture") == "tensura:textures/gui/blank.png" &&
      definitions.dig(owned_id, "size") == 0.1
  fail_validation("#{spell} Devour pair is not overlaid") unless
    skills.dig(owned_id, "x") == skills.dig(spell, "x") &&
    skills.dig(owned_id, "y") == skills.dig(spell, "y")
  fail_validation("#{spell} owned-to-dispenser edge is missing") unless
    connections.include?([owned_id, spell])

  spell_model_name = spell == "psychic" ? "spell_custom_psychic" : "spell_#{spell}"
  spell_model = File.join(MODEL_DIR, "#{spell_model_name}.json")
  icon_model = File.join(MODEL_DIR, "spell_icon_#{spell}.json")
  texture = File.join(TEXTURE_DIR, "#{spell}.png")
  fail_validation("#{spell} item model is missing") unless File.exist?(spell_model)
  fail_validation("#{spell} tree icon model is missing") unless File.exist?(icon_model)
  fail_validation("#{spell} texture is missing") unless File.exist?(texture)
  icon_parent = JSON.parse(File.read(icon_model))["parent"]
  fail_validation("#{spell} tree icon points at #{icon_parent}") unless
    icon_parent == "tensura:item/#{spell_model_name}"

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
fail_validation("channel beams do not bypass target i-frames") unless
  executor.match?(/"channel_beam"\.equals\([^)]*\.delivery\.type\).*?target\.invulnerableTime = 0/m)
fail_validation("channel cones do not bypass target i-frames") unless
  executor.match?(/castRuntimeCone.*?target\.invulnerableTime = 0/m)
fail_validation("persistent zones do not bypass target i-frames") unless
  runtime.match?(/tickMovingZones.*?target\.invulnerableTime = 0/m)

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
  executor.match?(/applyImpacts\(owner, effectCaster, directTarget,\s*\w+, true, false\)/)

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

held_channels = {
  "flamethrower" => "channel_beam",
  "ice_beam" => "channel_beam",
  "psybeam" => "channel_beam",
  "hydro_pump" => "channel_beam",
  "dragon_breath" => "channel_cone"
}
held_channels.each do |spell, delivery|
  definition = spell_definition.call(spell)
  fail_validation("#{spell} is not an immediate held channel") unless
    definition.dig("delivery", "type") == delivery &&
      definition.dig("delivery", "hold_to_channel") == true &&
      definition.fetch("cast_time_ticks", 0).to_i.zero? &&
      (spell == "dragon_breath" || definition.dig("delivery", "duration_ticks").to_i >= 100)
end
fail_validation("held channels are not stopped on item release") unless
  runtime.include?("definition.delivery.hold_to_channel") &&
    spell_casting.include?("stopPlayerChannels(player.getUUID())") &&
    spell_casting.include?("UseAnim.SPEAR")
fail_validation("held-channel cooldown is not deferred until release") unless
  spell_casting.include?("SpellExecutor.finishHeldChannel(player, spellId)") &&
    executor.include?("public static void finishHeldChannel") &&
    executor.include?("heldChannels.put(caster.getUUID(), spellId)")

dragon_breath = spell_definition.call("dragon_breath")
dragon_slow = dragon_breath.fetch("impact").find do |impact|
  impact["type"] == "status_effect" && impact["recipient"] == "caster" &&
    impact["effect"] == "minecraft:slowness"
end
fail_validation("Dragon Breath has a cooldown or lacks its strong self-slow") unless
  dragon_breath["cooldown_ticks"].to_i.zero? &&
    dragon_breath.dig("delivery", "duration_ticks").to_i.zero? &&
    dragon_slow&.fetch("amplifier", 0).to_i >= 4 &&
    dragon_slow&.fetch("duration", 0).to_i >
      dragon_breath.dig("delivery", "tick_interval_ticks").to_i

tackle = spell_definition.call("tackle")
fail_validation("Tackle is not a first-target dash") unless
  tackle.dig("delivery", "type") == "dash" &&
    tackle.dig("targeting", "max_targets").to_i == 1

hyper_beam = spell_definition.call("hyper_beam")
fail_validation("Hyper Beam lacks charge, piercing beam, or exhaustion") unless
  hyper_beam.dig("delivery", "type") == "channel_beam" &&
    hyper_beam.fetch("cast_time_ticks", 0).to_i >= 28 &&
    impacts.call("hyper_beam").any? do |impact|
      impact["type"] == "status_effect" && impact["recipient"] == "caster" &&
        impact["effect"] == "tensura:exhausted" && impact["duration"].to_i >= 80
    end

overheat = spell_definition.call("overheat")
fail_validation("Overheat lacks its broad burst or self-exhaustion") unless
  overheat.dig("delivery", "type") == "arc_strike" &&
    overheat.dig("delivery", "cone_angle").to_f >= 100.0 &&
    impacts.call("overheat").any? do |impact|
      impact["recipient"] == "caster" && impact["effect"] == "tensura:exhausted" &&
        impact["duration"].to_i >= 120
    end && executor.match?(/castArcStrike.*?targets\.get\(index\), definition, true, false.*?effectCaster, definition, true, true, false/m)

fail_validation("Leech Seed lacks periodic health transfer") unless
  impacts.call("leech_seed").any? do |impact|
    impact["type"] == "leech_seed" && impact["duration"].to_i == 160 &&
      impact["amount"].to_f == 2.0
  end && runtime.include?("tickLeechSeeds") &&
    runtime.include?("owner.damageSources().playerAttack(owner)") &&
    runtime.include?("healthBefore - target.getHealth()")

fail_validation("Powder Snow is not a chilling cone") unless
  spell_definition.call("powder_snow").dig("delivery", "type") == "arc_strike" &&
    impacts.call("powder_snow").any? do |impact|
      impact["type"] == "status_effect" && impact["effect"] == "minecraft:slowness"
    end

toxic = spell_definition.call("toxic")
fail_validation("Toxic lacks homing buildup with a boss duration cap") unless
  toxic.dig("delivery", "type") == "projectile" &&
    toxic.dig("delivery", "homing_strength").to_f > 0.0 &&
    impacts.call("toxic").any? { |impact| impact["type"] == "toxic" } &&
    executor.include?("recipient.getMaxHealth() >= 100.0F")

fail_validation("Night Shade does not scale with target health") unless
  impacts.call("night_shade").any? do |impact|
    impact["type"] == "health_scaled_damage" && impact["amount"].to_f == 16.0
  end && executor.include?("target.getMaxHealth() * 0.1")

fail_validation("Hex does not double against harmful effects") unless
  impacts.call("hex").any? do |impact|
    impact["type"] == "status_scaled_damage" &&
      impact["conditional_multiplier"].to_f == 2.0
  end && executor.include?("MobEffectCategory.HARMFUL")

outrage = spell_definition.call("outrage")
fail_validation("Outrage is not a three-dash forced combo ending in Confusion") unless
  outrage.dig("delivery", "type") == "dash_combo" &&
    outrage.dig("delivery", "combo_hits").to_i == 3 &&
    runtime.include?("tickDashCombos") && runtime.include?("MobEffects.CONFUSION")

fail_validation("Foul Play does not scale from target attack") unless
  impacts.call("foul_play").any? do |impact|
    impact["type"] == "target_attack_scaled_damage" && impact["amount"].to_f == 23.0
  end && executor.include?("target.getAttribute(Attributes.ATTACK_DAMAGE)")

flash_cannon = spell_definition.call("flash_cannon")
fail_validation("Flash Cannon lacks charge, piercing beam, or Exposed") unless
  flash_cannon.dig("delivery", "type") == "beam" &&
    flash_cannon.fetch("cast_time_ticks", 0).to_i >= 16 &&
    impacts.call("flash_cannon").any? { |impact| impact["type"] == "expose" }

dragon_rush = spell_definition.call("dragon_rush")
fail_validation("Dragon Rush lacks steering or central Stagger") unless
  dragon_rush.dig("delivery", "type") == "dash" &&
    dragon_rush.dig("delivery", "steerable") == true &&
    impacts.call("dragon_rush").any? { |impact| impact["type"] == "central_stagger" } &&
    movement.include?("definition.delivery.steerable")

phantom_force = spell_definition.call("phantom_force")
fail_validation("Phantom Force lacks delayed invisibility and teleport strike") unless
  phantom_force.dig("delivery", "type") == "teleport_strike" &&
    phantom_force.dig("delivery", "delay_ticks").to_i >= 20 &&
    runtime.include?("startDelayedTeleportStrike") &&
    runtime.include?("MobEffects.INVISIBILITY") &&
      runtime.include?("removeInvisibilityOnFinish") &&
      runtime.include?("clearTeleportStrikeInvisibility") &&
    runtime.include?("castRuntimeTeleportStrike")

rock_tomb = spell_definition.call("rock_tomb")
fail_validation("Rock Tomb lacks its temporary slowing trap formation") unless
  rock_tomb.dig("delivery", "type") == "trap" &&
    rock_tomb.dig("delivery", "projectile_count").to_i == 3 &&
    rock_tomb.dig("delivery", "duration_ticks").to_i == 80 &&
    impacts.call("rock_tomb").any? do |impact|
      impact["type"] == "status_effect" && impact["effect"] == "minecraft:slowness"
    end

stealth_rock = spell_definition.call("stealth_rock")
fail_validation("Stealth Rock lacks persistent re-entry damage") unless
  stealth_rock.dig("delivery", "type") == "trap" &&
    stealth_rock.dig("delivery", "duration_ticks").to_i == 400 &&
    impacts.call("stealth_rock").any? { |impact| impact["type"] == "damage" } &&
    runtime.include?("trap.occupants.clear()")

trick_room = spell_definition.call("trick_room")
fail_validation("Trick Room lacks its speed-inverting zone") unless
  trick_room.dig("delivery", "type") == "zone" &&
    trick_room.dig("delivery", "duration_ticks").to_i == 160 &&
    impacts.call("trick_room").any? { |impact| impact["type"] == "invert_speed" } &&
      runtime.include?("tickZones") && executor.include?("movement.getBaseValue() >= 0.12") &&
      !executor.include?("recipient.removeEffect(MobEffects.MOVEMENT_SPEED)") &&
      !executor.include?("recipient.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)")

%w[fire_punch thunder_punch ice_punch dragon_claw shadow_claw psycho_cut].each do |spell|
  fail_validation("#{spell} is not a targetless arc strike") unless
    spell_definition.call(spell).dig("delivery", "type") == "arc_strike"
end
fail_validation("arc strikes do not check facing and block obstruction") unless
  executor.include?("minimumDot") && executor.include?("ClipContext.Block.COLLIDER")

%w[bullet_punch psycho_cut].each do |spell|
  damage = impacts.call(spell).find { |impact| impact["type"] == "damage" }
  fail_validation("#{spell} lacks two-point armor penetration") unless
    damage&.fetch("armor_penetration", 0).to_f == 2.0
end
fail_validation("armor penetration is not applied transiently") unless
  executor.include?("addTransientModifier") && executor.include?("ARMOR_PENETRATION_ID")

venoshock_damage = impacts.call("venoshock").find { |impact| impact["type"] == "damage" }
fail_validation("Venoshock lacks doubled Poison/Toxic damage") unless
  venoshock_damage&.fetch("conditional_multiplier", 0).to_f == 2.0 &&
    venoshock_damage.fetch("effects", []).sort == %w[minecraft:poison tensura:toxic]
fail_validation("Electric Wet chaining is incomplete") unless
  %w[spark thunder_shock].all? do |spell|
    impacts.call(spell).any? { |impact| impact["type"] == "chain_damage" }
  end && executor.include?('case "chain_damage"')

snarl_weakness = impacts.call("snarl").find do |impact|
  impact["type"] == "status_effect" && impact["effect"] == "tensura:special_weakened"
end
fail_validation("Snarl does not reduce only special spell damage for five seconds") unless
  snarl_weakness&.fetch("duration", 0).to_i == 100 &&
    executor.include?('"special".equals(definition.category)') && executor.include?("*= 0.8F")
fail_validation("Smack Down does not enforce three seconds of grounding") unless
  impacts.call("smack_down").any? do |impact|
    impact["type"] == "ground" && impact["duration"].to_i == 60
  end && status_events.include?("tickGrounded") && status_events.include?("stopFallFlying")

fail_validation("Seismic Toss lacks its grab/throw delivery") unless
  spell_definition.call("seismic_toss").dig("delivery", "type") == "grab" &&
    impacts.call("seismic_toss").any? { |impact| impact["type"] == "throw" } &&
    executor.include?('case "grab"') && executor.include?('case "throw"')
metal_guard = impacts.call("metal_claw").find { |impact| impact["type"] == "guard" }
fail_validation("Metal Claw grants Guard before both hits connect") unless
  spell_definition.call("metal_claw").dig("delivery", "combo_hits").to_i == 2 &&
    metal_guard&.fetch("final_hit_only", false)

legacy_replacements = {
  "iron_strike" => "bullet_punch", "frost_nova" => "icy_wind",
  "nature_burst" => "giga_drain", "poison_strike" => "venoshock",
  "seismic_slam" => "seismic_toss", "thundershock" => "thunder_shock",
  "aerial_strike" => "hurricane", "psychic_blast" => "psychic"
}
legacy_replacements.each do |legacy, replacement|
  fail_validation("legacy definition still exists: #{legacy}") if
    File.exist?(File.join(SPELL_DIR, "#{legacy}.json"))
  fail_validation("missing saved-data migration #{legacy} -> #{replacement}") unless
    aliases.include?(%Q{"#{legacy}", "#{replacement}"})
  fail_validation("legacy spell is still referenced by the Cobblemon mapper: #{legacy}") if
    mapper.include?(legacy)
  fail_validation("legacy spell is still present in Devour: #{legacy}") if
    definitions.key?(legacy) || definitions.key?("#{legacy}_owned") ||
      skills.key?(legacy) || skills.key?("#{legacy}_owned")
  legacy_assets = [
    File.join(MODEL_DIR, "spell_#{legacy}.json"),
    File.join(MODEL_DIR, "spell_icon_#{legacy}.json"),
    File.join(TEXTURE_DIR, "#{legacy}.png")
  ]
  fail_validation("legacy icon assets still exist: #{legacy}") if legacy_assets.any?(File.method(:exist?))
end

fail_validation("lingering clouds are not connected to the runtime controller") unless
  executor.include?("definition.delivery.duration_ticks > 0") &&
    executor.match?(/SpellRuntimeController\.startMovingZone\(\s*caster, caster, definition, cloudPosition, Vec3\.ZERO\)/m)
fail_validation("fire impacts ignore configured chance") unless
  executor.include?("target.getRandom().nextDouble() <= impact.chance")
fail_validation("travel sounds are not wired for players and companions") unless
  executor.include?("sound.travel") &&
    executor.scan("if (started) SpellFeedback.playTravelSound").size >= 2
fail_validation("looping impact sounds are not rate-limited") unless
  executor.include?("IMPACT_SOUND_TIMES") && executor.include?("now - lastPlayed < 10") &&
    executor.include?("playImpactSound(owner, target, definition);")

fail_validation("delayed areas do not use school-aware particles") unless
  runtime.match?(/tickDelayedAreas.*?runtimeParticle\(area\.definition\.school\)/m)
fail_validation("delayed areas create lightning for non-Lightning schools") unless
  runtime.include?('if ("lightning".equals(area.definition.school))')
fail_validation("waves do not use school-aware particles") unless
  runtime.match?(/tickWaves.*?runtimeParticle\(wave\.definition\.school\)/m)
fail_validation("waves do not consume trail when aftermath is absent") unless
  runtime.include?("? wave.definition.visual.trail : wave.definition.visual.aftermath")

stone_edge_damage = impacts.call("stone_edge").find { |impact| impact["type"] == "damage" }
fail_validation("Stone Edge is under-scaled for one-hit meteor groups") unless
  stone_edge_damage&.fetch("damage_multiplier", 0).to_f >= 1.0
fail_validation("Shift Gear incorrectly grants Iron Strike") if
  mapper.match?(/n\("shift_gear",\s*"iron_strike"\);/)

ray_count = connections.count { |edge| edge.first == "devour_core" }
fail_validation("expected 18 Devour rays, got #{ray_count}") unless ray_count == 18

spell_files = Dir[File.join(SPELL_DIR, "*.json")]
spell_files.each do |path|
  spell = File.basename(path, ".json")
  source_definition = JSON.parse(File.read(path))
  fail_validation("#{spell} owned marker is not an independent root") unless
    skills.dig("#{spell}_owned", "root") == true
  expected_title = spell.split("_").map(&:capitalize).join(" ")
  title = definitions.dig(spell, "title").to_s
  description = definitions.dig(spell, "description").to_s
  fail_validation("#{spell} still has a technical tree title") unless title == expected_title
  fail_validation("#{spell} lacks a player-friendly description") unless
    description.length.between?(35, 160) && description.end_with?(".") &&
      !description.match?(/\d|cooldown|block range|duration_ticks|damage_multiplier|Dispenses|Absorbed/) &&
      !description.match?(/\s{2,}/)
  chance_impacts = Array(source_definition["impact"]).select do |impact|
    %w[fire expose].include?(impact["type"]) && impact.fetch("chance", 1).to_f < 1
  end
  fail_validation("#{spell} hides probabilistic fire/expose in its description") if
    chance_impacts.any? && !description.downcase.include?("sometimes")
end

puts "Done spells: #{spells.size}"
puts "Unique casts: #{casts.size}"
puts "Unique cast geometry profiles: #{profile_signatures.values.uniq.size}"
puts "Cast geometry families: #{families.size}"
puts "Unique 32x32 icons: #{texture_hashes.size}"
puts "Devour rays: #{ray_count}"
puts "Player-friendly Devour descriptions: #{spell_files.size}"
puts "All done-done checks passed."