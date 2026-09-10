package com.tensura.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.tensura.TensuraMod;
import com.tensura.registry.TensuraItemRegistry;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = TensuraMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BeamCastClientExtension implements IClientItemExtensions {

    public static final BeamCastClientExtension INSTANCE = new BeamCastClientExtension();

    private BeamCastClientExtension() {
    }

    @SubscribeEvent
    public static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(INSTANCE,
                TensuraItemRegistry.SPELL_ITEM, TensuraItemRegistry.SPELL_FOCUS);
    }

    @Override
    @Nullable
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand,
                                            ItemStack stack) {
        if (!entity.isUsingItem() || entity.getUsedItemHand() != hand) return null;
        return BeamCastPose.get();
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player,
                                           HumanoidArm arm, ItemStack stack,
                                           float partialTick, float equipProcess,
                                           float swingProcess) {
        if (!player.isUsingItem()) return false;
        HumanoidArm castingArm = player.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        if (castingArm != arm) return false;

        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(side * 0.48F, -0.42F - equipProcess * 0.6F, -0.82F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-12.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 8.0F));
        return true;
    }
}