package com.tensura.client;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.Alignment;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.PaneBuilders;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.townhall.AbstractWindowTownHall;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.tensura.TensuraMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;

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
        if (!(buildingViewOf(townHallWindow) instanceof BuildingTownHall.View townHall)) return;

        // This is an optional bookmark bolted onto someone else's screen. If BlockUI rejects
        // anything we add, the town hall must still open — an unhandled throw here reaches
        // Screen.init and takes the whole client down.
        try {
            injectButton(window, townHall);
        } catch (RuntimeException e) {
            TensuraMod.LOGGER.warn("[Tensura] Could not add the workforce button to the town hall screen", e);
        }
    }

    private static void injectButton(BOWindow window, BuildingTownHall.View townHall) {
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
        extension.setText(Component.translatable("tensura.gui.workforce.tab"));
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
        // Attach before wiring the hover pane and tooltip: BlockUI resolves both through the
        // target pane's parent window, and building a tooltip for a pane that has none throws
        // "Hover pane does not have parent window specified" as the town hall screen opens.
        window.addChild(button);
        button.setHoverPane(extension);
        PaneBuilders.singleLineTooltip(
            Component.translatable("tensura.gui.workforce.tooltip"), button);
    }

    /**
     * Reads AbstractBuildingWindow#buildingView, which is protected with no public accessor.
     *
     * This used to live in a helper class declared in MineColonies' own
     * com.minecolonies.core.client.gui package. That made tensura and minecolonies export the
     * same package, and the JVM module system refuses to resolve a split package — the
     * dedicated server died at boot with "Modules minecolonies and tensura export package
     * com.minecolonies.core.client.gui". Reflection keeps every class inside com.tensura.
     */
    private static Field buildingViewField;
    private static boolean buildingViewFieldResolved;

    private static IBuildingView buildingViewOf(AbstractWindowTownHall window) {
        if (!buildingViewFieldResolved) {
            buildingViewFieldResolved = true;
            for (Class<?> type = window.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField("buildingView");
                    field.setAccessible(true);
                    buildingViewField = field;
                    break;
                } catch (NoSuchFieldException ignored) {
                    // keep walking up the hierarchy
                } catch (RuntimeException e) {
                    TensuraMod.LOGGER.warn("[Tensura] Cannot access buildingView, "
                            + "workforce button disabled: {}", e.toString());
                    break;
                }
            }
        }
        if (buildingViewField == null) return null;
        try {
            return buildingViewField.get(window) instanceof IBuildingView view ? view : null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}