package com.tensura.gui;

import com.tensura.registry.TensuraMenuRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class RecallStationMenu extends AbstractContainerMenu {

    public record RecallEntry(int citizenId, String citizenName, String species, int colonyId, boolean canRecall) {}

    private final List<RecallEntry> entries;

    /** Server-side constructor */
    public RecallStationMenu(int windowId, Inventory playerInv, List<RecallEntry> entries) {
        super(TensuraMenuRegistry.RECALL_STATION.get(), windowId);
        this.entries = entries;
    }

    /** Client-side constructor — reads extra data sent by NetworkHooks.openScreen */
    public RecallStationMenu(int windowId, Inventory playerInv, FriendlyByteBuf buf) {
        super(TensuraMenuRegistry.RECALL_STATION.get(), windowId);
        int size = buf.readInt();
        this.entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            entries.add(new RecallEntry(buf.readInt(), buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readBoolean()));
        }
    }

    public List<RecallEntry> getEntries() { return entries; }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
