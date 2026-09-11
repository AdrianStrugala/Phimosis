package com.tensura.client;

import com.lowdragmc.photon.client.fx.BlockEffectExecutor;
import com.lowdragmc.photon.client.fx.EntityEffectExecutor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.tensura.TensuraMod;
import com.tensura.network.SpellVfxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class SpellVfxManager {
    private static final Set<ResourceLocation> GENERATED_EFFECTS = new HashSet<>();

    private SpellVfxManager() {
    }

    public static void accept(SpellVfxPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        ResourceLocation effectId = effectId(packet.style());
        if (effectId == null) {
            return;
        }

        ResourceLocation effectResource = ResourceLocation.fromNamespaceAndPath(
            effectId.getNamespace(), "fx/" + effectId.getPath() + ".fx");
        FX effect = minecraft.getResourceManager().getResource(effectResource).isPresent()
            ? FXHelper.getFX(effectId)
            : null;
        if (effect == null) {
            effect = ProgrammaticSpellFx.get(effectId, packet);
            if (GENERATED_EFFECTS.add(effectId)) {
                TensuraMod.LOGGER.debug("Using programmatic Photon effect {}", effectId);
            }
        }

        Entity source = packet.sourceEntityId() >= 0
                ? minecraft.level.getEntity(packet.sourceEntityId())
                : null;
        if (source != null && isEntityBound(packet.shape())) {
            playOnEntity(effect, source, packet);
        } else {
            playAtPosition(effect, packet);
        }
    }

    private static void playOnEntity(FX effect, Entity source, SpellVfxPacket packet) {
        EntityEffectExecutor executor = new TimedEntityEffectExecutor(
            effect, source, packet.durationTicks());
        if ("aura".equals(packet.shape())) {
            executor.setOffset(0.0, -source.getEyeHeight(), 0.0);
        } else if ("attachment".equals(packet.shape())) {
            executor.setOffset(0.0, -source.getEyeHeight() * 0.5, 0.0);
        }
        executor.setScale(scale(packet), scale(packet), scale(packet));
        executor.setForcedDeath(true);
        executor.setAllowMulti(true);
        executor.start();
    }

    private static void playAtPosition(FX effect, SpellVfxPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Vec3 origin = packet.origin();
        BlockPos anchor = BlockPos.containing(origin);
        BlockEffectExecutor executor = new TimedBlockEffectExecutor(
            effect, minecraft.level, anchor, packet.durationTicks());
        executor.setOffset(
                origin.x - anchor.getX() - 0.5,
                origin.y - anchor.getY() - 0.5,
                origin.z - anchor.getZ() - 0.5);

        Vec3 direction = packet.target().subtract(origin);
        if (direction.lengthSqr() > 1.0E-6) {
            double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
            double yaw = Math.toDegrees(Math.atan2(-direction.z, direction.x));
            double pitch = Math.toDegrees(Math.atan2(direction.y, horizontal));
            executor.setRotation(0.0, yaw, pitch);
        }

        double widthScale = scale(packet);
        double lengthScale = isDirectional(packet.shape())
            ? Math.max(0.01, packet.origin().distanceTo(packet.target()))
            : widthScale;
        executor.setScale(lengthScale, widthScale, widthScale);
        executor.setForcedDeath(false);
        executor.setAllowMulti(true);
        executor.start();
    }

    private static ResourceLocation effectId(String style) {
        if (style == null || style.isBlank()) {
            return null;
        }

        String normalized = style.toLowerCase(Locale.ROOT);
        return normalized.indexOf(':') >= 0
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.tryBuild(TensuraMod.MOD_ID, normalized);
    }

    private static boolean isEntityBound(String shape) {
        return "attachment".equals(shape) || "projectile".equals(shape) || "aura".equals(shape);
    }

    private static boolean isDirectional(String shape) {
        return "beam".equals(shape) || "ribbon".equals(shape)
                || "cone".equals(shape) || "wave".equals(shape);
    }

    private static double scale(SpellVfxPacket packet) {
        return packet.radius() > 0.0f ? packet.radius() : 1.0;
    }

    private static final class TimedBlockEffectExecutor extends BlockEffectExecutor {
        private final int durationTicks;
        private long expiresAt;

        private TimedBlockEffectExecutor(FX effect, net.minecraft.world.level.Level level,
                                         BlockPos position, int durationTicks) {
            super(effect, level, position);
            this.durationTicks = durationTicks;
        }

        @Override
        public void start() {
            super.start();
            expiresAt = level.getGameTime() + durationTicks;
        }

        @Override
        public void updateFXObjectTick(IFXObject object) {
            if (durationTicks > 0 && runtime != null && object == runtime.getRoot()
                    && level.getGameTime() >= expiresAt) {
                runtime.destroy(false);
                retire(CACHE, pos);
                return;
            }
            super.updateFXObjectTick(object);
        }
    }

    private static final class TimedEntityEffectExecutor extends EntityEffectExecutor {
        private final int durationTicks;
        private long expiresAt;

        private TimedEntityEffectExecutor(FX effect, Entity entity, int durationTicks) {
            super(effect, entity.level(), entity, AutoRotate.LOOK);
            this.durationTicks = durationTicks;
        }

        @Override
        public void start() {
            super.start();
            expiresAt = level.getGameTime() + durationTicks;
        }

        @Override
        public void updateFXObjectTick(IFXObject object) {
            if (durationTicks > 0 && runtime != null && object == runtime.getRoot()
                    && level.getGameTime() >= expiresAt) {
                runtime.destroy(false);
                retire(CACHE, entity);
                return;
            }
            super.updateFXObjectTick(object);
        }
    }
}