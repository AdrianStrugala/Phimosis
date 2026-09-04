package com.tensura;

import com.tensura.command.TensuraCommands;
import com.tensura.config.TensuraConfig;
import com.tensura.event.CombatCompanionEvents;
import com.tensura.event.ColonyGamemodeEvents;
import com.tensura.event.ColonyStartupEvents;
import com.tensura.event.ConversionEvents;
import com.tensura.event.NoHungerEvents;
import com.tensura.event.PredatorEvents;
import com.tensura.event.PredatorSyncEvents;
import com.tensura.event.TensuraAttributeEffects;
import com.tensura.gui.RecallStationScreen;
import com.tensura.item.SpellItem;
import com.tensura.network.NetworkHandler;
import com.tensura.registry.TensuraAttributes;
import com.tensura.registry.TensuraBlockRegistry;
import com.tensura.registry.TensuraEntityRegistry;
import com.tensura.registry.TensuraItemRegistry;
import com.tensura.registry.TensuraMenuRegistry;
import com.tensura.registry.TensuraMobEffects;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(TensuraMod.MOD_ID)
public class TensuraMod {

    public static final String MOD_ID = "tensura";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public TensuraMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, TensuraConfig.SPEC);

        TensuraBlockRegistry.BLOCKS.register(modBus);
        TensuraItemRegistry.ITEMS.register(modBus);
        TensuraMenuRegistry.MENUS.register(modBus);
        TensuraEntityRegistry.ENTITIES.register(modBus);
        TensuraAttributes.ATTRIBUTES.register(modBus);
        TensuraMobEffects.MOB_EFFECTS.register(modBus);

        modBus.register(NetworkHandler.class);

        NeoForge.EVENT_BUS.register(new NoHungerEvents());
        NeoForge.EVENT_BUS.register(new TensuraAttributeEffects());
        NeoForge.EVENT_BUS.register(new ColonyGamemodeEvents());
        NeoForge.EVENT_BUS.register(new TensuraCommands());
        NeoForge.EVENT_BUS.register(new ColonyStartupEvents());
        NeoForge.EVENT_BUS.register(new PredatorEvents());
        NeoForge.EVENT_BUS.register(new PredatorSyncEvents());
        // ConversionEvents must be registered BEFORE CombatCompanionEvents
        NeoForge.EVENT_BUS.register(ConversionEvents.class);
        ConversionEvents.registerCobblemonHooks();
        CombatCompanionEvents combatEvents = new CombatCompanionEvents();
        NeoForge.EVENT_BUS.register(combatEvents);
        CombatCompanionEvents.registerCobblemonHooks();
    }

    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onModifyAttributes(EntityAttributeModificationEvent event) {
            event.add(EntityType.PLAYER, TensuraAttributes.DARK_SENSE);
            event.add(EntityType.PLAYER, TensuraAttributes.COLONY_AURA);
            event.add(EntityType.PLAYER, TensuraAttributes.XP_GAIN_MULTIPLIER);
        }
    }

    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientEvents {

        @SubscribeEvent
        public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
            event.register(TensuraMenuRegistry.RECALL_STATION.get(), RecallStationScreen::new);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                ItemProperties.register(
                        TensuraItemRegistry.SPELL_ITEM.get(),
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "school"),
                        (stack, level, entity, seed) -> SpellItem.getSchoolIndex(stack)
                );
            });
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(TensuraEntityRegistry.SPELL_PROJECTILE.get(), NoopRenderer::new);
        }
    }
}
