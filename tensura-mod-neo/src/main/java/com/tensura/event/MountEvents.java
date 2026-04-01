package com.tensura.event;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.TensuraMod;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles mounting players onto large companion Pokémon.
 *
 * Trigger: Shift+right-click on a tensura:combat_companion.
 * Size gate: width >= 1.0 && height >= 1.2 (covers quadrupeds, large dragons).
 * Input: vanilla ServerboundPlayerInputPacket already sets player.xxa / player.zza
 *        on the server when riding — no custom packet needed.
 * Flying: species canFly flag from Cobblemon's behaviour data.
 */
public class MountEvents {

    // ownerUUID → can this mount fly
    private static final Map<UUID, Boolean> riderCanFly = new HashMap<>();
    // ownerUUID → noGravity state before mounting (so we can restore it)
    private static final Map<UUID, Boolean> preMountNoGravity = new HashMap<>();

    // ── Mounting trigger ────────────────────────────────────────────────────

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isShiftKeyDown()) return;
        if (!(event.getTarget() instanceof PokemonEntity pokemon)) return;
        if (!pokemon.getTags().contains("tensura:combat_companion")) return;

        // Only the owner may mount
        UUID ownerUUID = pokemon.getOwnerUUID();
        if (ownerUUID == null || !ownerUUID.equals(player.getUUID())) return;

        // Size gate: large quadrupeds / dragons only
        if (!isRideable(pokemon)) {
            TensuraMod.LOGGER.debug("[Tensura] {} tried to mount {} but it's too small ({}x{})",
                    player.getName().getString(),
                    pokemon.getPokemon().getSpecies().getName(),
                    pokemon.getBbWidth(), pokemon.getBbHeight());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        // Already riding something
        if (player.isPassenger()) return;

        // Detect flying capability (use reflection for Cobblemon API version safety)
        boolean canFly = false;
        try {
            Object behaviour = pokemon.getBehaviour();
            Object moving = behaviour.getClass().getMethod("getMoving").invoke(behaviour);
            Object fly = moving.getClass().getMethod("getFly").invoke(moving);
            Object canFlyVal = fly.getClass().getMethod("getCanFly").invoke(fly);
            canFly = Boolean.TRUE.equals(canFlyVal);
        } catch (Throwable e) {
            TensuraMod.LOGGER.warn("[Tensura] Could not read fly behaviour for {}: {}",
                    pokemon.getPokemon().getSpecies().getName(), e.getMessage());
        }

        boolean mounted = player.startRiding(pokemon, true);
        if (mounted) {
            preMountNoGravity.put(player.getUUID(), pokemon.isNoGravity());
            if (canFly) pokemon.setNoGravity(true);
            riderCanFly.put(player.getUUID(), canFly);

            TensuraMod.LOGGER.debug("[Tensura] {} mounted {} (canFly={}, size={}x{})",
                    player.getName().getString(),
                    pokemon.getPokemon().getSpecies().getName(),
                    canFly, pokemon.getBbWidth(), pokemon.getBbHeight());
        }

        // Cancel event regardless — prevents Cobblemon's own interactMob GUI
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    // ── Server tick: apply movement ─────────────────────────────────────────

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {

        for (UUID uuid : riderCanFly.keySet()) {
            PokemonEntity vehicle = CombatCompanionEvents.getCompanion(uuid);
            if (vehicle == null || !vehicle.isAlive()) {
                continue; // will be cleaned up via recall / leave event
            }

            // Find the riding player among passengers
            Player rider = null;
            for (var passenger : vehicle.getPassengers()) {
                if (passenger instanceof Player p && p.getUUID().equals(uuid)) {
                    rider = p;
                    break;
                }
            }

            if (rider == null) {
                // Player dismounted (vanilla shift-to-dismount)
                cleanupRider(uuid, vehicle);
                continue;
            }

            applyMovement(vehicle, rider, riderCanFly.get(uuid));
        }
    }

    // ── Cleanup on disconnect ───────────────────────────────────────────────

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        PokemonEntity vehicle = CombatCompanionEvents.getCompanion(uuid);
        if (vehicle != null) vehicle.ejectPassengers();
        cleanupRider(uuid, vehicle);
    }

    // ── Called from CombatCompanionEvents when Pokémon is recalled ──────────

    public static void onCompanionRecalled(UUID ownerUUID) {
        cleanupRider(ownerUUID, null); // ejectPassengers is called by the caller
    }

    // ── Movement ────────────────────────────────────────────────────────────

    private void applyMovement(PokemonEntity vehicle, Player rider, boolean canFly) {
        // Sync rotation
        vehicle.setYRot(rider.getYRot());
        vehicle.yBodyRot = rider.getYRot();
        vehicle.yHeadRot = rider.getYRot();

        // Vanilla ServerboundPlayerInputPacket sets rider.xxa (strafe) and rider.zza (forward)
        // when the player is riding — same values horses/pigs read from the rider.
        float forward = rider.zza;
        float strafe  = rider.xxa;

        float speed = canFly ? 0.45f : 0.25f;

        // Rotate input vector by yaw (mirrors Entity.getInputVector / AbstractHorse logic)
        float yawRad = rider.getYRot() * (float)(Math.PI / 180.0);
        double mx = (strafe * Mth.cos(yawRad) - forward * Mth.sin(yawRad)) * speed;
        double mz = (strafe * Mth.sin(yawRad) + forward * Mth.cos(yawRad)) * speed;

        double my;
        if (canFly) {
            if (isJumping(rider)) {
                my = 0.35;
            } else if (rider.isShiftKeyDown()) {
                my = -0.25;
            } else {
                my = 0.0; // hover
            }
        } else {
            // Keep gravity for ground mounts
            my = vehicle.onGround() ? 0.0 : vehicle.getDeltaMovement().y - 0.08;
        }

        // Stop navigation so Cobblemon's AI doesn't override our movement
        vehicle.getNavigation().stop();
        vehicle.move(MoverType.SELF, new Vec3(mx, my, mz));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static boolean isJumping(Player player) {
        try {
            java.lang.reflect.Field f = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("jumping");
            f.setAccessible(true);
            return f.getBoolean(player);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isRideable(PokemonEntity pokemon) {
        return pokemon.getBbWidth() >= 1.0f && pokemon.getBbHeight() >= 1.2f;
    }

    private static void cleanupRider(UUID playerUUID, PokemonEntity vehicle) {
        Boolean prev = preMountNoGravity.remove(playerUUID);
        if (vehicle != null && prev != null) {
            vehicle.setNoGravity(prev);
        }
        riderCanFly.remove(playerUUID);
    }
}
