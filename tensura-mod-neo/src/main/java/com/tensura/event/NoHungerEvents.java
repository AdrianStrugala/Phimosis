package com.tensura.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class NoHungerEvents {

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;
        FoodData food = player.getFoodData();
        if (food.getFoodLevel() < 20) {
            food.setFoodLevel(20);
        }
        if (food.getSaturationLevel() < 5f) {
            food.setSaturation(5f);
        }
    }
}
