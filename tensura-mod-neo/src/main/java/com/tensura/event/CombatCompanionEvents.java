package com.tensura.event;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tensura.TensuraMod;
import com.tensura.engine.SpellTargetingRules;
import com.tensura.goal.AllyFollowGoal;
import com.tensura.goal.CompanionSpellGoal;
import com.tensura.spell.CobblemonMoveMapper;
import kotlin.Unit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CombatCompanionEvents {

    // ownerUUID → their active companion
    private static final Map<UUID, PokemonEntity> activeCompanions = new HashMap<>();

    public static void registerCobblemonHooks() {
        // Called once from TensuraMod constructor (after mod init)
        CobblemonEvents.POKEMON_SENT_POST.subscribe(Priority.NORMAL, event -> {
            PokemonEntity pokemon = event.getPokemonEntity();
            if (pokemon == null) return Unit.INSTANCE;

            // Only attach AI when sent out by a player
            if (!(pokemon.getOwner() instanceof ServerPlayer owner)) return Unit.INSTANCE;

            // ConversionEvents (HIGH priority) already handled this as a village enrollment
            if (pokemon.getTags().contains("tensura:village_resident")) return Unit.INSTANCE;

            // Build spell list from this Pokemon's current moveset
            List<ResourceLocation> spells = CobblemonMoveMapper.getSpellsForPokemon(pokemon);

            // Attach goals (goalSelector/targetSelector public via accesstransformer.cfg)
            pokemon.goalSelector.addGoal(2, new AllyFollowGoal(pokemon, owner, 1.2, 3, 24));
            pokemon.goalSelector.addGoal(4, new CompanionSpellGoal(pokemon, owner, spells));

            pokemon.addTag("tensura:combat_companion");
            activeCompanions.put(owner.getUUID(), pokemon);

            TensuraMod.LOGGER.debug("[Tensura] Companion AI attached to {} for {}",
                    pokemon.getPokemon().getSpecies().getName(), owner.getName().getString());
            return Unit.INSTANCE;
        });

        CobblemonEvents.POKEMON_RECALL_POST.subscribe(Priority.NORMAL, event -> {
            PokemonEntity oldEntity = event.getOldEntity();
            if (oldEntity == null) return Unit.INSTANCE;
            if (!(oldEntity.getOwner() instanceof ServerPlayer owner)) return Unit.INSTANCE;

            oldEntity.ejectPassengers();

            PokemonEntity companion = activeCompanions.remove(owner.getUUID());
            if (companion == null) return Unit.INSTANCE;

            companion.removeTag("tensura:combat_companion");
            companion.setTarget(null);
            TensuraMod.LOGGER.debug("[Tensura] Companion recalled for {}", owner.getName().getString());
            return Unit.INSTANCE;
        });
    }

    // ── Owner attacks something → companion targets it too ───────────────────

    @SubscribeEvent
    public void onOwnerAttacks(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        PokemonEntity companion = activeCompanions.get(player.getUUID());
        if (companion == null || !companion.isAlive()) return;

        LivingEntity attacked = event.getEntity();
        if (!SpellTargetingRules.canHarm(player, companion, attacked)) return;

        companion.setTarget(attacked);
    }

    // ── Something attacks the owner → companion retaliates ───────────────────

    @SubscribeEvent
    public void onOwnerHurt(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer owner)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        PokemonEntity companion = activeCompanions.get(owner.getUUID());
        if (companion == null || !companion.isAlive()) return;
        if (!SpellTargetingRules.canHarm(owner, companion, attacker)) return;

        companion.setTarget(attacker);
    }

    // ── Public accessor for other systems ────────────────────────────────────

    public static PokemonEntity getCompanion(UUID ownerUUID) {
        return activeCompanions.get(ownerUUID);
    }
}
