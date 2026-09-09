package com.tensura.command;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tensura.data.PredatorAbsorption;
import com.tensura.data.PredatorData;
import com.tensura.engine.SpellRegistry;
import com.tensura.item.SpellCasting;
import com.tensura.network.OpenRadialPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;

public class TensuraCommands {

    private static final String SPECIES_TAG_PREFIX = "tensura:species:";

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("tensura")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("convert")
                    .then(Commands.argument("species", StringArgumentType.word())
                        .executes(ctx -> {
                            String species = StringArgumentType.getString(ctx, "species");
                            return convertNearestCitizen(ctx.getSource(), species);
                        })
                    )
                )
                .then(Commands.literal("unconvert")
                    .executes(ctx -> unconvertNearestCitizen(ctx.getSource()))
                )
                // Progresja: oznacz zaklęcie jako pochłonięte i zapal węzeł w drzewku
                .then(Commands.literal("unlock")
                    .then(Commands.literal("spell")
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("spell", StringArgumentType.word())
                                .suggests(SPELL_SUGGESTIONS)
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    String spell = StringArgumentType.getString(ctx, "spell");
                                    return unlockSpell(ctx.getSource(), target, spell);
                                })
                            )
                        )
                    )
                    .then(Commands.literal("all")
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                return unlockAll(ctx.getSource(), target);
                            })
                        )
                    )
                )
                // Devour tree inner button: open the catalyst radial, then re-arm the node
                .then(Commands.literal("devour_recover")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("spell", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                String spell = StringArgumentType.getString(ctx, "spell");
                                return devourRecover(target, spell);
                            })
                        )
                    )
                )
        );
    }

    /**
     * Spell ids for tab completion, without the namespace — the same short form the
     * commands take. Read from the live registry, so it follows datapack reloads.
     */
    private static final SuggestionProvider<CommandSourceStack> SPELL_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    SpellRegistry.all().keySet().stream()
                            .map(ResourceLocation::getPath)
                            .sorted(),
                    builder);


    /**
     * Grants a spell the way devouring one would: record it in {@link PredatorData} and light
     * up its marker in the Devour tree. The player then attunes it to a catalyst themselves.
     *
     * This replaced "givespell", which handed out a SpellItem — with the catalyst, spells are
     * not items any more, so handing one out no longer represents progress.
     */
    private static int unlockSpell(CommandSourceStack src, ServerPlayer target, String spellName) {
        ResourceLocation id = ResourceLocation.tryParse("tensura:" + spellName);
        if (id == null) {
            src.sendFailure(Component.literal("Niepoprawne ID zaklecia: " + spellName));
            return 0;
        }
        if (SpellRegistry.get(id).isEmpty()) {
            src.sendFailure(Component.literal("Nieznane zaklecie: " + spellName));
            return 0;
        }

        boolean fresh = !PredatorData.hasAbsorbed(target, id);
        if (fresh) PredatorData.markAbsorbed(target, id);
        // Re-run even when it was already absorbed: the tree is a mirror and can drift.
        PredatorAbsorption.unlockOwned(target, id);

        String pretty = PredatorAbsorption.prettyName(id);
        src.sendSuccess(() -> Component.literal(
                "§aOdblokowano §e" + pretty + "§a dla §f"
                        + target.getName().getString()
                        + (fresh ? "" : " §7(mial juz wczesniej)")), true);
        if (fresh) {
            target.sendSystemMessage(Component.literal(
                    "§a[Predator] Odblokowano §e" + pretty
                    + "§a. Przypisz je do katalizatora w drzewku (K)."));
        }
        return 1;
    }

    /** Every known spell at once. Admin tool for testing a full roster. */
    private static int unlockAll(CommandSourceStack src, ServerPlayer target) {
        int newly = 0;
        for (ResourceLocation id : SpellRegistry.all().keySet()) {
            if (!PredatorData.hasAbsorbed(target, id)) {
                PredatorData.markAbsorbed(target, id);
                newly++;
            }
            PredatorAbsorption.unlockOwned(target, id);
        }
        int total = SpellRegistry.all().size();
        int added = newly;
        src.sendSuccess(() -> Component.literal(
                "§aOdblokowano §e" + total + "§a spelli dla §f"
                        + target.getName().getString() + "§7 (nowych: " + added + ")"), true);
        target.sendSystemMessage(Component.literal(
                "§a[Predator] Odblokowano wszystkie spelle (§e" + total + "§a)."));
        return total;
    }

    /**
     * Reward of the devour tree's inner button. Opens the catalyst radial with this spell on
     * the cursor if the player has absorbed it, then re-locks that button so it can be
     * clicked again.
     *
     * The re-lock is deferred to the next server task on purpose: this runs from inside
     * puffish's own unlock handling, and locking the skill in the middle of that would
     * race with puffish writing the unlock down.
     */
    private static int devourRecover(ServerPlayer target, String spellName) {
        ResourceLocation id = ResourceLocation.tryParse("tensura:" + spellName);
        if (id == null || SpellRegistry.get(id).isEmpty()) return 0;

        if (!PredatorData.hasAbsorbed(target, id)) {
            target.sendSystemMessage(Component.literal(
                    "§c[Predator] Nie pochłonąłeś jeszcze §e" + PredatorAbsorption.prettyName(id)
                    + "§c! Zabij odpowiedniego Pokémona."));
        } else if (SpellCasting.findFocus(target) == null) {
            // Nothing to assign to — say so rather than opening an empty screen.
            target.sendSystemMessage(Component.literal(
                    "§c[Katalizator] Weź katalizator do ręki lub w drugą rękę, żeby przypisać §e"
                    + PredatorAbsorption.prettyName(id)));
        } else {
            PacketDistributor.sendToPlayer(target, new OpenRadialPacket(id));
        }

        target.getServer().execute(() -> {
            if (!target.hasDisconnected()) PredatorAbsorption.lockDispenser(target, id);
        });
        return 1;
    }

    /**
     * Marks every known spell as absorbed and lights up the whole devour tree. Admin tool.
     */


    private static int convertNearestCitizen(CommandSourceStack src, String species) {
        ServerLevel level = src.getLevel();
        AABB box = new AABB(src.getPosition(), src.getPosition()).inflate(5);
        List<AbstractEntityCitizen> citizens = level.getEntitiesOfClass(AbstractEntityCitizen.class, box);
        if (citizens.isEmpty()) {
            src.sendFailure(Component.literal("No citizen within 5 blocks."));
            return 0;
        }
        AbstractEntityCitizen citizen = citizens.stream()
                .min((a, b) -> Double.compare(a.distanceToSqr(src.getPosition()), b.distanceToSqr(src.getPosition())))
                .orElse(null);
        if (citizen == null) return 0;

        citizen.getTags().stream()
                .filter(t -> t.startsWith(SPECIES_TAG_PREFIX))
                .toList()
                .forEach(citizen::removeTag);
        citizen.addTag(SPECIES_TAG_PREFIX + species.toLowerCase());

        src.sendSuccess(() -> Component.literal(
                "§aConverted §f" + citizen.getCustomName().getString()
                + " §a→ §e" + species + "§a. Rejoin or relog to see render."), true);
        return 1;
    }

    private static int unconvertNearestCitizen(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        AABB box = new AABB(src.getPosition(), src.getPosition()).inflate(5);
        List<AbstractEntityCitizen> citizens = level.getEntitiesOfClass(AbstractEntityCitizen.class, box);
        for (AbstractEntityCitizen citizen : citizens) {
            citizen.getTags().stream()
                    .filter(t -> t.startsWith(SPECIES_TAG_PREFIX))
                    .toList()
                    .forEach(citizen::removeTag);
        }
        src.sendSuccess(() -> Component.literal("§aReverted nearest citizens to normal."), true);
        return 1;
    }
}
