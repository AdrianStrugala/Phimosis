package com.tensura.network;

import com.tensura.TensuraMod;
import com.tensura.client.SpellVfxManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SpellVfxPacket(String shape, String style, String school,
                             Vec3 origin, Vec3 target, float radius,
                             int durationTicks, int sourceEntityId,
                             boolean friendly) implements CustomPacketPayload {

    public static final Type<SpellVfxPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TensuraMod.MOD_ID, "spell_vfx"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Vec3> VEC3_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, Vec3::x,
                    ByteBufCodecs.DOUBLE, Vec3::y,
                    ByteBufCodecs.DOUBLE, Vec3::z,
                    Vec3::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, SpellVfxPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buffer, packet.shape());
                        ByteBufCodecs.STRING_UTF8.encode(buffer, packet.style());
                        ByteBufCodecs.STRING_UTF8.encode(buffer, packet.school());
                        VEC3_CODEC.encode(buffer, packet.origin());
                        VEC3_CODEC.encode(buffer, packet.target());
                        ByteBufCodecs.FLOAT.encode(buffer, packet.radius());
                        ByteBufCodecs.VAR_INT.encode(buffer, packet.durationTicks());
                        ByteBufCodecs.VAR_INT.encode(buffer, packet.sourceEntityId());
                        ByteBufCodecs.BOOL.encode(buffer, packet.friendly());
                    },
                    buffer -> new SpellVfxPacket(
                            ByteBufCodecs.STRING_UTF8.decode(buffer),
                            ByteBufCodecs.STRING_UTF8.decode(buffer),
                            ByteBufCodecs.STRING_UTF8.decode(buffer),
                            VEC3_CODEC.decode(buffer),
                            VEC3_CODEC.decode(buffer),
                            ByteBufCodecs.FLOAT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.BOOL.decode(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellVfxPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SpellVfxManager.accept(packet));
    }
}