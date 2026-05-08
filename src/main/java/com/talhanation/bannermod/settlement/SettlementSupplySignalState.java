package com.talhanation.bannermod.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public record SettlementSupplySignalState(
        int signalCount,
        int shortageSignalCount,
        int shortageUnitCount,
        int reservationHintUnitCount,
        List<SettlementSupplySignal> signals
) {
    public SettlementSupplySignalState {
        signalCount = Math.max(0, signalCount);
        shortageSignalCount = Math.max(0, Math.min(shortageSignalCount, signalCount));
        shortageUnitCount = Math.max(0, shortageUnitCount);
        reservationHintUnitCount = Math.max(0, reservationHintUnitCount);
        signals = List.copyOf(signals == null ? List.of() : signals);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SignalCount", this.signalCount);
        tag.putInt("ShortageSignalCount", this.shortageSignalCount);
        tag.putInt("ShortageUnitCount", this.shortageUnitCount);
        tag.putInt("ReservationHintUnitCount", this.reservationHintUnitCount);
        ListTag signalList = new ListTag();
        for (SettlementSupplySignal signal : this.signals) {
            signalList.add(signal.toTag());
        }
        tag.put("Signals", signalList);
        return tag;
    }

    public static SettlementSupplySignalState fromTag(CompoundTag tag) {
        return new SettlementSupplySignalState(
                tag.getInt("SignalCount"),
                tag.getInt("ShortageSignalCount"),
                tag.getInt("ShortageUnitCount"),
                tag.getInt("ReservationHintUnitCount"),
                readSignals(tag.getList("Signals", Tag.TAG_COMPOUND))
        );
    }

    public static SettlementSupplySignalState empty() {
        return new SettlementSupplySignalState(0, 0, 0, 0, List.of());
    }

    private static List<SettlementSupplySignal> readSignals(ListTag list) {
        List<SettlementSupplySignal> signals = new ArrayList<>();
        for (Tag entry : list) {
            signals.add(SettlementSupplySignal.fromTag((CompoundTag) entry));
        }
        return signals;
    }
}
