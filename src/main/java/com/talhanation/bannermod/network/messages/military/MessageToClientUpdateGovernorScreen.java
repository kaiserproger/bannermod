package com.talhanation.bannermod.network.messages.military;

import com.talhanation.bannermod.client.military.gui.GovernorScreen;
import de.maxhenkel.corelib.net.Message;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MessageToClientUpdateGovernorScreen implements Message<MessageToClientUpdateGovernorScreen> {
    private UUID recruit;
    private String settlementStatus;
    private int citizenCount;
    private int taxesDue;
    private int taxesCollected;
    private long lastHeartbeatTick;
    private String garrisonRecommendation;
    private String fortificationRecommendation;
    private int garrisonPriority;
    private int fortificationPriority;
    private int taxPressure;
    private int treasuryBalance;
    private int lastTreasuryNet;
    private int projectedTreasuryBalance;
    private boolean autoManage;
    private List<String> incidents;
    private List<String> recommendations;

    public MessageToClientUpdateGovernorScreen() {
    }

    public MessageToClientUpdateGovernorScreen(UUID recruit,
                                               String settlementStatus,
                                               int citizenCount,
                                                int taxesDue,
                                                int taxesCollected,
                                                long lastHeartbeatTick,
                                                String garrisonRecommendation,
                                                String fortificationRecommendation,
                                                int garrisonPriority,
                                                 int fortificationPriority,
                                                 int taxPressure,
                                                 int treasuryBalance,
                                                 int lastTreasuryNet,
                                                 int projectedTreasuryBalance,
                                                 boolean autoManage,
                                                 List<String> incidents,
                                                 List<String> recommendations) {
        this.recruit = recruit;
        this.settlementStatus = settlementStatus;
        this.citizenCount = citizenCount;
        this.taxesDue = taxesDue;
        this.taxesCollected = taxesCollected;
        this.lastHeartbeatTick = lastHeartbeatTick;
        this.garrisonRecommendation = garrisonRecommendation;
        this.fortificationRecommendation = fortificationRecommendation;
        this.garrisonPriority = garrisonPriority;
        this.fortificationPriority = fortificationPriority;
        this.taxPressure = taxPressure;
        this.treasuryBalance = treasuryBalance;
        this.lastTreasuryNet = lastTreasuryNet;
        this.projectedTreasuryBalance = projectedTreasuryBalance;
        this.autoManage = autoManage;
        this.incidents = incidents;
        this.recommendations = recommendations;
    }

    @Override
    public Dist getExecutingSide() {
        return Dist.CLIENT;
    }

    @Override
    public void executeClientSide(NetworkEvent.Context context) {
        GovernorScreen.applyUpdate(recruit, settlementStatus, citizenCount, taxesDue, taxesCollected, lastHeartbeatTick,
                garrisonRecommendation, fortificationRecommendation, garrisonPriority, fortificationPriority, taxPressure,
                treasuryBalance, lastTreasuryNet, projectedTreasuryBalance,
                autoManage, incidents, recommendations);
    }

    @Override
    public MessageToClientUpdateGovernorScreen fromBytes(FriendlyByteBuf buf) {
        this.recruit = buf.readUUID();
        this.settlementStatus = buf.readUtf();
        this.citizenCount = buf.readInt();
        this.taxesDue = buf.readInt();
        this.taxesCollected = buf.readInt();
        this.lastHeartbeatTick = buf.readLong();
        this.garrisonRecommendation = buf.readUtf();
        this.fortificationRecommendation = buf.readUtf();
        this.garrisonPriority = buf.readInt();
        this.fortificationPriority = buf.readInt();
        this.taxPressure = buf.readInt();
        this.treasuryBalance = buf.readInt();
        this.lastTreasuryNet = buf.readInt();
        this.projectedTreasuryBalance = buf.readInt();
        this.autoManage = buf.readBoolean();
        this.incidents = new ArrayList<>(buf.readList(FriendlyByteBuf::readUtf));
        this.recommendations = new ArrayList<>(buf.readList(FriendlyByteBuf::readUtf));
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.recruit);
        buf.writeUtf(this.settlementStatus);
        buf.writeInt(this.citizenCount);
        buf.writeInt(this.taxesDue);
        buf.writeInt(this.taxesCollected);
        buf.writeLong(this.lastHeartbeatTick);
        buf.writeUtf(this.garrisonRecommendation == null ? "" : this.garrisonRecommendation);
        buf.writeUtf(this.fortificationRecommendation == null ? "" : this.fortificationRecommendation);
        buf.writeInt(this.garrisonPriority);
        buf.writeInt(this.fortificationPriority);
        buf.writeInt(this.taxPressure);
        buf.writeInt(this.treasuryBalance);
        buf.writeInt(this.lastTreasuryNet);
        buf.writeInt(this.projectedTreasuryBalance);
        buf.writeBoolean(this.autoManage);
        buf.writeCollection(this.incidents, FriendlyByteBuf::writeUtf);
        buf.writeCollection(this.recommendations, FriendlyByteBuf::writeUtf);
    }
}
