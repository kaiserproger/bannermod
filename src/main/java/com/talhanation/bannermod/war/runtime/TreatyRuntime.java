package com.talhanation.bannermod.war.runtime;

import com.talhanation.bannermod.settlement.economy.StrategicResourceBucket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TreatyRuntime {
    private final Map<UUID, TributeTreatyRecord> tributeTreaties = new LinkedHashMap<>();
    private final Map<UUID, VassalRelationshipRecord> vassalRelationships = new LinkedHashMap<>();
    private final Map<UUID, TreatyDefaultFact> defaultFacts = new LinkedHashMap<>();
    private Runnable dirtyListener = () -> { };

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> { } : dirtyListener;
    }

    public TributeTreatyRecord addTribute(UUID payerEntityId, UUID receiverEntityId,
                                          StrategicResourceBucket resourceBucket, int amount,
                                          long intervalTicks, UUID sourceWarId,
                                          @Nullable UUID sourceClaimUuid,
                                          long createdAtGameTime) {
        TributeTreatyRecord record = new TributeTreatyRecord(UUID.randomUUID(), payerEntityId,
                receiverEntityId, resourceBucket, amount, intervalTicks, sourceWarId,
                sourceClaimUuid, createdAtGameTime, createdAtGameTime, 0, 0, true);
        tributeTreaties.put(record.id(), record);
        dirtyListener.run();
        return record;
    }

    public VassalRelationshipRecord addVassalRelationship(UUID overlordEntityId,
                                                           UUID vassalEntityId,
                                                           String obligations,
                                                           int tributeAmount,
                                                           long tributeIntervalTicks,
                                                           UUID sourceWarId,
                                                           long createdAtGameTime) {
        VassalRelationshipRecord record = new VassalRelationshipRecord(UUID.randomUUID(),
                overlordEntityId, vassalEntityId, obligations, tributeAmount,
                tributeIntervalTicks, sourceWarId, createdAtGameTime, true);
        vassalRelationships.put(record.id(), record);
        dirtyListener.run();
        return record;
    }

    public void recordPayment(UUID treatyId, long paidAtGameTime) {
        TributeTreatyRecord record = tributeTreaties.get(treatyId);
        if (record == null) return;
        TributeTreatyRecord updated = record.withPayment(paidAtGameTime);
        if (!updated.equals(record)) {
            tributeTreaties.put(treatyId, updated);
            dirtyListener.run();
        }
    }

    public TreatyDefaultFact recordDefault(TributeTreatyRecord treaty, int requested,
                                           int paid, int defaulted, long gameTime) {
        TributeTreatyRecord current = tributeTreaties.getOrDefault(treaty.id(), treaty);
        TributeTreatyRecord updated = current.withDefault(defaulted);
        tributeTreaties.put(updated.id(), updated);
        TreatyDefaultFact fact = new TreatyDefaultFact(UUID.randomUUID(), treaty.id(),
                treaty.payerEntityId(), treaty.receiverEntityId(), "TRIBUTE_PAYMENT",
                requested, paid, defaulted, gameTime);
        defaultFacts.put(fact.id(), fact);
        dirtyListener.run();
        return fact;
    }

    public Collection<TributeTreatyRecord> tributeTreaties() {
        return List.copyOf(tributeTreaties.values());
    }

    public Collection<VassalRelationshipRecord> vassalRelationships() {
        return List.copyOf(vassalRelationships.values());
    }

    public Collection<TreatyDefaultFact> defaultFacts() {
        return List.copyOf(defaultFacts.values());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag tributeList = new ListTag();
        for (TributeTreatyRecord record : tributeTreaties.values()) {
            tributeList.add(record.toTag());
        }
        tag.put("TributeTreaties", tributeList);
        ListTag vassalList = new ListTag();
        for (VassalRelationshipRecord record : vassalRelationships.values()) {
            vassalList.add(record.toTag());
        }
        tag.put("VassalRelationships", vassalList);
        ListTag defaultList = new ListTag();
        for (TreatyDefaultFact fact : defaultFacts.values()) {
            defaultList.add(fact.toTag());
        }
        tag.put("DefaultFacts", defaultList);
        return tag;
    }

    public static TreatyRuntime fromTag(CompoundTag tag) {
        TreatyRuntime runtime = new TreatyRuntime();
        ListTag tributeList = tag.getList("TributeTreaties", Tag.TAG_COMPOUND);
        for (int i = 0; i < tributeList.size(); i++) {
            TributeTreatyRecord record = TributeTreatyRecord.fromTag(tributeList.getCompound(i));
            runtime.tributeTreaties.put(record.id(), record);
        }
        ListTag vassalList = tag.getList("VassalRelationships", Tag.TAG_COMPOUND);
        for (int i = 0; i < vassalList.size(); i++) {
            VassalRelationshipRecord record = VassalRelationshipRecord.fromTag(vassalList.getCompound(i));
            runtime.vassalRelationships.put(record.id(), record);
        }
        ListTag defaultList = tag.getList("DefaultFacts", Tag.TAG_COMPOUND);
        for (int i = 0; i < defaultList.size(); i++) {
            TreatyDefaultFact fact = TreatyDefaultFact.fromTag(defaultList.getCompound(i));
            runtime.defaultFacts.put(fact.id(), fact);
        }
        return runtime;
    }
}
