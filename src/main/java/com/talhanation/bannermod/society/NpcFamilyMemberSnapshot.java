package com.talhanation.bannermod.society;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record NpcFamilyMemberSnapshot(
        UUID residentUuid,
        int entityId,
        String displayName,
        String lifeStageTag,
        String sexTag,
        String relationTag
) {
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.residentUuid);
        buf.writeVarInt(this.entityId);
        buf.writeUtf(this.displayName == null ? "" : this.displayName);
        buf.writeUtf(this.lifeStageTag == null ? NpcLifeStage.UNSPECIFIED.name() : this.lifeStageTag);
        buf.writeUtf(this.sexTag == null ? NpcSex.UNSPECIFIED.name() : this.sexTag);
        buf.writeUtf(this.relationTag == null ? "self" : this.relationTag);
    }

    public static NpcFamilyMemberSnapshot fromBytes(FriendlyByteBuf buf) {
        return new NpcFamilyMemberSnapshot(
                buf.readUUID(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf()
        );
    }

    public String lifeStageTranslationKey() {
        return "gui.bannermod.society.life_stage." + this.lifeStageTag.toLowerCase(java.util.Locale.ROOT);
    }

    public String sexTranslationKey() {
        return "gui.bannermod.society.sex." + this.sexTag.toLowerCase(java.util.Locale.ROOT);
    }

    public String relationTranslationKey() {
        return "gui.bannermod.society.family_relation." + this.relationTag.toLowerCase(java.util.Locale.ROOT);
    }
}
