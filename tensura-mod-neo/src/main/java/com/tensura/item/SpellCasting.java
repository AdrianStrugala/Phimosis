package com.tensura.item;

import com.tensura.engine.SpellDefinition;
import com.tensura.engine.SpellExecutor;
import com.tensura.engine.SpellRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Casting logic shared by {@link SpellItem} and {@link SpellFocusItem}.
 *
 * The two items disagree only about where the channel timings come from — SpellItem reads
 * the NBT written when the stack was created (with fallbacks for stacks that predate those
 * keys), the focus derives them from the spell definition. Everything after that decision
 * is identical, so it lives here and both items pass in a resolved {@link Channel}.
 */
public final class SpellCasting {

    /** Resolved channel timings for one cast. {@code NONE} means a plain instant cast. */
    public record Channel(boolean held, boolean chargeRelease, int windup, int duration) {
        public static final Channel NONE = new Channel(false, false, 0, 0);

        public boolean isChannel() {
            return held || chargeRelease || windup > 0;
        }
    }

    private SpellCasting() {}

    /**
     * The catalyst the player is casting with: main hand first, then offhand.
     *
     * The offhand lookup is what makes "sword in the right hand, magic in the shield slot"
     * work for the keybind and the radial. Vanilla already falls through to the offhand for
     * right-click on its own, so {@code use} does not need this.
     */
    @Nullable
    public static ItemStack findFocus(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof SpellFocusItem) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof SpellFocusItem) return off;
        return null;
    }

    /** Channel timings taken from the spell's own definition. Used by the focus. */
    public static Channel fromDefinition(ResourceLocation spellId) {
        SpellDefinition def = SpellRegistry.get(spellId)
                .or(() -> com.tensura.client.ClientSpellCatalog.get(spellId))
                .orElse(null);
        if (def == null || def.delivery == null) return Channel.NONE;

        if (def.delivery.charge_release) {
            return new Channel(false, true,
                Math.max(1, def.delivery.minimum_charge_ticks),
                Math.max(def.delivery.minimum_charge_ticks,
                    def.delivery.maximum_charge_ticks));
        }
        if (def.delivery.hold_to_channel) {
            int duration = def.delivery.duration_ticks > 0
                    ? def.delivery.duration_ticks : Integer.MAX_VALUE;
            return new Channel(true, false, 0, duration);
        }
        if ("channel_beam".equals(def.delivery.type) && def.cast_time_ticks > 0) {
                return new Channel(false, false, def.cast_time_ticks,
                    Math.max(1, def.delivery.duration_ticks));
        }
        return Channel.NONE;
    }

    // ── The shared item hooks ────────────────────────────────────────────────

    public static InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                         InteractionHand hand, ItemStack stack,
                                                         ResourceLocation spellId, Channel channel) {
        if (spellId == null) return InteractionResultHolder.fail(stack);

        if (channel.chargeRelease()) {
            player.startUsingItem(hand);
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                    && !SpellExecutor.beginCharge(serverPlayer, spellId)) {
                player.stopUsingItem();
                return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.consume(stack);
        }

        if (channel.held()) {
            player.startUsingItem(hand);
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                    && !SpellExecutor.castHeldChannel(serverPlayer, spellId)) {
                player.stopUsingItem();
                return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.consume(stack);
        }

        if (channel.windup() > 0) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        if (level.isClientSide) return InteractionResultHolder.success(stack);

        if (player instanceof ServerPlayer sp) {
            SpellExecutor.cast(sp, spellId);
        }
        return InteractionResultHolder.success(stack);
    }

    public static int useDuration(Channel channel) {
        if (channel.held() || channel.chargeRelease()) return channel.duration();
        return channel.windup() > 0 ? channel.windup() + channel.duration() : 0;
    }

    public static UseAnim useAnimation(Channel channel) {
        return UseAnim.NONE;
    }

    /** Fires the prepared cast the moment the windup elapses. No-op for held channels. */
    public static void onUseTick(Level level, LivingEntity entity, ItemStack stack,
                                 int remainingUseDuration, ResourceLocation spellId, Channel channel) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (channel.held() || channel.chargeRelease() || channel.windup() <= 0) return;

        int elapsed = useDuration(channel) - remainingUseDuration;
        if (elapsed != channel.windup()) return;

        if (spellId == null || !SpellExecutor.castPrepared(player, spellId)) {
            player.stopUsingItem();
        }
    }

    public static void finishHeldChannel(Level level, LivingEntity entity,
                                         ResourceLocation spellId, Channel channel) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player) || !channel.held()) return;
        if (spellId == null) return;
        com.tensura.event.SpellCastController.stopPlayerChannels(player.getUUID());
        SpellExecutor.finishHeldChannel(player, spellId);
    }

    public static void releaseUsing(Level level, LivingEntity entity,
                                    ResourceLocation spellId, Channel channel,
                                    int remainingUseDuration) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (spellId == null) return;
        if (channel.chargeRelease()) {
            SpellExecutor.castChargedProjectile(player);
            return;
        }
        finishHeldChannel(level, entity, spellId, channel);
    }

    /** Shared display helper: {@code fire_blast} → {@code Fire Blast}. */
    public static String formatName(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
