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
import com.tensura.data.ConversionHelper;
import com.tensura.data.DynamicCitizenSpeciesData;
import kotlin.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
            if (!ConversionHelper.isColonyOwner(colony, owner)) {
                owner.sendSystemMessage(Component.literal("§cTylko właściciel kolonii może zamieniać Pokémony w citizenów."));
                return Unit.INSTANCE;
            }

            // ── Enroll ───────────────────────────────────────────────────────
            Pokemon poke = pokemon.getPokemon();
            CompoundTag pokemonNbt = poke.saveToNBT(level.registryAccess(), new CompoundTag());
            String species = poke.getSpecies().getName().toLowerCase();
            UUID ownerUUID = owner.getUUID();

            // Tag entity so CombatCompanionEvents skips companion AI
            pokemon.addTag("tensura:village_resident");

            // Defer storage mutation until Cobblemon has finished handling the send-out event.
            level.getServer().execute(() -> {
                PlayerPartyStore party;
                try {
                    party = Cobblemon.INSTANCE.getStorage().getParty(owner);
                } catch (Exception e) {
                    TensuraMod.LOGGER.warn("[Tensura] Failed to access party during enrollment: {}", e.getMessage());
                    pokemon.removeTag("tensura:village_resident");
                    return;
                }

                boolean removedFromParty;
                try {
                    removedFromParty = party.remove(poke);
                } catch (Exception e) {
                    TensuraMod.LOGGER.warn("[Tensura] Failed to remove Pokemon from party during enrollment", e);
                    removedFromParty = false;
                }
                if (!removedFromParty) {
                    pokemon.removeTag("tensura:village_resident");
                    owner.sendSystemMessage(Component.literal("§cNie udało się przenieść Pokémona do kolonii."));
                    return;
                }

                ICivilianData civilianData = null;
                int citizenId = -1;
                DynamicCitizenSpeciesData data = DynamicCitizenSpeciesData.get(level);
                try {
                    civilianData = colony.getCitizenManager().createAndRegisterCivilianData();
                    ICitizenData citizenData = (ICitizenData) civilianData;
                    citizenId = civilianData.getId();

                    citizenData.setName(poke.getDisplayName(false).getString());
                    civilianData.setGender(poke.getGender() == com.cobblemon.mod.common.pokemon.Gender.FEMALE);

                    var baseStats = poke.getSpecies().getBaseStats();
                    int pokeLevel = poke.getLevel();
                    ICitizenSkillHandler skills = citizenData.getCitizenSkillHandler();
                    setSkill(skills, Skill.Stamina,    baseStats.getOrDefault(Stats.HP, 45),              pokeLevel);
                    setSkill(skills, Skill.Strength,   baseStats.getOrDefault(Stats.ATTACK, 45),          pokeLevel);
                    setSkill(skills, Skill.Athletics,  baseStats.getOrDefault(Stats.DEFENCE, 45),         pokeLevel);
                    setSkill(skills, Skill.Mana,       baseStats.getOrDefault(Stats.SPECIAL_ATTACK, 45),  pokeLevel);
                    setSkill(skills, Skill.Knowledge,  baseStats.getOrDefault(Stats.SPECIAL_DEFENCE, 45), pokeLevel);
                    setSkill(skills, Skill.Agility,    baseStats.getOrDefault(Stats.SPEED, 45),           pokeLevel);
                    setSkill(skills, Skill.Dexterity,  baseStats.getOrDefault(Stats.SPEED, 45),           pokeLevel);

                    data.add(citizenId, species, pokemonNbt, ownerUUID, colony.getID());
                    colony.getCitizenManager().spawnOrCreateCitizen(citizenData, level, playerPos);
                    pokemon.remove(Entity.RemovalReason.DISCARDED);
                } catch (Exception e) {
                    if (citizenId >= 0) data.remove(citizenId);
                    if (civilianData != null) colony.getCitizenManager().removeCivilian(civilianData);
                    pokemon.removeTag("tensura:village_resident");
                    boolean restoredToParty;
                    try {
                        restoredToParty = party.add(poke);
                    } catch (Exception rollbackError) {
                        TensuraMod.LOGGER.error("[Tensura] Enrollment rollback failed for {}", species, rollbackError);
                        restoredToParty = false;
                    }
                    if (!restoredToParty) {
                        TensuraMod.LOGGER.error("[Tensura] Enrollment rollback could not restore {} to the party", species);
                    }
                    TensuraMod.LOGGER.error("[Tensura] Enrollment failed for {}", species, e);
                    owner.sendSystemMessage(Component.literal(restoredToParty
                            ? "§cNie udało się utworzyć citizena; Pokémon został przywrócony."
                            : "§4Nie udało się utworzyć citizena ani automatycznie przywrócić Pokémona. Sprawdź log serwera."));
                    return;
                }

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
        if (!(event.getItemStack().getItem() instanceof PokeBallItem)) return;
        if (!(event.getTarget() instanceof AbstractEntityCitizen citizen)) return;
        if (!(event.getEntity() instanceof ServerPlayer sender)) return;
        if (!(citizen.level() instanceof ServerLevel level)) return;

        event.setCanceled(true);

        var dataView = citizen.getCitizenDataView();
        if (dataView == null) {
            sender.sendSystemMessage(Component.literal("§cNie można odczytać danych tego citizena."));
            return;
        }
        int citizenId = dataView.getId();

        DynamicCitizenSpeciesData data = DynamicCitizenSpeciesData.get(level);

        IColony colony = citizen.getCitizenColonyHandler().getColony();
        if (colony == null) {
            Integer storedColonyId = data.colonyIdMap.get(citizenId);
            if (storedColonyId != null) {
                colony = IColonyManager.getInstance().getColonyByWorld(storedColonyId, level);
            }
        }
        if (colony == null || !ConversionHelper.isColonyOwner(colony, sender)) {
            sender.sendSystemMessage(Component.literal("§cTylko właściciel kolonii może przywracać jej citizenów."));
            return;
        }

        boolean enrolled = data.contains(citizenId);
        UUID recipientId = enrolled ? data.ownerMap.get(citizenId) : sender.getUUID();
        if (recipientId == null) {
            sender.sendSystemMessage(Component.literal("§cBrak informacji o właścicielu tego Pokémona."));
            return;
        }

        // For non-enrolled citizens we still need a mappable species — bail silently if none found
        var citizenSkills = citizen.getCitizenDataView() != null
                ? citizen.getCitizenDataView().getCitizenSkillHandler() : null;
        if (citizenSkills == null) {
            sender.sendSystemMessage(Component.literal("§cNie można odczytać umiejętności tego citizena."));
            return;
        }

        Pokemon restoredPokemon = ConversionHelper.buildRecalledPokemon(
                citizenId, citizen, citizenSkills, data, level.registryAccess());
        if (restoredPokemon == null) {
            sender.sendSystemMessage(Component.literal("§cBrak poprawnych danych Pokémona dla tego citizena."));
            return;
        }

        try {
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(recipientId, level.registryAccess());
            if (!party.add(restoredPokemon)) {
                sender.sendSystemMessage(Component.literal("§cNie ma miejsca na przywróconego Pokémona."));
                return;
            }
        } catch (Exception e) {
            TensuraMod.LOGGER.warn("[Tensura] Failed to restore Pokemon to party on recall: {}", e.getMessage());
            sender.sendSystemMessage(Component.literal("§cBłąd podczas recall — spróbuj ponownie."));
            return;
        }

        citizen.remove(Entity.RemovalReason.DISCARDED);
        ICivilianData civilianData = colony.getCitizenManager().getCivilian(citizenId);
        if (civilianData != null) {
            colony.getCitizenManager().removeCivilian(civilianData);
        }

        String speciesName = capitalize(restoredPokemon.getSpecies().getName());

        if (enrolled) {
            data.remove(citizenId);
        }
        ColonyStartupEvents.broadcastSpeciesMap(level);

        TensuraMod.LOGGER.info("[Tensura] Recalled citizen #{} by colony owner {} for Pokemon owner {}",
                citizenId, sender.getUUID(), recipientId);
        ServerPlayer recipient = level.getServer().getPlayerList().getPlayer(recipientId);
        if (recipient != null) {
            recipient.sendSystemMessage(Component.literal("\u00a7b" + speciesName + " powrócił do drużyny."));
        }
        if (!recipientId.equals(sender.getUUID())) {
            sender.sendSystemMessage(Component.literal("§aPokémon wrócił do drużyny pierwotnego właściciela."));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Scales a Pokemon base stat (0–255) and level (1–100) to a MineColonies skill level (1–100). */
    private static void setSkill(ICitizenSkillHandler handler, Skill skill, int baseStat, int pokeLevel) {
        int level = Math.max(1, (int)(baseStat / 255.0 * pokeLevel));
        handler.incrementLevel(skill, level);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
