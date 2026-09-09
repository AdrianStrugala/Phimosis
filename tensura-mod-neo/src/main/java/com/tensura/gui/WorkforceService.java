package com.tensura.gui;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.IAssignsJob;
import com.minecolonies.api.colony.buildings.modules.IBuildingModule;
import com.minecolonies.api.colony.permissions.Action;
import com.tensura.data.ConversionHelper;
import com.tensura.data.DynamicCitizenSpeciesData;
import com.tensura.event.ColonyStartupEvents;
import com.tensura.network.WorkforceSnapshotPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class WorkforceService {

    private WorkforceService() {}

    public static void sendSnapshot(ServerPlayer player, IColony colony) {
        if (!canAccess(player, colony)) {
            player.sendSystemMessage(Component.literal("§cNie masz dostępu do zarządzania tą kolonią."));
            return;
        }

        List<WorkforceSnapshotPacket.WorkplaceEntry> workplaces = colony.getServerBuildingManager()
                .getBuildings().values().stream()
                // NOT filtered by IBuildingWorker: that interface extends IBuilding but has zero
                // implementers in 1.21.1 (it still imports Forge's IItemHandler), so it matched
                // nothing and the workplace list was always empty. A workplace is simply a built
                // building that owns a job-assignment module.
                .filter(IBuilding::isBuilt)
                .flatMap(building -> building.getModules().stream()
                        .filter(IAssignsJob.class::isInstance)
                        .map(IAssignsJob.class::cast)
                        .map(module -> new WorkforceSnapshotPacket.WorkplaceEntry(
                                building.getPosition(),
                                // getModule(int) is keyed by ModuleProducer runtime ID, not by
                                // list position, so the round trip must send the runtime ID.
                                module.getProducer().getRuntimeID(),
                                building.getBuildingDisplayName() + " · " + formatJobName(module),
                                module.getAssignedCitizen().size(),
                                module.getModuleMax())))
                .sorted(Comparator.comparing(WorkforceSnapshotPacket.WorkplaceEntry::name,
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(entry -> entry.position().getX())
                        .thenComparingInt(entry -> entry.position().getZ()))
                .toList();

        Map<Integer, String> species = DynamicCitizenSpeciesData.get(player.serverLevel())
                .mergedWith(ColonyStartupEvents.getHardcodedSpeciesMap());
        boolean mayRecall = ConversionHelper.isColonyOwner(colony, player);
        List<WorkforceSnapshotPacket.CitizenEntry> citizens = colony.getCitizenManager().getCitizens().stream()
                .map(citizen -> new WorkforceSnapshotPacket.CitizenEntry(
                        citizen.getId(), citizen.getName(), species.getOrDefault(citizen.getId(), "villager"),
                        citizen.getHomePosition(), workPosition(citizen), workModuleIndex(citizen),
                        mayRecall && species.containsKey(citizen.getId())))
                .sorted(Comparator.comparing(WorkforceSnapshotPacket.CitizenEntry::citizenName,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();

        PacketDistributor.sendToPlayer(player,
                new WorkforceSnapshotPacket(colony.getID(), workplaces, citizens));
    }

    public static boolean canAccess(ServerPlayer player, IColony colony) {
        return colony.isCoordInColony(player.serverLevel(), player.blockPosition())
                && colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS);
    }

    private static BlockPos workPosition(ICitizenData citizen) {
        IBuilding building = citizen.getWorkBuilding();
        return building == null ? null : building.getPosition();
    }

    private static int workModuleIndex(ICitizenData citizen) {
        IBuilding building = citizen.getWorkBuilding();
        if (building == null) return -1;
        for (IBuildingModule module : building.getModules()) {
            if (module instanceof IAssignsJob job && job.hasAssignedCitizen(citizen)) {
                return module.getProducer().getRuntimeID();
            }
        }
        return -1;
    }

    private static String formatJobName(IAssignsJob module) {
        String path = module.getJobEntry().getKey().getPath().replace('_', ' ');
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

}