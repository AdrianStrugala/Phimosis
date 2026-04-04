package com.tensura.gui;

import com.tensura.network.RecallCitizenPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class RecallStationScreen extends AbstractContainerScreen<RecallStationMenu> {

    private static final int GUI_WIDTH  = 260;
    private static final int GUI_HEIGHT = 200;
    private static final int ROW_HEIGHT = 22;
    private static final int PADDING    = 10;

    public RecallStationScreen(RecallStationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int listTop = topPos + 30;

        for (int i = 0; i < menu.getEntries().size(); i++) {
            var entry = menu.getEntries().get(i);
            if (!entry.canRecall()) continue;

            int y = listTop + i * ROW_HEIGHT;
            int btnX = leftPos + GUI_WIDTH - PADDING - 60;

            addRenderableWidget(Button.builder(
                    Component.literal("Recall"),
                    btn -> {
                        PacketDistributor.sendToServer(new RecallCitizenPacket(entry.citizenId(), entry.colonyId()));
                        onClose();
                    })
                    .pos(btnX, y)
                    .size(58, 16)
                    .build());
        }
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float delta, int mouseX, int mouseY) {
        gfx.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xCC000000);
        gfx.fill(leftPos + 1, topPos + 1, leftPos + GUI_WIDTH - 1, topPos + 2, 0xFF888888);
        gfx.fill(leftPos + 1, topPos + GUI_HEIGHT - 2, leftPos + GUI_WIDTH - 1, topPos + GUI_HEIGHT - 1, 0xFF888888);
        gfx.fill(leftPos + 1, topPos + 1, leftPos + 2, topPos + GUI_HEIGHT - 1, 0xFF888888);
        gfx.fill(leftPos + GUI_WIDTH - 2, topPos + 1, leftPos + GUI_WIDTH - 1, topPos + GUI_HEIGHT - 1, 0xFF888888);
        gfx.fill(leftPos + PADDING, topPos + 22, leftPos + GUI_WIDTH - PADDING, topPos + 23, 0xFF888888);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        super.render(gfx, mouseX, mouseY, delta);
        gfx.drawCenteredString(font, title, leftPos + GUI_WIDTH / 2, topPos + 10, 0xFFFFAA00);

        int listTop = topPos + 30;
        if (menu.getEntries().isEmpty()) {
            gfx.drawCenteredString(font, Component.literal("No citizens in colony"),
                    leftPos + GUI_WIDTH / 2, listTop + 20, 0xFFAAAAAA);
        } else {
            for (int i = 0; i < menu.getEntries().size(); i++) {
                var entry = menu.getEntries().get(i);
                int textColor;
                String label;
                if (entry.canRecall()) {
                    String name = entry.citizenName() == null || entry.citizenName().isEmpty()
                            ? capitalize(entry.species()) : entry.citizenName();
                    label = name + "  \u00a7e[" + capitalize(entry.species()) + "]\u00a7r";
                    textColor = 0xFFFFFFFF;
                } else {
                    label = entry.citizenName() == null || entry.citizenName().isEmpty() ? "Unknown" : entry.citizenName();
                    textColor = 0xFF888888;
                }
                gfx.drawString(font, label, leftPos + PADDING, listTop + i * ROW_HEIGHT + 4, textColor, false);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        // suppress vanilla title/inventory label rendering
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
