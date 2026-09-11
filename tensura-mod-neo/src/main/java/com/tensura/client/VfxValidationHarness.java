package com.tensura.client;

import com.tensura.TensuraMod;
import com.tensura.network.SpellVfxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = TensuraMod.MOD_ID, value = Dist.CLIENT)
public final class VfxValidationHarness {
    private static final String[] NAMES = {
            "aqua-jet", "hydro-pump", "electro-ball",
            "thunder", "aurora-veil", "blizzard",
            "hyper-beam", "earthquake", "draco-meteor", "surf",
            "trick-room", "stealth-rock", "crunch", "solar-beam"
    };
    private static final boolean ENABLED = Boolean.getBoolean("tensura.vfxValidation");
    private static int warmupTicks = 100;
    private static int sceneTicks;
    private static int sceneIndex;
    private static boolean complete;

    private VfxValidationHarness() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || complete) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (warmupTicks-- > 0) {
            return;
        }

        if (sceneTicks == 0) {
            playScene(minecraft, sceneIndex);
        } else if (sceneTicks == 8) {
            capture(minecraft, NAMES[sceneIndex]);
        }

        sceneTicks++;
        if (sceneTicks < 60) {
            return;
        }

        sceneTicks = 0;
        sceneIndex++;
        if (sceneIndex >= NAMES.length) {
            complete = true;
            TensuraMod.LOGGER.info("VFX validation captured all {} scenes", NAMES.length);
        }
    }

    private static void playScene(Minecraft minecraft, int index) {
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getLookAngle().normalize();
        Vec3 origin = eye.add(look.scale(0.75));
        Vec3 target = eye.add(look.scale(8.0));
        Vec3 ground = new Vec3(target.x, minecraft.player.getY() + 0.15, target.z);

        switch (index) {
            case 0 -> {
                play("ribbon", "water_shell", "water", origin, target, 1.0f, 30);
                play("impact", "water_burst", "water", target, target, 1.4f, 24);
            }
            case 1 -> {
                play("beam", "water_spiral", "water", origin, target, 1.3f, 30);
                play("impact", "heavy_splash", "water", target, target, 2.0f, 30);
            }
            case 2 -> {
                Vec3 projectile = eye.add(look.scale(5.0));
                play("projectile", "electro_ball", "lightning",
                        projectile, projectile.add(look), 0.8f, 30);
                play("impact", "electric_burst", "lightning", target, target, 1.7f, 24);
            }
            case 3 -> {
                play("telegraph", "electric_ground_ring", "lightning",
                        ground, ground, 3.0f, 40);
                play("beam", "lightning_column", "lightning",
                        ground, ground.add(0.0, 10.0, 0.0), 0.7f, 30);
                play("aftermath", "electric_afterglow", "lightning",
                        ground, ground, 2.5f, 40);
            }
            case 4 -> {
                play("aura", "aurora_dome", "ice", ground, ground, 4.0f, 120);
                play("zone", "aurora_curtain", "ice", ground, ground, 3.5f, 120);
            }
            case 5 -> {
                play("zone", "snow_zone", "ice", ground, ground, 5.0f, 120);
                play("aftermath", "moving_blizzard", "ice", ground, ground, 5.0f, 120);
            }
                case 6 -> {
                play("attachment", "hyper_beam_charge", "physical",
                    origin, origin, 1.0f, 28);
                play("beam", "hyper_beam_core", "physical",
                    origin, target, 2.0f, 16);
                play("impact", "hyper_beam_blast", "physical",
                    target, target, 2.5f, 16);
                }
                case 7 -> {
                play("zone", "earthquake_first_ring", "earth",
                    ground, ground, 12.0f, 40);
                play("impact", "earthquake_fissure", "earth",
                    ground, ground, 12.0f, 16);
                play("aftermath", "earthquake_dust", "earth",
                    ground, ground, 12.0f, 80);
                }
                case 8 -> {
                Vec3 meteor = ground.add(0.0, 6.0, 0.0);
                play("telegraph", "meteor_shadow", "dragon",
                    ground, ground, 2.0f, 30);
                play("projectile", "dragon_meteor", "dragon",
                    meteor, ground, 1.5f, 30);
                play("projectile", "dragon_fire", "dragon",
                    meteor, ground, 1.2f, 30);
                play("impact", "dragon_crater", "dragon",
                    ground, ground, 3.0f, 20);
                }
                case 9 -> {
                play("wave", "water_front", "water",
                    origin, target, 7.0f, 20);
                play("wave", "surf_wave", "water",
                    origin, target, 7.0f, 30);
                play("impact", "heavy_splash", "water",
                    target, target, 2.0f, 20);
                }
                case 10 -> {
                play("zone", "trick_room_grid", "psychic",
                    ground, ground, 8.0f, 30);
                play("zone", "trick_room_cube", "psychic",
                    ground, ground, 8.0f, 120);
                play("impact", "trick_room_shift", "psychic",
                    ground, ground, 1.5f, 16);
                }
                case 11 -> {
                play("telegraph", "stealth_rock_runes", "earth",
                    ground, ground, 3.0f, 24);
                play("attachment", "stealth_rock_field", "earth",
                    ground, ground, 3.0f, 120);
                play("impact", "stealth_rock_shards", "earth",
                    target, target, 1.5f, 16);
                }
                case 12 -> {
                play("telegraph", "crunch_maw_warning", "shadow",
                    ground, ground, 2.5f, 20);
                play("impact", "crushing_jaws", "shadow",
                    ground, ground, 2.5f, 16);
                play("aftermath", "crunch_maw_residue", "shadow",
                    ground, ground, 2.5f, 30);
                }
                case 13 -> {
                play("attachment", "solar_beam_charge", "nature",
                    origin, origin, 1.0f, 36);
                play("telegraph", "solar_focus_ring", "nature",
                    ground, ground, 2.0f, 36);
                play("beam", "sunlit_stream", "nature",
                    origin, target, 1.4f, 20);
                play("impact", "solar_flare_burst", "nature",
                    target, target, 2.0f, 20);
                }
            default -> throw new IllegalArgumentException("Unknown VFX scene " + index);
        }
        TensuraMod.LOGGER.info("VFX validation playing {}", NAMES[index]);
    }

    private static void play(String shape, String style, String school,
                             Vec3 origin, Vec3 target, float radius, int durationTicks) {
        SpellVfxManager.accept(new SpellVfxPacket(
                shape, style, school, origin, target, radius,
                durationTicks, -1, false));
    }

    private static void capture(Minecraft minecraft, String name) {
        String fileName = "vfx-validation-" + name + ".png";
        Screenshot.grab(minecraft.gameDirectory, fileName, minecraft.getMainRenderTarget(),
                message -> TensuraMod.LOGGER.info("VFX validation screenshot {}: {}",
                        fileName, message.getString()));
    }
}