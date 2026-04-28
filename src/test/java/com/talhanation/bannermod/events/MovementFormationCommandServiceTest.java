package com.talhanation.bannermod.events;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MovementFormationCommandServiceTest {

    @Test
    void loginInitializesCommandPreferenceDefaults() {
        CompoundTag playerData = new CompoundTag();

        MovementFormationCommandService.initializePlayerCommandState(playerData, 12, -4, 50);

        CompoundTag persisted = playerData.getCompound(Player.PERSISTED_NBT_TAG);
        assertEquals(0, persisted.getInt("Formation"));
        assertEquals(0, persisted.getList("ActiveGroups", Tag.TAG_COMPOUND).size());
        assertArrayEquals(new int[]{12, -4}, persisted.getIntArray("FormationPos"));
    }

    @Test
    void cloneCopiesOnlyPersistentCommandPreferences() {
        UUID firstGroup = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondGroup = UUID.fromString("00000000-0000-0000-0000-000000000002");
        CompoundTag originalPlayerData = new CompoundTag();
        CompoundTag originalPersisted = new CompoundTag();
        originalPersisted.putInt("Formation", 6);
        originalPersisted.put("ActiveGroups", activeGroups(firstGroup, secondGroup));
        originalPersisted.putIntArray("FormationPos", new int[]{100, 200});
        originalPersisted.putDouble("FormationCenterX", 1.0D);
        originalPersisted.putDouble("FormationCenterY", 2.0D);
        originalPersisted.putDouble("FormationCenterZ", 3.0D);
        originalPlayerData.put(Player.PERSISTED_NBT_TAG, originalPersisted);
        CompoundTag clonePlayerData = new CompoundTag();

        MovementFormationCommandService.copyPersistentCommandPreferences(originalPlayerData, clonePlayerData);

        CompoundTag clonePersisted = clonePlayerData.getCompound(Player.PERSISTED_NBT_TAG);
        assertEquals(6, clonePersisted.getInt("Formation"));
        assertEquals(List.of(firstGroup, secondGroup), activeGroupUuids(clonePersisted));
        assertFalse(clonePersisted.contains("FormationPos"));
        assertFalse(clonePersisted.contains("FormationCenterX"));
        assertFalse(clonePersisted.contains("FormationCenterY"));
        assertFalse(clonePersisted.contains("FormationCenterZ"));
    }

    @Test
    void reloadInitializationDoesNotOverwriteSavedPreferences() {
        UUID group = UUID.fromString("00000000-0000-0000-0000-000000000003");
        CompoundTag playerData = new CompoundTag();
        CompoundTag persisted = new CompoundTag();
        persisted.putInt("Formation", 9);
        persisted.put("ActiveGroups", activeGroups(group));
        persisted.putIntArray("FormationPos", new int[]{3, 4});
        playerData.put(Player.PERSISTED_NBT_TAG, persisted);

        MovementFormationCommandService.initializePlayerCommandState(playerData, 90, 91, 50);

        CompoundTag reloaded = playerData.getCompound(Player.PERSISTED_NBT_TAG);
        assertEquals(9, reloaded.getInt("Formation"));
        assertEquals(List.of(group), activeGroupUuids(reloaded));
        assertArrayEquals(new int[]{3, 4}, reloaded.getIntArray("FormationPos"));
    }

    private static ListTag activeGroups(UUID... uuids) {
        ListTag list = new ListTag();
        for (UUID uuid : uuids) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("UUID", uuid);
            list.add(tag);
        }
        return list;
    }

    private static List<UUID> activeGroupUuids(CompoundTag persisted) {
        return persisted.getList("ActiveGroups", Tag.TAG_COMPOUND).stream()
                .map(CompoundTag.class::cast)
                .map(tag -> tag.getUUID("UUID"))
                .toList();
    }
}
