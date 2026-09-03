package com.tensura.registry;

import com.tensura.TensuraMod;
import com.tensura.gui.RecallStationMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class TensuraMenuRegistry {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, TensuraMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<RecallStationMenu>> RECALL_STATION =
            MENUS.register("recall_station", () -> IMenuTypeExtension.create(RecallStationMenu::new));
}
