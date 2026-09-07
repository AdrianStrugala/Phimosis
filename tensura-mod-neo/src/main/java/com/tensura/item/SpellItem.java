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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SpellItem extends Item {

    public static final String NBT_SPELL_ID = "SpellId";
    public static final String NBT_SCHOOL   = "School";
    public static final String NBT_CHANNEL_WINDUP = "ChannelWindup";
    public static final String NBT_CHANNEL_DURATION = "ChannelDuration";
    public static final String NBT_HOLD_TO_CHANNEL = "HoldToChannel";

    private static final java.util.Set<String> HELD_CHANNEL_SPELLS = java.util.Set.of(
        "flamethrower", "ice_beam", "psybeam", "hydro_pump", "dragon_breath"
    );

    private static final java.util.List<String> SCHOOL_ORDER = java.util.List.of(
        "physical", "lightning", "fire", "water", "ice",
        "shadow", "psychic", "dragon", "nature", "poison",
        "earth", "wind", "fairy", "steel"
    );

    public static final java.util.List<String> CUSTOM_ICON_ORDER = java.util.List.of(
        "flamethrower", "surf", "toxic_spikes", "close_combat", "shadow_sneak",
        "psybeam", "volt_tackle", "fire_spin", "rock_slide", "recover", "dark_pulse",
        "aqua_jet", "aurora_veil", "blizzard", "draco_meteor", "electro_ball",
        "ember", "future_sight", "hydro_pump", "ice_beam", "iron_defense",
        "quick_attack", "rest", "string_shot", "sucker_punch", "thunder",
        "tri_attack", "vine_whip", "whirlpool", "pin_missile", "u_turn",
        "x_scissor", "bug_buzz", "mud_shot", "bulldoze", "dig", "earth_power",
        "earthquake", "fairy_wind", "draining_kiss", "charm", "dazzling_gleam",
        "moonblast", "gust", "air_cutter", "aerial_ace", "tailwind", "hurricane",
        "swift", "hyper_voice", "water_gun", "thundershock", "psychic", "confusion",
        "razor_leaf", "leaf_blade", "will_o_wisp", "poison_sting", "rock_throw",
        "ice_shard", "thunderbolt", "fire_blast", "scald", "bubble_beam",
        "energy_ball", "petal_blizzard", "solar_beam", "stone_edge", "discharge",
        "sacred_fire", "dragon_pulse", "iron_strike", "mach_punch", "focus_blast",
        "shadow_ball"
    );

    public SpellItem(Properties props) {
        super(props);
    }

    public static ItemStack create(ResourceLocation spellId) {
        ItemStack stack = new ItemStack(com.tensura.registry.TensuraItemRegistry.SPELL_ITEM.get());
        CompoundTag tag = new CompoundTag();
        tag.putString(NBT_SPELL_ID, spellId.toString());
        // Store school for client-side model selection (SpellRegistry is server-side only)
        SpellRegistry.get(spellId).ifPresent(def -> {
            tag.putString(NBT_SCHOOL, def.school);
            if (def.delivery.hold_to_channel) {
                tag.putBoolean(NBT_HOLD_TO_CHANNEL, true);
                tag.putInt(NBT_CHANNEL_DURATION, def.delivery.duration_ticks);
            } else if ("channel_beam".equals(def.delivery.type) && def.cast_time_ticks > 0) {
                tag.putInt(NBT_CHANNEL_WINDUP, def.cast_time_ticks);
                tag.putInt(NBT_CHANNEL_DURATION, def.delivery.duration_ticks);
            }
        });
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

        int index = CUSTOM_ICON_ORDER.indexOf(spellId.getPath());
        return index < 0 ? 0f : index + 1f;
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
            tooltip.add(Component.literal(def.cooldown_ticks > 0
                    ? "Cooldown: " + (def.cooldown_ticks / 20) + "s"
                    : "Cooldown: None"));
            if (def.charges > 1) {
                tooltip.add(Component.literal("Charges: " + def.charges));
            }
            tooltip.add(Component.literal("Range: " + (int) def.targeting.range + "m"));
            tooltip.add(Component.literal(def.delivery.hold_to_channel
                    ? "Use: Hold right-click" : "Use: Right-click"));
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
        ResourceLocation id = getSpellId(stack);
        if (id == null) return InteractionResultHolder.fail(stack);

        if (isHeldChannel(stack)) {
            player.startUsingItem(hand);
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                    && !SpellExecutor.castHeldChannel(serverPlayer, id)) {
                player.stopUsingItem();
                return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.consume(stack);
        }

        if (getChannelWindup(stack) > 0) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        if (level.isClientSide) return InteractionResultHolder.success(stack);

        if (player instanceof ServerPlayer sp) {
            SpellExecutor.cast(sp, id);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        if (isHeldChannel(stack)) return getHeldChannelDuration(stack);
        int windup = getChannelWindup(stack);
        return windup > 0 ? windup + getChannelDuration(stack) : 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return isHeldChannel(stack) || getChannelWindup(stack) > 0
                ? UseAnim.SPEAR : UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack,
                          int remainingUseDuration) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (isHeldChannel(stack)) return;

        int windup = getChannelWindup(stack);
        int elapsed = getUseDuration(stack, entity) - remainingUseDuration;
        if (windup > 0 && elapsed == windup) {
            ResourceLocation spellId = getSpellId(stack);
            if (spellId == null || !SpellExecutor.castPrepared(player, spellId)) {
                player.stopUsingItem();
            }
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity,
                             int timeCharged) {
        finishHeldChannel(stack, level, entity);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        finishHeldChannel(stack, level, entity);
        return stack;
    }

    private static void finishHeldChannel(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)
                || !isHeldChannel(stack)) return;
        ResourceLocation spellId = getSpellId(stack);
        if (spellId == null) return;
        com.tensura.event.SpellRuntimeController.stopPlayerChannels(player.getUUID());
        SpellExecutor.finishHeldChannel(player, spellId);
    }

    private static boolean isHeldChannel(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().getBoolean(NBT_HOLD_TO_CHANNEL)) return true;
        ResourceLocation spellId = getSpellId(stack);
        return spellId != null && HELD_CHANNEL_SPELLS.contains(spellId.getPath());
    }

    private static int getHeldChannelDuration(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            if (tag.contains(NBT_CHANNEL_DURATION)) {
                int configured = tag.getInt(NBT_CHANNEL_DURATION);
                return configured > 0 ? configured : Integer.MAX_VALUE;
            }
        }
        ResourceLocation spellId = getSpellId(stack);
        if (spellId == null) return 1;
        return switch (spellId.getPath()) {
            case "hydro_pump" -> 200;
            case "dragon_breath" -> Integer.MAX_VALUE;
            case "flamethrower", "ice_beam", "psybeam" -> 100;
            default -> 1;
        };
    }

    private static int getChannelWindup(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        CompoundTag tag = data.copyTag();
        int configured = tag.getInt(NBT_CHANNEL_WINDUP);
        ResourceLocation spellId = getSpellId(stack);
        return configured > 0 || spellId == null || !"hydro_pump".equals(spellId.getPath())
                ? configured : 16;
    }

    private static int getChannelDuration(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 1;
        int configured = data.copyTag().getInt(NBT_CHANNEL_DURATION);
        ResourceLocation spellId = getSpellId(stack);
        if (spellId != null && "hydro_pump".equals(spellId.getPath())) {
            configured = Math.max(configured, 200);
        }
        return Math.max(1, configured > 0 ? configured : 24);
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
