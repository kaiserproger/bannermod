package com.talhanation.bannermod.war.runtime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class WarAllyInviteSavedData extends SavedData {
    private static final String FILE_ID = "bannermodWarAllyInvites";

    private final WarAllyInviteRuntime runtime;

    public WarAllyInviteSavedData() {
        this(new WarAllyInviteRuntime());
    }

    private WarAllyInviteSavedData(WarAllyInviteRuntime runtime) {
        this.runtime = runtime;
        this.runtime.setDirtyListener(this::setDirty);
    }

    public static WarAllyInviteSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                WarAllyInviteSavedData::load,
                WarAllyInviteSavedData::new,
                FILE_ID
        );
    }

    public static WarAllyInviteSavedData load(CompoundTag tag) {
        return new WarAllyInviteSavedData(WarAllyInviteRuntime.fromTag(tag));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag inner = runtime.toTag();
        tag.put("Invites", inner.getList("Invites", Tag.TAG_COMPOUND));
        return tag;
    }

    public WarAllyInviteRuntime runtime() {
        return runtime;
    }
}
