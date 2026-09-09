package com.tensura.network;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.IBuildingWorker;
import com.minecolonies.api.colony.buildings.modules.IAssignsJob;
import com.tensura.TensuraMod;
import com.tensura.gui.WorkforceService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AssignWorkplacePacket(int colonyId, int citizenId, BlockPos workplace, int moduleIndex)
        implements CustomPacketPayload {

    public static final Type<AssignWorkplacePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, "assign_workplace"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssignWorkplacePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AssignWorkplacePacket::colonyId,
                    ByteBufCodecs.VAR_INT, AssignWorkplacePacket::citizenId,
                    BlockPos.STREAM_CODEC, AssignWorkplacePacket::workplace,
                    ByteBufCodecs.VAR_INT, AssignWorkplacePacket::moduleIndex,
                    AssignWorkplacePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AssignWorkplacePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            IColony colony = IColonyManager.getInstance().getColonyByWorld(packet.colonyId(), player.serverLevel());
            if (colony == null || !WorkforceService.canAccess(player, colony)) return;

            ICitizenData citizen = colony.getCitizenManager().getCitizen(packet.citizenId());
            IBuilding building = colony.getServerBuildingManager().getBuilding(packet.workplace());
                if (citizen == null || !(building instanceof IBuildingWorker workplace)
                    || packet.moduleIndex() < 0 || packet.moduleIndex() >= workplace.getModules().size()
                    || !(workplace.getModule(packet.moduleIndex()) instanceof IAssignsJob assignment)) return;

            if (!assignment.assignCitizen(citizen)) {
                player.sendSystemMessage(Component.literal("§cNie można przypisać tego citizena do wybranego miejsca pracy."));
            }
            WorkforceService.sendSnapshot(player, colony);
        });
    }
}