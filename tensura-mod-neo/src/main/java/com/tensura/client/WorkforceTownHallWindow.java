package com.tensura.client;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.townhall.AbstractWindowTownHall;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.tensura.network.AssignWorkplacePacket;
import com.tensura.network.OpenWorkforcePacket;
import com.tensura.network.RecallCitizenPacket;
import com.tensura.network.WorkforceSnapshotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class WorkforceTownHallWindow extends AbstractWindowTownHall {

    private final int colonyId;
    private final ScrollingList workplaceList;
    private final ScrollingList citizenList;
    private List<WorkforceSnapshotPacket.WorkplaceEntry> workplaces = List.of();
    private List<WorkforceSnapshotPacket.CitizenEntry> citizens = List.of();
    private int selectedWorkplace = -1;

    public WorkforceTownHallWindow(BuildingTownHall.View townHall) {
        super(townHall, "layoutworkforce.xml");
        colonyId = townHall.getColony().getID();
        workplaceList = findPaneOfTypeByID("workplaceList", ScrollingList.class);
        citizenList = findPaneOfTypeByID("workforceCitizenList", ScrollingList.class);

        registerButton("workplaceRow", this::selectWorkplace);
        registerButton("assignCitizen", this::assignCitizen);
        registerButton("recallCitizen", this::recallCitizen);
        findPaneOfTypeByID("workforce1", Button.class).setText(
            Component.translatable("tensura.gui.workforce.tab"));
        findPaneOfTypeByID("workforceTitle", Text.class).setText(
            Component.translatable("tensura.gui.workforce.title"));
        findPaneOfTypeByID("workforceStatus", Text.class).setText(
            Component.translatable("tensura.gui.workforce.loading"));
        findPaneOfTypeByID("workplaceHeading", Text.class).setText(
            Component.translatable("tensura.gui.workforce.workplaces"));
        findPaneOfTypeByID("citizenHeading", Text.class).setText(
            Component.translatable("tensura.gui.workforce.citizens"));
        configureLists();
    }

    @Override
    public void onOpened() {
        super.onOpened();
        PacketDistributor.sendToServer(new OpenWorkforcePacket(colonyId));
    }

    public static void acceptSnapshot(WorkforceSnapshotPacket packet) {
        if (!(Minecraft.getInstance().screen instanceof BOScreen screen)
                || !(screen.getWindow() instanceof WorkforceTownHallWindow window)
                || window.colonyId != packet.colonyId()) return;
        window.updateSnapshot(packet);
    }

    private void updateSnapshot(WorkforceSnapshotPacket packet) {
        workplaces = packet.workplaces();
        citizens = packet.citizens();
        if (workplaces.isEmpty()) {
            selectedWorkplace = -1;
        } else if (selectedWorkplace < 0 || selectedWorkplace >= workplaces.size()) {
            selectedWorkplace = 0;
        }
        findPaneOfTypeByID("workforceStatus", Text.class).setText(Component.translatable(
            "tensura.gui.workforce.status", workplaces.size(), citizens.size()));
        workplaceList.refreshElementPanes(true);
        citizenList.refreshElementPanes(true);
    }

    private void configureLists() {
        workplaceList.setDataProvider(new ScrollingList.DataProvider() {
            @Override
            public int getElementCount() {
                return workplaces.size();
            }

            @Override
            public void updateElement(int index, Pane row) {
                WorkforceSnapshotPacket.WorkplaceEntry workplace = workplaces.get(index);
                String marker = index == selectedWorkplace ? "▶ " : "";
                row.findPaneOfTypeByID("workplaceRow", Button.class).setText(Component.literal(
                        marker + workplaceLabel(workplace) + "  " + workplace.assignedWorkers()
                                + "/" + workplace.capacity()));
            }
        });

        citizenList.setDataProvider(new ScrollingList.DataProvider() {
            @Override
            public int getElementCount() {
                return citizens.size();
            }

            @Override
            public void updateElement(int index, Pane row) {
                WorkforceSnapshotPacket.CitizenEntry citizen = citizens.get(index);
                row.findPaneOfTypeByID("citizenName", Text.class).setText(Component.literal(
                        citizen.citizenName()));
                row.findPaneOfTypeByID("citizenPokemon", Text.class).setText(Component.literal(
                    capitalize(citizen.species())));

                String detail = selectedWorkplace < 0
                        ? currentWorkLabel(citizen)
                        : distanceLabel(citizen.homePosition(), workplaces.get(selectedWorkplace).position());
                row.findPaneOfTypeByID("citizenDetail", Text.class).setText(Component.literal(detail));

                Button assign = row.findPaneOfTypeByID("assignCitizen", Button.class);
                assign.setVisible(selectedWorkplace >= 0);
                boolean selectedJob = isSelectedJob(citizen);
                assign.setEnabled(!selectedJob);
                assign.setText(Component.translatable("tensura.gui.workforce.assign"));

                Button recall = row.findPaneOfTypeByID("recallCitizen", Button.class);
                recall.setVisible(citizen.canRecall());
                recall.setText(Component.translatable("tensura.gui.workforce.recall"));
            }
        });
    }

    /**
     * MineColonies hands out a translation key from getBuildingDisplayName, not a finished
     * string, so it has to be resolved here — otherwise the list renders raw keys like
     * "com.minecolonies.building.barracks".
     */
    private static String workplaceLabel(WorkforceSnapshotPacket.WorkplaceEntry workplace) {
        return Component.translatable(workplace.buildingNameKey()).getString()
                + " · " + Component.translatable(workplace.jobNameKey()).getString();
    }

    private void selectWorkplace(Button button) {
        selectedWorkplace = workplaceList.getListElementIndexByPane(button);
        workplaceList.refreshElementPanes(true);
        citizenList.refreshElementPanes(true);
    }

    private void assignCitizen(Button button) {
        int citizenIndex = citizenList.getListElementIndexByPane(button);
        if (selectedWorkplace < 0 || citizenIndex < 0) return;
        WorkforceSnapshotPacket.WorkplaceEntry workplace = workplaces.get(selectedWorkplace);
        WorkforceSnapshotPacket.CitizenEntry citizen = citizens.get(citizenIndex);
        PacketDistributor.sendToServer(new AssignWorkplacePacket(
                colonyId, citizen.citizenId(), workplace.position(), workplace.moduleIndex()));
    }

    private void recallCitizen(Button button) {
        int citizenIndex = citizenList.getListElementIndexByPane(button);
        if (citizenIndex < 0) return;
        WorkforceSnapshotPacket.CitizenEntry citizen = citizens.get(citizenIndex);
        if (citizen.canRecall()) {
            PacketDistributor.sendToServer(new RecallCitizenPacket(citizen.citizenId(), colonyId));
        }
    }

    private boolean isSelectedJob(WorkforceSnapshotPacket.CitizenEntry citizen) {
        if (selectedWorkplace < 0) return false;
        WorkforceSnapshotPacket.WorkplaceEntry workplace = workplaces.get(selectedWorkplace);
        return workplace.position().equals(citizen.workPosition())
                && workplace.moduleIndex() == citizen.workModuleIndex();
    }

    private String currentWorkLabel(WorkforceSnapshotPacket.CitizenEntry citizen) {
        if (citizen.workPosition() == null) {
            return Component.translatable("tensura.gui.workforce.unemployed").getString();
        }
        return Component.translatable("tensura.gui.workforce.working_at",
                citizen.workPosition().toShortString()).getString();
    }

    private static String distanceLabel(BlockPos home, BlockPos workplace) {
        if (home == null) {
            return Component.translatable("tensura.gui.workforce.no_home").getString();
        }
        return Component.translatable("tensura.gui.workforce.home_distance",
                Math.round(Math.sqrt(home.distSqr(workplace)))).getString();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    @Override
    protected String getWindowId() {
        return "workforce";
    }
}