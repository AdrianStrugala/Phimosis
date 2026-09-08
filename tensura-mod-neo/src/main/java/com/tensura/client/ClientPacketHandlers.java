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
        mc.setScreen(new SpellRadialScreen(spellId, -1));
    }
}
