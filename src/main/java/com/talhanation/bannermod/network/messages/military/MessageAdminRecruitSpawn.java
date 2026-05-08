package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;


public class MessageAdminRecruitSpawn implements BannerModMessage<MessageAdminRecruitSpawn> {
    private String entityId;
    private int count;

    public MessageAdminRecruitSpawn() {
        this("bannermod:recruit", 1);
    }

    public MessageAdminRecruitSpawn(String entityId, int count) {
        this.entityId = entityId;
        this.count = count;
    }

    @Override
    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    @Override
    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!player.hasPermissions(2) || !player.isCreative()) {
                return;
            }
            ServerLevel level = player.serverLevel();
            ResourceLocation key = ResourceLocation.tryParse(this.entityId);
            if (key == null) {
                return;
            }
            EntityType<?> rawType = BuiltInRegistries.ENTITY_TYPE.get(key);
            if (!(rawType instanceof EntityType<?> resolvedType) || !"bannermod".equals(key.getNamespace())) {
                return;
            }
            int spawnCount = Math.max(1, Math.min(16, this.count));
            int spawned = 0;
            for (int i = 0; i < spawnCount; i++) {
                if (!(resolvedType.create(level) instanceof AbstractRecruitEntity recruit)) {
                    continue;
                }
                BlockPos spawnPos = spawnPos(level, player.blockPosition(), player.getDirection(), i);
                recruit.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, player.getYRot(), 0.0F);
                recruit.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.COMMAND, null, null);
                recruit.setPersistenceRequired();
                level.addFreshEntity(recruit);
                spawned++;
            }
            player.sendSystemMessage(Component.translatable("gui.bannermod.admin_recruit_spawn.feedback.spawned", spawned, rawType.getDescription())
                    .withStyle(ChatFormatting.GREEN));
        });
    }

    @Override
    public MessageAdminRecruitSpawn fromBytes(FriendlyByteBuf buf) {
        this.entityId = buf.readUtf();
        this.count = buf.readInt();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.entityId);
        buf.writeInt(this.count);
    }

    private static BlockPos spawnPos(ServerLevel level, BlockPos playerPos, Direction facing, int index) {
        Direction right = facing.getClockWise();
        int row = index / 4;
        int column = index % 4;
        int sideOffset = column - 1;
        BlockPos base = playerPos.relative(facing, 2 + row).relative(right, sideOffset * 2);
        for (int y = 3; y >= -1; y--) {
            BlockPos candidate = base.offset(0, y, 0);
            if (level.getBlockState(candidate.below()).isSolid() && level.getBlockState(candidate).isAir() && level.getBlockState(candidate.above()).isAir()) {
                return candidate;
            }
        }
        return base.above();
    }
}
