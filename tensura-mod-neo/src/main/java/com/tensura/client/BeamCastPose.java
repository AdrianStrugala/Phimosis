package com.tensura.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

@OnlyIn(Dist.CLIENT)
public final class BeamCastPose {

    public static final EnumProxy<HumanoidModel.ArmPose> ENUM_PROXY = new EnumProxy<>(
            HumanoidModel.ArmPose.class, false,
            (IArmPoseTransformer) BeamCastPose::apply);

    private BeamCastPose() {
    }

    public static HumanoidModel.ArmPose get() {
        return ENUM_PROXY.getValue();
    }

    private static void apply(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        ModelPart castingArm = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        castingArm.xRot = model.head.xRot - (float) (Math.PI / 2.0);
        castingArm.yRot = model.head.yRot;
        castingArm.zRot = 0.0F;
    }
}