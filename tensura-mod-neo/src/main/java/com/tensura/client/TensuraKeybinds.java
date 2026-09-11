package com.tensura.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tensura.TensuraMod;
import com.tensura.item.SpellCasting;
import com.tensura.item.SpellFocusItem;
import com.tensura.network.SetActiveSpellPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Catalyst controls. Holding the key opens the radial, Shift+scroll cycles slots without
 * opening anything — the fast path for mid-fight switching.
 */
@OnlyIn(Dist.CLIENT)
public final class TensuraKeybinds {

    public static final String CATEGORY = "key.categories.tensura";

    public static final KeyMapping OPEN_RADIAL = new KeyMapping(
            "key.tensura.open_radial",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY);

    private TensuraKeybinds() {}

    @EventBusSubscriber(modid = TensuraMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class Registration {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_RADIAL);
        }
    }

    @EventBusSubscriber(modid = TensuraMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static class Handlers {

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null || mc.player == null) return;
            if (!OPEN_RADIAL.isDown()) return;
            if (SpellCasting.findFocus(mc.player) == null) return;

            // The radial polls this binding to know when the player lets go.
            mc.setScreen(new SpellRadialScreen(null, OPEN_RADIAL.getKey(), null));
        }

        /** Shift+scroll walks the slots and swallows the hotbar scroll while it does. */
        @SubscribeEvent
        public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || !player.isShiftKeyDown()) return;

            ItemStack focus = SpellCasting.findFocus(player);
            if (focus == null) return;

            int slots = SpellFocusItem.maxSlotsOf(focus);
            if (slots <= 0) return;

            int direction = event.getScrollDeltaY() > 0 ? -1 : 1;
            int next = Math.floorMod(SpellFocusItem.getActiveIndex(focus) + direction, slots);

            // Applied locally as well so the held item updates on the same frame; the server
            // is the one that persists it.
            SpellFocusItem.setActiveIndex(focus, next);
            PacketDistributor.sendToServer(new SetActiveSpellPacket(next));
            event.setCanceled(true);
        }
    }
}
