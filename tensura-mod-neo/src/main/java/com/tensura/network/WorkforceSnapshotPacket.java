package com.tensura.network;

import com.tensura.TensuraMod;
import com.tensura.client.WorkforceTownHallWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record WorkforceSnapshotPacket(int colonyId, List<WorkplaceEntry> workplaces,
                                      List<CitizenEntry> citizens) implements CustomPacketPayload {

    public record WorkplaceEntry(BlockPos position, int moduleIndex, String name,
                                 int assignedWorkers, int capacity) {}

    public record CitizenEntry(int citizenId, String citizenName, String species,
                               BlockPos homePosition, BlockPos workPosition, int workModuleIndex,
                               boolean canRecall) {}

    public static final Type<WorkforceSnapshotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, "workforce_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WorkforceSnapshotPacket> STREAM_CODEC =
            StreamCodec.of(WorkforceSnapshotPacket::encode, WorkforceSnapshotPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WorkforceSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> WorkforceTownHallWindow.acceptSnapshot(packet));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, WorkforceSnapshotPacket packet) {
        buffer.writeVarInt(packet.colonyId());
        buffer.writeVarInt(packet.workplaces().size());
        for (WorkplaceEntry workplace : packet.workplaces()) {
            buffer.writeBlockPos(workplace.position());
            buffer.writeVarInt(workplace.moduleIndex());
            buffer.writeUtf(workplace.name());
            buffer.writeVarInt(workplace.assignedWorkers());
            buffer.writeVarInt(workplace.capacity());
        }

        buffer.writeVarInt(packet.citizens().size());
        for (CitizenEntry citizen : packet.citizens()) {
            buffer.writeVarInt(citizen.citizenId());
            buffer.writeUtf(citizen.citizenName());
            buffer.writeUtf(citizen.species());
            writeNullablePos(buffer, citizen.homePosition());
            writeNullablePos(buffer, citizen.workPosition());
            buffer.writeVarInt(citizen.workModuleIndex() + 1);
            buffer.writeBoolean(citizen.canRecall());
        }
    }

    private static WorkforceSnapshotPacket decode(RegistryFriendlyByteBuf buffer) {
        int colonyId = buffer.readVarInt();
        List<WorkplaceEntry> workplaces = new ArrayList<>();
        int workplaceCount = buffer.readVarInt();
        for (int index = 0; index < workplaceCount; index++) {
            workplaces.add(new WorkplaceEntry(buffer.readBlockPos(), buffer.readVarInt(), buffer.readUtf(),
                    buffer.readVarInt(), buffer.readVarInt()));
        }

        List<CitizenEntry> citizens = new ArrayList<>();
        int citizenCount = buffer.readVarInt();
        for (int index = 0; index < citizenCount; index++) {
            citizens.add(new CitizenEntry(
                    buffer.readVarInt(), buffer.readUtf(), buffer.readUtf(),
                    readNullablePos(buffer), readNullablePos(buffer), buffer.readVarInt() - 1,
                    buffer.readBoolean()));
        }
        return new WorkforceSnapshotPacket(colonyId, List.copyOf(workplaces), List.copyOf(citizens));
    }

    private static void writeNullablePos(RegistryFriendlyByteBuf buffer, BlockPos position) {
        buffer.writeBoolean(position != null);
        if (position != null) buffer.writeBlockPos(position);
    }

    private static BlockPos readNullablePos(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readBlockPos() : null;
    }
}