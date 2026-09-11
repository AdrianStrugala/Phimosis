package com.tensura.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandlers {

    /**
     * Opens the catalyst radial with one spell waiting to be dropped into a slot. Fired after
     * a click on a Devour tree node; without a catalyst on the player the server has already
     * said so on chat, so there is nothing to show.
     */
    public static void openRadialForAssign(ResourceLocation spellId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // The current screen is the Devour tree the player just clicked in; the radial hands
        // control back to it once the spell is placed.
        mc.setScreen(new SpellRadialScreen(spellId, null, mc.screen));
    }

    /**
     * Opens the radial with nothing on the cursor, for a right-click on a catalyst whose
     * active slot is empty. No key is being held, so the screen closes on the click that
     * picks a slot rather than on a key release.
     */
    public static void openRadialForSelect() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        mc.setScreen(new SpellRadialScreen(null, null, null));
    }
}
