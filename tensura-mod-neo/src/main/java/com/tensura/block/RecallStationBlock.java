package com.tensura.block;

import com.tensura.data.DynamicCitizenSpeciesData;
import com.tensura.gui.RecallStationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecallStationBlock extends Block {

    public RecallStationBlock() {
        super(Properties.of().strength(2.5f).sound(SoundType.STONE).requiresCorrectToolForDrops());
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        DynamicCitizenSpeciesData data = DynamicCitizenSpeciesData.get(serverLevel);
        List<RecallStationMenu.RecallEntry> entries = new ArrayList<>();
        for (var entry : data.dynamicSpecies.entrySet()) {
            int citizenId = entry.getKey();
            String species = entry.getValue();
            UUID ownerUUID = data.ownerMap.get(citizenId);
            String ownerName = resolveOwnerName(serverLevel, ownerUUID);
            entries.add(new RecallStationMenu.RecallEntry(citizenId, species, ownerName));
        }

        serverPlayer.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() { return Component.literal("Recall Station"); }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new RecallStationMenu(id, inv, entries);
            }
        }, (RegistryFriendlyByteBuf buf) -> {
            buf.writeInt(entries.size());
            for (var e : entries) {
                buf.writeInt(e.citizenId());
                buf.writeUtf(e.species());
                buf.writeUtf(e.ownerName());
            }
        });

        return InteractionResult.SUCCESS;
    }

    private static String resolveOwnerName(ServerLevel level, UUID uuid) {
        if (uuid == null) return "Unknown";
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(uuid);
        if (online != null) return online.getName().getString();
        return level.getServer().getProfileCache()
                .get(uuid).map(p -> p.getName()).orElse(uuid.toString().substring(0, 8));
    }
}
