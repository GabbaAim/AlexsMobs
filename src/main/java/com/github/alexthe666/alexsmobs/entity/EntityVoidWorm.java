package com.github.alexthe666.alexsmobs.entity;

import com.github.alexthe666.alexsmobs.entity.ai.DirectPathNavigator;
import com.github.alexthe666.alexsmobs.entity.ai.EntityAINearestTarget3D;
import com.github.alexthe666.alexsmobs.entity.ai.FlightMoveController;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class EntityVoidWorm extends MonsterEntity {

    private static final DataParameter<Optional<UUID>> CHILD_UUID = EntityDataManager.createKey(EntityVoidWorm.class, DataSerializers.OPTIONAL_UNIQUE_ID);
    private static final DataParameter<Optional<UUID>> SPLIT_FROM_UUID = EntityDataManager.createKey(EntityVoidWorm.class, DataSerializers.OPTIONAL_UNIQUE_ID);
    private static final DataParameter<Integer> SEGMENT_COUNT = EntityDataManager.createKey(EntityVoidWorm.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> JAW_TICKS = EntityDataManager.createKey(EntityVoidWorm.class, DataSerializers.VARINT);
    private static final DataParameter<Float> WORM_ANGLE = EntityDataManager.createKey(EntityVoidWorm.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> SPEEDMOD = EntityDataManager.createKey(EntityVoidWorm.class, DataSerializers.FLOAT);
    private static final DataParameter<Boolean> SPLITTER = EntityDataManager.createKey(EntityVoidWorm.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Integer> PORTAL_TICKS = EntityDataManager.createKey(EntityVoidWorm.class, DataSerializers.VARINT);
    public float prevWormAngle;
    public float prevJawProgress;
    public float jawProgress;
    public Vector3d teleportPos = null;
    public Vector3d moveIntoPortalPos = null;

    protected EntityVoidWorm(EntityType<? extends MonsterEntity> type, World worldIn) {
        super(type, worldIn);
        this.moveController = new FlightMoveController(this, 1F, false, true);
    }

    public static AttributeModifierMap.MutableAttribute bakeAttributes() {
        return MonsterEntity.func_234295_eP_().createMutableAttribute(Attributes.MAX_HEALTH, 160.0D).createMutableAttribute(Attributes.ARMOR, 4.0D).createMutableAttribute(Attributes.FOLLOW_RANGE, 256.0D).createMutableAttribute(Attributes.MOVEMENT_SPEED, 0.3F).createMutableAttribute(Attributes.ATTACK_DAMAGE, 5);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source == DamageSource.FALL || source == DamageSource.DROWN || source == DamageSource.IN_WALL || source == DamageSource.FALLING_BLOCK || source == DamageSource.LAVA || source.isFireDamage() || super.isInvulnerableTo(source);
    }

    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new EntityVoidWorm.AIAttack());
        this.goalSelector.addGoal(2, new EntityVoidWorm.AIFlyIdle());
        this.targetSelector.addGoal(1, new EntityAINearestTarget3D(this, EntityMungus.class, 10, false, true, null) {
            public boolean shouldExecute() {
                return super.shouldExecute();
            }
        });
        this.targetSelector.addGoal(1, new EntityAINearestTarget3D(this, PlayerEntity.class, 10, false, true, null) {
            public boolean shouldExecute() {
                return super.shouldExecute();
            }
        });
        this.targetSelector.addGoal(1, new EntityAINearestTarget3D(this, EntityWarpedMosco.class, 10, false, true, null) {
            public boolean shouldExecute() {
                return super.shouldExecute();
            }
        });
    }

    protected PathNavigator createNavigator(World worldIn) {
        return new DirectPathNavigator(this, world);
    }

    public void readAdditional(CompoundNBT compound) {
        super.readAdditional(compound);
        if (compound.hasUniqueId("ChildUUID")) {
            this.setChildId(compound.getUniqueId("ChildUUID"));
        }
        this.setWormSpeed(compound.getFloat("WormSpeed"));
        this.setSplitter(compound.getBoolean("Splitter"));
        this.setPortalTicks(compound.getInt("PortalTicks"));
    }

    public boolean hasNoGravity() {
        return true;
    }

    public void writeAdditional(CompoundNBT compound) {
        super.writeAdditional(compound);
        if (this.getChildId() != null) {
            compound.putUniqueId("ChildUUID", this.getChildId());
        }
        compound.putInt("PortalTicks", getPortalTicks());
        compound.putFloat("WormSpeed", getWormSpeed());
        compound.putBoolean("Splitter", isSplitter());
    }

    public Entity getChild() {
        UUID id = getChildId();
        if (id != null && !world.isRemote) {
            return ((ServerWorld) world).getEntityByUuid(id);
        }
        return null;
    }

    public boolean canBeLeashedTo(PlayerEntity player) {
        return true;
    }


    public void tick() {
        super.tick();
        prevWormAngle = this.getWormAngle();
        prevJawProgress = this.jawProgress;
       /*
        if (this.ticksExisted % 720 > 360) {
            rotationYaw++;
        } else {
            rotationYaw--;
        }
        */
        float threshold = 0.05F;
        if (this.prevRotationYaw - this.rotationYaw > threshold) {
            this.setWormAngle(this.getWormAngle() + 15);
        } else if (this.prevRotationYaw - this.rotationYaw < -threshold) {
            this.setWormAngle(this.getWormAngle() - 15);
        } else if (this.getWormAngle() > 0) {
            this.setWormAngle(Math.max(this.getWormAngle() - 20, 0));
        } else if (this.getWormAngle() < 0) {
            this.setWormAngle(Math.min(this.getWormAngle() + 20, 0));
        }
        this.setMotion(this.getMotion().add(0, 0.01, 0));
        if (this.dataManager.get(JAW_TICKS) > 0) {
            if (this.jawProgress < 5) {
                jawProgress++;
            }
            this.dataManager.set(JAW_TICKS, this.dataManager.get(JAW_TICKS) - 1);
        } else {
            if (this.jawProgress > 0) {
                jawProgress--;
            }
        }
        if (this.isAlive()) {
            for (Entity entity : this.world.getEntitiesWithinAABB(LivingEntity.class, this.getBoundingBox().grow(1.0D), null)) {
                if (!entity.isEntityEqual(this) && !(entity instanceof EntityVoidWormPart) && !entity.isOnSameTeam(this) && entity != this) {
                    launch(entity, false);
                }
            }
            stepHeight = 2;
        }
        renderYawOffset = rotationYaw;
        float f2 = (float) -((float) this.getMotion().y * (double) (180F / (float) Math.PI));
        this.rotationPitch = f2;
        this.stepHeight = 2;
        if (!world.isRemote) {
            if (ticksExisted % 10 == 0) {
                createStuckPortal();
            }
            Entity child = getChild();
            if (child == null) {
                LivingEntity partParent = this;
                int tailstart = Math.min(3 + rand.nextInt(2), getSegmentCount());
                int segments = getSegmentCount();
                for (int i = 0; i < segments; i++) {
                    float scale = 1F + (i / (float) segments) * 0.5F;
                    boolean tail = false;
                    if (i >= segments - tailstart) {
                        tail = true;
                        scale = scale * 0.85F;
                    }
                    EntityVoidWormPart part = new EntityVoidWormPart(AMEntityRegistry.VOID_WORM_PART, partParent, 1.0F + (scale * (tail ? 0.65F : 0.35F)), 180, i == 0 ? -0.0F : i == segments - tailstart ? -0.3F : 0);
                    part.setParent(partParent);

                    part.setBodyIndex(i);
                    part.setTail(tail);
                    part.setWormScale(scale);
                    if (partParent == this) {
                        this.setChildId(part.getUniqueID());
                    } else if (partParent instanceof EntityVoidWormPart) {
                        ((EntityVoidWormPart) partParent).setChildId(part.getUniqueID());
                    }
                    part.setInitialPartPos(this);
                    partParent = part;
                    world.addEntity(part);
                }
            }
        }
        if (this.getPortalTicks() > 0) {
            this.setPortalTicks(this.getPortalTicks() - 1);
            if (this.getPortalTicks() == 2 && teleportPos != null) {
                this.setPosition(teleportPos.x, teleportPos.y, teleportPos.z);
                if (this.getChild() instanceof EntityVoidWormPart) {
                    ((EntityVoidWormPart) this.getChild()).teleportTo(teleportPos);
                }
                teleportPos = null;
            }
        }

    }

    public void teleportTo(Vector3d vec) {
        this.setPortalTicks(25);
        teleportPos = vec;
    }

    private void launch(Entity e, boolean huge) {
        if (e.isOnGround()) {
            double d0 = e.getPosX() - this.getPosX();
            double d1 = e.getPosZ() - this.getPosZ();
            double d2 = Math.max(d0 * d0 + d1 * d1, 0.001D);
            float f = huge ? 2F : 0.5F;
            e.addVelocity(d0 / d2 * f, huge ? 0.5D : 0.2F, d1 / d2 * f);
        }
    }

    public void resetWormScales() {
        if (!world.isRemote) {
            Entity child = getChild();
            if (child == null) {
                LivingEntity nextPart = this;
                int tailstart = Math.min(3 + rand.nextInt(2), getSegmentCount());
                int segments = getSegmentCount();
                int i = 0;
                while (nextPart instanceof EntityVoidWormPart) {
                    EntityVoidWormPart part = ((EntityVoidWormPart) ((EntityVoidWormPart) nextPart).getChild());
                    i++;
                    float scale = 1F + (i / (float) segments) * 0.5F;
                    part.setTail(i >= segments - tailstart);
                    part.setWormScale(scale);
                    nextPart = part;
                }
            }
        }
    }

    @Nullable
    public ILivingEntityData onInitialSpawn(IServerWorld worldIn, DifficultyInstance difficultyIn, SpawnReason
            reason, @Nullable ILivingEntityData spawnDataIn, @Nullable CompoundNBT dataTag) {
        this.setSegmentCount(25 + rand.nextInt(15));
        this.rotationPitch = 0.0F;
        return super.onInitialSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
    }

    @Override
    protected void registerData() {
        super.registerData();
        this.dataManager.register(SPLIT_FROM_UUID, Optional.empty());
        this.dataManager.register(CHILD_UUID, Optional.empty());
        this.dataManager.register(SEGMENT_COUNT, 10);
        this.dataManager.register(JAW_TICKS, 0);
        this.dataManager.register(WORM_ANGLE, 0F);
        this.dataManager.register(SPEEDMOD, 1F);
        this.dataManager.register(SPLITTER, false);
        this.dataManager.register(PORTAL_TICKS, 0);
    }


    public float getWormAngle() {
        return this.dataManager.get(WORM_ANGLE);
    }

    public void setWormAngle(float progress) {
        this.dataManager.set(WORM_ANGLE, progress);
    }

    public float getWormSpeed() {
        return this.dataManager.get(SPEEDMOD);
    }

    public void setWormSpeed(float progress) {
        if (getWormSpeed() != progress) {
            moveController = new FlightMoveController(this, progress, false, true);
        }
        this.dataManager.set(SPEEDMOD, progress);
    }

    public boolean isSplitter() {
        return this.dataManager.get(SPLITTER);
    }

    public void setSplitter(boolean splitter) {
        this.dataManager.set(SPLITTER, splitter);
    }

    public void openMouth(int time) {
        this.dataManager.set(JAW_TICKS, time);
    }

    public boolean isMouthOpen() {
        return dataManager.get(JAW_TICKS) >= 5F;
    }

    @Nullable
    public UUID getChildId() {
        return this.dataManager.get(CHILD_UUID).orElse(null);
    }

    public void setChildId(@Nullable UUID uniqueId) {
        this.dataManager.set(CHILD_UUID, Optional.ofNullable(uniqueId));
    }

    @Nullable
    public UUID getSplitFromUUID() {
        return this.dataManager.get(SPLIT_FROM_UUID).orElse(null);
    }

    public void setSplitFromUuid(@Nullable UUID uniqueId) {
        this.dataManager.set(SPLIT_FROM_UUID, Optional.ofNullable(uniqueId));
    }

    public int getPortalTicks() {
        return this.dataManager.get(PORTAL_TICKS).intValue();
    }

    public void setPortalTicks(int ticks) {
        this.dataManager.set(PORTAL_TICKS, Integer.valueOf(ticks));
    }

    public int getSegmentCount() {
        return this.dataManager.get(SEGMENT_COUNT).intValue();
    }

    public void setSegmentCount(int command) {
        this.dataManager.set(SEGMENT_COUNT, Integer.valueOf(command));
    }

    public void collideWithNearbyEntities() {
        List<Entity> entities = this.world.getEntitiesWithinAABBExcludingEntity(this, this.getBoundingBox().expand(0.20000000298023224D, 0.0D, 0.20000000298023224D));
        entities.stream().filter(entity -> !(entity instanceof EntityVoidWormPart) && entity.canBePushed()).forEach(entity -> entity.applyEntityCollision(this));
    }

    @Override
    public void applyEntityCollision(Entity entityIn) {

    }

    public void createStuckPortal() {
        Vector3d Vector3d = new Vector3d(this.getPosX(), this.getPosYEye(), this.getPosZ());
        RayTraceResult result = this.world.rayTraceBlocks(new RayTraceContext(Vector3d, this.getPositionVec().add(this.getLookVec().scale(20)), RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, this));
        Vector3d vec = result.getHitVec() != null ? result.getHitVec() : this.getPositionVec();
        EntityVoidPortal portal = AMEntityRegistry.VOID_PORTAL.create(world);
        portal.setPosition(vec.x, vec.y, vec.z);
        if (!world.isRemote) {
            world.addEntity(portal);
        }
        moveIntoPortalPos = vec;
        Vector3d destVec = this.getPositionVec().add(this.getLookVec().scale(100));
        portal.setDestination(new BlockPos(destVec.x, destVec.y, destVec.z));
    }

    public boolean canBePushed() {
        return false;
    }

    public boolean isTargetBlocked(Vector3d target) {
        Vector3d Vector3d = new Vector3d(this.getPosX(), this.getPosYEye(), this.getPosZ());

        return this.world.rayTraceBlocks(new RayTraceContext(Vector3d, target, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, this)).getType() != RayTraceResult.Type.MISS;
    }

    public Vector3d getBlockInViewAway(Vector3d fleePos, float radiusAdd) {
        float radius = (0.75F * (0.7F * 6) * -3 - this.getRNG().nextInt(24)) * radiusAdd;
        float neg = this.getRNG().nextBoolean() ? 1 : -1;
        float renderYawOffset = this.renderYawOffset;
        float angle = (0.01745329251F * renderYawOffset) + 3.15F + (this.getRNG().nextFloat() * neg);
        double extraX = radius * MathHelper.sin((float) (Math.PI + angle));
        double extraZ = radius * MathHelper.cos(angle);
        BlockPos radialPos = new BlockPos(fleePos.getX() + extraX, 0, fleePos.getZ() + extraZ);
        BlockPos ground = getGround(radialPos);
        int distFromGround = (int) this.getPosY() - ground.getY();
        int flightHeight = 10 + this.getRNG().nextInt(20);
        BlockPos newPos = ground.up(distFromGround > 8 ? flightHeight : this.getRNG().nextInt(10) + 15);
        if (!this.isTargetBlocked(Vector3d.copyCentered(newPos)) && this.getDistanceSq(Vector3d.copyCentered(newPos)) > 1) {
            return Vector3d.copyCentered(newPos);
        }
        return null;
    }

    public Vector3d getBlockInViewAwaySlam(Vector3d fleePos, int slamHeight) {
        float radius = 3 + rand.nextInt(3);
        float neg = this.getRNG().nextBoolean() ? 1 : -1;
        float renderYawOffset = this.renderYawOffset;
        float angle = (0.01745329251F * renderYawOffset) + 3.15F + (this.getRNG().nextFloat() * neg);
        double extraX = radius * MathHelper.sin((float) (Math.PI + angle));
        double extraZ = radius * MathHelper.cos(angle);
        BlockPos radialPos = new BlockPos(fleePos.getX() + extraX, 0, fleePos.getZ() + extraZ);
        BlockPos ground = getHeighestAirAbove(radialPos, slamHeight);
        if (!this.isTargetBlocked(Vector3d.copyCentered(ground)) && this.getDistanceSq(Vector3d.copyCentered(ground)) > 1) {
            return Vector3d.copyCentered(ground);
        }
        return null;
    }

    private BlockPos getHeighestAirAbove(BlockPos radialPos, int limit) {
        BlockPos position = new BlockPos(radialPos.getX(), this.getPosY(), radialPos.getZ());
        while (position.getY() < 256 && position.getY() < this.getPosY() + limit && world.isAirBlock(position)) {
            position = position.up();
        }
        return position;
    }

    private BlockPos getGround(BlockPos in) {
        BlockPos position = new BlockPos(in.getX(), this.getPosY(), in.getZ());
        while (position.getY() > 1 && world.isAirBlock(position)) {
            position = position.down();
        }
        if (position.getY() < 2) {
            return position.up(60 + rand.nextInt(5));
        }

        return position;
    }

    public boolean isOnSameTeam(Entity entityIn) {
        return super.isOnSameTeam(entityIn) || this.getSplitFromUUID() != null && this.getSplitFromUUID().equals(entityIn.getUniqueID()) ||
                entityIn instanceof EntityVoidWorm && ((EntityVoidWorm) entityIn).getSplitFromUUID() != null && ((EntityVoidWorm) entityIn).getSplitFromUUID().equals(entityIn.getUniqueID());
    }

    private void spit(Vector3d shotAt, boolean portal) {
        shotAt = shotAt.rotateYaw(-this.rotationYaw * ((float) Math.PI / 180F));
        EntityVoidWormShot shot = new EntityVoidWormShot(this.world, this);
        double d0 = shotAt.x;
        double d1 = shotAt.y;
        double d2 = shotAt.z;
        float f = MathHelper.sqrt(d0 * d0 + d2 * d2) * 0.35F;
        shot.shoot(d0, d1 + (double) f, d2, 0.5F, 3.0F);
        if (!this.isSilent()) {
            this.world.playSound(null, this.getPosX(), this.getPosY(), this.getPosZ(), SoundEvents.ENTITY_DROWNED_SHOOT, this.getSoundCategory(), 1.0F, 1.0F + (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F);
        }
        shot.setPortalType(portal);
        this.openMouth(5);
        this.world.addEntity(shot);
    }


    private enum AttackMode {
        CIRCLE,
        SLAM_RISE,
        SLAM_FALL,
        PORTAL
    }

    private class AIFlyIdle extends Goal {
        protected final EntityVoidWorm voidWorm;
        protected double x;
        protected double y;
        protected double z;

        public AIFlyIdle() {
            super();
            this.setMutexFlags(EnumSet.of(Flag.MOVE));
            this.voidWorm = EntityVoidWorm.this;
        }

        @Override
        public boolean shouldExecute() {
            if (this.voidWorm.isBeingRidden() || (voidWorm.getAttackTarget() != null && voidWorm.getAttackTarget().isAlive()) || this.voidWorm.isPassenger()) {
                return false;
            } else {
                Vector3d lvt_1_1_ = this.getPosition();
                if (lvt_1_1_ == null) {
                    return false;
                } else {
                    this.x = lvt_1_1_.x;
                    this.y = lvt_1_1_.y;
                    this.z = lvt_1_1_.z;
                    return true;
                }
            }
        }

        public void tick() {
            voidWorm.getMoveHelper().setMoveTo(x, y, z, 1F);
        }

        @Nullable
        protected Vector3d getPosition() {
            Vector3d vector3d = voidWorm.getPositionVec();
            return voidWorm.getBlockInViewAway(vector3d, 1);
        }

        public boolean shouldContinueExecuting() {
            return voidWorm.getDistanceSq(x, y, z) > 20F && !voidWorm.collidedHorizontally && (voidWorm.getAttackTarget() == null || !voidWorm.getAttackTarget().isAlive());
        }

        public void startExecuting() {
            voidWorm.getMoveHelper().setMoveTo(x, y, z, 1F);
        }

        public void resetTask() {
            this.voidWorm.getNavigator().clearPath();
            super.resetTask();
        }
    }

    public class AIAttack extends Goal {

        private AttackMode mode = AttackMode.CIRCLE;
        private int modeTicks = 0;
        private int maxCircleTime = 500;
        private Vector3d moveTo = null;

        public AIAttack() {
            super();
            this.setMutexFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean shouldExecute() {
            return EntityVoidWorm.this.getAttackTarget() != null && EntityVoidWorm.this.getAttackTarget().isAlive();
        }

        public void resetTask() {
            mode = AttackMode.CIRCLE;
            modeTicks = 0;
        }

        public void startExecuting() {
            mode = AttackMode.CIRCLE;
            maxCircleTime = 60 + rand.nextInt(250);
        }

        public void tick() {
            LivingEntity target = EntityVoidWorm.this.getAttackTarget();
            boolean flag = false;
            for (Entity entity : EntityVoidWorm.this.world.getEntitiesWithinAABB(LivingEntity.class, EntityVoidWorm.this.getBoundingBox().grow(2.0D), null)) {
                if (!entity.isEntityEqual(EntityVoidWorm.this) && !(entity instanceof EntityVoidWormPart) && !entity.isOnSameTeam(EntityVoidWorm.this) && entity != EntityVoidWorm.this) {
                    if (EntityVoidWorm.this.isMouthOpen()) {
                        launch(entity, true);
                        flag = true;
                        entity.attackEntityFrom(DamageSource.causeMobDamage(EntityVoidWorm.this), 8.0F + rand.nextFloat() * 8.0F);
                    } else {
                        EntityVoidWorm.this.openMouth(15);
                    }
                }
            }
            if (target != null) {
                if (mode == AttackMode.CIRCLE) {
                    if (moveTo == null || EntityVoidWorm.this.getDistanceSq(moveTo) < 16 || EntityVoidWorm.this.collidedHorizontally) {
                        moveTo = EntityVoidWorm.this.getBlockInViewAway(target.getPositionVec(), 0.4F + rand.nextFloat() * 0.2F);
                    }
                    modeTicks++;
                    if (modeTicks % 40 == 0) {
                        EntityVoidWorm.this.spit(new Vector3d(3, 3, 0), false);
                        EntityVoidWorm.this.spit(new Vector3d(-3, 3, 0), false);
                        EntityVoidWorm.this.spit(new Vector3d(3, -3, 0), false);
                        EntityVoidWorm.this.spit(new Vector3d(-3, -3, 0), false);
                    }
                    if (modeTicks > maxCircleTime) {
                        maxCircleTime = 60 + rand.nextInt(250);
                        mode = AttackMode.SLAM_RISE;
                        modeTicks = 0;
                        moveTo = null;
                    }
                } else if (mode == AttackMode.SLAM_RISE) {
                    if (moveTo == null) {
                        moveTo = EntityVoidWorm.this.getBlockInViewAwaySlam(target.getPositionVec(), 20 + rand.nextInt(20));
                    }
                    if (moveTo != null) {
                        if (EntityVoidWorm.this.getPosY() > target.getPosY() + 15) {
                            moveTo = null;
                            modeTicks = 0;
                            mode = AttackMode.SLAM_FALL;
                        }
                    }
                } else if (mode == AttackMode.SLAM_FALL) {
                    EntityVoidWorm.this.faceEntity(target, 360, 360);
                    moveTo = target.getPositionVec();
                    if (EntityVoidWorm.this.collidedHorizontally) {
                        moveTo = new Vector3d(target.getPosX(), EntityVoidWorm.this.getPosY() + 3, target.getPosZ());
                    }
                    EntityVoidWorm.this.openMouth(20);
                    if (EntityVoidWorm.this.getDistanceSq(moveTo) < 4 || flag) {
                        mode = AttackMode.CIRCLE;
                        moveTo = null;
                        modeTicks = 0;
                    }
                }
            }
            if (moveTo != null) {
                EntityVoidWorm.this.getMoveHelper().setMoveTo(moveTo.x, moveTo.y, moveTo.z, 1);
            }
        }
    }
}