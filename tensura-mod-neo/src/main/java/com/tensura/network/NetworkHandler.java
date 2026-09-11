package com.tensura.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // C → S
        registrar.playToServer(RecallCitizenPacket.TYPE, RecallCitizenPacket.STREAM_CODEC, RecallCitizenPacket::handle);
        registrar.playToServer(OpenWorkforcePacket.TYPE, OpenWorkforcePacket.STREAM_CODEC, OpenWorkforcePacket::handle);
        registrar.playToServer(AssignWorkplacePacket.TYPE, AssignWorkplacePacket.STREAM_CODEC, AssignWorkplacePacket::handle);
        registrar.playToServer(SetActiveSpellPacket.TYPE, SetActiveSpellPacket.STREAM_CODEC, SetActiveSpellPacket::handle);
        registrar.playToServer(AttuneSpellPacket.TYPE, AttuneSpellPacket.STREAM_CODEC, AttuneSpellPacket::handle);

        // S → C
        registrar.playToClient(CooldownSyncPacket.TYPE, CooldownSyncPacket.STREAM_CODEC, CooldownSyncPacket::handle);
        registrar.playToClient(CitizenSpeciesSyncPacket.TYPE, CitizenSpeciesSyncPacket.STREAM_CODEC, CitizenSpeciesSyncPacket::handle);
        registrar.playToClient(WorkforceSnapshotPacket.TYPE, WorkforceSnapshotPacket.STREAM_CODEC, WorkforceSnapshotPacket::handle);
        registrar.playToClient(OpenRadialPacket.TYPE, OpenRadialPacket.STREAM_CODEC, OpenRadialPacket::handle);
        registrar.playToClient(SpellVfxPacket.TYPE, SpellVfxPacket.STREAM_CODEC, SpellVfxPacket::handle);
    }
}
