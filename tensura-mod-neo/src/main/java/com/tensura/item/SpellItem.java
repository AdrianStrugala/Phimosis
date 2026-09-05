package com.tensura.item;

import com.tensura.engine.SpellExecutor;
import com.tensura.engine.SpellRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SpellItem extends Item {

    public static final String NBT_SPELL_ID = "SpellId";
    public static final String NBT_SCHOOL   = "School";

    private static final java.util.List<String> SCHOOL_ORDER = java.util.List.of(
        "physical", "lightning", "fire", "water", "ice",
        "shadow", "psychic", "dragon", "nature", "poison",
        "earth", "wind", "fairy", "steel"
    );

    public SpellItem(Properties props) {
        super(props);
    }

    public static ItemStack create(ResourceLocation spellId) {
        ItemStack stack = new ItemStack(com.tensura.registry.TensuraItemRegistry.SPELL_ITEM.get());
        CompoundTag tag = new CompoundTag();
        tag.putString(NBT_SPELL_ID, spellId.toString());
        // Store school for client-side model selection (SpellRegistry is server-side only)
        SpellRegistry.get(spellId).ifPresent(def -> tag.putString(NBT_SCHOOL, def.school));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @Nullable
    public static ResourceLocation getSpellId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(NBT_SPELL_ID)) return null;
        return ResourceLocation.tryParse(tag.getString(NBT_SPELL_ID));
    }

    public static float getSchoolIndex(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0f;
        String school = data.copyTag().getString(NBT_SCHOOL);
        int idx = SCHOOL_ORDER.indexOf(school);
        return idx < 0 ? 0f : (float) idx;
    }

    public static float getIconIndex(ItemStack stack) {
        ResourceLocation spellId = getSpellId(stack);
        if (spellId == null) return 0f;

        return switch (spellId.getPath()) {
            case "flamethrower" -> 1f;
            case "surf" -> 2f;
            case "toxic_spikes" -> 3f;
            case "close_combat" -> 4f;
            case "shadow_sneak" -> 5f;
            case "psybeam" -> 6f;
            case "volt_tackle" -> 7f;
            case "fire_spin" -> 8f;
            case "rock_slide" -> 9f;
            case "recover" -> 10f;
            case "dark_pulse" -> 11f;
            default -> 0f;
        };
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation id = getSpellId(stack);
        if (id == null) return Component.literal("Unknown Skill");
        return Component.literal(formatName(id.getPath()));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation id = getSpellId(stack);
        if (id == null) return;
        SpellRegistry.get(id).ifPresent(def -> {
            tooltip.add(Component.literal("School: " + def.school));
            tooltip.add(Component.literal("Cooldown: " + (def.cooldown_ticks / 20) + "s"));
            if (def.charges > 1) {
                tooltip.add(Component.literal("Charges: " + def.charges));
            }
            tooltip.add(Component.literal("Range: " + (int) def.targeting.range + "m"));
            tooltip.add(Component.literal("Use: Right-click"));
        });
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        if (!net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) return false;
        ResourceLocation id = getSpellId(stack);
        if (id == null) return false;
        return com.tensura.client.ClientCooldownTracker.isOnCooldown(id);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        ResourceLocation id = getSpellId(stack);
        if (id == null) return 0;
        return Math.round(com.tensura.client.ClientCooldownTracker.getRemainingFraction(id) * 13f);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        // Red → green based on remaining cooldown
        float f = com.tensura.client.ClientCooldownTracker.getRemainingFraction(getSpellId(stack));
        int r = Math.round(255 * f);
        int g = Math.round(255 * (1f - f));
        return (r << 16) | (g << 8);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        ResourceLocation id = getSpellId(stack);
        if (id == null) return InteractionResultHolder.fail(stack);

        if (player instanceof ServerPlayer sp) {
            SpellExecutor.cast(sp, id);
        }
        return InteractionResultHolder.success(stack);
    }

    private static String formatName(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1));
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }
}
