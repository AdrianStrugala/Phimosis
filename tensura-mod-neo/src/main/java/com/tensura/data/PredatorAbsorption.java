package com.tensura.data;

import com.tensura.item.SpellItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The single place that performs a devour absorption: record it, drop the item,
 * mirror it onto the puffish_skills tree, tell the player.
 *
 * PredatorData.absorbed is the source of truth. The puffish node is a mirror —
 * if the command fails the absorption still stands, and PredatorSyncEvents brings
 * the tree back in line on the next login.
 */
public final class PredatorAbsorption {

    /**
     * Category id of the devour tree in the predator_skills datapack.
     *
     * Fully qualified on purpose: puffish only falls back to bare-path matching for
     * its own "puffish_skills" namespace, so a bare "devour" is not guaranteed to
     * resolve to this datapack's category.
     */
    public static final String DEVOUR_CATEGORY = "tensura:devour";

    private PredatorAbsorption() {}

    public static void absorb(ServerPlayer player, ResourceLocation spellId,
                              ServerLevel level, Vec3 pos) {
        PredatorData.markAbsorbed(player, spellId);
        level.addFreshEntity(new ItemEntity(level, pos.x, pos.y, pos.z, SpellItem.create(spellId)));
        unlockNode(player, spellId);
        player.sendSystemMessage(Component.literal(
                "§a[Predator] Pochłonięto: §e" + prettyName(spellId)));
    }

    public static void unlockNode(ServerPlayer player, ResourceLocation spellId) {
        runPuffish(player, "unlock", spellId);
    }

    public static void lockNode(ServerPlayer player, ResourceLocation spellId) {
        runPuffish(player, "lock", spellId);
    }

    /** Server command source = permission 4, so this works for non-op players too. */
    private static void runPuffish(ServerPlayer player, String op, ResourceLocation spellId) {
        player.getServer().getCommands().performPrefixedCommand(
                player.getServer().createCommandSourceStack(),
                "puffish_skills skills " + op + " "
                        + player.getGameProfile().getName() + " "
                        + DEVOUR_CATEGORY + " "
                        + spellId.getPath());
    }

    public static String prettyName(ResourceLocation spellId) {
        String[] parts = spellId.getPath().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
