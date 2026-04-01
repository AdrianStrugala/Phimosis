package com.tensura.item;

import com.tensura.data.PredatorData;
import com.tensura.network.OpenCodexPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public class PredatorCodexItem extends Item {

    public PredatorCodexItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    new OpenCodexPacket(PredatorData.getAbsorbed(serverPlayer)));
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}
