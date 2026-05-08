package com.talhanation.bannermod.entity.civilian.workarea;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.ai.civilian.BuilderBuildProgress;
import com.talhanation.bannermod.entity.civilian.workarea.AbstractWorkAreaEntity;
import com.talhanation.bannermod.network.messages.civilian.MessageToClientOpenWorkAreaScreen;
import com.talhanation.bannermod.persistence.civilian.BuildBlock;
import com.talhanation.bannermod.persistence.civilian.BuildBlockParse;
import com.talhanation.bannermod.persistence.civilian.StructureManager;
import com.talhanation.bannermod.settlement.prefab.staffing.PrefabAutoStaffingRuntime;
import com.talhanation.bannermod.settlement.project.SettlementProjectRuntime;
import com.talhanation.bannermod.shared.settlement.BannerModSettlementRefreshSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import com.talhanation.bannermod.network.compat.BannerModPacketDistributor;

import javax.annotation.Nullable;
import java.util.*;

public class BuildArea extends AbstractWorkAreaEntity {
    public static final EntityDataAccessor<CompoundTag> STRUCTURE = SynchedEntityData.defineId(BuildArea.class, EntityDataSerializers.COMPOUND_TAG);
    public Stack<BlockPos> stackToBreak = new Stack<>();
    public Stack<BuildBlock> stackToPlace = new Stack<>();
    public Stack<BuildBlock> stackToPlaceMultiBlock = new Stack<>();
    private boolean buildStarted;
    private boolean creativeBuild;
    private boolean resumePlanPending;
    public BuildArea(EntityType<?> type, Level level) {
        super(type, level);
    }

    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STRUCTURE, new CompoundTag());
    }
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setStructureNBT(tag.getCompound("structureNBT"));
        this.buildStarted = tag.getBoolean("buildStarted");
        this.creativeBuild = tag.getBoolean("creativeBuild");
        this.resumePlanPending = BuilderBuildProgress.classify(this.buildStarted, this.isDone(), this.hasStructureTemplate())
                == BuilderBuildProgress.State.IN_PROGRESS;
    }
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("structureNBT", this.getStructureNBT());
        tag.putBoolean("buildStarted", this.buildStarted);
        tag.putBoolean("creativeBuild", this.creativeBuild);
    }

    @Override
    public Item getRenderItem() {
        return Items.IRON_SHOVEL;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            this.refreshPendingBuildPlanIfNeeded();
        }
        if(isDone()) {
            if (this.getCommandSenderWorld() instanceof ServerLevel serverLevel) {
                PrefabAutoStaffingRuntime.onBuildAreaCompleted(serverLevel, this);
                BannerModSettlementRefreshSupport.refreshSnapshot(serverLevel, this.blockPosition());
            }
            this.remove(RemovalReason.DISCARDED);
        }
    }

    public void setStructureNBT(CompoundTag tag){
        this.entityData.set(STRUCTURE, tag);
    }

    public CompoundTag getStructureNBT(){
        return this.entityData.get(STRUCTURE);
    }

    public boolean hasStructureTemplate() {
        CompoundTag tag = this.getStructureNBT();
        return tag != null && !tag.isEmpty() && tag.contains("blocks", Tag.TAG_LIST);
    }

    public boolean hasPendingBuildWork() {
        this.refreshPendingBuildPlanIfNeeded();
        if (BuilderBuildProgress.classify(this.buildStarted, this.isDone(), this.hasStructureTemplate())
                != BuilderBuildProgress.State.IN_PROGRESS) {
            return false;
        }

        if (BuilderBuildProgress.hasPendingWorldWork(this.stackToBreak.size(), this.stackToPlace.size(), this.stackToPlaceMultiBlock.size())) {
            return true;
        }

        this.scanBreakArea();
        return BuilderBuildProgress.hasPendingWorldWork(this.stackToBreak.size(), this.stackToPlace.size(), this.stackToPlaceMultiBlock.size());
    }

    public void clearPlannedBuild() {
        this.buildStarted = false;
        this.creativeBuild = false;
        this.resumePlanPending = false;
        this.stackToBreak.clear();
        this.stackToPlace.clear();
        this.stackToPlaceMultiBlock.clear();
    }

    private void refreshPendingBuildPlanIfNeeded() {
        if (!this.resumePlanPending) {
            return;
        }

        if (BuilderBuildProgress.classify(this.buildStarted, this.isDone(), this.hasStructureTemplate())
                != BuilderBuildProgress.State.IN_PROGRESS) {
            this.resumePlanPending = false;
            return;
        }

        this.resumePlanPending = false;
        this.setStartBuild(this.creativeBuild);
    }

    public void setStartBuild(boolean isCreative) {
        this.buildStarted = true;
        this.creativeBuild = isCreative;
        this.resumePlanPending = false;
        this.setDone(false);
        if (this.getCommandSenderWorld() instanceof ServerLevel serverLevel) {
            SettlementProjectRuntime.onBuildAreaStarted(serverLevel, this.getUUID());
        }
        stackToPlace.clear();
        stackToBreak.clear();
        stackToPlaceMultiBlock.clear();

        CompoundTag tag = getStructureNBT();
        if (tag == null || !tag.contains("blocks", Tag.TAG_LIST)) return;
        int width = tag.getInt("width");
        String dir = tag.getString("facing");
        Direction scanFacing = Direction.byName(dir);
        ListTag blockList = tag.getList("blocks", Tag.TAG_COMPOUND);
        BlockPos origin = this.getOriginPos();
        Direction facing = this.getFacing();
        Direction right = facing.getClockWise();
        int rotationSteps = (4 + facing.get2DDataValue() - scanFacing.get2DDataValue()) % 4;

        for (Tag t : blockList) {
            CompoundTag blockTag = (CompoundTag) t;

            int relX = blockTag.getInt("x");
            int relY = blockTag.getInt("y");
            int relZ = blockTag.getInt("z");

            BlockPos worldPos = origin
                    .relative(facing, relZ)
                    .relative(right, width - 1 - relX)
                    .above(relY);

            BlockState state = StructureManager.resolveBlockState(blockTag);
            if (state == null || state.isAir()) {
                continue;
            }
            BlockState rotatedState = rotateBlockState(state, rotationSteps);

            if (isCreative) {
                this.getCommandSenderWorld().setBlock(worldPos, rotatedState, 3);
            } else {
                if (isMultiBlockSecondary(rotatedState)) {
                    this.stackToPlaceMultiBlock.push(new BuildBlock(worldPos, rotatedState));
                } else {
                    BlockState levelState = this.getCommandSenderWorld().getBlockState(worldPos);
                    if (rotatedState != null && !statesMatch(levelState, rotatedState)) {
                        this.stackToPlace.push(new BuildBlock(worldPos, rotatedState));
                    }
                }
            }
        }

        // For creative (Place), spawn entities immediately after all blocks are placed
        if (isCreative) {
            spawnScannedEntities(tag, scanFacing, facing, origin, width);
            this.setDone(true);
            this.buildStarted = false;
            this.creativeBuild = false;
        }
    }

    @Override
    public void setDone(boolean done) {
        boolean wasDone = this.isDone();
        super.setDone(done);
        if (done && !wasDone && this.getCommandSenderWorld() instanceof ServerLevel serverLevel) {
            SettlementProjectRuntime.onBuildAreaCompleted(serverLevel, this.getUUID());
        }
    }

    /**
     * Spawns work-area entities stored in the structure NBT.
     * Rotates their facing from scan direction to build direction and transfers owner/team.
     */
    private void spawnScannedEntities(CompoundTag tag, Direction scanFacing, Direction buildFacing, BlockPos origin, int width) {
        if (!(this.getCommandSenderWorld() instanceof ServerLevel serverLevel)) return;
        if (!tag.contains("entities", Tag.TAG_LIST)) return;

        ListTag entityList = tag.getList("entities", Tag.TAG_COMPOUND);
        Direction buildRight = buildFacing.getClockWise();
        int rotSteps = ((buildFacing.get2DDataValue() - scanFacing.get2DDataValue()) % 4 + 4) % 4;

        for (Tag t : entityList) {
            CompoundTag entityTag = (CompoundTag) t;
            String typeId = entityTag.getString("entity_type");
            int relX = entityTag.getInt("x");
            int relY = entityTag.getInt("y");
            int relZ = entityTag.getInt("z");
            int scanFacingVal = entityTag.getInt("facing");

            ResourceLocation rl = ResourceLocation.parse(typeId);
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(rl);
            if (entityType == null) continue;

            BlockPos worldPos = origin
                    .relative(buildFacing, relZ)
                    .relative(buildRight, width - 1 - relX)
                    .above(relY);

            Entity entity = entityType.create(serverLevel);
            if (entity == null) continue;

            entity.moveTo(worldPos.getX() + 0.5, worldPos.getY() + 1.0, worldPos.getZ() + 0.5, 0, 0);

            if (entity instanceof AbstractWorkAreaEntity wa) {
                Direction entityFacing = Direction.from2DDataValue(scanFacingVal);
                // rotate clockwise rotSteps times
                for (int i = 0; i < rotSteps; i++) entityFacing = entityFacing.getClockWise();
                wa.setFacing(entityFacing);

                // Restore stored dimensions, swapping width↔depth for 90° / 270° rotations
                if (entityTag.contains("wa_width")) {
                    int waW = entityTag.getInt("wa_width");
                    int waH = entityTag.getInt("wa_height");
                    int waD = entityTag.getInt("wa_depth");
                    // width is perpendicular to facing, depth is parallel — swapped on odd rotations
                    if (rotSteps % 2 == 1) { int tmp = waW; waW = waD; waD = tmp; }
                    wa.setWidthSize(waW);
                    wa.setHeightSize(waH);
                    wa.setDepthSize(waD);
                }

                if (this.getPlayerUUID() != null) wa.setPlayerUUID(this.getPlayerUUID());
                String team = this.getTeamStringID();
                if (team != null && !team.isEmpty()) wa.setTeamStringID(team);

                wa.setCustomName(Component.literal(""));
            }

            serverLevel.addFreshEntity(entity);
        }
    }

    /**
     * Returns true for secondary parts of multi-block structures (upper door/plant half, bed head).
     * These are placed in a dedicated second stage without consuming extra items.
     */
    public static boolean isMultiBlockSecondary(BlockState state) {
        if (state == null) return false;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)) {
            return state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
        }
        return false;
    }

    public static BlockState rotateBlockState(BlockState state, int steps) {
        if (state == null) return null;
        steps = ((steps % 4) + 4) % 4;
        return switch (steps) {
            case 1 -> state.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
            case 2 -> state.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_180);
            case 3 -> state.rotate(net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90);
            default -> state;
        };
    }


    /**
     * Extra blocks scanned above the declared build volume so tall obstructions (trees,
     * overhangs) get cleared even when the prefab itself is short.
     */
    public static final int SCAN_CLEARANCE_BUFFER_Y = 6;

    public void scanBreakArea() {
        stackToBreak.clear();
        Level level = this.getCommandSenderWorld();
        net.minecraft.world.phys.AABB area = getArea();

        BlockPos.betweenClosedStream(area).forEach(pos -> {
            BlockState buildingState = this.getStateFromPos(pos);
            BlockState levelState = level.getBlockState(pos);

            if (levelState.isAir()) {
                return;
            }
            if (buildingState == null) {
                // Position inside the build volume has no blueprint block assigned but the
                // world has something there (tree, stray dirt, grass tuft…). Clear it.
                stackToBreak.push(pos.immutable());
                return;
            }
            if (!statesMatch(levelState, buildingState) && !canDirectlyReplace(levelState, buildingState)) {
                stackToBreak.push(pos.immutable());
            }
        });

        // Vertical clearance pass: scan N blocks above the declared volume for each footprint
        // cell and break anything solid so floating tree canopies do not persist.
        int clearanceMinY = (int) Math.ceil(area.maxY);
        int clearanceMaxY = clearanceMinY + SCAN_CLEARANCE_BUFFER_Y - 1;
        int xMin = (int) Math.floor(area.minX);
        int xMax = (int) Math.ceil(area.maxX) - 1;
        int zMin = (int) Math.floor(area.minZ);
        int zMax = (int) Math.ceil(area.maxZ) - 1;
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                for (int y = clearanceMinY; y <= clearanceMaxY; y++) {
                    BlockPos cPos = new BlockPos(x, y, z);
                    if (!level.getBlockState(cPos).isAir()) {
                        stackToBreak.push(cPos.immutable());
                    }
                }
            }
        }
    }

    public static boolean canDirectlyReplace(BlockState levelState, BlockState buildingState) {
        if (levelState.isAir()) return true;
        if (buildingState.is(Blocks.DIRT_PATH)) {
            return levelState.is(Blocks.GRASS_BLOCK)
                    || levelState.is(Blocks.DIRT)
                    || levelState.is(Blocks.FARMLAND)
                    || levelState.is(Blocks.COARSE_DIRT);
        }
        return false;
    }

    public boolean statesMatch(BlockState levelState, BlockState buildingState) {
        if(buildingState == null) return true;
        if(levelState.is(Blocks.GRASS_BLOCK) && buildingState.is(Blocks.DIRT)) return true;
        if(levelState.is(Blocks.DIRT) && buildingState.is(Blocks.GRASS_BLOCK)) return true;



        return levelState.equals(buildingState);
    }

    public List<ItemStack> getRequiredMaterials(){
        return this.getRequiredMaterials(this.getStructureNBT());
    }

    public List<ItemStack> getRequiredMaterials(CompoundTag tag) {
        Map<Item, Integer> materialMap = new HashMap<>();
        List<ItemStack> stacks = new ArrayList<>();

        if (tag == null || !tag.contains("blocks", Tag.TAG_LIST)) return stacks;

        ListTag blockList = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (Tag t : blockList) {
            CompoundTag blockTag = (CompoundTag) t;

            BlockState state = StructureManager.resolveBlockState(blockTag);
            if (state == null || state.isAir()) {
                continue;
            }

            Block block = state.getBlock();
            Item item = BuildBlockParse.parseBlock(block).getItem();

            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
                if (half != DoubleBlockHalf.LOWER) {
                    continue;
                }
            }

            if (state.hasProperty(BlockStateProperties.BED_PART)) {
                BedPart part = state.getValue(BlockStateProperties.BED_PART);
                if (part != BedPart.FOOT) {
                    continue;
                }
            }


            if (item instanceof BlockItem) {
                materialMap.merge(item, 1, Integer::sum);
            }
        }

        for (Map.Entry<Item, Integer> entry : materialMap.entrySet()) {
            ItemStack stack = new ItemStack(entry.getKey());
            if (stack.isEmpty()) continue;

            stack.setCount(entry.getValue());
            stacks.add(stack);
        }

        return stacks;
    }
    @Nullable
    public BlockState getStateFromPos(BlockPos blockPos) {
        for (BuildBlock buildBlock : stackToPlace) {
            if (buildBlock.getPos().equals(blockPos)) {
                return buildBlock.getState();
            }
        }
        for (BuildBlock buildBlock : stackToPlaceMultiBlock) {
            if (buildBlock.getPos().equals(blockPos)) {
                return buildBlock.getState();
            }
        }
        return null;
    }

    @Nullable
    public BlockState getStateFromMultiBlockPos(BlockPos blockPos) {
        for (BuildBlock buildBlock : stackToPlaceMultiBlock) {
            if (buildBlock.getPos().equals(blockPos)) {
                return buildBlock.getState();
            }
        }
        return null;
    }

    @Nullable
    public BlockState findPairedMultiBlockState(BlockPos primaryPos) {
        BlockPos above = primaryPos.above();
        return getStateFromMultiBlockPos(above);
    }

    @Nullable
    public BlockPos findPairedMultiBlockPos(BlockPos primaryPos) {
        BlockPos above = primaryPos.above();
        for (BuildBlock bb : stackToPlaceMultiBlock) {
            if (bb.getPos().equals(above)) return above;
        }
        return null;
    }

    public void removeBuildBlockToPlace(BlockPos blockPos) {
        stackToPlace.removeIf(buildBlock -> buildBlock.getPos().equals(blockPos));
    }

    public void removeMultiBlockToPlace(BlockPos blockPos) {
        stackToPlaceMultiBlock.removeIf(buildBlock -> buildBlock.getPos().equals(blockPos));
    }
}
