package com.tensura.engine;

import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CobblemonUltimateVfx {
    private static final double VIEW_DISTANCE_SQR = 96.0 * 96.0;
    private static final int HIT_INTERVAL_TICKS = 20;
    private static final Map<HitKey, Long> LAST_HYDRO_HIT = new HashMap<>();
    private static final Map<EffectHitKey, Long> LAST_ULTIMATE_HIT = new HashMap<>();

    private CobblemonUltimateVfx() {}

    public static boolean isFireBlast(SpellDefinition definition) {
        return "fire_blast_nova".equals(definition.visual.impact);
    }

    public static boolean isHydroPump(SpellDefinition definition) {
        return "water_spiral".equals(definition.visual.trail);
    }

    public static void sendCastEffects(LivingEntity caster, SpellDefinition definition) {
        if (!(caster.level() instanceof ServerLevel level)) return;
        Vec3 position = caster.getBoundingBox().getCenter();
        if ("aurora_curtain".equals(definition.visual.aftermath)) {
            send(level, "protect-shine", position);
            send(level, "cottonguard", position);
        }
        switch (definition.visual.impact) {
            case "petal_blizzard_cut" -> send(level, "magicalleaf_actor", position);
            case "solar_flare_burst" -> {
                send(level, "synthesis_sun", position.add(0.0, 1.5, 0.0));
                send(level, "synthesis_sunlight", position);
            }
            case "blizzard_frost_hit" -> {
                send(level, "mist_actorshroud", position);
                send(level, "powdersnow_actor", position);
            }
            case "focus_blast_crater" -> send(
                    level, "explosion_charge_accretion", position);
            case "toxic_splash" -> send(level, "toxpoison_actor", position);
            case "poison_burst" -> send(level, "poisonpowder_land", position);
            case "dig_eruption" -> send(level, "bodyslam_actor_dust", position);
            case "earthquake_fissure" -> send(level, "eruption_actorburst", position);
            case "tailwind_swirl", "hurricane_lift" -> send(
                    level, "mist_actorshroud", position);
            case "trick_room_shift" -> send(level, "kinesis_actorspiral", position);
            case "psychic_implosion" -> send(level, "psychic_actor", position);
            case "x_scissor_impact" -> send(level, "aerialace_actorjumpline", position);
            case "bug_buzz_impact" -> send(level, "infestation_actorflydisperse", position);
            case "rock_tomb_crush", "stealth_rock_shards" -> send(
                    level, "rockthrow_actor", position);
            case "shadow_ball_implosion" -> send(level, "shadowball_actorblob", position);
            case "phantom_force_rend" -> send(level, "shadowball_actorlaunch", position);
            case "dragon_crater" -> send(level, "explosion_charge_accretion", position);
            case "outrage_slash" -> send(level, "dragonclaw_aura", position);
            case "crushing_jaws" -> send(level, "nastyplot_actorcloud", position);
            case "dark_pulse_impact" -> send(level, "shadowball_targetblob", position);
            case "steel_plates" -> send(level, "protect-shine", position);
            case "flash_cannon_prism" -> send(level, "aurorabeam_charge", position);
            case "prismatic_gleam" -> send(level, "mysticalfire_actorburst", position);
            case "moonblast_bloom" -> send(level, "synthesis_sun", position);
            default -> {
            }
        }
    }

            public static void sendImpactEffects(ServerLevel level, LivingEntity caster,
                             LivingEntity target,
                                         SpellDefinition definition) {
            String effectPath = switch (definition.visual.impact) {
                case "petal_blizzard_cut" -> "razorleaf_targetexcess";
                case "solar_flare_burst" -> "aurorabeam_targetburst";
                case "blizzard_frost_hit" -> "icywind_targetfrost";
                case "focus_blast_crater" -> "explosion_target";
                case "poison_burst" -> "poisonpowder_landcloud";
                case "toxic_splash" -> "poisongas_target";
                case "dig_eruption" -> "eruption_targetburst";
                case "hurricane_lift" -> "bodyslam_actor_smoke";
                case "psychic_implosion" -> "psychic_impact";
                case "bug_buzz_impact" -> "infestation_targetsmallhits";
                case "rock_tomb_crush", "stealth_rock_shards" -> "rockthrow_target";
                case "shadow_ball_implosion" -> "shadowball_targetimpact";
                case "phantom_force_rend" -> "shadowclaw_target";
                case "outrage_slash" -> "dragonclaw_target";
                case "crushing_jaws" -> "hyperfang_target";
                case "dark_pulse_impact" -> "shadowball_targetsplotching";
                case "steel_plates" -> "protect-blockchip";
                case "flash_cannon_prism" -> "aurorabeam_target";
                case "prismatic_gleam" -> "mysticalfire_actorsparkle";
                case "moonblast_bloom" -> "aurorabeam_targetsparkle";
                default -> null;
            };
            if (effectPath == null) return;
            long now = level.getGameTime();
            LAST_ULTIMATE_HIT.values().removeIf(lastSent -> now - lastSent >= 10);
            EffectHitKey key = new EffectHitKey(
                definition.visual.impact, caster.getUUID(), target.getUUID());
            if (now - LAST_ULTIMATE_HIT.getOrDefault(key, Long.MIN_VALUE / 2) < 10) return;
            LAST_ULTIMATE_HIT.put(key, now);
        Vec3 position = target.getBoundingBox().getCenter();
            send(level, effectPath, position);
    }

    public static void sendCloseCombatHit(ServerLevel level, LivingEntity target,
                                          int hitIndex, boolean finalHit) {
        Vec3 position = target.getBoundingBox().getCenter();
        send(level, hitIndex == 0 ? "closecombat_target" : "closecombat_target2", position);
        if (finalHit) send(level, "closecombat_targetimpact", position);
    }

    public static void sendXScissorHit(ServerLevel level, LivingEntity target, int hitIndex) {
        send(level, hitIndex == 0 ? "aerialace_targetcut1" : "aerialace_targetcut2",
                target.getBoundingBox().getCenter());
    }

    public static void sendDracoMeteorImpact(ServerLevel level, Vec3 position) {
        send(level, "eruption_targetrocks", position);
        send(level, "explosion_target", position);
    }

    public static void sendAuroraFracture(ServerLevel level, Vec3 position) {
        send(level, "protect-blockchip", position.add(0.0, 1.0, 0.0));
        send(level, "icywind_targetbreak", position.add(0.0, 1.0, 0.0));
    }

    public static void sendIronDefenseFracture(ServerLevel level, Vec3 position) {
        send(level, "protect-blockchip", position.add(0.0, 1.0, 0.0));
        send(level, "rockthrow_target", position.add(0.0, 1.0, 0.0));
    }

    public static void sendEarthquakePulse(ServerLevel level, Vec3 position) {
        send(level, "eruption_targetburst", position);
        send(level, "eruption_targetrocks", position);
        send(level, "eruption_targetsmoke", position);
    }

    public static void sendFireBlastImpact(ServerLevel level, Vec3 position) {
        send(level, "daimonji1", position);
        send(level, "daimonji2", position);
        send(level, "daimonji3", position);
        send(level, "daimonji4", position);
        send(level, "fireblast_target", position);
    }

    public static void sendOverheatWave(ServerLevel level, Vec3 origin, Vec3 end,
                                        int pulseIndex, int pulseCount) {
        double progress = Math.min(1.0, (pulseIndex + 1.0) / Math.max(1, pulseCount));
        send(level, "eruption_actorburst", origin);
        send(level, "lavaplume_target", origin.lerp(end, 0.45 + 0.45 * progress));
    }

    public static void sendSurfStart(ServerLevel level, Vec3 position) {
        send(level, "waterpulse_actorsplash", position);
        send(level, "watergun_spray", position);
    }

    public static void sendHydroHitIfDue(ServerLevel level, LivingEntity caster,
                                         LivingEntity target) {
        long now = level.getGameTime();
        LAST_HYDRO_HIT.values().removeIf(lastSent -> now - lastSent >= HIT_INTERVAL_TICKS);
        HitKey key = new HitKey(caster.getUUID(), target.getUUID());
        if (now - LAST_HYDRO_HIT.getOrDefault(key, Long.MIN_VALUE / 2)
                < HIT_INTERVAL_TICKS) return;
        LAST_HYDRO_HIT.put(key, now);
        Vec3 position = target.getBoundingBox().getCenter();
        send(level, "watergun_targetfoam", position);
        send(level, "waterpulse_targetsplash", position);
    }

    public static void clear() {
        LAST_HYDRO_HIT.clear();
        LAST_ULTIMATE_HIT.clear();
    }

    private static void send(ServerLevel level, String effectPath, Vec3 position) {
        List<ServerPlayer> players = level.players().stream()
                .filter(player -> player.distanceToSqr(position) <= VIEW_DISTANCE_SQR)
                .toList();
        new SpawnSnowstormParticlePacket(
                ResourceLocation.fromNamespaceAndPath("cobblemon", effectPath), position)
                .sendToPlayers(players);
    }

    private record HitKey(UUID casterId, UUID targetId) {}

    private record EffectHitKey(String effect, UUID casterId, UUID targetId) {}
}