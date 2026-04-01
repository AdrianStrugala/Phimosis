package com.tensura.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // C → S
        registrar.playToServer(SelectSkillPacket.TYPE, SelectSkillPacket.STREAM_CODEC, SelectSkillPacket::handle);
        registrar.playToServer(RecallCitizenPacket.TYPE, RecallCitizenPacket.STREAM_CODEC, RecallCitizenPacket::handle);
        registrar.playToServer(RetrieveAbsorbedSpellPacket.TYPE, RetrieveAbsorbedSpellPacket.STREAM_CODEC, RetrieveAbsorbedSpellPacket::handle);

        // S → C
        registrar.playToClient(OpenSkillSelectPacket.TYPE, OpenSkillSelectPacket.STREAM_CODEC, OpenSkillSelectPacket::handle);
        registrar.playToClient(CooldownSyncPacket.TYPE, CooldownSyncPacket.STREAM_CODEC, CooldownSyncPacket::handle);
        registrar.playToClient(CitizenSpeciesSyncPacket.TYPE, CitizenSpeciesSyncPacket.STREAM_CODEC, CitizenSpeciesSyncPacket::handle);
        registrar.playToClient(OpenCodexPacket.TYPE, OpenCodexPacket.STREAM_CODEC, OpenCodexPacket::handle);
    }
}
