package com.tensura.client;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.Alignment;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.PaneBuilders;
import com.minecolonies.core.client.gui.BuildingWindowAccessor;
import com.minecolonies.core.client.gui.townhall.AbstractWindowTownHall;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.tensura.TensuraMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = TensuraMod.MOD_ID, value = Dist.CLIENT)
public final class TownHallWorkforceButton {

    private static final String BUTTON_ID = "workforce";

    private TownHallWorkforceButton() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof BOScreen screen)) return;
        BOWindow window = screen.getWindow();
        if (!(window instanceof AbstractWindowTownHall townHallWindow)
            || window.findPaneByID(BUTTON_ID) != null) return;
        if (!(BuildingWindowAccessor.getBuildingView(townHallWindow)
            instanceof BuildingTownHall.View townHall)) return;

        Image ribbon = new Image();
        ribbon.setID(BUTTON_ID + "0");
        ribbon.setImage(ResourceLocation.fromNamespaceAndPath(
            "minecolonies", "textures/gui/bookmark_short_ribbon_04.png"), false);
        ribbon.setPosition(56, 239);
        ribbon.setSize(31, 14);
        window.addChild(ribbon);

        ButtonImage extension = new ButtonImage();
        extension.setID(BUTTON_ID + "Ext");
        extension.setImage(ResourceLocation.fromNamespaceAndPath(
            "minecolonies", "textures/gui/bookmark_medium_ribbon_04.png"));
        extension.setPosition(-17, 239);
        extension.setSize(104, 14);
        extension.setText(Component.literal("Pracownicy"));
        extension.setTextAlignment(Alignment.MIDDLE_LEFT);
        extension.setTextOffset(8, 1);
        extension.hide();
        window.addChild(extension);

        ButtonImage button = new ButtonImage();
        button.setID(BUTTON_ID);
        button.setImage(ResourceLocation.fromNamespaceAndPath(
            "minecolonies", "textures/gui/red_wax_citizens.png"));
        button.setPosition(62, 237);
        button.setSize(17, 17);
        button.setHandler(clicked -> new WorkforceTownHallWindow(townHall).open());
        button.setHoverPane(extension);
        PaneBuilders.singleLineTooltip(Component.literal("Pracownicy i recall"), button);
        window.addChild(button);
    }
}