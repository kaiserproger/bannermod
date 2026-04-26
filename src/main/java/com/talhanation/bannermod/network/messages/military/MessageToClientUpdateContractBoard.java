package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.client.military.gui.GovernorContractScreen;
import com.talhanation.bannermod.governance.BannerModGovernorContractStatus;
import com.talhanation.bannermod.governance.BannerModGovernorContractType;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MessageToClientUpdateContractBoard implements Message<MessageToClientUpdateContractBoard> {
    private UUID governorRecruitId;
    private UUID claimUuid;
    private boolean isOwner;
    private int maxContractReward;
    private long currentTick;
    private List<ContractDto> contracts;

    public MessageToClientUpdateContractBoard() {
    }

    public MessageToClientUpdateContractBoard(UUID governorRecruitId, UUID claimUuid, boolean isOwner,
                                              int maxContractReward, long currentTick,
                                              List<ContractDto> contracts) {
        this.governorRecruitId = governorRecruitId;
        this.claimUuid = claimUuid;
        this.isOwner = isOwner;
        this.maxContractReward = maxContractReward;
        this.currentTick = currentTick;
        this.contracts = contracts;
    }

    @Override
    public Dist getExecutingSide() {
        return Dist.CLIENT;
    }

    @Override
    public void executeClientSide(NetworkEvent.Context context) {
        GovernorContractScreen.applyUpdate(governorRecruitId, claimUuid, isOwner, maxContractReward, currentTick, contracts);
    }

    @Override
    public MessageToClientUpdateContractBoard fromBytes(FriendlyByteBuf buf) {
        this.governorRecruitId = buf.readUUID();
        this.claimUuid = buf.readUUID();
        this.isOwner = buf.readBoolean();
        this.maxContractReward = buf.readInt();
        this.currentTick = buf.readLong();
        int count = buf.readInt();
        this.contracts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.contracts.add(ContractDto.fromBuf(buf));
        }
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(governorRecruitId);
        buf.writeUUID(claimUuid);
        buf.writeBoolean(isOwner);
        buf.writeInt(maxContractReward);
        buf.writeLong(currentTick);
        buf.writeInt(contracts == null ? 0 : contracts.size());
        if (contracts != null) {
            for (ContractDto dto : contracts) dto.toBuf(buf);
        }
    }

    public record ContractDto(UUID contractId, String type, int reward, String status,
                              long deadlineTick, boolean pinned, boolean hasAcceptor) {
        public static ContractDto fromBuf(FriendlyByteBuf buf) {
            return new ContractDto(buf.readUUID(), buf.readUtf(), buf.readInt(),
                    buf.readUtf(), buf.readLong(), buf.readBoolean(), buf.readBoolean());
        }

        public void toBuf(FriendlyByteBuf buf) {
            buf.writeUUID(contractId);
            buf.writeUtf(type);
            buf.writeInt(reward);
            buf.writeUtf(status);
            buf.writeLong(deadlineTick);
            buf.writeBoolean(pinned);
            buf.writeBoolean(hasAcceptor);
        }
    }
}
