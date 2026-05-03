package com.talhanation.bannermod.inventory.civilian;

import com.talhanation.bannermod.entity.citizen.CitizenEntity;
import com.talhanation.bannermod.registry.civilian.ModMenuTypes;
import com.talhanation.bannermod.society.NpcFamilyTreeSnapshot;
import com.talhanation.bannermod.society.NpcPhaseOneSnapshot;
import de.maxhenkel.corelib.inventory.ContainerBase;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

public class CitizenProfileMenu extends ContainerBase {
    private final CitizenEntity citizen;
    private final Container citizenInventory;
    private final NpcPhaseOneSnapshot phaseOneSnapshot;
    private final NpcFamilyTreeSnapshot familyTreeSnapshot;

    public CitizenProfileMenu(int id, CitizenEntity citizen, Inventory playerInventory) {
        this(id, citizen, playerInventory, NpcPhaseOneSnapshot.empty(), NpcFamilyTreeSnapshot.empty());
    }

    public CitizenProfileMenu(int id, CitizenEntity citizen, Inventory playerInventory, NpcPhaseOneSnapshot phaseOneSnapshot) {
        this(id, citizen, playerInventory, phaseOneSnapshot, NpcFamilyTreeSnapshot.empty());
    }

    public CitizenProfileMenu(int id,
                              CitizenEntity citizen,
                              Inventory playerInventory,
                              NpcPhaseOneSnapshot phaseOneSnapshot,
                              NpcFamilyTreeSnapshot familyTreeSnapshot) {
        super(ModMenuTypes.CITIZEN_PROFILE_CONTAINER_TYPE.get(), id, playerInventory, citizen.getInventory());
        this.citizen = citizen;
        this.citizenInventory = citizen.getInventory();
        this.phaseOneSnapshot = phaseOneSnapshot == null ? NpcPhaseOneSnapshot.empty() : phaseOneSnapshot;
        this.familyTreeSnapshot = familyTreeSnapshot == null ? NpcFamilyTreeSnapshot.empty() : familyTreeSnapshot;
        addCitizenInventorySlots();
        addPlayerInventorySlots(playerInventory);
    }

    public CitizenEntity getCitizen() {
        return citizen;
    }

    public NpcPhaseOneSnapshot getPhaseOneSnapshot() {
        return this.phaseOneSnapshot;
    }

    public NpcFamilyTreeSnapshot getFamilyTreeSnapshot() {
        return this.familyTreeSnapshot;
    }

    @Override
    public boolean stillValid(Player player) {
        return citizen.isAlive() && player.distanceToSqr(citizen) < 64.0D;
    }

    private void addCitizenInventorySlots() {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(citizenInventory, col + row * 9, 96 + col * 18, 116 + row * 18));
            }
        }
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 14 + col * 18, 174 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 14 + col * 18, 232));
        }
    }
}
