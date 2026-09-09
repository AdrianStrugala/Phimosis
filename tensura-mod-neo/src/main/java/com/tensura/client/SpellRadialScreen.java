package com.tensura.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tensura.engine.SpellDefinition;
import com.tensura.item.SpellCasting;
import com.tensura.item.SpellFocusItem;
import com.tensura.item.SpellItem;
import com.tensura.network.AttuneSpellPacket;
import com.tensura.network.SetActiveSpellPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The catalyst's only UI: one ring of slots that both selects the active spell and receives
 * new attunements.
 *
 * Two ways in, one screen:
 *  - holding the keybind opens it in select mode; releasing the key commits the hovered slot;
 *  - clicking a spell in the Devour tree opens it with that spell on the cursor, and a click
 *    drops it into a slot.
 *
 * There is deliberately no spell list here — browsing lives in the Devour tree, so the radial
 * only ever has to show the handful of slots the catalyst actually holds.
 */
@OnlyIn(Dist.CLIENT)
public class SpellRadialScreen extends Screen {

    private static final int RING_RADIUS = 74;
    private static final int SLOT_HALF = 13;
    private static final int CENTER_DEAD_ZONE = 26;

    private static final int COLOR_PANEL      = 0xB0101018;
    private static final int COLOR_SLOT       = 0xC02A2A3A;
    private static final int COLOR_SLOT_HOVER = 0xFF4A4A6A;
    private static final int COLOR_ACTIVE     = 0xFFFFAA00;
    private static final int COLOR_COOLDOWN   = 0xA0402020;

    /** Non-null when the screen was opened to place this spell into a slot. */
    @Nullable
    private final ResourceLocation pendingSpell;
    /**
     * Input that opened the screen in select mode, polled to know when the player lets go.
     * Null in assign mode, and also when the binding cannot be polled — see
     * {@link #pollable(InputConstants.Key)}. The screen then closes on ESC or on a click.
     */
    @Nullable
    private final InputConstants.Key holdKey;
    /** Screen to return to on close — the Devour tree, when we were opened from it. */
    @Nullable
    private final Screen parent;

    private List<ResourceLocation> spells = List.of();
    private int activeIndex;
    private int hovered = -1;
    private int dragFrom = -1;

    public SpellRadialScreen(@Nullable ResourceLocation pendingSpell,
                             @Nullable InputConstants.Key holdKey,
                             @Nullable Screen parent) {
        super(Component.literal("Katalizator"));
        this.pendingSpell = pendingSpell;
        this.holdKey = pollable(holdKey);
        this.parent = parent;
    }

    /**
     * GLFW can be asked about a key or a mouse button, but there is no way to ask about a raw
     * scancode. A binding we cannot poll gets dropped rather than reported as released, which
     * would slam the screen shut on the first tick.
     */
    @Nullable
    private static InputConstants.Key pollable(@Nullable InputConstants.Key key) {
        if (key == null || key.getValue() < 0) return null;
        return key.getType() == InputConstants.Type.SCANCODE ? null : key;
    }

    @Override
    protected void init() {
        readFocus();
    }

    private void readFocus() {
        ItemStack focus = minecraft == null || minecraft.player == null
                ? null : SpellCasting.findFocus(minecraft.player);
        if (focus == null) {
            onClose();
            return;
        }
        spells = SpellFocusItem.getSpells(focus);
        activeIndex = SpellFocusItem.getActiveIndex(focus);
    }

    /** The game must keep running — this opens mid-fight. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Back to the Devour tree when that is where we came from. Attuning five spells in a row
     * otherwise means reopening the tree five times.
     */
    @Override
    public void onClose() {
        if (minecraft != null && parent != null) {
            minecraft.setScreen(parent);
            return;
        }
        super.onClose();
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (spells.isEmpty()) {
            onClose();
            return;
        }

        int cx = width / 2;
        int cy = height / 2;
        hovered = slotAt(mouseX - cx, mouseY - cy);

        graphics.fill(cx - RING_RADIUS - 30, cy - RING_RADIUS - 30,
                      cx + RING_RADIUS + 30, cy + RING_RADIUS + 30, COLOR_PANEL);

        for (int i = 0; i < spells.size(); i++) {
            renderSlot(graphics, cx, cy, i);
        }

        renderCenterLabel(graphics, cx, cy);

        // In assign mode the incoming spell rides the cursor until it is dropped.
        if (pendingSpell != null) {
            graphics.renderItem(displayStack(pendingSpell), mouseX + 6, mouseY + 6);
        }
    }

    private void renderSlot(GuiGraphics graphics, int cx, int cy, int slot) {
        double angle = slotAngle(slot);
        int x = cx + (int) Math.round(Math.cos(angle) * RING_RADIUS);
        int y = cy + (int) Math.round(Math.sin(angle) * RING_RADIUS);

        ResourceLocation spellId = spells.get(slot);
        boolean isHovered = slot == hovered;

        graphics.fill(x - SLOT_HALF, y - SLOT_HALF, x + SLOT_HALF, y + SLOT_HALF,
                isHovered ? COLOR_SLOT_HOVER : COLOR_SLOT);

        // The active slot keeps a gold frame so it stays identifiable while hovering elsewhere.
        if (slot == activeIndex) {
            drawFrame(graphics, x - SLOT_HALF, y - SLOT_HALF, x + SLOT_HALF, y + SLOT_HALF, COLOR_ACTIVE);
        }

        if (spellId == null) {
            graphics.drawCenteredString(font, "+", x, y - 4, 0xFF666677);
            return;
        }

        graphics.renderItem(displayStack(spellId), x - 8, y - 8);

        if (ClientCooldownTracker.isOnCooldown(spellId)) {
            float remaining = ClientCooldownTracker.getRemainingFraction(spellId);
            int filled = Math.round(remaining * (SLOT_HALF * 2));
            graphics.fill(x - SLOT_HALF, y + SLOT_HALF - filled, x + SLOT_HALF, y + SLOT_HALF,
                    COLOR_COOLDOWN);
        }
    }

    private void renderCenterLabel(GuiGraphics graphics, int cx, int cy) {
        int shown = hovered >= 0 ? hovered : activeIndex;
        ResourceLocation spellId = spells.get(shown);

        if (spellId == null) {
            graphics.drawCenteredString(font, "§8— pusty slot —", cx, cy - 4, 0xFFFFFF);
            return;
        }

        graphics.drawCenteredString(font, "§f" + SpellCasting.formatName(spellId.getPath()),
                cx, cy - 9, 0xFFFFFF);

        SpellDefinition def = SpellFocusItem.definitionOf(spellId);
        if (def != null) {
            graphics.drawCenteredString(font, "§7" + def.school, cx, cy + 2, 0xAAAAAA);
        }
    }

    private void drawFrame(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y0 + 1, color);
        graphics.fill(x0, y1 - 1, x1, y1, color);
        graphics.fill(x0, y0, x0 + 1, y1, color);
        graphics.fill(x1 - 1, y0, x1, y1, color);
    }

    /**
     * A SpellItem stack standing in as the icon: its model overrides already resolve each
     * spell to its own texture, so the radial gets the right picture for free.
     */
    private ItemStack displayStack(ResourceLocation spellId) {
        return SpellItem.create(spellId, SpellFocusItem.definitionOf(spellId));
    }

    // ── Geometry ─────────────────────────────────────────────────────────────

    private double slotAngle(int slot) {
        // Slot 0 sits at the top, the rest run clockwise.
        return -Math.PI / 2 + (2 * Math.PI * slot) / spells.size();
    }

    /** Slot under the cursor, chosen by angle so the mouse never has to land on the icon. */
    private int slotAt(int dx, int dy) {
        if (spells.isEmpty()) return -1;
        if (dx * dx + dy * dy < CENTER_DEAD_ZONE * CENTER_DEAD_ZONE) return -1;

        double angle = Math.atan2(dy, dx) + Math.PI / 2;
        while (angle < 0) angle += 2 * Math.PI;
        while (angle >= 2 * Math.PI) angle -= 2 * Math.PI;

        double sector = 2 * Math.PI / spells.size();
        return (int) Math.floor((angle + sector / 2) / sector) % spells.size();
    }

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        // Select mode commits when the input that opened the menu comes back up. Polling GLFW
        // beats keyReleased here: the key went down before the screen existed, and opening a
        // screen makes vanilla call KeyMapping.releaseAll().
        if (holdKey == null || minecraft == null) return;
        if (!isHoldKeyDown()) {
            commitSelection();
        }
    }

    private boolean isHoldKeyDown() {
        long window = minecraft.getWindow().getWindow();
        return holdKey.getType() == InputConstants.Type.MOUSE
                ? GLFW.glfwGetMouseButton(window, holdKey.getValue()) == GLFW.GLFW_PRESS
                : InputConstants.isKeyDown(window, holdKey.getValue());
    }

    private void commitSelection() {
        if (hovered >= 0 && hovered != activeIndex) {
            PacketDistributor.sendToServer(new SetActiveSpellPacket(hovered));
        }
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = width / 2;
        int cy = height / 2;
        int slot = slotAt((int) mouseX - cx, (int) mouseY - cy);

        if (slot < 0) {
            // Clicking the middle in assign mode cancels the placement without closing.
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (button == 1) {
            PacketDistributor.sendToServer(AttuneSpellPacket.clear(slot));
            onClose();
            return true;
        }

        if (pendingSpell != null) {
            PacketDistributor.sendToServer(AttuneSpellPacket.bind(slot, pendingSpell));
            onClose();
            return true;
        }

        dragFrom = slot;
        PacketDistributor.sendToServer(new SetActiveSpellPacket(slot));
        activeIndex = slot;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Dragging a slot into the middle empties it.
        if (dragFrom >= 0) {
            int dx = (int) mouseX - width / 2;
            int dy = (int) mouseY - height / 2;
            if (dx * dx + dy * dy < CENTER_DEAD_ZONE * CENTER_DEAD_ZONE) {
                PacketDistributor.sendToServer(AttuneSpellPacket.clear(dragFrom));
                onClose();
            }
            dragFrom = -1;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
