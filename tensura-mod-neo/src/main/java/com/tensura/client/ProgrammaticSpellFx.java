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
        CacheKey key = new CacheKey(id, packet.shape(), duration);
        return CACHE.computeIfAbsent(key, ignored -> create(id, packet.shape(), duration));
    }

    private static FX create(ResourceLocation id, String shape, int duration) {
        Palette palette = palette(id.getPath());
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
            default -> effect.getFxData().objects().add(
                    cast(duration, palette));
        }
        return effect;
    }

    private static BeamEmitter beam(int duration, Palette palette) {
        BeamEmitter emitter = new BeamEmitter();
        emitter.getConfig().setDuration(duration);
        emitter.getConfig().setLooping(false);
        emitter.getConfig().getEnd().set(1.0f, 0.0f, 0.0f);
        emitter.getConfig().setWidth(NumberFunction.constant(0.16f));
        emitter.getConfig().setColor(fade(palette.primary(), palette.secondary()));
        emitter.getConfig().getRenderer().setUseGPUInstance(true);
        return emitter;
    }

    private static ParticleEmitter cast(int duration, Palette palette) {
        ParticleEmitter emitter = particle(duration, palette, 3.5f, 0.16f, 0.22f, false);
        Cone cone = new Cone();
        cone.setRadius(0.35f);
        cone.setRadiusThickness(0.35f);
        cone.setAngle(18.0f);
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
        ParticleEmitter emitter = particle(duration, palette, emission,
                0.10f, 0.025f, duration > 30);
        Circle circle = new Circle();
        circle.setRadius(0.92f);
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

    private static Palette palette(String style) {
        return switch (style) {
            case "electric_ground_ring", "electric_arc" ->
                    new Palette(0xFFFDE047, 0xAA60A5FA);
            case "electro_ball", "electric_burst", "lightning_column",
                    "electric_afterglow", "sky_call" ->
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
                    "channel_forward" ->
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
            default -> new Palette(0xFFE2E8F0, 0x888B5CF6);
        };
    }

    private record CacheKey(ResourceLocation id, String shape, int duration) {
    }

    private record Palette(int primary, int secondary) {
    }
}