package com.tensura.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tensura.network.SelectSkillPacket;
import com.tensura.spell.SkillEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Client-side only screen — opens when OpenSkillSelectPacket arrives.
 * Shows available skills for absorbed mob, plus a Cancel button.
 */
public class SkillSelectScreen extends Screen {

    private final List<SkillEntry> skills;
    private final String mobName;
    private final ResourceLocation currentMobSpecies;

    public SkillSelectScreen(List<SkillEntry> skills, String mobName, ResourceLocation species) {
        super(Component.literal("Pochłoń umiejętność"));
        this.skills = skills;
        this.mobName = mobName;
        this.currentMobSpecies = species;
    }

    // Extra height for the separator gap between skills and cancel
    private static final int SEPARATOR_GAP = 14;

    @Override
    protected void init() {
        int panelW = 200;
        int panelH = 50 + skills.size() * 28 + SEPARATOR_GAP;
        int startX = (width - panelW) / 2;
        int startY = (height - panelH) / 2;

        for (int i = 0; i < skills.size(); i++) {
            SkillEntry entry = skills.get(i);
            String label = formatSpellName(entry.spellId().getPath()) + " — " + entry.xpCost() + " lvl";

            addRenderableWidget(Button.builder(Component.literal(label), btn -> {
                PacketDistributor.sendToServer(new SelectSkillPacket(entry.spellId(), entry.xpCost()));
                onClose();
            }).bounds(startX + 10, startY + 30 + i * 28, panelW - 20, 22).build());
        }

        // Cancel button — below separator gap
        addRenderableWidget(Button.builder(Component.literal("✗ Anuluj"), btn -> onClose())
                .bounds(startX + 10, startY + panelH - 26, panelW - 20, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);

        int panelW = 200;
        int panelH = 50 + skills.size() * 28 + SEPARATOR_GAP;
        int startX = (width - panelW) / 2;
        int startY = (height - panelH) / 2;

        // Background panel
        graphics.fill(startX, startY, startX + panelW, startY + panelH, 0xCC1A1A2E);
        graphics.fill(startX, startY, startX + panelW, startY + 2, 0xFF5555FF);  // top border

        // Title
        graphics.drawCenteredString(font,
                "§b🧬 Pochłoń: §f" + mobName,
                width / 2, startY + 8, 0xFFFFFF);

        int xpLevel = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.experienceLevel : 0;
        graphics.drawCenteredString(font,
                "§7Twoje XP: §e" + xpLevel + " lvl",
                width / 2, startY + 18, 0xFFFFFF);

        // Separator line between skills and cancel
        int sepY = startY + 30 + skills.size() * 28 + 4;
        graphics.fill(startX + 10, sepY,     startX + panelW - 10, sepY + 1, 0x885555FF);
        graphics.drawCenteredString(font, "§8─────────────────", width / 2, sepY + 3, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String formatSpellName(String path) {
        String[] words = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                sb.append(w.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }
}
