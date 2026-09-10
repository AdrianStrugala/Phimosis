package com.tensura.item;

import com.tensura.engine.SpellDefinition;
import com.tensura.engine.SpellIdAliases;
import com.tensura.engine.SpellRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        "swift", "hyper_voice", "water_gun", "thunder_shock", "psychic", "confusion",
        "razor_leaf", "leaf_blade", "poison_sting", "rock_throw", "ice_shard",
        "fire_blast", "bubble_beam", "petal_blizzard", "solar_beam", "stone_edge",
        "discharge", "dragon_pulse", "bullet_punch", "mach_punch", "focus_blast",
        "shadow_ball", "fire_punch", "acid_spray", "bite", "crunch", "dragon_claw",
        "dragon_tail", "drain_punch", "flame_charge", "force_palm", "giga_drain",
        "ice_punch", "icy_wind", "iron_head", "lick", "metal_claw", "psycho_cut",
        "seismic_toss", "shadow_claw", "smack_down", "snarl", "spark",
        "thunder_punch", "venoshock", "tackle", "hyper_beam", "overheat",
        "leech_seed", "powder_snow", "toxic", "night_shade", "hex",
        "dragon_breath", "outrage", "foul_play", "flash_cannon", "dragon_rush",
        "phantom_force", "rock_tomb", "stealth_rock", "trick_room"
    );

    public SpellItem(Properties props) {
        super(props);
    }

    public static ItemStack create(ResourceLocation spellId) {
        return create(spellId, SpellRegistry.get(spellId).orElse(null));
    }

    /**
     * Builds the stack from a definition the caller already has. The creative tab uses this
     * with a definition out of {@link com.tensura.client.ClientSpellCatalog}, since a client
     * connected to a dedicated server has no SpellRegistry to look one up in.
     */
    public static ItemStack create(ResourceLocation spellId, @Nullable SpellDefinition def) {
        ItemStack stack = new ItemStack(com.tensura.registry.TensuraItemRegistry.SPELL_ITEM.get());
        CompoundTag tag = new CompoundTag();
        tag.putString(NBT_SPELL_ID, spellId.toString());
        // Store school for client-side model selection (SpellRegistry is server-side only)
        if (def != null) {
            tag.putString(NBT_SCHOOL, def.school);
            if (def.delivery.hold_to_channel) {
                tag.putBoolean(NBT_HOLD_TO_CHANNEL, true);
                tag.putInt(NBT_CHANNEL_DURATION, def.delivery.duration_ticks);
            } else if ("channel_beam".equals(def.delivery.type) && def.cast_time_ticks > 0) {
                tag.putInt(NBT_CHANNEL_WINDUP, def.cast_time_ticks);
                tag.putInt(NBT_CHANNEL_DURATION, def.delivery.duration_ticks);
            }
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** Display rank of a school, for grouping spells in the creative tab. Unknown schools sort last. */
    public static int schoolOrder(String school) {
        int idx = SCHOOL_ORDER.indexOf(school);
        return idx < 0 ? SCHOOL_ORDER.size() : idx;
    }

    /** Number of known schools — anything {@code >=} this came back from {@link #schoolOrder} unknown. */
    public static int schoolCount() {
        return SCHOOL_ORDER.size();
    }

    @Nullable
    public static ResourceLocation getSpellId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(NBT_SPELL_ID)) return null;
        return SpellIdAliases.canonicalize(ResourceLocation.tryParse(tag.getString(NBT_SPELL_ID)));
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
        return Component.literal(SpellCasting.formatName(id.getPath()));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation id = getSpellId(stack);
        if (id == null) return;
        // On a client connected to a dedicated server SpellRegistry is empty, so fall back
        // to the copy of the definitions that ships in the jar.
        SpellDefinition def = SpellRegistry.get(id)
                .or(() -> com.tensura.client.ClientSpellCatalog.get(id))
                .orElse(null);
        if (def == null) return;

        tooltip.add(Component.literal("School: " + def.school));
        tooltip.add(Component.literal(def.cooldown_ticks > 0
                ? "Cooldown: " + (def.cooldown_ticks / 20) + "s"
                : "Cooldown: None"));
        if (def.charges > 1) {
            tooltip.add(Component.literal("Charges: " + def.charges));
        }
        // A self-centred area spell keeps its size in radius and leaves range at 0, so reading
        // range alone would advertise "Range: 0m" for something that covers 12 blocks.
        if (def.targeting.range > 0.0) {
            tooltip.add(Component.literal("Range: " + (int) def.targeting.range + "m"));
        } else if (def.targeting.radius > 0.0) {
            tooltip.add(Component.literal("Radius: " + (int) def.targeting.radius + "m"));
        } else {
            tooltip.add(Component.literal("Range: Self"));
        }
        tooltip.add(Component.literal(def.delivery.hold_to_channel
            ? "Use: Hold right-click"
            : def.delivery.charge_release
                ? "Use: Hold and release right-click"
                : "Use: Right-click"));
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
        return SpellCasting.use(level, player, hand, stack, id, channelOf(stack));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return SpellCasting.useDuration(channelOf(stack));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return SpellCasting.useAnimation(channelOf(stack));
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack,
                          int remainingUseDuration) {
        SpellCasting.onUseTick(level, entity, stack, remainingUseDuration,
                getSpellId(stack), channelOf(stack));
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity,
                             int timeCharged) {
        SpellCasting.releaseUsing(
            level, entity, getSpellId(stack), channelOf(stack), timeCharged);
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        SpellCasting.onStopUsing(entity.level(), entity, getSpellId(stack), channelOf(stack));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        SpellCasting.releaseUsing(level, entity, getSpellId(stack), channelOf(stack), 0);
        return stack;
    }

    /**
     * Channel timings for this stack, read from the NBT written at creation time with the
     * fallbacks that keep stacks predating those keys working. The focus resolves the same
     * thing from the spell definition instead — see {@link SpellCasting#fromDefinition}.
     */
    private static SpellCasting.Channel channelOf(ItemStack stack) {
        ResourceLocation spellId = getSpellId(stack);
        if (spellId != null) {
            SpellCasting.Channel definitionChannel = SpellCasting.fromDefinition(spellId);
            if (definitionChannel.chargeRelease()) return definitionChannel;
        }
        if (isHeldChannel(stack)) {
            return new SpellCasting.Channel(true, false, 0, getHeldChannelDuration(stack));
        }
        int windup = getChannelWindup(stack);
        return windup > 0
                ? new SpellCasting.Channel(false, false, windup, getChannelDuration(stack))
                : SpellCasting.Channel.NONE;
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

}
