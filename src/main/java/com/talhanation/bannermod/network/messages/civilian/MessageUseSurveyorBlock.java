package com.talhanation.bannermod.network.messages.civilian;

import com.talhanation.bannermod.items.civilian.SettlementSurveyorToolItem;
import com.talhanation.bannermod.network.compat.BannerModNetworkContext;
import com.talhanation.bannermod.network.payload.BannerModMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class MessageUseSurveyorBlock implements BannerModMessage<MessageUseSurveyorBlock> {
    private static final double MAX_CLICK_DISTANCE_SQR = 49.0D;

    public int handIndex;
    public BlockPos clickedPos;

    public MessageUseSurveyorBlock() {
        this.clickedPos = BlockPos.ZERO;
    }

    public MessageUseSurveyorBlock(InteractionHand hand, BlockPos clickedPos) {
        this.handIndex = hand == InteractionHand.OFF_HAND ? 1 : 0;
        this.clickedPos = clickedPos == null ? BlockPos.ZERO : clickedPos.immutable();
    }

    @Override
    public PacketFlow getExecutingSide() {
        return BannerModMessage.serverbound();
    }

    @Override
    public void executeServerSide(BannerModNetworkContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(this.clickedPos)) > MAX_CLICK_DISTANCE_SQR) {
                return;
            }
            ItemStack stack = player.getItemInHand(handIndex == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (!(stack.getItem() instanceof SettlementSurveyorToolItem)) {
                return;
            }
            SettlementSurveyorToolItem.handleBlockClick(player, stack, this.clickedPos);
        });
    }

    @Override
    public MessageUseSurveyorBlock fromBytes(FriendlyByteBuf buf) {
        this.handIndex = buf.readVarInt();
        this.clickedPos = buf.readBlockPos();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.handIndex);
        buf.writeBlockPos(this.clickedPos == null ? BlockPos.ZERO : this.clickedPos);
    }
}
