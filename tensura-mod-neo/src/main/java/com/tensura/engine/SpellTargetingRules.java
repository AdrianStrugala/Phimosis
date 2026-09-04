package com.tensura.engine;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

public final class SpellTargetingRules {

    private SpellTargetingRules() {
    }

    public static boolean canHarm(ServerPlayer owner, LivingEntity effectCaster,
                                  LivingEntity target) {
        return target.isAlive() && !isProtectedAlly(owner, effectCaster, target);
    }

    public static boolean isProtectedAlly(ServerPlayer owner, LivingEntity effectCaster,
                                          LivingEntity target) {
        if (target == owner || target == effectCaster) return true;
        if (target.getTags().contains("tensura:combat_companion")) return true;

        UUID ownerId = owner.getUUID();
        if (target instanceof PokemonEntity pokemon && ownerId.equals(pokemon.getOwnerUUID())) {
            return true;
        }

        if (target instanceof AbstractEntityCitizen citizen) {
            var citizenData = citizen.getCitizenDataView();
            if (citizenData == null) return false;

            IColony ownerColony = IColonyManager.getInstance()
                    .getIColonyByOwner(owner.level(), ownerId);
            return ownerColony != null && citizenData.getColonyId() == ownerColony.getID();
        }

        return false;
    }
}