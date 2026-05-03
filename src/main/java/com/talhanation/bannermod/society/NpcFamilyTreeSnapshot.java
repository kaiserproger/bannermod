package com.talhanation.bannermod.society;

import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public record NpcFamilyTreeSnapshot(
        NpcFamilyMemberSnapshot self,
        @Nullable NpcFamilyMemberSnapshot spouse,
        @Nullable NpcFamilyMemberSnapshot mother,
        @Nullable NpcFamilyMemberSnapshot father,
        List<NpcFamilyMemberSnapshot> children
) {
    public static NpcFamilyTreeSnapshot empty() {
        return new NpcFamilyTreeSnapshot(
                new NpcFamilyMemberSnapshot(new java.util.UUID(0L, 0L), -1, "-", NpcLifeStage.UNSPECIFIED.name(), NpcSex.UNSPECIFIED.name(), "self"),
                null,
                null,
                null,
                List.of()
        );
    }

    public void toBytes(FriendlyByteBuf buf) {
        (this.self == null ? empty().self() : this.self).toBytes(buf);
        buf.writeBoolean(this.spouse != null);
        if (this.spouse != null) {
            this.spouse.toBytes(buf);
        }
        buf.writeBoolean(this.mother != null);
        if (this.mother != null) {
            this.mother.toBytes(buf);
        }
        buf.writeBoolean(this.father != null);
        if (this.father != null) {
            this.father.toBytes(buf);
        }
        buf.writeVarInt(this.children == null ? 0 : this.children.size());
        if (this.children != null) {
            for (NpcFamilyMemberSnapshot child : this.children) {
                child.toBytes(buf);
            }
        }
    }

    public static NpcFamilyTreeSnapshot fromBytes(FriendlyByteBuf buf) {
        NpcFamilyMemberSnapshot self = NpcFamilyMemberSnapshot.fromBytes(buf);
        NpcFamilyMemberSnapshot spouse = buf.readBoolean() ? NpcFamilyMemberSnapshot.fromBytes(buf) : null;
        NpcFamilyMemberSnapshot mother = buf.readBoolean() ? NpcFamilyMemberSnapshot.fromBytes(buf) : null;
        NpcFamilyMemberSnapshot father = buf.readBoolean() ? NpcFamilyMemberSnapshot.fromBytes(buf) : null;
        int childCount = buf.readVarInt();
        List<NpcFamilyMemberSnapshot> children = new ArrayList<>();
        for (int i = 0; i < childCount; i++) {
            children.add(NpcFamilyMemberSnapshot.fromBytes(buf));
        }
        return new NpcFamilyTreeSnapshot(self, spouse, mother, father, List.copyOf(children));
    }
}
