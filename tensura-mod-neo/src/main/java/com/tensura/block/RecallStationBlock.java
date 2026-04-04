package com.tensura.block;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.ICivilianData;
import com.tensura.data.DynamicCitizenSpeciesData;
import com.tensura.event.ColonyStartupEvents;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Map<Integer, String> hardcodedMap = ColonyStartupEvents.getHardcodedSpeciesMap();
        Map<Integer, String> speciesMap = data.mergedWith(hardcodedMap);

        // Use LinkedHashMap to deduplicate by citizenId while preserving order
        Map<Integer, RecallStationMenu.RecallEntry> entriesById = new LinkedHashMap<>();

        // 1) Enrolled citizens from DynamicCitizenSpeciesData — reliable, always correct
        for (Map.Entry<Integer, String> e : data.dynamicSpecies.entrySet()) {
            int citizenId = e.getKey();
            String species = e.getValue();
            Integer colonyId = data.colonyIdMap.get(citizenId);
            if (colonyId == null) continue;

            String citizenName = resolveCitizenName(serverLevel, colonyId, citizenId);

            entriesById.put(citizenId, new RecallStationMenu.RecallEntry(
                    citizenId, citizenName, species, colonyId, true));
        }

        // 2) Citizens from the closest colony — for hardcoded-species citizens
        IColony colony = IColonyManager.getInstance().getClosestIColony(serverLevel, pos);
        if (colony != null) {
            for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
                int citizenId = citizen.getId();
                if (entriesById.containsKey(citizenId)) continue; // already added above

                String species = speciesMap.get(citizenId);
                boolean canRecall = species != null;

                entriesById.put(citizenId, new RecallStationMenu.RecallEntry(
                        citizenId, citizen.getName(),
                        species != null ? species : "",
                        colony.getID(), canRecall));
            }
        }

        List<RecallStationMenu.RecallEntry> entries = new ArrayList<>(entriesById.values());

        serverPlayer.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() { return Component.literal("Recall Station"); }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new RecallStationMenu(id, inv, entries);
            }
        }, (RegistryFriendlyByteBuf buf) -> {
            buf.writeInt(entries.size());
            for (var entry : entries) {
                buf.writeInt(entry.citizenId());
                buf.writeUtf(entry.citizenName());
                buf.writeUtf(entry.species());
                buf.writeInt(entry.colonyId());
                buf.writeBoolean(entry.canRecall());
            }
        });

        return InteractionResult.SUCCESS;
    }

    private static String resolveCitizenName(ServerLevel level, int colonyId, int citizenId) {
        IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, level);
        if (colony == null) return "";
        ICivilianData civilian = colony.getCitizenManager().getCivilian(citizenId);
        return civilian != null ? civilian.getName() : "";
    }

}
