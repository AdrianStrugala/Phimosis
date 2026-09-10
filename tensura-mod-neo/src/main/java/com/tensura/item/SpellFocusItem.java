package com.tensura.item;

import com.tensura.engine.SpellDefinition;
import com.tensura.engine.SpellIdAliases;
import com.tensura.engine.SpellRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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

import java.util.ArrayList;
import java.util.List;

/**
 * One item holding several attuned spells with an active selection — the Witcher-sign model.
 *
 * Replaces "one item per spell": the player attunes spells from the Devour tree and switches
 * between them in the radial. Cooldowns stay per spell, so the durability bar shows the state
 * of whichever spell is active.
 *
 * Casting itself is untouched — everything goes through {@link SpellCasting}, the same code
 * path {@link SpellItem} uses.
 */
public class SpellFocusItem extends Item {

    public static final String NBT_SPELLS = "AttunedSpells";
    public static final String NBT_ACTIVE = "ActiveIndex";

    /** Empty slots are stored as an empty string, so the list keeps its slot positions. */
    private static final String EMPTY_SLOT = "";

    private final int maxSlots;

    public SpellFocusItem(Properties props, int maxSlots) {
        super(props);
        this.maxSlots = maxSlots;
    }

    public int getMaxSlots() {
        return maxSlots;
    }

    public static int maxSlotsOf(ItemStack stack) {
        return stack.getItem() instanceof SpellFocusItem focus ? focus.getMaxSlots() : 0;
    }

    // ── NBT access ───────────────────────────────────────────────────────────

    /** Attuned spells, always exactly {@code maxSlots} long. {@code null} marks a free slot. */
    public static List<ResourceLocation> getSpells(ItemStack stack) {
        int slots = maxSlotsOf(stack);
        List<ResourceLocation> result = new ArrayList<>(slots);

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        ListTag list = data == null ? new ListTag() : data.copyTag().getList(NBT_SPELLS, Tag.TAG_STRING);

        for (int i = 0; i < slots; i++) {
            result.add(parseSlot(i < list.size() ? list.getString(i) : EMPTY_SLOT));
        }
        return result;
    }

    @Nullable
    private static ResourceLocation parseSlot(String raw) {
        if (raw.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(raw);
        return id == null ? null : SpellIdAliases.canonicalize(id);
    }

    public static void setSpell(ItemStack stack, int slot, @Nullable ResourceLocation spellId) {
        int slots = maxSlotsOf(stack);
        if (slot < 0 || slot >= slots) return;

        List<ResourceLocation> spells = getSpells(stack);
        spells.set(slot, spellId);

        ListTag list = new ListTag();
        for (ResourceLocation id : spells) {
            list.add(StringTag.valueOf(id == null ? EMPTY_SLOT : id.toString()));
        }
        mutateTag(stack, tag -> tag.put(NBT_SPELLS, list));
    }

    public static int getActiveIndex(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : clampIndex(data.copyTag().getInt(NBT_ACTIVE), maxSlotsOf(stack));
    }

    private static int clampIndex(int index, int slots) {
        return index < 0 || index >= slots ? 0 : index;
    }

    public static void setActiveIndex(ItemStack stack, int slot) {
        if (slot < 0 || slot >= maxSlotsOf(stack)) return;
        mutateTag(stack, tag -> tag.putInt(NBT_ACTIVE, slot));
    }

    /**
     * The active spell, read without materialising the whole slot list.
     *
     * This runs from two {@code ItemProperties} functions, so it is called for every rendered
     * catalyst on every frame — building a list of five parsed ResourceLocations to throw four
     * of them away was pure garbage in the render loop.
     */
    @Nullable
    public static ResourceLocation getActiveSpell(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;

        CompoundTag tag = data.copyTag();
        ListTag list = tag.getList(NBT_SPELLS, Tag.TAG_STRING);
        int index = clampIndex(tag.getInt(NBT_ACTIVE), maxSlotsOf(stack));
        return index < list.size() ? parseSlot(list.getString(index)) : null;
    }

    /** First free slot, or -1 when the focus is full. */
    public static int firstFreeSlot(ItemStack stack) {
        List<ResourceLocation> spells = getSpells(stack);
        for (int i = 0; i < spells.size(); i++) {
            if (spells.get(i) == null) return i;
        }
        return -1;
    }

    /** First slot holding a spell, or -1 when the focus is empty. */
    public static int firstAttunedSlot(ItemStack stack) {
        List<ResourceLocation> spells = getSpells(stack);
        for (int i = 0; i < spells.size(); i++) {
            if (spells.get(i) != null) return i;
        }
        return -1;
    }

    private static void mutateTag(ItemStack stack, java.util.function.Consumer<CompoundTag> mutation) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        mutation.accept(tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // ── Model properties ─────────────────────────────────────────────────────

    /**
     * No school model applies — an empty catalyst, or a spell whose school has no model. The
     * item then falls through to its own texture.
     *
     * It has to be negative. Model overrides match on {@code value >= predicate} and the last
     * match wins, so 0 would pick the physical model and anything past the end of the school
     * list would pick the last one (steel).
     */
    private static final float NO_SCHOOL_MODEL = -1f;

    public static float getSchoolIndex(ItemStack stack) {
        ResourceLocation id = getActiveSpell(stack);
        if (id == null) return NO_SCHOOL_MODEL;
        SpellDefinition def = definitionOf(id);
        if (def == null) return NO_SCHOOL_MODEL;
        int order = SpellItem.schoolOrder(def.school);
        return order >= SpellItem.schoolCount() ? NO_SCHOOL_MODEL : order;
    }

    public static float getIconIndex(ItemStack stack) {
        ResourceLocation id = getActiveSpell(stack);
        if (id == null) return 0f;
        int index = SpellItem.CUSTOM_ICON_ORDER.indexOf(id.getPath());
        return index < 0 ? 0f : index + 1f;
    }

    /** Server registry first, jar catalog as the client-side fallback. */
    @Nullable
    public static SpellDefinition definitionOf(ResourceLocation spellId) {
        return SpellRegistry.get(spellId)
                .or(() -> com.tensura.client.ClientSpellCatalog.get(spellId))
                .orElse(null);
    }

    // ── Display ──────────────────────────────────────────────────────────────

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation active = getActiveSpell(stack);
        if (active == null) return super.getName(stack);
        return Component.literal(SpellCasting.formatName(active.getPath()));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        List<ResourceLocation> spells = getSpells(stack);
        int active = getActiveIndex(stack);

        for (int i = 0; i < spells.size(); i++) {
            ResourceLocation id = spells.get(i);
            String marker = i == active ? "§e▶ " : "§8  ";
            tooltip.add(Component.literal(id == null
                    ? marker + "§8— pusty —"
                    : marker + "§f" + SpellCasting.formatName(id.getPath())));
        }

        ResourceLocation activeId = spells.get(active);
        if (activeId != null) {
            SpellDefinition def = definitionOf(activeId);
            if (def != null) {
                tooltip.add(Component.literal("§7Szkoła: §f" + def.school));
                tooltip.add(Component.literal(def.cooldown_ticks > 0
                        ? "§7Cooldown: §f" + (def.cooldown_ticks / 20) + "s"
                        : "§7Cooldown: §fbrak"));
            }
        }
        tooltip.add(Component.literal("§8Przytrzymaj R — wybór zaklęcia"));
        tooltip.add(Component.literal("§8Shift + scroll — zmiana slotu w biegu"));
    }

    // ── Cooldown bar, mirroring SpellItem ────────────────────────────────────

    @Override
    public boolean isBarVisible(ItemStack stack) {
        if (!net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) return false;
        ResourceLocation id = getActiveSpell(stack);
        return id != null && com.tensura.client.ClientCooldownTracker.isOnCooldown(id);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        ResourceLocation id = getActiveSpell(stack);
        if (id == null) return 0;
        return Math.round(com.tensura.client.ClientCooldownTracker.getRemainingFraction(id) * 13f);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float f = com.tensura.client.ClientCooldownTracker.getRemainingFraction(getActiveSpell(stack));
        int r = Math.round(255 * f);
        int g = Math.round(255 * (1f - f));
        return (r << 16) | (g << 8);
    }

    // ── Casting — delegated wholesale to SpellCasting ────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ResourceLocation spellId = getActiveSpell(stack);

        // Nothing bound to cast — open the picker instead. The client-only call sits behind
        // the side check, so the class is never resolved on a dedicated server.
        if (spellId == null) {
            if (level.isClientSide) com.tensura.client.ClientPacketHandlers.openRadialForSelect();
            return InteractionResultHolder.consume(stack);
        }

        return SpellCasting.use(level, player, hand, stack, spellId, SpellCasting.fromDefinition(spellId));
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
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        SpellCasting.onUseTick(level, entity, stack, remainingUseDuration,
                getActiveSpell(stack), channelOf(stack));
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        SpellCasting.releaseUsing(
            level, entity, getActiveSpell(stack), channelOf(stack), timeCharged);
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        SpellCasting.onStopUsing(entity.level(), entity, getActiveSpell(stack), channelOf(stack));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        SpellCasting.releaseUsing(level, entity, getActiveSpell(stack), channelOf(stack), 0);
        return stack;
    }

    private static SpellCasting.Channel channelOf(ItemStack stack) {
        ResourceLocation id = getActiveSpell(stack);
        return id == null ? SpellCasting.Channel.NONE : SpellCasting.fromDefinition(id);
    }
}
