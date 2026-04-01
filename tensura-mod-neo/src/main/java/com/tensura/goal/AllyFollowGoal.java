package com.tensura.goal;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class AllyFollowGoal extends Goal {

    private final PathfinderMob mob;
    private final Player owner;
    private final double speed;
    private final float minDist;
    private final float maxDist;

    public AllyFollowGoal(PathfinderMob mob, Player owner, double speed, float minDist, float maxDist) {
        this.mob = mob;
        this.owner = owner;
        this.speed = speed;
        this.minDist = minDist;
        this.maxDist = maxDist;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.isVehicle()) return false;
        return owner != null && !owner.isRemoved()
                && mob.distanceToSqr(owner) > (double) (minDist * minDist);
    }

    @Override
    public void tick() {
        mob.getLookControl().setLookAt(owner, 10.0F, mob.getMaxHeadXRot());
        if (mob.distanceToSqr(owner) > (double) (maxDist * maxDist)) {
            mob.teleportTo(owner.getX(), owner.getY(), owner.getZ());
        } else {
            mob.getNavigation().moveTo(owner, speed);
        }
    }
}
