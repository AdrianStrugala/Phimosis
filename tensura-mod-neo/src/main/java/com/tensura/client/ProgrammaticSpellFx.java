package com.tensura.client;

import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Gradient;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Circle;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Cone;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.tensura.network.SpellVfxPacket;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class ProgrammaticSpellFx {
    private static final Map<CacheKey, FX> CACHE = new ConcurrentHashMap<>();

    private ProgrammaticSpellFx() {
    }

    public static FX get(ResourceLocation id, SpellVfxPacket packet) {
        int duration = effectiveDuration(packet);
        CacheKey key = new CacheKey(id, packet.shape(), packet.school(), duration);
        return CACHE.computeIfAbsent(key,
                ignored -> create(id, packet.shape(), packet.school(), duration));
    }

    private static FX create(ResourceLocation id, String shape, String school, int duration) {
        Palette palette = palette(id.getPath(), school);
        FX effect = new FX();
        effect.setFxLocation(id);

        switch (shape) {
            case "beam", "ribbon" -> effect.getFxData().objects().add(
                    beam(duration, palette));
            case "cone" -> effect.getFxData().objects().add(
                cone(duration, palette));
            case "wave" -> {
                effect.getFxData().objects().add(ring(duration, palette, 8.0f, 0.16f));
                effect.getFxData().objects().add(sphere(duration, palette,
                    6.0f, 0.18f, 0.22f, false, false));
            }
            case "telegraph" -> effect.getFxData().objects().add(
                    ring(duration, palette, 2.2f, 0.10f));
            case "zone" -> {
                effect.getFxData().objects().add(ring(duration, palette, 1.8f, 0.18f));
                effect.getFxData().objects().add(sphere(duration, palette,
                        1.8f, 0.18f, 0.16f, true, false));
            }
            case "aura" -> {
                effect.getFxData().objects().add(sphere(duration, palette,
                        2.0f, 0.12f, 0.08f, true, false));
                effect.getFxData().objects().add(ring(duration, palette, 1.2f, 0.08f));
            }
            case "projectile" -> effect.getFxData().objects().add(
                    sphere(duration, palette, 5.0f, 0.20f, 0.08f, false, true));
            case "impact", "aftermath" -> effect.getFxData().objects().add(
                    sphere(duration, palette, 9.0f, 0.22f, 0.75f, false, false));
                default -> addCastGeometry(effect, id.getPath(), duration, palette);
        }
        return effect;
    }

    private static BeamEmitter beam(int duration, Palette palette) {
        return beam(duration, palette, 0.16f);
    }

    private static BeamEmitter beam(int duration, Palette palette, float width) {
        BeamEmitter emitter = new BeamEmitter();
        emitter.getConfig().setDuration(duration);
        emitter.getConfig().setLooping(false);
        emitter.getConfig().getEnd().set(1.0f, 0.0f, 0.0f);
        emitter.getConfig().setWidth(NumberFunction.constant(width));
        emitter.getConfig().setColor(fade(palette.primary(), palette.secondary()));
        emitter.getConfig().getRenderer().setUseGPUInstance(true);
        return emitter;
    }

        private static void addCastGeometry(FX effect, String style, int duration, Palette palette) {
        CastProfile profile = castProfile(style);
        float variant = profile.variant();
        switch (profile.family()) {
            case DASH -> {
            effect.getFxData().objects().add(beam(duration, palette, 0.08f + variant * 0.012f));
            effect.getFxData().objects().add(castCone(duration, palette,
                0.35f + variant * 0.04f, 10.0f + variant * 2.0f,
                5.0f + variant * 0.4f));
            }
            case STANCE -> {
            effect.getFxData().objects().add(ring(duration, palette,
                4.0f + variant * 0.5f, 0.06f + variant * 0.012f,
                0.7f + variant * 0.08f));
            effect.getFxData().objects().add(sphere(duration, palette,
                2.0f + variant * 0.35f, 0.10f + variant * 0.015f,
                0.03f, true, false));
            }
            case CHANNEL -> {
            effect.getFxData().objects().add(castCone(duration, palette,
                0.28f + variant * 0.035f, 14.0f + variant * 2.0f,
                5.0f + variant * 0.5f));
            effect.getFxData().objects().add(sphere(duration, palette,
                1.5f + variant * 0.3f, 0.08f + variant * 0.01f,
                0.04f + variant * 0.006f, false, false));
            }
            case GROUND -> {
            effect.getFxData().objects().add(ring(duration, palette,
                5.0f + variant * 0.6f, 0.08f + variant * 0.01f,
                0.55f + variant * 0.09f));
            effect.getFxData().objects().add(ring(duration, palette,
                2.5f + variant * 0.35f, 0.04f + variant * 0.008f,
                0.25f + variant * 0.06f));
            }
            case FOCUS -> {
            effect.getFxData().objects().add(sphere(duration, palette,
                3.0f + variant * 0.45f, 0.09f + variant * 0.012f,
                0.025f + variant * 0.006f, true, false));
            effect.getFxData().objects().add(ring(duration, palette,
                2.0f + variant * 0.25f, 0.05f + variant * 0.009f,
                0.35f + variant * 0.07f));
            }
            case SWEEP -> {
            effect.getFxData().objects().add(beam(duration, palette,
                0.06f + variant * 0.014f));
            effect.getFxData().objects().add(ring(duration, palette,
                3.0f + variant * 0.45f, 0.025f + variant * 0.01f,
                0.45f + variant * 0.08f));
            }
            case VOLLEY -> {
            effect.getFxData().objects().add(castCone(duration, palette,
                0.22f + variant * 0.05f, 22.0f + variant * 3.0f,
                6.0f + variant * 0.7f));
            effect.getFxData().objects().add(sphere(duration, palette,
                3.0f + variant * 0.4f, 0.07f + variant * 0.012f,
                0.12f + variant * 0.01f, false, false));
            }
            case BURST -> {
            effect.getFxData().objects().add(sphere(duration, palette,
                4.0f + variant * 0.5f, 0.11f + variant * 0.014f,
                0.10f + variant * 0.012f, false, false));
            effect.getFxData().objects().add(castCone(duration, palette,
                0.18f + variant * 0.045f, 28.0f + variant * 2.5f,
                2.5f + variant * 0.35f));
            }
        }
        }

        private static CastProfile castProfile(String style) {
        return switch (style) {
            case "aerial_ace_cast" -> new CastProfile(CastFamily.DASH, 1);
            case "aqua_jet_dash" -> new CastProfile(CastFamily.DASH, 2);
            case "quick_attack_dash" -> new CastProfile(CastFamily.DASH, 3);
            case "shadow_step" -> new CastProfile(CastFamily.DASH, 4);
            case "bug_dash_cast" -> new CastProfile(CastFamily.DASH, 5);
            case "volt_charge" -> new CastProfile(CastFamily.DASH, 6);
            case "aurora_veil_stance" -> new CastProfile(CastFamily.STANCE, 1);
            case "iron_defense_stance" -> new CastProfile(CastFamily.STANCE, 2);
            case "counter_stance" -> new CastProfile(CastFamily.STANCE, 3);
            case "rest" -> new CastProfile(CastFamily.STANCE, 4);
            case "storm_cast" -> new CastProfile(CastFamily.CHANNEL, 1);
            case "bug_buzz_channel" -> new CastProfile(CastFamily.CHANNEL, 2);
            case "overhead_channel" -> new CastProfile(CastFamily.CHANNEL, 3);
            case "earth_channel_cast" -> new CastProfile(CastFamily.CHANNEL, 4);
            case "hurricane_channel" -> new CastProfile(CastFamily.CHANNEL, 5);
            case "two_hand_channel" -> new CastProfile(CastFamily.CHANNEL, 6);
            case "channel_forward" -> new CastProfile(CastFamily.CHANNEL, 7);
            case "two_hand_beam" -> new CastProfile(CastFamily.CHANNEL, 8);
            case "moonblast_channel" -> new CastProfile(CastFamily.CHANNEL, 9);
            case "earth_stomp_cast" -> new CastProfile(CastFamily.GROUND, 1);
            case "burrow_cast" -> new CastProfile(CastFamily.GROUND, 2);
            case "earthquake_slam" -> new CastProfile(CastFamily.GROUND, 3);
            case "ground_slam" -> new CastProfile(CastFamily.GROUND, 4);
            case "ground_cast" -> new CastProfile(CastFamily.GROUND, 5);
            case "fire_spin_cast" -> new CastProfile(CastFamily.GROUND, 6);
            case "dark_focus" -> new CastProfile(CastFamily.FOCUS, 1);
            case "psychic_focus" -> new CastProfile(CastFamily.FOCUS, 2);
            case "electro_ball_charge" -> new CastProfile(CastFamily.FOCUS, 3);
            case "recover_focus" -> new CastProfile(CastFamily.FOCUS, 4);
            case "psybeam_focus" -> new CastProfile(CastFamily.FOCUS, 5);
            case "sky_call" -> new CastProfile(CastFamily.FOCUS, 6);
            case "air_cutter_cast" -> new CastProfile(CastFamily.SWEEP, 1);
            case "fairy_ribbon_cast" -> new CastProfile(CastFamily.SWEEP, 2);
            case "gust_sweep_cast" -> new CastProfile(CastFamily.SWEEP, 3);
            case "whip" -> new CastProfile(CastFamily.SWEEP, 4);
            case "string_shot_cast" -> new CastProfile(CastFamily.SWEEP, 5);
            case "pin_missile_cast", "bug_volley_cast" -> new CastProfile(CastFamily.VOLLEY, 1);
            case "hyper_voice_cast" -> new CastProfile(CastFamily.VOLLEY, 2);
            case "triple_cast" -> new CastProfile(CastFamily.VOLLEY, 3);
            case "x_scissor_cast" -> new CastProfile(CastFamily.VOLLEY, 4);
            case "throw_overhead" -> new CastProfile(CastFamily.VOLLEY, 5);
            case "rock_call" -> new CastProfile(CastFamily.VOLLEY, 6);
            case "melee_left" -> new CastProfile(CastFamily.VOLLEY, 7);
            case "charm_cast" -> new CastProfile(CastFamily.BURST, 1);
            case "gleam_dome_cast" -> new CastProfile(CastFamily.BURST, 2);
            case "draining_kiss_cast" -> new CastProfile(CastFamily.BURST, 3);
            case "ember_flick_cast" -> new CastProfile(CastFamily.BURST, 4);
            case "earth_bolt_cast" -> new CastProfile(CastFamily.BURST, 5);
            case "swift_star_cast" -> new CastProfile(CastFamily.BURST, 6);
            case "tailwind_cast" -> new CastProfile(CastFamily.BURST, 7);
            default -> new CastProfile(CastFamily.BURST, 0);
        };
        }

        private static ParticleEmitter castCone(int duration, Palette palette, float radius,
                            float angle, float emission) {
        ParticleEmitter emitter = particle(duration, palette, emission, 0.16f, 0.22f, false);
        Cone cone = new Cone();
        cone.setRadius(radius);
        cone.setRadiusThickness(radius);
        cone.setAngle(angle);
        emitter.config.shape.setShape(cone);
        return emitter;
    }

    private static ParticleEmitter cone(int duration, Palette palette) {
        ParticleEmitter emitter = particle(duration, palette, 12.0f, 0.18f, 0.28f, false);
        Cone cone = new Cone();
        cone.setRadius(0.85f);
        cone.setRadiusThickness(0.75f);
        cone.setAngle(35.0f);
        emitter.config.shape.setShape(cone);
        return emitter;
    }

    private static ParticleEmitter ring(int duration, Palette palette,
                                        float emission, float thickness) {
        return ring(duration, palette, emission, thickness, 0.92f);
        }

        private static ParticleEmitter ring(int duration, Palette palette,
                        float emission, float thickness, float radius) {
        ParticleEmitter emitter = particle(duration, palette, emission,
                0.10f, 0.025f, duration > 30);
        Circle circle = new Circle();
        circle.setRadius(radius);
        circle.setRadiusThickness(thickness);
        emitter.config.shape.setShape(circle);
        emitter.config.shape.setScale(new NumberFunction3(1.0, 1.0, 1.0));
        return emitter;
    }

    private static ParticleEmitter sphere(int duration, Palette palette,
                                          float emission, float size, float speed,
                                          boolean shell, boolean looping) {
        ParticleEmitter emitter = particle(duration, palette, emission, size, speed, looping);
        Sphere sphere = new Sphere();
        sphere.setRadius(shell ? 0.92f : 0.38f);
        sphere.setRadiusThickness(shell ? 0.08f : 1.0f);
        emitter.config.shape.setShape(sphere);
        return emitter;
    }

    private static ParticleEmitter particle(int duration, Palette palette,
                                            float emission, float size, float speed,
                                            boolean looping) {
        ParticleEmitter emitter = new ParticleEmitter();
        emitter.config.setDuration(duration);
        emitter.config.setLooping(looping);
        emitter.config.setStartLifetime(NumberFunction.constant(
                Math.max(4, Math.min(24, duration))));
        emitter.config.setStartSpeed(NumberFunction.constant(speed));
        emitter.config.setStartSize(new NumberFunction3(size, size, size));
        emitter.config.setStartColor(NumberFunction.color(palette.primary()));
        emitter.config.setMaxParticles(512);
        emitter.config.setParallelUpdate(duration > 40);
        emitter.config.emission.setEmissionRate(NumberFunction.constant(emission));
        emitter.config.colorOverLifetime.setEnable(true);
        emitter.config.colorOverLifetime.setColor(
                fade(palette.primary(), palette.secondary()));
        emitter.config.renderer.setUseGPUInstance(true);
        return emitter;
    }

    private static Gradient fade(int start, int end) {
        return new Gradient(new GradientColor(start, end & 0x00FFFFFF));
    }

    private static int effectiveDuration(SpellVfxPacket packet) {
        if (packet.durationTicks() > 0) {
            return packet.durationTicks();
        }
        return switch (packet.shape()) {
            case "impact" -> 8;
            case "aftermath" -> 16;
            case "projectile" -> 200;
            case "beam", "ribbon" -> 8;
            default -> 12;
        };
    }

    private static Palette palette(String style, String school) {
        return switch (style) {
            case "electric_ground_ring", "electric_arc" ->
                    new Palette(0xFFFDE047, 0xAA60A5FA);
            case "electro_ball", "electric_burst", "lightning_column",
                    "electric_afterglow", "sky_call", "volt_charge",
                    "volt_tackle_shell", "electric_collision" ->
                    new Palette(0xFFFFFFFF, 0xCCFACC15);
            case "aurora_dome", "aurora_curtain", "guard_stance" ->
                    new Palette(0xCC67E8F9, 0xAAFB7185);
            case "snow_zone", "moving_blizzard", "storm_cast" ->
                    new Palette(0xFFE0F2FE, 0xAA7DD3FC);
            case "water_shell", "water_spiral", "heavy_splash",
                    "water_burst", "dash_forward", "two_hand_channel",
                    "water_front", "surf_wave", "ground_slam" ->
                    new Palette(0xDD38BDF8, 0x8867E8F9);
                case "flame_stream", "fire_burst", "smoke_afterglow",
                        "channel_forward", "fire_spin_cast", "fire_spiral",
                        "fire_spin_ground" ->
                    new Palette(0xFFFFC857, 0xCCEF4444);
                case "toxic_spikes", "poison_burst", "toxic_ground",
                    "throw_overhead" ->
                    new Palette(0xFFD8B4FE, 0xCC65A30D);
                case "combat_impact", "melee_finisher", "melee_left" ->
                    new Palette(0xFFFFF7ED, 0xCCE11D48);
                case "shadow_path", "shadow_slash", "shadow_afterimage",
                    "shadow_step" ->
                    new Palette(0xFF312E81, 0xAA111827);
                case "prismatic_beam", "psychic_burst", "ricochet_arc",
                    "cast_point" ->
                    new Palette(0xFFF0ABFC, 0xCC22D3EE);
                    case "rock_call", "falling_rock", "stone_dust",
                        "rock_slide_shadow", "rock_impact" ->
                        new Palette(0xFFD6D3D1, 0xCC78716C);
                    case "recover_focus", "recover_bloom", "recover_afterglow" ->
                        new Palette(0xFFF0FDF4, 0xCC4ADE80);
                    case "dark_focus", "dark_pulse_beam", "dark_pulse_impact",
                        "dark_residue" ->
                        new Palette(0xFFC084FC, 0xCC1F2937);
            default -> schoolPalette(school);
        };
    }

    private static Palette schoolPalette(String school) {
        return switch (school) {
            case "fire" -> new Palette(0xFFFF662D, 0xCCFFCA4A);
            case "water" -> new Palette(0xFF2DA9FF, 0xCC5BEBEE);
            case "lightning" -> new Palette(0xFFFFE034, 0xCCFFFFBC);
            case "nature" -> new Palette(0xFF59D65B, 0xCCBCFF78);
            case "ice" -> new Palette(0xFF82E8FF, 0xCCE7FFFF);
            case "poison" -> new Palette(0xFFC55BDE, 0xCC8EF575);
            case "earth" -> new Palette(0xFFD3944C, 0xCCFFD67E);
            case "wind" -> new Palette(0xFF97D5FF, 0xCCF5FCFF);
            case "psychic" -> new Palette(0xFFF55EB8, 0xCC70E8FF);
            case "bug" -> new Palette(0xFF9DD33B, 0xCCE7FF8A);
            case "shadow" -> new Palette(0xFF7E65CA, 0xCCD2AAFF);
            case "dragon" -> new Palette(0xFF745BFF, 0xCCFF5B62);
            case "fairy" -> new Palette(0xFFFF87CF, 0xCCFFEBFA);
            case "steel" -> new Palette(0xFFA4BECD, 0xCCEEFAFF);
            case "physical" -> new Palette(0xFFF2F0E6, 0xCC9B978B);
            default -> new Palette(0xFFE2E8F0, 0x888B5CF6);
        };
    }

    private record CacheKey(ResourceLocation id, String shape, String school, int duration) {
    }

    private enum CastFamily {
        DASH, STANCE, CHANNEL, GROUND, FOCUS, SWEEP, VOLLEY, BURST
    }

    private record CastProfile(CastFamily family, int variant) {
    }

    private record Palette(int primary, int secondary) {
    }
}