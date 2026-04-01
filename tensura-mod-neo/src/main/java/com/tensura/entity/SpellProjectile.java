package com.tensura.entity;

import com.tensura.engine.SpellDefinition;
import com.tensura.engine.SpellExecutor;
import com.tensura.engine.SpellRegistry;
import com.tensura.registry.TensuraEntityRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;


public class SpellProjectile extends AbstractHurtingProjectile {

    private String spellId = "";
    private String school = "physical";
    private int maxTicks = 40;

    // Deserialization constructor (required by EntityType.Builder.of)
    public SpellProjectile(EntityType<? extends SpellProjectile> type, Level level) {
        super(type, level);
    }

    public static SpellProjectile create(ServerPlayer caster, ResourceLocation spellId, SpellDefinition def) {
        Level level = caster.level();
        SpellProjectile proj = new SpellProjectile(TensuraEntityRegistry.SPELL_PROJECTILE.get(), level);
        proj.spellId = spellId.toString();
        proj.school = def.school;

        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        proj.setOwner(caster);
        proj.moveTo(eye.x, eye.y, eye.z, caster.getYRot(), caster.getXRot());
        proj.setNoGravity(true);

        // Constant velocity, straight line — analogous to Spell Engine
        double speed = Math.max(0.5, def.delivery.speed);
        proj.setDeltaMovement(look.scale(speed * 0.6));
        proj.maxTicks = (int) (def.targeting.range / (speed * 0.6)) + 10;
        // power defaults to Vec3.ZERO in AbstractHurtingProjectile constructor

        return proj;
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > maxTicks) {
            this.discard();
            return;
        }
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(schoolParticle(), getX(), getY(), getZ(), 4, 0.15, 0.15, 0.15, 0.01);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide) return;
        if (!(result.getEntity() instanceof LivingEntity target)) return;
        if (target.equals(getOwner())) return;

        SpellDefinition def = SpellRegistry.get(ResourceLocation.parse(spellId)).orElse(null);
        if (def != null && getOwner() instanceof ServerPlayer caster) {
            SpellExecutor.applyImpacts(caster, target, def);
        }
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SpellId", spellId);
        tag.putString("School", school);
        tag.putInt("MaxTicks", maxTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        spellId = tag.getString("SpellId");
        school = tag.getString("School");
        maxTicks = tag.contains("MaxTicks") ? tag.getInt("MaxTicks") : maxTicks;
    }

    private SimpleParticleType schoolParticle() {
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
