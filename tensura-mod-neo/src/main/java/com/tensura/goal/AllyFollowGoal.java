package com.tensura.goal;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.UUID;

public class AllyFollowGoal extends Goal {

    private final PathfinderMob mob;
    private final UUID ownerId;
    private final double speed;
    private final float minDist;
    private final float maxDist;

    public AllyFollowGoal(PathfinderMob mob, Player owner, double speed, float minDist, float maxDist) {
        this.mob = mob;
        this.ownerId = owner.getUUID();
        this.speed = speed;
        this.minDist = minDist;
        this.maxDist = maxDist;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * Resolved per call rather than held: a Player instance is replaced on respawn and on
     * dimension change, and the stale one stays isRemoved() forever, which would stop the mob
     * from ever following again. Null also covers "owner is in another level".
     */
    private Player owner() {
        return mob.level().getPlayerByUUID(ownerId);
    }

    @Override
    public boolean canUse() {
        if (mob.isVehicle()) return false;
        Player owner = owner();
        return owner != null && !owner.isRemoved()
                && mob.distanceToSqr(owner) > (double) (minDist * minDist);
    }

    @Override
    public void tick() {
        Player owner = owner();
        if (owner == null) return;
        mob.getLookControl().setLookAt(owner, 10.0F, mob.getMaxHeadXRot());
        if (mob.distanceToSqr(owner) > (double) (maxDist * maxDist)) {
            mob.teleportTo(owner.getX(), owner.getY(), owner.getZ());
        } else {
            mob.getNavigation().moveTo(owner, speed);
        }
    }
}
