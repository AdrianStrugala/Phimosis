package com.tensura.command;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tensura.data.PredatorAbsorption;
import com.tensura.data.PredatorData;
import com.tensura.engine.SpellRegistry;
import com.tensura.item.SpellItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
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
                .then(Commands.literal("givespell")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("spell", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                String spell = StringArgumentType.getString(ctx, "spell");
                                return giveSpell(ctx.getSource(), target, spell);
                            })
                        )
                    )
                )
                // Devour tree inner button: hand out a copy, then re-arm the node
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
                // Admin/debug: hand out a spell the player has already absorbed
                .then(Commands.literal("absorb_spell")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("spell", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                String spell = StringArgumentType.getString(ctx, "spell");
                                return giveAbsorbedSpell(target, spell);
                            })
                        )
                    )
                )
                // Admin/debug: mark every known spell as absorbed and light up the devour tree
                .then(Commands.literal("absorb_all")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            return absorbAll(ctx.getSource(), target);
                        })
                    )
                )
        );
    }

    private static int giveSpell(CommandSourceStack src, ServerPlayer target, String spellName) {
        ResourceLocation id = ResourceLocation.tryParse("tensura:" + spellName);
        if (id == null) {
            src.sendFailure(Component.literal("Invalid spell ID: " + spellName));
            return 0;
        }
        if (SpellRegistry.get(id).isEmpty()) {
            src.sendFailure(Component.literal("Unknown spell: " + spellName));
            return 0;
        }
        ItemStack item = SpellItem.create(id);
        target.addItem(item);
        src.sendSuccess(() -> Component.literal(
            "§aGave §e" + spellName + " §ato §f" + target.getName().getString()), true);
        return 1;
    }

    /**
     * Reward of the devour tree's inner button. Hands out a copy if the spell is absorbed,
     * then re-locks that button so it can be clicked again.
     *
     * The re-lock is deferred to the next server task on purpose: this runs from inside
     * puffish's own unlock handling, and locking the skill in the middle of that would
     * race with puffish writing the unlock down.
     */
    private static int devourRecover(ServerPlayer target, String spellName) {
        ResourceLocation id = ResourceLocation.tryParse("tensura:" + spellName);
        if (id == null || SpellRegistry.get(id).isEmpty()) return 0;

        if (PredatorData.hasAbsorbed(target, id)) {
            target.addItem(SpellItem.create(id));
            target.sendSystemMessage(Component.literal(
                    "§a[Predator] Odzyskano: §e" + PredatorAbsorption.prettyName(id)));
        } else {
            target.sendSystemMessage(Component.literal(
                    "§c[Predator] Nie pochłonąłeś jeszcze §e" + PredatorAbsorption.prettyName(id)
                    + "§c! Zabij odpowiedniego Pokémona."));
        }

        target.getServer().execute(() -> {
            if (!target.hasDisconnected()) PredatorAbsorption.lockDispenser(target, id);
        });
        return 1;
    }

    /**
     * Hands the player a copy of a spell they have already absorbed. Same guarantee as
     * the Predator Codex — never grants a spell that is not in the absorbed list.
     */
    private static int absorbAll(CommandSourceStack src, ServerPlayer target) {
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

    private static int giveAbsorbedSpell(ServerPlayer target, String spellName) {
        ResourceLocation id = ResourceLocation.tryParse("tensura:" + spellName);
        if (id == null || SpellRegistry.get(id).isEmpty()) return 0;

        if (!PredatorData.hasAbsorbed(target, id)) {
            target.sendSystemMessage(Component.literal(
                    "§c[Predator] Nie pochłonąłeś jeszcze §e" + spellName.replace("_", " ")
                    + "§c! Zabij odpowiedniego Pokemona."));
            return 0;
        }
        target.addItem(SpellItem.create(id));
        return 1;
    }

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
