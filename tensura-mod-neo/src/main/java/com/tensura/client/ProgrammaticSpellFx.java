package com.tensura.client;

import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Gradient;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Box;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Circle;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Cone;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.tensura.network.SpellVfxPacket;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.List;
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

        if (addSignatureGeometry(effect, id.getPath(), shape, duration, palette)) {
            return effect;
        }

        switch (shape) {
            case "beam", "ribbon" -> addBeamGeometry(effect, id.getPath(), duration, palette);
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

    /**
     * A single 0.16-wide BeamEmitter reads as a hairline: for most beams it is only the bright
     * accent laid over the vanilla particle line that {@code SpellBeamDelivery} draws along the
     * trace. Flamethrower has no such line - Snowstorm owns its Pokemon visuals and the vanilla
     * fallback is deliberately suppressed - so its stream has to carry the whole silhouette and
     * is built from stacked layers instead. Client-side width is multiplied by the spell's
     * {@code targeting.width}, so these numbers are blocks at width 1.0.
     */
    private static void addBeamGeometry(FX effect, String style, int duration, Palette palette) {
        if ("protect_wall".equals(style)) {
            effect.getFxData().objects().add(boxShellAt(duration,
                new Palette(0xCCB8F3FF, 0x001FA2FF),
                32.0f, 0.085f, 1.0f, 2.0f, 0.05f,
                0.5f, 0.0f, 0.0f, Box.Type.Edge, true));
            effect.getFxData().objects().add(boxShellAt(duration,
                new Palette(0x3344D8FF, 0x001FA2FF),
                12.0f, 0.10f, 0.98f, 1.96f, 0.04f,
                0.5f, 0.0f, 0.0f, Box.Type.Shell, true));
            return;
        }
        if ("hyper_beam_core".equals(style)) {
            addHyperBeamGeometry(effect, Math.max(14, duration), palette);
            return;
        }
        if ("sunlit_stream".equals(style)) {
            addSolarBeamGeometry(effect, duration);
            return;
        }
        if (!"flame_stream".equals(style)) {
            effect.getFxData().objects().add(beam(duration, palette));
            return;
        }

        effect.getFxData().objects().add(flameVolume(duration,
            new Palette(0x99EF4444, 0x00EF4444), 34.0f, 0.34f, 0.95f));
        effect.getFxData().objects().add(flameVolume(duration,
            new Palette(0xDDFFB020, 0x00EF4444), 48.0f, 0.22f, 0.58f));
        effect.getFxData().objects().add(beam(duration,
            new Palette(0xFFFFF3C4, 0x00FFC857), 0.11f));
    }

        private static void addHyperBeamGeometry(FX effect, int duration, Palette palette) {
            effect.getFxData().objects().add(energyVolume(duration,
                    new Palette(0x887DD3FC, 0x007DD3FC), 42.0f, 0.24f, 0.82f, 8));
            effect.getFxData().objects().add(energyVolume(duration,
                    new Palette(0xCCBAE6FD, 0x0060A5FA), 58.0f, 0.14f, 0.46f, 6));
        effect.getFxData().objects().add(beam(duration,
                    new Palette(0xFFFFFFFF, 0x00E0F2FE), 0.13f));
        effect.getFxData().objects().add(hyperBeamAftershock(duration, 3, 0.28f));
        effect.getFxData().objects().add(hyperBeamAftershock(duration, 6, 0.56f));
        effect.getFxData().objects().add(hyperBeamAftershock(duration, 9, 0.84f));
        }

        private static boolean addSignatureGeometry(FX effect, String style, String shape,
                                                    int duration, Palette palette) {
            if (addBlizzardGeometry(effect, style, duration)) return true;
            if (addTrickRoomGeometry(effect, style, duration)) return true;
            if (addStealthRockGeometry(effect, style, duration)) return true;
            if (addCrunchGeometry(effect, style, duration)) return true;
            if (addSolarGeometry(effect, style, duration)) return true;
            switch (style) {
                case "protect_cast" -> {
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0xDDB8F3FF, 0x001FA2FF),
                        24.0f, 0.055f, 0.72f));
                    effect.getFxData().objects().add(sphere(duration,
                        new Palette(0x66FFFFFF, 0x001FA2FF),
                        18.0f, 0.10f, 0.04f, true, false));
                    return true;
                }
                case "protect_block" -> {
                    effect.getFxData().objects().add(burstSphere(duration,
                        new Palette(0xEEFFFFFF, 0x001FA2FF),
                        34.0f, 0.13f, 0.66f, 0));
                    return true;
                }
                case "hyper_beam_charge" -> {
                    effect.getFxData().objects().add(
                            sphere(duration, new Palette(0xFFFFFFFF, 0x007DD3FC),
                                    34.0f, 0.16f, 0.035f, false, false));
                    effect.getFxData().objects().add(
                            ring(duration, new Palette(0xCC7DD3FC, 0x002563EB),
                                    18.0f, 0.07f, 0.55f));
                    return true;
                }
                case "hyper_beam_focus" -> {
                    effect.getFxData().objects().add(
                            ring(duration, new Palette(0xFFFFFFFF, 0x0060A5FA),
                                    28.0f, 0.055f, 0.92f));
                    effect.getFxData().objects().add(
                            ring(duration, new Palette(0xAA7DD3FC, 0x002563EB),
                                    18.0f, 0.08f, 0.62f));
                    return true;
                }
                case "hyper_beam_blast" -> {
                    effect.getFxData().objects().add(
                            burstSphere(duration, new Palette(0xFFFFFFFF, 0x007DD3FC),
                                    72.0f, 0.28f, 1.0f, 0));
                    effect.getFxData().objects().add(
                            burstRing(duration, new Palette(0xDD7DD3FC, 0x002563EB),
                                    44.0f, 0.11f, 0.95f, 1));
                    return true;
                }
                            case "sky_call" -> {
                            effect.getFxData().objects().add(boxVolume(duration,
                                new Palette(0xCCFDE047, 0x0060A5FA),
                                26.0f, 0.12f, 0.12f,
                                0.7f, 1.8f, 0.7f, 0.9f, 9, false));
                            effect.getFxData().objects().add(ring(duration,
                                new Palette(0xFFFFFFFF, 0x00FDE047),
                                18.0f, 0.06f, 0.62f));
                            return true;
                            }
                            case "electric_ground_ring" -> {
                            effect.getFxData().objects().add(ring(duration,
                                new Palette(0xFFFDE047, 0x0060A5FA),
                                30.0f, 0.055f, 0.96f));
                            effect.getFxData().objects().add(ring(duration,
                                new Palette(0xAAFFFFFF, 0x00FDE047),
                                22.0f, 0.10f, 0.72f));
                            effect.getFxData().objects().add(boxVolume(duration,
                                new Palette(0x99FDE047, 0x0060A5FA),
                                18.0f, 0.08f, 0.05f,
                                1.6f, 0.05f, 1.6f, 0.03f, 6, false));
                            return true;
                            }
                            case "lightning_column" -> {
                            effect.getFxData().objects().add(boxVolume(duration,
                                new Palette(0xEEFFFFFF, 0x0060A5FA),
                                90.0f, 0.13f, 0.10f,
                                0.20f, 3.0f, 0.20f, 1.45f, 5, false));
                            effect.getFxData().objects().add(burstSphere(duration,
                                new Palette(0xFFFFFFFF, 0x00FDE047),
                                68.0f, 0.22f, 0.75f, 0));
                            effect.getFxData().objects().add(burstRing(duration,
                                new Palette(0xDDFDE047, 0x0060A5FA),
                                48.0f, 0.10f, 1.0f, 1));
                            return true;
                            }
                            case "electric_afterglow" -> {
                            effect.getFxData().objects().add(ring(duration,
                                new Palette(0xAAFDE047, 0x0060A5FA),
                                18.0f, 0.09f, 0.92f));
                            effect.getFxData().objects().add(boxVolume(duration,
                                new Palette(0x88FFFFFF, 0x0060A5FA),
                                16.0f, 0.07f, 0.04f,
                                1.5f, 0.06f, 1.5f, 0.05f, 8, false));
                            return true;
                            }
                            case "earthquake_slam" -> {
                            effect.getFxData().objects().add(ring(duration,
                                new Palette(0xFFD3944C, 0x0078716C),
                                28.0f, 0.10f, 0.72f));
                            effect.getFxData().objects().add(boxVolume(duration,
                                new Palette(0xCCD6D3D1, 0x0078716C),
                                32.0f, 0.15f, 0.16f,
                                1.2f, 0.08f, 1.2f, 0.04f, 10, false));
                            return true;
                            }
                            case "earthquake_first_ring" -> {
                            effect.getFxData().objects().add(ring(duration,
                                new Palette(0xFFFFD67E, 0x00D3944C),
                                34.0f, 0.045f, 0.98f));
                            effect.getFxData().objects().add(ring(duration,
                                new Palette(0xAAD3944C, 0x0078716C),
                                22.0f, 0.10f, 0.82f));
                            return true;
                            }
                            case "earthquake_fissure" -> {
                            effect.getFxData().objects().add(burstRing(duration,
                                new Palette(0xFFFFD67E, 0x00D3944C),
                                72.0f, 0.14f, 0.94f, 0));
                            effect.getFxData().objects().add(burstSphere(duration,
                                new Palette(0xDDD6D3D1, 0x0078716C),
                                52.0f, 0.20f, 0.82f, 1));
                            return true;
                            }
                            case "earthquake_dust" -> {
                            effect.getFxData().objects().add(ring(duration,
                                new Palette(0x88D3944C, 0x0078716C),
                                10.0f, 0.07f, 0.95f));
                            effect.getFxData().objects().add(boxVolume(duration,
                                new Palette(0x66D6D3D1, 0x0078716C),
                                14.0f, 0.09f, 0.04f,
                                1.7f, 0.08f, 1.7f, 0.06f, 12, true));
                            return true;
                            }
                            case "overhead_channel" -> {
                            effect.getFxData().objects().add(sphere(duration,
                                new Palette(0xFFE9D5FF, 0x007E22CE),
                                36.0f, 0.18f, 0.035f, false, false));
                            effect.getFxData().objects().add(ring(duration,
                                new Palette(0xCCF97316, 0x007E22CE),
                                18.0f, 0.08f, 0.72f));
                            return true;
                            }
                            case "meteor_shadow" -> {
                            effect.getFxData().objects().add(ring(duration,
                                new Palette(0xCC1F1235, 0x00F97316),
                                26.0f, 0.07f, 0.96f));
                            effect.getFxData().objects().add(ring(duration,
                                new Palette(0x88F97316, 0x007E22CE),
                                16.0f, 0.12f, 0.68f));
                            return true;
                            }
                            case "dragon_meteor" -> {
                            effect.getFxData().objects().add(sphere(duration,
                                new Palette(0xFFFFF7ED, 0x00F97316),
                                34.0f, 0.30f, 0.04f, false, true));
                            effect.getFxData().objects().add(sphere(duration,
                                new Palette(0xCC7E22CE, 0x001F1235),
                                22.0f, 0.44f, 0.08f, true, true));
                            return true;
                            }
                            case "dragon_fire" -> {
                            effect.getFxData().objects().add(boxVolume(duration,
                                new Palette(0xCCF97316, 0x007E22CE),
                                40.0f, 0.18f, 0.12f,
                                0.55f, 0.30f, 0.30f, 0.0f, 8, true));
                            effect.getFxData().objects().add(boxVolume(duration,
                                new Palette(0xAAE9D5FF, 0x001F1235),
                                24.0f, 0.11f, 0.08f,
                                0.75f, 0.16f, 0.16f, 0.0f, 6, true));
                            return true;
                            }
                            case "dragon_crater" -> {
                            effect.getFxData().objects().add(burstSphere(duration,
                                new Palette(0xFFF97316, 0x007E22CE),
                                72.0f, 0.24f, 0.92f, 0));
                            effect.getFxData().objects().add(burstRing(duration,
                                new Palette(0xCCE9D5FF, 0x001F1235),
                                52.0f, 0.13f, 1.0f, 1));
                            effect.getFxData().objects().add(boxVolume(duration,
                                new Palette(0x99D6D3D1, 0x0078716C),
                                28.0f, 0.18f, 0.16f,
                                1.4f, 0.12f, 1.4f, 0.08f, 10, false));
                            return true;
                            }
                            case "surf_wave" -> {
                            effect.getFxData().objects().add(boxVolume(duration,
                                new Palette(0xCC0284C7, 0x0038BDF8),
                                54.0f, 0.23f, 0.10f,
                                                1.0f, 0.48f, 1.0f, 0.20f, 10, false));
                            effect.getFxData().objects().add(boxVolume(duration,
                                new Palette(0xDDE0F2FE, 0x0038BDF8),
                                42.0f, 0.14f, 0.12f,
                                                1.0f, 0.16f, 1.06f, 0.48f, 7, false));
                            effect.getFxData().objects().add(boxVolume(duration,
                                new Palette(0xAAFFFFFF, 0x0067E8F9),
                                30.0f, 0.10f, 0.06f,
                                                1.0f, 0.08f, 1.12f, 0.04f, 8, false));
                            return true;
                            }
                            case "water_front" -> {
                            effect.getFxData().objects().add(ring(duration,
                                new Palette(0xCC38BDF8, 0x0067E8F9),
                                24.0f, 0.055f, 0.96f));
                            effect.getFxData().objects().add(boxVolume(duration,
                                new Palette(0x88E0F2FE, 0x0038BDF8),
                                20.0f, 0.10f, 0.04f,
                                1.5f, 0.06f, 0.30f, 0.04f, 8, false));
                            return true;
                            }
                            case "heavy_splash" -> {
                            effect.getFxData().objects().add(burstSphere(duration,
                                new Palette(0xFFE0F2FE, 0x0038BDF8),
                                64.0f, 0.17f, 0.86f, 0));
                            effect.getFxData().objects().add(burstRing(duration,
                                new Palette(0xCC38BDF8, 0x0067E8F9),
                                42.0f, 0.10f, 0.98f, 1));
                            return true;
                            }
                            case "ground_slam" -> {
                            effect.getFxData().objects().add(boxVolume(duration,
                                new Palette(0xAA38BDF8, 0x0067E8F9),
                                32.0f, 0.13f, 0.08f,
                                1.1f, 0.08f, 1.1f, 0.06f, 8, false));
                            effect.getFxData().objects().add(burstRing(duration,
                                new Palette(0xDDE0F2FE, 0x0038BDF8),
                                36.0f, 0.10f, 0.92f, 1));
                            return true;
                            }
                default -> {
                    return false;
                }
            }
        }

        private static boolean addBlizzardGeometry(FX effect, String style, int duration) {
            switch (style) {
                case "storm_cast" -> {
                    effect.getFxData().objects().add(sphere(duration,
                        new Palette(0xCCE0F2FE, 0x007DD3FC),
                        30.0f, 0.13f, 0.05f, true, false));
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0xAAFFFFFF, 0x007DD3FC),
                        20.0f, 0.06f, 0.68f));
                    return true;
                }
                case "snow_zone" -> {
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0xFFE0F2FE, 0x007DD3FC),
                        30.0f, 0.045f, 0.98f));
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0x887DD3FC, 0x00FFFFFF),
                        18.0f, 0.11f, 0.78f));
                    return true;
                }
                case "moving_blizzard" -> {
                    effect.getFxData().objects().add(boxVolume(duration,
                        new Palette(0x99E0F2FE, 0x007DD3FC),
                        42.0f, 0.12f, 0.10f,
                        0.90f, 0.42f, 0.90f, 0.34f, 12, true));
                    effect.getFxData().objects().add(boxVolume(duration,
                        new Palette(0x88FFFFFF, 0x0038BDF8),
                        30.0f, 0.08f, 0.16f,
                        0.94f, 0.58f, 0.94f, 0.42f, 9, true));
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0xDDE0F2FE, 0x007DD3FC),
                        12.0f, 0.045f, 0.98f));
                    return true;
                }
                case "blizzard_frost_hit" -> {
                    effect.getFxData().objects().add(burstSphere(duration,
                        new Palette(0xFFFFFFFF, 0x007DD3FC),
                        44.0f, 0.16f, 0.82f, 0));
                    effect.getFxData().objects().add(burstRing(duration,
                        new Palette(0xCC82E8FF, 0x00E7FFFF),
                        30.0f, 0.09f, 0.92f, 1));
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        private static boolean addTrickRoomGeometry(FX effect, String style, int duration) {
            switch (style) {
                case "trick_room_cast" -> {
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0xDDF0ABFC, 0x0022D3EE),
                        24.0f, 0.06f, 0.72f));
                    effect.getFxData().objects().add(boxShell(duration,
                        new Palette(0x99F0ABFC, 0x0022D3EE),
                        22.0f, 0.08f, 0.55f, 0.55f, 0.55f,
                        0.28f, Box.Type.Edge, false));
                    return true;
                }
                case "trick_room_grid" -> {
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0xFFF0ABFC, 0x0022D3EE),
                        30.0f, 0.045f, 0.98f));
                    effect.getFxData().objects().add(boxShell(duration,
                        new Palette(0x88F0ABFC, 0x0022D3EE),
                        30.0f, 0.07f, 0.96f, 0.75f, 0.96f,
                        0.38f, Box.Type.Edge, false));
                    return true;
                }
                case "trick_room_cube" -> {
                    effect.getFxData().objects().add(boxShell(duration,
                        new Palette(0xCCF0ABFC, 0x0022D3EE),
                        28.0f, 0.075f, 0.96f, 0.75f, 0.96f,
                        0.38f, Box.Type.Edge, true));
                    effect.getFxData().objects().add(boxShell(duration,
                        new Palette(0x3322D3EE, 0x00F0ABFC),
                        10.0f, 0.09f, 0.94f, 0.72f, 0.94f,
                        0.36f, Box.Type.Shell, true));
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0x88F0ABFC, 0x0022D3EE),
                        10.0f, 0.055f, 0.92f));
                    return true;
                }
                case "trick_room_shift" -> {
                    effect.getFxData().objects().add(burstSphere(duration,
                        new Palette(0xDDF0ABFC, 0x0022D3EE),
                        34.0f, 0.13f, 0.72f, 0));
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        private static boolean addStealthRockGeometry(FX effect, String style, int duration) {
            switch (style) {
                case "stealth_rock_cast", "stealth_rock_runes" -> {
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0xFFD6D3D1, 0x0078716C),
                        24.0f, 0.055f, 0.88f));
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0x99FFD67E, 0x0078716C),
                        16.0f, 0.10f, 0.62f));
                    return true;
                }
                case "stealth_rock_field" -> {
                    for (int index = 0; index < 6; index++) {
                        double angle = index * Math.PI * 2.0 / 6.0;
                        effect.getFxData().objects().add(boxVolumeAt(duration,
                            new Palette(0xCCD6D3D1, 0x0078716C),
                            8.0f, 0.12f, 0.025f,
                            0.10f, 0.34f, 0.10f,
                            (float) (Math.cos(angle) * 0.68), 0.40f,
                            (float) (Math.sin(angle) * 0.68), 12, true));
                    }
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0x88D3944C, 0x0078716C),
                        8.0f, 0.045f, 0.72f));
                    return true;
                }
                case "stealth_rock_shards" -> {
                    effect.getFxData().objects().add(burstSphere(duration,
                        new Palette(0xFFD6D3D1, 0x0078716C),
                        42.0f, 0.18f, 0.72f, 0));
                    effect.getFxData().objects().add(burstRing(duration,
                        new Palette(0xAAD3944C, 0x0078716C),
                        24.0f, 0.09f, 0.82f, 1));
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        private static boolean addCrunchGeometry(FX effect, String style, int duration) {
            switch (style) {
                case "crunch_lunge_cast" -> {
                    effect.getFxData().objects().add(sphere(duration,
                        new Palette(0xAA312E81, 0x00111827),
                        22.0f, 0.14f, 0.04f, true, false));
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0xCCC084FC, 0x00111827),
                        16.0f, 0.06f, 0.62f));
                    return true;
                }
                case "crunch_maw_warning" -> {
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0xFFC084FC, 0x00111827),
                        28.0f, 0.045f, 0.98f));
                    effect.getFxData().objects().add(boxShellAt(duration,
                        new Palette(0x99312E81, 0x00111827),
                        20.0f, 0.08f, 0.18f, 0.42f, 0.78f,
                        -0.42f, 0.30f, 0.0f, Box.Type.Edge, false));
                    effect.getFxData().objects().add(boxShellAt(duration,
                        new Palette(0x99312E81, 0x00111827),
                        20.0f, 0.08f, 0.18f, 0.42f, 0.78f,
                        0.42f, 0.30f, 0.0f, Box.Type.Edge, false));
                    return true;
                }
                case "crushing_jaws" -> {
                    effect.getFxData().objects().add(boxShellAt(duration,
                        new Palette(0xEEC084FC, 0x00111827),
                        42.0f, 0.13f, 0.16f, 0.52f, 0.82f,
                        -0.24f, 0.35f, 0.0f, Box.Type.Edge, false));
                    effect.getFxData().objects().add(boxShellAt(duration,
                        new Palette(0xEEC084FC, 0x00111827),
                        42.0f, 0.13f, 0.16f, 0.52f, 0.82f,
                        0.24f, 0.35f, 0.0f, Box.Type.Edge, false));
                    effect.getFxData().objects().add(burstSphere(duration,
                        new Palette(0xCC312E81, 0x00111827),
                        38.0f, 0.18f, 0.72f, 1));
                    return true;
                }
                case "crunch_maw_residue" -> {
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0x88312E81, 0x00111827),
                        12.0f, 0.09f, 0.88f));
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        private static boolean addSolarGeometry(FX effect, String style, int duration) {
            switch (style) {
                case "solar_beam_charge" -> {
                    effect.getFxData().objects().add(boxVolumeAt(duration,
                        new Palette(0xFFFFF7AE, 0x0059D65B),
                        36.0f, 0.13f, 0.04f,
                        0.62f, 0.24f, 0.62f,
                        0.0f, 1.45f, 0.0f, 10, false));
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0xCCFACC15, 0x0059D65B),
                        22.0f, 0.06f, 0.68f));
                    return true;
                }
                case "solar_focus_ring" -> {
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0xFFFFFFA8, 0x0059D65B),
                        30.0f, 0.045f, 0.98f));
                    effect.getFxData().objects().add(ring(duration,
                        new Palette(0xAA59D65B, 0x00FACC15),
                        18.0f, 0.10f, 0.72f));
                    return true;
                }
                case "solar_beam_column" -> {
                    effect.getFxData().objects().add(sphere(duration,
                        new Palette(0xFFFFFFFF, 0x00FACC15),
                        36.0f, 0.24f, 0.05f, false, true));
                    return true;
                }
                case "solar_flare_burst" -> {
                    effect.getFxData().objects().add(burstSphere(duration,
                        new Palette(0xFFFFFFD6, 0x0059D65B),
                        68.0f, 0.22f, 0.92f, 0));
                    effect.getFxData().objects().add(burstRing(duration,
                        new Palette(0xDDFACC15, 0x0059D65B),
                        44.0f, 0.11f, 1.0f, 1));
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        private static void addSolarBeamGeometry(FX effect, int duration) {
            effect.getFxData().objects().add(energyVolume(duration,
                new Palette(0x99FACC15, 0x0059D65B), 38.0f, 0.20f, 0.72f, 9));
            effect.getFxData().objects().add(energyVolume(duration,
                new Palette(0xCCFFFFA8, 0x0059D65B), 50.0f, 0.12f, 0.38f, 7));
            effect.getFxData().objects().add(beam(duration,
                new Palette(0xFFFFFFFF, 0x00FACC15), 0.10f));
        }

        private static ParticleEmitter energyVolume(int duration, Palette palette,
                                                    float emission, float size,
                                                    float diameter, int lifetime) {
            ParticleEmitter emitter = particle(duration, palette, emission, size, 0.06f, false);
            emitter.config.setStartLifetime(NumberFunction.constant((float) lifetime));
            emitter.config.setMaxParticles(384);
            Box box = new Box();
            emitter.config.shape.setShape(box);
            emitter.config.shape.setPosition(new NumberFunction3(0.5, 0.0, 0.0));
            emitter.config.shape.setScale(new NumberFunction3(1.0, diameter, diameter));
            return emitter;
        }

        private static ParticleEmitter burstSphere(int duration, Palette palette, float count,
                                                   float size, float radius, int delay) {
            ParticleEmitter emitter = particle(duration, palette, 0.0f, size, 0.24f, false);
            Sphere sphere = new Sphere();
            sphere.setRadius(radius);
            sphere.setRadiusThickness(0.22f);
            emitter.config.shape.setShape(sphere);
            configureBurst(emitter, delay, count);
            return emitter;
        }

        private static ParticleEmitter burstRing(int duration, Palette palette, float count,
                                                 float size, float radius, int delay) {
            ParticleEmitter emitter = particle(duration, palette, 0.0f, size, 0.12f, false);
            Circle circle = new Circle();
            circle.setRadius(radius);
            circle.setRadiusThickness(0.08f);
            emitter.config.shape.setShape(circle);
            configureBurst(emitter, delay, count);
            return emitter;
        }

        private static void configureBurst(ParticleEmitter emitter, int delay, float count) {
            EmissionSetting.Burst burst = new EmissionSetting.Burst();
            burst.time = delay;
            burst.cycles = 1;
            burst.interval = 1;
            burst.probability = 1.0f;
            burst.setCount(NumberFunction.constant(count));
            emitter.config.emission.setBursts(List.of(burst));
        }

        private static ParticleEmitter hyperBeamAftershock(int duration, int delay,
                                   float linePosition) {
        Palette palette = new Palette(0xFFFFFFFF, 0x0060A5FA);
        ParticleEmitter emitter = particle(duration, palette, 0.0f, 0.30f, 0.16f, false);
        emitter.config.setStartLifetime(NumberFunction.constant(8.0f));
        emitter.config.setMaxParticles(96);
        Box box = new Box();
        emitter.config.shape.setShape(box);
        emitter.config.shape.setPosition(new NumberFunction3(linePosition, 0.0, 0.0));
        emitter.config.shape.setScale(new NumberFunction3(0.015, 0.85, 0.85));
        EmissionSetting.Burst burst = new EmissionSetting.Burst();
        burst.time = delay;
        burst.cycles = 1;
        burst.interval = 1;
        burst.probability = 1.0f;
        burst.setCount(NumberFunction.constant(30.0f));
        emitter.config.emission.setBursts(List.of(burst));
        return emitter;
        }

        private static ParticleEmitter flameVolume(int duration, Palette palette,
                               float emission, float size,
                               float diameter) {
        ParticleEmitter emitter = particle(duration, palette, emission, size, 0.08f, false);
        emitter.config.setStartLifetime(NumberFunction.constant(7.0f));
        emitter.config.setMaxParticles(384);
        Box box = new Box();
        emitter.config.shape.setShape(box);
        emitter.config.shape.setPosition(new NumberFunction3(0.5, 0.0, 0.0));
        emitter.config.shape.setScale(new NumberFunction3(1.0, diameter, diameter));
        return emitter;
    }

    private static ParticleEmitter boxVolume(int duration, Palette palette,
                                             float emission, float size, float speed,
                                             float scaleX, float scaleY, float scaleZ,
                                             float offsetY, int lifetime,
                                             boolean looping) {
        ParticleEmitter emitter = particle(
                duration, palette, emission, size, speed, looping);
        emitter.config.setStartLifetime(NumberFunction.constant((float) lifetime));
        emitter.config.setMaxParticles(384);
        Box box = new Box();
        emitter.config.shape.setShape(box);
        emitter.config.shape.setPosition(new NumberFunction3(0.0, offsetY, 0.0));
        emitter.config.shape.setScale(new NumberFunction3(scaleX, scaleY, scaleZ));
        return emitter;
    }

        private static ParticleEmitter boxShell(int duration, Palette palette,
                                                float emission, float size,
                                                float scaleX, float scaleY, float scaleZ,
                                                float offsetY, Box.Type type,
                                                boolean looping) {
            ParticleEmitter emitter = particle(
                duration, palette, emission, size, 0.025f, looping);
            emitter.config.setStartLifetime(NumberFunction.constant(10.0f));
            emitter.config.setMaxParticles(384);
            Box box = new Box();
            box.setEmitFrom(type);
            emitter.config.shape.setShape(box);
            emitter.config.shape.setPosition(new NumberFunction3(0.0, offsetY, 0.0));
            emitter.config.shape.setScale(new NumberFunction3(scaleX, scaleY, scaleZ));
            return emitter;
        }

        private static ParticleEmitter boxVolumeAt(int duration, Palette palette,
                                                   float emission, float size, float speed,
                                                   float scaleX, float scaleY, float scaleZ,
                                                   float offsetX, float offsetY, float offsetZ,
                                                   int lifetime, boolean looping) {
            ParticleEmitter emitter = particle(
                duration, palette, emission, size, speed, looping);
            emitter.config.setStartLifetime(NumberFunction.constant((float) lifetime));
            emitter.config.setMaxParticles(384);
            Box box = new Box();
            emitter.config.shape.setShape(box);
            emitter.config.shape.setPosition(new NumberFunction3(offsetX, offsetY, offsetZ));
            emitter.config.shape.setScale(new NumberFunction3(scaleX, scaleY, scaleZ));
            return emitter;
        }

        private static ParticleEmitter boxShellAt(int duration, Palette palette,
                                                  float emission, float size,
                                                  float scaleX, float scaleY, float scaleZ,
                                                  float offsetX, float offsetY, float offsetZ,
                                                  Box.Type type, boolean looping) {
            ParticleEmitter emitter = particle(
                duration, palette, emission, size, 0.025f, looping);
            emitter.config.setStartLifetime(NumberFunction.constant(10.0f));
            emitter.config.setMaxParticles(384);
            Box box = new Box();
            box.setEmitFrom(type);
            emitter.config.shape.setShape(box);
            emitter.config.shape.setPosition(new NumberFunction3(offsetX, offsetY, offsetZ));
            emitter.config.shape.setScale(new NumberFunction3(scaleX, scaleY, scaleZ));
            return emitter;
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
            case "water_gun_brace" -> new CastProfile(CastFamily.CHANNEL, 10);
            case "thunder_shock_snap" -> new CastProfile(CastFamily.BURST, 10);
            case "psychic_lift_cast" -> new CastProfile(CastFamily.GROUND, 7);
            case "protect_cast" -> new CastProfile(CastFamily.STANCE, 7);
            case "razor_leaf_fan" -> new CastProfile(CastFamily.VOLLEY, 8);
            case "leaf_blade_dash" -> new CastProfile(CastFamily.DASH, 7);
            case "poison_sting_volley" -> new CastProfile(CastFamily.VOLLEY, 9);
            case "rock_throw_heave" -> new CastProfile(CastFamily.VOLLEY, 10);
            case "ice_shard_snap" -> new CastProfile(CastFamily.VOLLEY, 11);
            case "fire_blast_star_cast" -> new CastProfile(CastFamily.BURST, 9);
            case "bubble_beam_stream" -> new CastProfile(CastFamily.VOLLEY, 12);
            case "petal_blizzard_sweep" -> new CastProfile(CastFamily.SWEEP, 6);
            case "solar_beam_charge" -> new CastProfile(CastFamily.CHANNEL, 12);
            case "stone_edge_raise" -> new CastProfile(CastFamily.GROUND, 8);
            case "discharge_field_cast" -> new CastProfile(CastFamily.GROUND, 9);
            case "dragon_pulse_coil" -> new CastProfile(CastFamily.FOCUS, 10);
            case "bullet_punch_flash" -> new CastProfile(CastFamily.DASH, 11);
            case "mach_punch_flash_step" -> new CastProfile(CastFamily.DASH, 9);
            case "focus_blast_compress" -> new CastProfile(CastFamily.FOCUS, 11);
            case "shadow_ball_orbit" -> new CastProfile(CastFamily.FOCUS, 12);
            case "fire_punch_swing" -> new CastProfile(CastFamily.SWEEP, 7);
            case "acid_spray_cast" -> new CastProfile(CastFamily.SWEEP, 8);
            case "bite_lunge_cast" -> new CastProfile(CastFamily.DASH, 10);
            case "crunch_lunge_cast" -> new CastProfile(CastFamily.DASH, 12);
            case "dragon_claw_swing" -> new CastProfile(CastFamily.SWEEP, 9);
            case "dragon_tail_sweep" -> new CastProfile(CastFamily.SWEEP, 10);
            case "drain_punch_swing" -> new CastProfile(CastFamily.SWEEP, 11);
            case "flame_charge_dash" -> new CastProfile(CastFamily.DASH, 13);
            case "force_palm_thrust" -> new CastProfile(CastFamily.SWEEP, 12);
            case "giga_drain_channel" -> new CastProfile(CastFamily.CHANNEL, 13);
            case "ice_punch_swing" -> new CastProfile(CastFamily.SWEEP, 13);
            case "icy_wind_sweep" -> new CastProfile(CastFamily.SWEEP, 14);
            case "iron_head_charge" -> new CastProfile(CastFamily.DASH, 14);
            case "lick_sweep_cast" -> new CastProfile(CastFamily.SWEEP, 15);
            case "metal_claw_cross" -> new CastProfile(CastFamily.VOLLEY, 13);
            case "psycho_cut_swing" -> new CastProfile(CastFamily.SWEEP, 16);
            case "seismic_toss_heave" -> new CastProfile(CastFamily.VOLLEY, 14);
            case "shadow_claw_swing" -> new CastProfile(CastFamily.SWEEP, 17);
            case "smack_down_cast" -> new CastProfile(CastFamily.VOLLEY, 15);
            case "snarl_wave_cast" -> new CastProfile(CastFamily.SWEEP, 18);
            case "spark_dash_cast" -> new CastProfile(CastFamily.DASH, 15);
            case "thunder_punch_swing" -> new CastProfile(CastFamily.SWEEP, 19);
            case "venoshock_compress" -> new CastProfile(CastFamily.FOCUS, 13);
            case "tackle_charge" -> new CastProfile(CastFamily.DASH, 16);
            case "hyper_beam_charge" -> new CastProfile(CastFamily.CHANNEL, 14);
            case "overheat_release" -> new CastProfile(CastFamily.BURST, 11);
            case "leech_seed_cast" -> new CastProfile(CastFamily.FOCUS, 14);
            case "powder_snow_cast" -> new CastProfile(CastFamily.SWEEP, 20);
            case "toxic_cast" -> new CastProfile(CastFamily.VOLLEY, 16);
            case "night_shade_cast" -> new CastProfile(CastFamily.FOCUS, 15);
            case "hex_cast" -> new CastProfile(CastFamily.FOCUS, 16);
            case "dragon_breath_channel" -> new CastProfile(CastFamily.CHANNEL, 15);
            case "outrage_frenzy" -> new CastProfile(CastFamily.DASH, 17);
            case "foul_play_cast" -> new CastProfile(CastFamily.STANCE, 5);
            case "flash_cannon_charge" -> new CastProfile(CastFamily.CHANNEL, 16);
            case "dragon_rush_charge" -> new CastProfile(CastFamily.DASH, 18);
            case "phantom_force_vanish" -> new CastProfile(CastFamily.DASH, 19);
            case "rock_tomb_cast" -> new CastProfile(CastFamily.GROUND, 10);
            case "stealth_rock_cast" -> new CastProfile(CastFamily.GROUND, 11);
            case "trick_room_cast" -> new CastProfile(CastFamily.STANCE, 6);
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