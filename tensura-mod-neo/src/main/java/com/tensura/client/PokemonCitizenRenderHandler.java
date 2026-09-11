package com.tensura.client;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonBehaviourFlag;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.tensura.TensuraMod;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = "tensura", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class PokemonCitizenRenderHandler {

    private static final Map<Integer, PokemonEntity> fakeEntities = new HashMap<>();
    // Track entity IDs of our fake Pokemon so we can cancel their own render events
    private static final Set<Integer> fakeEntityIds = new HashSet<>();
    private static final Map<Integer, Long> lastRenderedTick = new HashMap<>();
    // Separate from lastRenderedTick: that one is stamped on every render pass (cleanup uses it),
    // while this gates the limb-swing advance to once per game tick.
    private static final Map<Integer, Long> lastAnimTick = new HashMap<>();
    private static final Map<Integer, Long> animationEpochTicks = new HashMap<>();
    // Set to true while we are manually rendering a fake entity — prevents our handler from cancelling its own render
    private static boolean manualRendering = false;

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        // Cancel render of our own fake Pokemon entities when triggered by the level renderer.
        // Do NOT cancel when we are the ones calling render manually (manualRendering flag).
        if (!manualRendering && event.getEntity() instanceof PokemonEntity pokemon && fakeEntityIds.contains(pokemon.getId())) {
            event.setCanceled(true);
            return;
        }

        if (!(event.getEntity() instanceof AbstractEntityCitizen citizen)) return;

        var dataView = citizen.getCitizenDataView();
        if (dataView == null) return;
        int citizenId = dataView.getId();
        String species = CitizenSpeciesClient.getSpecies(citizenId);
        if (species == null) return;

        Level level = citizen.level();
        PokemonEntity fake = fakeEntities.get(citizenId);
        if (fake != null && (fake.level() != level || fake.isRemoved())) {
            removeFake(citizenId);
            fake = null;
        }
        if (fake == null) {
            fake = createFake(level, species);
            fakeEntities.put(citizenId, fake);
            animationEpochTicks.put(citizenId, level.getGameTime());
        }

        if (!fake.getPokemon().getSpecies().getName().equalsIgnoreCase(species)) {
            removeFake(citizenId);
            fake = createFake(level, species);
            fakeEntities.put(citizenId, fake);
            animationEpochTicks.put(citizenId, level.getGameTime());
        }
        lastRenderedTick.put(citizenId, level.getGameTime());

        // Sync transform
        fake.setPosRaw(citizen.getX(), citizen.getY(), citizen.getZ());
        fake.xo = citizen.xo; fake.yo = citizen.yo; fake.zo = citizen.zo;
        fake.setYRot(citizen.getYRot()); fake.yRotO = citizen.yRotO;
        fake.setXRot(citizen.getXRot()); fake.xRotO = citizen.xRotO;
        fake.setOnGround(citizen.onGround());

        double dx = citizen.getX() - citizen.xo;
        double dz = citizen.getZ() - citizen.zo;
        boolean moving = (dx * dx + dz * dz) > 0.0001
            || citizen.getDeltaMovement().horizontalDistanceSqr() > 0.0001
            || citizen.walkAnimation.speed(event.getPartialTick()) > 0.01f;

        // Advance the fake's own limb swing once per tick. Selecting the walk pose is not enough:
        // without this the fake's limbSwing stays at 0, so q.limb_swing never moves and the legs
        // hold still while the model glides along.
        long animTick = level.getGameTime();
        if (lastAnimTick.getOrDefault(citizenId, -1L) != animTick) {
            float walkSpeed = moving ? Math.min((float) Math.sqrt(dx * dx + dz * dz) * 4.0f, 1.0f) : 0.0f;
            fake.walkAnimation.update(walkSpeed, 0.4f);
            lastAnimTick.put(citizenId, animTick);
        }

        if (moving) {
            float bodyDelta = Mth.wrapDegrees(citizen.yBodyRot - fake.yBodyRot);
            fake.yBodyRotO = fake.yBodyRot;
            fake.yBodyRot += Mth.clamp(bodyDelta, -8f, 8f);
        } else {
            fake.yBodyRotO = fake.yBodyRot;
        }

        fake.setDeltaMovement(citizen.getDeltaMovement());
        fake.getEntityData().set(PokemonEntity.getMOVING(), moving);

        PoseType targetPose = resolvePoseType(fake, moving);
        if (fake.getEntityData().get(PokemonEntity.getPOSE_TYPE()) != targetPose) {
            fake.getEntityData().set(PokemonEntity.getPOSE_TYPE(), targetPose);
        }
        if (fake.getDelegate() instanceof PosableState posableState) {
            long epochTick = animationEpochTicks.getOrDefault(citizenId, level.getGameTime());
            long elapsedTicks = Math.max(0L, level.getGameTime() - epochTick);
            posableState.updateAge((int) Math.min(Integer.MAX_VALUE, elapsedTicks));
            posableState.setPoseToFirstSuitable(targetPose);
        }

        boolean working = citizen.swingTime > 0 && !moving;
        fake.setBehaviourFlag(PokemonBehaviourFlag.EXCITED, working);

        event.getPoseStack().pushPose();
        manualRendering = true;
        try {
            Minecraft.getInstance().getEntityRenderDispatcher().render(
                    fake, 0, 0, 0,
                    citizen.getYRot(), event.getPartialTick(),
                    event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight()
            );
        } finally {
            manualRendering = false;
        }
        event.getPoseStack().popPose();

        // Render name tag — Cobblemon's shouldRenderLabel() requires looking directly at the entity.
        // Draw unconditionally via vanilla font instead.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && citizen.distanceToSqr(mc.player) < 4096.0) {
            net.minecraft.client.gui.Font font = mc.font;
            net.minecraft.network.chat.Component nameTag = citizen.getName();
            event.getPoseStack().pushPose();
            event.getPoseStack().translate(0.0, citizen.getBbHeight() + 0.5, 0.0);
            event.getPoseStack().mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            event.getPoseStack().scale(0.025f, -0.025f, 0.025f);
            org.joml.Matrix4f matrix = event.getPoseStack().last().pose();
            float textX = (float) (-font.width(nameTag) / 2);
            int bgColor = (int) (0.25f * 255.0f) << 24;
            font.drawInBatch(nameTag, textX, 0f, 0x20FFFFFF, false, matrix,
                    event.getMultiBufferSource(), net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, bgColor, event.getPackedLight());
            font.drawInBatch(nameTag, textX, 0f, -1, false, matrix,
                    event.getMultiBufferSource(), net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, event.getPackedLight());
            event.getPoseStack().popPose();
        }

        event.setCanceled(true);
    }

    private static PokemonEntity createFake(Level level, String speciesName) {
        Pokemon pokemon = new Pokemon();
        var species = PokemonSpecies.INSTANCE.getByName(speciesName);
        if (species != null) {
            pokemon.setSpecies(species);
        } else {
            TensuraMod.LOGGER.warn("[Tensura] Unknown Pokemon species '{}' — citizen will be invisible!", speciesName);
        }
        PokemonEntity entity = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
        entity.noPhysics = true;
        entity.setInvisible(false);

        // Add to the client level so Cobblemon fully initializes the entity:
        // delegate.initialize(), model loading, pose setup all happen via normal addFreshEntity path.
        level.addFreshEntity(entity);
        fakeEntityIds.add(entity.getId());

        // Suppress Cobblemon's name tag — we render the citizen's name ourselves.
        entity.getEntityData().set(PokemonEntity.getHIDE_LABEL(), true);

        return entity;
    }

    private static PoseType resolvePoseType(PokemonEntity pokemon, boolean moving) {
        var movement = pokemon.getBehaviour().getMoving();
        if (!movement.getWalk().getCanWalk()) {
            if (movement.getFly().getCanFly()) {
                return moving ? PoseType.FLY : PoseType.HOVER;
            }
            if (movement.getSwim().getCanSwimInWater()) {
                return moving ? PoseType.SWIM : PoseType.FLOAT;
            }
        }
        return moving ? PoseType.WALK : PoseType.STAND;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            for (Integer citizenId : Set.copyOf(fakeEntities.keySet())) {
                removeFake(citizenId);
            }
            return;
        }

        long currentTick = level.getGameTime();
        for (Integer citizenId : Set.copyOf(fakeEntities.keySet())) {
            PokemonEntity fake = fakeEntities.get(citizenId);
            Long lastSeen = lastRenderedTick.get(citizenId);
            if (fake == null || fake.level() != level || lastSeen == null || currentTick - lastSeen > 20) {
                removeFake(citizenId);
            }
        }
    }

    private static void removeFake(int citizenId) {
        PokemonEntity old = fakeEntities.remove(citizenId);
        lastRenderedTick.remove(citizenId);
        lastAnimTick.remove(citizenId);
        animationEpochTicks.remove(citizenId);
        if (old != null) {
            fakeEntityIds.remove(old.getId());
            old.discard();
        }
    }

    public static void onCitizenRemoved(int citizenId) {
        removeFake(citizenId);
    }
}
