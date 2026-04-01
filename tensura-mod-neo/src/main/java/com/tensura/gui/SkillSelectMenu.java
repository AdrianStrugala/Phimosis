package com.tensura.gui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * Stub container menu — SkillSelectScreen is a pure Screen (no container slots).
 * Required only for MenuType registration.
 */
public class SkillSelectMenu extends AbstractContainerMenu {

    public SkillSelectMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(null, containerId);
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return true;
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}
