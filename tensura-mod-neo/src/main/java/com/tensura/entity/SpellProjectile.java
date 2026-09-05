package com.tensura.entity;

import com.tensura.engine.SpellDefinition;
import com.tensura.engine.SpellExecutor;
import com.tensura.engine.SpellRegistry;
import com.tensura.engine.SpellTargetingRules;
import com.tensura.event.SpellRuntimeController;
import com.tensura.registry.TensuraEntityRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;


public class SpellProjectile extends AbstractHurtingProjectile implements ItemSupplier {

    private static final EntityDataAccessor<String> DATA_SPELL_ID =
        SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_PROJECTILE_INDEX =
        SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.INT);

    private String spellId = "";
    private String school = "physical";
    private int maxTicks = 40;
    private int projectileIndex = 0;
    private int projectileCount = 1;
    private UUID meteorGroup;
    private UUID projectileGroup;
    private UUID sourceEntityId;
    private UUID homingTargetId;
    private double homingStrength;

    // Deserialization constructor (required by EntityType.Builder.of)
    public SpellProjectile(EntityType<? extends SpellProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPELL_ID, "");
        builder.define(DATA_PROJECTILE_INDEX, 0);
    }

    public static SpellProjectile create(ServerPlayer caster, ResourceLocation spellId, SpellDefinition def) {
        return create(caster, spellId, def, caster.getLookAngle(), 0, 1);
    }

    public static SpellProjectile create(ServerPlayer caster, ResourceLocation spellId, SpellDefinition def,
                                         Vec3 direction, int projectileIndex, int projectileCount) {
        return create(caster, caster, spellId, def, direction, projectileIndex, projectileCount);
    }

    public static SpellProjectile create(ServerPlayer owner, LivingEntity source,
                                         ResourceLocation spellId, SpellDefinition def,
                                         Vec3 direction, int projectileIndex, int projectileCount) {
        return create(owner, source, spellId, def, direction, projectileIndex, projectileCount, null);
    }

    public static SpellProjectile create(ServerPlayer owner, LivingEntity source,
                                         ResourceLocation spellId, SpellDefinition def,
                                         Vec3 direction, int projectileIndex, int projectileCount,
                                         UUID projectileGroup) {
        return create(owner, source, spellId, def, direction, projectileIndex, projectileCount,
                projectileGroup, null);
    }

    public static SpellProjectile create(ServerPlayer owner, LivingEntity source,
                                         ResourceLocation spellId, SpellDefinition def,
                                         Vec3 direction, int projectileIndex, int projectileCount,
                                         UUID projectileGroup, LivingEntity homingTarget) {
        Level level = source.level();
        SpellProjectile proj = new SpellProjectile(TensuraEntityRegistry.SPELL_PROJECTILE.get(), level);
        proj.spellId = spellId.toString();
        proj.entityData.set(DATA_SPELL_ID, proj.spellId);
        proj.school = def.school;
        proj.projectileIndex = projectileIndex;
        proj.entityData.set(DATA_PROJECTILE_INDEX, projectileIndex);
        proj.projectileCount = projectileCount;
        proj.projectileGroup = projectileGroup;
        proj.sourceEntityId = source.getUUID();
        proj.homingTargetId = homingTarget == null ? null : homingTarget.getUUID();
        proj.homingStrength = Math.max(0.0, Math.min(1.0, def.delivery.homing_strength));

        Vec3 eye = source.getEyePosition();
        proj.setOwner(owner);
        proj.moveTo(eye.x, eye.y, eye.z, source.getYRot(), source.getXRot());
        proj.setNoGravity(true);

        // Constant velocity, straight line — analogous to Spell Engine
        double speed = Math.max(0.5, def.delivery.speed);
        proj.setDeltaMovement(direction.normalize().scale(speed * 0.6));
        proj.maxTicks = (int) (def.targeting.range / (speed * 0.6)) + 10;
        // power defaults to Vec3.ZERO in AbstractHurtingProjectile constructor

        return proj;
    }

    public static SpellProjectile createMeteor(ServerPlayer caster, ResourceLocation spellId, SpellDefinition def,
                                               Vec3 spawnPosition, Vec3 impactPosition, UUID groupId,
                                               int projectileIndex, int projectileCount) {
        return createMeteor(caster, caster, spellId, def, spawnPosition, impactPosition,
                groupId, projectileIndex, projectileCount);
    }

    public static SpellProjectile createMeteor(ServerPlayer owner, LivingEntity source,
                                               ResourceLocation spellId, SpellDefinition def,
                                               Vec3 spawnPosition, Vec3 impactPosition, UUID groupId,
                                               int projectileIndex, int projectileCount) {
        SpellProjectile projectile = new SpellProjectile(TensuraEntityRegistry.SPELL_PROJECTILE.get(), source.level());
        projectile.spellId = spellId.toString();
        projectile.entityData.set(DATA_SPELL_ID, projectile.spellId);
        projectile.school = def.school;
        projectile.projectileIndex = projectileIndex;
        projectile.entityData.set(DATA_PROJECTILE_INDEX, projectileIndex);
        projectile.projectileCount = projectileCount;
        projectile.meteorGroup = groupId;
        projectile.sourceEntityId = source.getUUID();
        projectile.setOwner(owner);
        projectile.moveTo(spawnPosition.x, spawnPosition.y, spawnPosition.z);
        projectile.setNoGravity(true);
        double speed = Math.max(0.5, def.delivery.speed);
        projectile.setDeltaMovement(impactPosition.subtract(spawnPosition).normalize().scale(speed * 0.6));
        projectile.maxTicks = 100;
        return projectile;
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > maxTicks) {
            this.discard();
            return;
        }
        if (level() instanceof ServerLevel serverLevel) {
            updateHoming(serverLevel);
            serverLevel.sendParticles(schoolParticle(), getX(), getY(), getZ(),
                    4, 0.15, 0.15, 0.15, 0.01);
        }
    }

    private void updateHoming(ServerLevel level) {
        if (homingTargetId == null || homingStrength <= 0.0) return;
        Entity entity = level.getEntity(homingTargetId);
        if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
            homingTargetId = null;
            return;
        }
        Entity ownerEntity = getOwner();
        if (!(ownerEntity instanceof ServerPlayer owner)) return;
        Entity source = sourceEntityId == null ? null : level.getEntity(sourceEntityId);
        LivingEntity effectCaster = source instanceof LivingEntity living ? living : owner;
        if (!SpellTargetingRules.canHarm(owner, effectCaster, target)) {
            homingTargetId = null;
            return;
        }

        Vec3 movement = getDeltaMovement();
        double speed = movement.length();
        if (speed <= 1.0E-6) return;
        Vec3 desired = target.getBoundingBox().getCenter().subtract(position()).normalize().scale(speed);
        setDeltaMovement(movement.lerp(desired, homingStrength));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide) return;
        if (!(result.getEntity() instanceof LivingEntity target)) return;
        if (target.equals(getOwner()) || target.getUUID().equals(sourceEntityId)) return;

        SpellDefinition def = SpellRegistry.get(ResourceLocation.parse(spellId)).orElse(null);
        if (def != null && getOwner() instanceof ServerPlayer caster) {
            Entity source = (sourceEntityId == null || !(level() instanceof ServerLevel serverLevel))
                    ? null : serverLevel.getEntity(sourceEntityId);
            LivingEntity effectCaster = source instanceof LivingEntity living ? living : caster;
            if (!SpellTargetingRules.canHarm(caster, effectCaster, target)) {
                discard();
                return;
            }
            if (meteorGroup != null) {
                SpellRuntimeController.applyMeteorImpact(caster, def, position(), meteorGroup);
            } else {
                boolean finalImpact = projectileIndex == projectileCount - 1;
                if (projectileGroup != null) {
                    SpellRuntimeController.ProjectileImpact impact =
                            SpellRuntimeController.registerProjectileImpact(projectileGroup,
                                    target.getUUID(), def.targeting.max_targets);
                    if (!impact.allowed()) {
                        discard();
                        return;
                    }
                    finalImpact = impact.firstHit();
                    if (!impact.firstHit()) {
                        target.invulnerableTime = 0;
                    }
                }
                if (def.targeting.radius > 0.0) {
                    SpellExecutor.applyProjectileSplash(caster, effectCaster, target, def);
                } else {
                    SpellExecutor.applyImpacts(caster, effectCaster, target, def,
                            finalImpact);
                }
            }
        }
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!level().isClientSide && meteorGroup != null && getOwner() instanceof ServerPlayer caster) {
            SpellDefinition def = SpellRegistry.get(ResourceLocation.parse(spellId)).orElse(null);
            if (def != null) {
                SpellRuntimeController.applyMeteorImpact(caster, def, result.getLocation(), meteorGroup);
            }
        }
        super.onHitBlock(result);
        this.discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public ItemStack getItem() {
        String syncedSpellId = entityData.get(DATA_SPELL_ID);
        if (syncedSpellId.endsWith(":tri_attack")) {
            return switch (entityData.get(DATA_PROJECTILE_INDEX)) {
                case 0 -> new ItemStack(Items.FIRE_CHARGE);
                case 1 -> new ItemStack(Items.SNOWBALL);
                default -> new ItemStack(Items.GLOWSTONE_DUST);
            };
        }
        if (syncedSpellId.endsWith(":draco_meteor")) return new ItemStack(Items.MAGMA_CREAM);
        if (syncedSpellId.endsWith(":rock_slide")) return new ItemStack(Items.COBBLESTONE);
        if (syncedSpellId.endsWith(":ember")) return new ItemStack(Items.FIRE_CHARGE);
        return new ItemStack(Items.ENDER_PEARL);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SpellId", spellId);
        tag.putString("School", school);
        tag.putInt("MaxTicks", maxTicks);
        tag.putInt("ProjectileIndex", projectileIndex);
        tag.putInt("ProjectileCount", projectileCount);
        if (meteorGroup != null) tag.putUUID("MeteorGroup", meteorGroup);
        if (projectileGroup != null) tag.putUUID("ProjectileGroup", projectileGroup);
        if (sourceEntityId != null) tag.putUUID("SourceEntity", sourceEntityId);
        if (homingTargetId != null) tag.putUUID("HomingTarget", homingTargetId);
        tag.putDouble("HomingStrength", homingStrength);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        spellId = tag.getString("SpellId");
        entityData.set(DATA_SPELL_ID, spellId);
        school = tag.getString("School");
        maxTicks = tag.contains("MaxTicks") ? tag.getInt("MaxTicks") : maxTicks;
        projectileIndex = tag.getInt("ProjectileIndex");
        entityData.set(DATA_PROJECTILE_INDEX, projectileIndex);
        projectileCount = Math.max(1, tag.getInt("ProjectileCount"));
        meteorGroup = tag.hasUUID("MeteorGroup") ? tag.getUUID("MeteorGroup") : null;
        projectileGroup = tag.hasUUID("ProjectileGroup") ? tag.getUUID("ProjectileGroup") : null;
        sourceEntityId = tag.hasUUID("SourceEntity") ? tag.getUUID("SourceEntity") : null;
        homingTargetId = tag.hasUUID("HomingTarget") ? tag.getUUID("HomingTarget") : null;
        homingStrength = tag.getDouble("HomingStrength");
    }

    private SimpleParticleType schoolParticle() {
        if (spellId.endsWith(":tri_attack")) {
            return switch (projectileIndex) {
                case 0 -> ParticleTypes.FLAME;
                case 1 -> ParticleTypes.SNOWFLAKE;
                default -> ParticleTypes.ELECTRIC_SPARK;
            };
        }
        return switch (school) {
            case "lightning" -> ParticleTypes.ELECTRIC_SPARK;
            case "fire"      -> ParticleTypes.FLAME;
            case "water"     -> ParticleTypes.DRIPPING_WATER;
            case "ice"       -> ParticleTypes.SNOWFLAKE;
            case "shadow"    -> ParticleTypes.PORTAL;
            case "psychic"   -> ParticleTypes.ENCHANT;
            case "dragon"    -> ParticleTypes.DRAGON_BREATH;
            case "nature"    -> ParticleTypes.COMPOSTER;
            case "poison"    -> ParticleTypes.WITCH;
            case "earth"     -> ParticleTypes.EXPLOSION;
            case "wind"      -> ParticleTypes.CLOUD;
            case "fairy"     -> ParticleTypes.TOTEM_OF_UNDYING;
            default          -> ParticleTypes.CRIT;
        };
    }
}
