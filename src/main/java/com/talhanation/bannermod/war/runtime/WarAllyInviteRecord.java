package com.talhanation.bannermod.war.runtime;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Pending invitation for an ally entity to join one side of a war. Created by
 * the side leader, accepted/declined by the invitee leader. Cleared when the
 * invite is consumed, cancelled, or its parent war leaves the pre-active phase.
 */
public record WarAllyInviteRecord(
        UUID id,
        UUID warId,
        WarSide side,
        UUID inviteePoliticalEntityId,
        UUID inviterPlayerUuid,
        long createdAtGameTime
) {
    public WarAllyInviteRecord {
        if (id == null || warId == null || side == null || inviteePoliticalEntityId == null) {
            throw new IllegalArgumentException("WarAllyInviteRecord requires id, warId, side, invitee");
        }
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("WarId", warId);
        tag.putString("Side", side.name());
        tag.putUUID("Invitee", inviteePoliticalEntityId);
        if (inviterPlayerUuid != null) {
            tag.putUUID("Inviter", inviterPlayerUuid);
        }
        tag.putLong("CreatedAtGameTime", createdAtGameTime);
        return tag;
    }

    public static WarAllyInviteRecord fromTag(CompoundTag tag) {
        if (tag == null) return null;
        WarSide side;
        try {
            side = WarSide.valueOf(tag.getString("Side"));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        UUID inviter = tag.hasUUID("Inviter") ? tag.getUUID("Inviter") : null;
        return new WarAllyInviteRecord(
                tag.getUUID("Id"),
                tag.getUUID("WarId"),
                side,
                tag.getUUID("Invitee"),
                inviter,
                tag.getLong("CreatedAtGameTime")
        );
    }
}
