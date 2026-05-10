package com.talhanation.bannermod.persistence.military;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import com.talhanation.bannermod.settlement.economy.FortLevelDefinition;

import javax.annotation.Nullable;
import java.util.*;

public class RecruitsClaim {

    private final UUID uuid;
    private final List<ChunkPos> claimedChunks = new ArrayList<>();
    private String name;
    @Nullable
    private UUID ownerPoliticalEntityId;
    private boolean allowBlockInteraction = false;
    private boolean allowBlockPlacement   = false;
    private boolean allowBlockBreaking    = false;
    private int fortLevel = FortLevelDefinition.MIN_LEVEL;
    private final List<RecruitsPlayerInfo> trustedPlayers = new ArrayList<>();
    public ChunkPos center;
    public RecruitsPlayerInfo playerInfo;
    public boolean isAdmin;
    public boolean isRemoved;
    public static int MAX_SIZE = 50;

    public RecruitsClaim(String name, @Nullable UUID politicalEntityId) {
        this.uuid = UUID.randomUUID();
        this.name = name;
        this.ownerPoliticalEntityId = politicalEntityId;
        this.isAdmin = false;
    }

    private RecruitsClaim(UUID uuid, String name, @Nullable UUID politicalEntityId){
        this.uuid = uuid;
        this.name = name;
        this.ownerPoliticalEntityId = politicalEntityId;
        this.isAdmin = false;
    }

    public UUID getUUID() {
        return uuid;
    }

    public void setCenter(ChunkPos center){
        this.center = center;
    }

    public ChunkPos getCenter(){
        return this.center;
    }
    public void addChunk(ChunkPos chunkPos) {
        if(claimedChunks.size() >= MAX_SIZE) return;

        if (!claimedChunks.contains(chunkPos)) {
            claimedChunks.add(chunkPos);
        }
    }

    public void removeChunk(ChunkPos chunkPos) {
        claimedChunks.remove(chunkPos);
    }

    public boolean containsChunk(ChunkPos chunkPos) {
        return claimedChunks.contains(chunkPos);
    }

    public List<ChunkPos> getClaimedChunks() {
        return claimedChunks;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public UUID getOwnerPoliticalEntityId() {
        return ownerPoliticalEntityId;
    }

    public RecruitsPlayerInfo getPlayerInfo(){
        return playerInfo;
    }
    public boolean isBlockInteractionAllowed() {
        return allowBlockInteraction;
    }

    public boolean isBlockPlacementAllowed() {
        return allowBlockPlacement;
    }

    public boolean isBlockBreakingAllowed() {
        return allowBlockBreaking;
    }

    public List<RecruitsPlayerInfo> getTrustedPlayers() {
        return trustedPlayers;
    }

    public int getFortLevel() {
        return fortLevel;
    }

    public boolean isTrustedPlayer(@Nullable UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        for (RecruitsPlayerInfo trustedPlayer : this.trustedPlayers) {
            if (trustedPlayer != null && playerUuid.equals(trustedPlayer.getUUID())) {
                return true;
            }
        }
        return false;
    }

    public void setName(String name) {
        this.name = name;
    }
    public void setAdminClaim(boolean admin){
        this.isAdmin = admin;
    }
    public void setOwnerPoliticalEntityId(@Nullable UUID politicalEntityId) {
        this.ownerPoliticalEntityId = politicalEntityId;
    }
    public void setPlayer(RecruitsPlayerInfo playerInfo) {
        this.playerInfo = playerInfo;
    }
    public void setBlockInteractionAllowed(boolean allow) {
        this.allowBlockInteraction = allow;
    }

    public void setBlockPlacementAllowed(boolean allow) {
        this.allowBlockPlacement = allow;
    }

    public void setBlockBreakingAllowed(boolean allow) {
        this.allowBlockBreaking = allow;
    }

    public void setFortLevel(int fortLevel) {
        this.fortLevel = FortLevelDefinition.clampLevel(fortLevel);
    }

    public void setTrustedPlayers(@Nullable List<RecruitsPlayerInfo> players) {
        this.trustedPlayers.clear();
        if (players == null) {
            return;
        }
        Set<UUID> seen = new LinkedHashSet<>();
        for (RecruitsPlayerInfo player : players) {
            if (player == null || player.getUUID() == null || !seen.add(player.getUUID())) {
                continue;
            }
            this.trustedPlayers.add(new RecruitsPlayerInfo(player.getUUID(), player.getName()));
        }
    }

    public void removeTrustedPlayer(@Nullable UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        this.trustedPlayers.removeIf(player -> player != null && playerUuid.equals(player.getUUID()));
    }

    @Override
    public String toString() {
        return this.getName();
    }

    public CompoundTag toNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putUUID("UUID", this.uuid);
        nbt.putString("name", name);
        if (ownerPoliticalEntityId != null) nbt.putUUID("ownerPoliticalEntity", ownerPoliticalEntityId);
        if (playerInfo != null) nbt.put("playerInfo", playerInfo.toNBT());
        nbt.put("trustedPlayers", RecruitsPlayerInfo.toNBT(this.trustedPlayers).get("Players"));
        nbt.putBoolean("allowInteraction", allowBlockInteraction);
        nbt.putBoolean("allowPlacement", allowBlockPlacement);
        nbt.putBoolean("allowBreaking", allowBlockBreaking);
        nbt.putInt("fortLevel", fortLevel);
        nbt.putBoolean("isAdmin", isAdmin);
        nbt.putBoolean("isRemoved", isRemoved);
        ListTag chunkList = new ListTag();
        for (ChunkPos pos : claimedChunks) {
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putInt("x", pos.x);
            chunkTag.putInt("z", pos.z);
            chunkList.add(chunkTag);
        }
        nbt.put("chunks", chunkList);

        nbt.putInt("centerX", this.getCenter().x);
        nbt.putInt("centerZ", this.getCenter().z);

        return nbt;
    }

    public static RecruitsClaim fromNBT(CompoundTag nbt) {
        UUID uuid = nbt.getUUID("UUID");
        String name = nbt.getString("name");
        UUID ownerPoliticalEntityId = nbt.hasUUID("ownerPoliticalEntity") ? nbt.getUUID("ownerPoliticalEntity") : null;
        RecruitsClaim claim = new RecruitsClaim(uuid, name, ownerPoliticalEntityId);
        RecruitsPlayerInfo playerInfo = RecruitsPlayerInfo.getFromNBT(nbt.getCompound("playerInfo"));
        if (playerInfo != null) claim.setPlayer(playerInfo);
        if (nbt.contains("trustedPlayers", Tag.TAG_LIST)) {
            CompoundTag trustedPlayersTag = new CompoundTag();
            trustedPlayersTag.put("Players", nbt.getList("trustedPlayers", Tag.TAG_COMPOUND));
            claim.setTrustedPlayers(RecruitsPlayerInfo.getListFromNBT(trustedPlayersTag));
        }


        claim.setBlockInteractionAllowed(nbt.getBoolean("allowInteraction"));
        claim.setBlockPlacementAllowed(nbt.getBoolean("allowPlacement"));
        claim.setBlockBreakingAllowed(nbt.getBoolean("allowBreaking"));
        if (nbt.contains("fortLevel", Tag.TAG_INT)) {
            claim.setFortLevel(nbt.getInt("fortLevel"));
        }
        claim.setAdminClaim(nbt.getBoolean("isAdmin"));
        claim.isRemoved = nbt.getBoolean("isRemoved");

        if (nbt.contains("chunks", Tag.TAG_LIST)) {
            ListTag chunkList = nbt.getList("chunks", Tag.TAG_COMPOUND);
            for (Tag tag : chunkList) {
                CompoundTag chunkTag = (CompoundTag) tag;
                int x = chunkTag.getInt("x");
                int z = chunkTag.getInt("z");
                claim.addChunk(new ChunkPos(x, z));
            }
        }

        int x = nbt.getInt("centerX");
        int z = nbt.getInt("centerZ");
        claim.setCenter(new ChunkPos(x, z));

        return claim;
    }


    public static CompoundTag toNBT(List<RecruitsClaim> list) {
        CompoundTag nbt = new CompoundTag();
        ListTag claimList = new ListTag();

        for (RecruitsClaim claim : list) {
            claimList.add(claim.toNBT());
        }

        nbt.put("Claims", claimList);
        return nbt;
    }

    public static List<RecruitsClaim> getListFromNBT(CompoundTag nbt) {
        List<RecruitsClaim> list = new ArrayList<>();
        ListTag claimList = nbt.getList("Claims", Tag.TAG_COMPOUND);

        for (int i = 0; i < claimList.size(); i++) {
            CompoundTag claimTag = claimList.getCompound(i);
            RecruitsClaim claim = RecruitsClaim.fromNBT(claimTag);
            list.add(claim);
        }

        return list;
    }
}
