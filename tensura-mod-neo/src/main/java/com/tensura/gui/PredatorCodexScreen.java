package com.tensura.gui;

import com.tensura.network.RetrieveAbsorbedSpellPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Shows all spells the player has absorbed via the Predator system.
 * Click a spell to receive a new SpellItem copy (server verifies absorption).
 * Paginated — 18 entries per page.
 */
public class PredatorCodexScreen extends Screen {

    private static final int PAGE_SIZE = 18;

    private final List<ResourceLocation> absorbed;
    private int page = 0;

    public PredatorCodexScreen(List<ResourceLocation> absorbed) {
        super(Component.literal("Predator Codex"));
        this.absorbed = absorbed;
    }

    private int totalPages() {
        return Math.max(1, (absorbed.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override
    protected void init() {
        int panelW = 220;
        int startX = (width - panelW) / 2;

        List<ResourceLocation> pageSpells = getPageSpells();
        int panelH = 50 + pageSpells.size() * 24 + 40;
        int startY = (height - panelH) / 2;

        for (int i = 0; i < pageSpells.size(); i++) {
            ResourceLocation spellId = pageSpells.get(i);
            String label = "✦ " + formatSpellName(spellId.getPath());
            addRenderableWidget(Button.builder(Component.literal(label), btn -> {
                PacketDistributor.sendToServer(new RetrieveAbsorbedSpellPacket(spellId));
                onClose();
            }).bounds(startX + 10, startY + 30 + i * 24, panelW - 20, 20).build());
        }

        // Pagination
        if (totalPages() > 1) {
            if (page > 0) {
                addRenderableWidget(Button.builder(Component.literal("◀"), btn -> {
                    page--;
                    rebuildWidgets();
                }).bounds(startX + 10, startY + panelH - 24, 30, 20).build());
            }
            if (page < totalPages() - 1) {
                addRenderableWidget(Button.builder(Component.literal("▶"), btn -> {
                    page++;
                    rebuildWidgets();
                }).bounds(startX + panelW - 40, startY + panelH - 24, 30, 20).build());
            }
        }

        // Close button
        addRenderableWidget(Button.builder(Component.literal("✗"), btn -> onClose())
                .bounds(startX + panelW / 2 - 15, startY + panelH - 24, 30, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);

        List<ResourceLocation> pageSpells = getPageSpells();
        int panelW = 220;
        int panelH = 50 + pageSpells.size() * 24 + 40;
        int startX = (width - panelW) / 2;
        int startY = (height - panelH) / 2;

        graphics.fill(startX, startY, startX + panelW, startY + panelH, 0xCC1A1A2E);
        graphics.fill(startX, startY, startX + panelW, startY + 2, 0xFFFFAA00);

        String title = "§6📖 Pochłonięte Czary §e(" + absorbed.size() + ")";
        if (totalPages() > 1) title += " §7[" + (page + 1) + "/" + totalPages() + "]";
        graphics.drawCenteredString(font, title, width / 2, startY + 8, 0xFFFFFF);

        if (absorbed.isEmpty()) {
            graphics.drawCenteredString(font, "§7Brak pochłoniętych czarów.", width / 2, startY + 35, 0xAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    private List<ResourceLocation> getPageSpells() {
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, absorbed.size());
        return absorbed.subList(from, to);
    }

    private static String formatSpellName(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
