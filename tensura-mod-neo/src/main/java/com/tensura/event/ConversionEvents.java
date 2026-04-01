package com.tensura.event;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.item.PokeBallItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.ICivilianData;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenSkillHandler;
import com.tensura.TensuraMod;
import com.tensura.data.DynamicCitizenSpeciesData;
import kotlin.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.UUID;

/**
 * Handles Pokemon ↔ MineColonies citizen conversion.
 *
 * Enroll: throw Pokeball while standing INSIDE the Town Hall building
 *         → new citizen created, Pokemon removed from party.
 *
 * Recall: right-click a citizen-Pokemon while holding any Pokeball
 *         → citizen deleted, Pokemon restored to owner's party.
 */
public class ConversionEvents {

    public static void registerCobblemonHooks() {
        // HIGH priority so we run before CombatCompanionEvents (NORMAL)
        CobblemonEvents.POKEMON_SENT_POST.subscribe(Priority.HIGH, event -> {
            PokemonEntity pokemon = event.getPokemonEntity();
            if (pokemon == null) return Unit.INSTANCE;
            if (!(pokemon.getOwner() instanceof ServerPlayer owner)) return Unit.INSTANCE;
            if (!(owner.level() instanceof ServerLevel level)) return Unit.INSTANCE;

            BlockPos playerPos = owner.blockPosition();

            // Must be inside an actual Town Hall building
            IColony colony = IColonyManager.getInstance().getIColony(level, playerPos);
            if (colony == null || colony.getServerBuildingManager().getTownHall() == null) return Unit.INSTANCE;

            ITownHall townHall = colony.getServerBuildingManager().getTownHall();
            if (townHall == null || !townHall.isInBuilding(playerPos)) return Unit.INSTANCE;

            // ── Enroll ───────────────────────────────────────────────────────
            Pokemon poke = pokemon.getPokemon();
            CompoundTag pokemonNbt = poke.saveToNBT(level.registryAccess(), new CompoundTag());
            String species = poke.getSpecies().getName().toLowerCase();
            UUID ownerUUID = owner.getUUID();

            // Tag entity so CombatCompanionEvents skips companion AI
            pokemon.addTag("tensura:village_resident");

            // Create citizen data
            ICivilianData civilianData = colony.getCitizenManager().createAndRegisterCivilianData();
            ICitizenData citizenData = (ICitizenData) civilianData;
            int citizenId = civilianData.getId();

            // Set citizen name (Pokemon's display name)
            String name = poke.getDisplayName(false).getString();
            citizenData.setName(name);

            // Map base stats → citizen skills
            var baseStats = poke.getSpecies().getBaseStats();
            ICitizenSkillHandler skills = citizenData.getCitizenSkillHandler();
            setSkill(skills, Skill.Stamina,    baseStats.getOrDefault(Stats.HP, 45));
            setSkill(skills, Skill.Strength,   baseStats.getOrDefault(Stats.ATTACK, 45));
            setSkill(skills, Skill.Athletics,  baseStats.getOrDefault(Stats.DEFENCE, 45));
            setSkill(skills, Skill.Mana,       baseStats.getOrDefault(Stats.SPECIAL_ATTACK, 45));
            setSkill(skills, Skill.Knowledge,  baseStats.getOrDefault(Stats.SPECIAL_DEFENCE, 45));
            setSkill(skills, Skill.Agility,    baseStats.getOrDefault(Stats.SPEED, 45));
            setSkill(skills, Skill.Dexterity,  baseStats.getOrDefault(Stats.SPEED, 45));

            // Save to persistent data
            DynamicCitizenSpeciesData data = DynamicCitizenSpeciesData.get(level);
            data.add(citizenId, species, pokemonNbt, ownerUUID, colony.getID());

            // Schedule removal of party slot + entity removal + spawn + broadcast (next tick)
            level.getServer().execute(() -> {
                try {
                    PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(owner);
                    party.remove(poke);
                } catch (Exception e) {
                    TensuraMod.LOGGER.warn("[Tensura] Failed to remove Pokemon from party during enrollment: {}", e.getMessage());
                }
                pokemon.remove(Entity.RemovalReason.DISCARDED);

                // Spawn the citizen entity near the Town Hall
                colony.getCitizenManager().spawnOrCreateCitizen(citizenData, level, playerPos);

                // Push updated species map to all online players
                ColonyStartupEvents.broadcastSpeciesMap(level);

                TensuraMod.LOGGER.info("[Tensura] Enrolled {} as citizen #{} for player {}",
                        species, citizenId, owner.getName().getString());

                String displayName = capitalize(species);
                owner.sendSystemMessage(Component.literal("\u00a76" + displayName + " zamieszkał w wiosce."));
            });

            return Unit.INSTANCE;
        });
    }

    // ── Recall: right-click citizen-Pokemon with any Pokeball ─────────────────

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.OFF_HAND) return;
        if (!(event.getItemStack().getItem() instanceof PokeBallItem)) return;
        if (!(event.getTarget() instanceof AbstractEntityCitizen citizen)) return;
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        if (!(citizen.level() instanceof ServerLevel level)) return;

        var dataView = citizen.getCitizenDataView();
        if (dataView == null) return;
        int citizenId = dataView.getId();

        DynamicCitizenSpeciesData data = DynamicCitizenSpeciesData.get(level);
        if (!data.contains(citizenId)) return;

        // Cancel so the Pokeball item isn't thrown
        event.setCanceled(true);

        UUID ownerUUID = data.ownerMap.get(citizenId);
        CompoundTag pokemonNbt = data.pokemonNbt.get(citizenId);
        Integer colonyId = data.colonyIdMap.get(citizenId);
        if (pokemonNbt == null || ownerUUID == null || colonyId == null) return;

        // Restore Pokemon from saved NBT (all IVs, EVs, moveset, etc.)
        Pokemon restoredPokemon = new Pokemon();
        restoredPokemon.loadFromNBT(level.registryAccess(), pokemonNbt);

        // Add to owner's party
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUUID);
        if (owner != null) {
            try {
                PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(owner);
                party.add(restoredPokemon);
            } catch (Exception e) {
                TensuraMod.LOGGER.warn("[Tensura] Failed to restore Pokemon to party on recall: {}", e.getMessage());
            }
        }

        // Remove citizen from colony
        IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, level);
        if (colony != null) {
            ICivilianData civilianData = colony.getCitizenManager().getCivilian(citizenId);
            if (civilianData != null) {
                citizen.remove(Entity.RemovalReason.DISCARDED);
                colony.getCitizenManager().removeCivilian(civilianData);
            }
        } else {
            // Colony not found by ID — still remove the entity
            citizen.remove(Entity.RemovalReason.DISCARDED);
        }

        // Remove from data store and broadcast
        data.remove(citizenId);
        ColonyStartupEvents.broadcastSpeciesMap(level);

        TensuraMod.LOGGER.info("[Tensura] Recalled citizen #{} (owner={})", citizenId, ownerUUID);

        String speciesName = capitalize(data.dynamicSpecies.getOrDefault(citizenId, "Pokemon"));
        if (owner != null) {
            owner.sendSystemMessage(Component.literal("\u00a7b" + speciesName + " powrócił do drużyny."));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Scales a Pokemon base stat (0–255) to a MineColonies skill level (1–10). */
    private static void setSkill(ICitizenSkillHandler handler, Skill skill, int baseStat) {
        int level = Math.max(1, baseStat * 10 / 255);
        handler.incrementLevel(skill, level);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
