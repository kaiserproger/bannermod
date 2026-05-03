package com.talhanation.bannermod.ai.civilian;

import com.talhanation.bannermod.entity.civilian.AbstractWorkerEntity;
import com.talhanation.bannermod.entity.civilian.workarea.StorageArea;
import com.talhanation.bannermod.shared.logistics.BannerModLogisticsBlockedReason;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;

import java.util.*;

public class DepositItemsToStorage extends AbstractChestGoal {

    public DepositItemsToStorage(AbstractWorkerEntity worker){
        super(worker);
        setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
    }
    @Override
    public boolean canUse() {
        boolean courierOverride = worker.hasActiveCourierTask();
        return (courierOverride || worker.shouldWork())
                && !worker.needsToSleep()
                && worker.needsToDeposit()
                && super.canUse();
    }

    @Override
    public void start() {
        this.visited = new ArrayList<>();
        errorMessageDone = false;
        setState(State.SELECT_STORAGE);
        timer = 0;
    }

    int timer;
    public State state;
    public boolean errorMessageDone;
    public int retryTime;
    public boolean chestFull;
    public void tick(){
        if(this.worker.getCommandSenderWorld().isClientSide()) return;

        if(this.chestPos != null) worker.getLookControl().setLookAt(chestPos.getCenter());

        switch (state){
            case SELECT_STORAGE -> {
                errorMessageDone = false;

                if (storageAreaStack.isEmpty()) {

                    scanAvailableStorageAreas();

                    if(storageAreaStack.isEmpty()){
                        setState(State.ERROR_NO_STORAGE_FOUND);
                        return;
                    }
                }

                this.storageArea = storageAreaStack.pop();

                setState(State.MOVE_TO_STORAGE);
            }

            case MOVE_TO_STORAGE -> {
                if(moveToPosition(storageArea.getOnPos())) return;

                setState(State.SCAN_STORAGE);
            }

            case SCAN_STORAGE -> {
                storageArea.scanStorageBlocks();
                blockPosStack = new Stack<>();

                for(BlockPos pos : storageArea.storageMap.keySet()){
                    this.blockPosStack.push(pos);
                }

                if(blockPosStack.isEmpty()) {
                    this.visited.add(storageArea.getUUID());

                    setState(State.ERROR_STORAGE_NO_CONTAINERS);
                    return;
                }

                setState(State.SELECT_CHEST);
            }

            case SELECT_CHEST -> {
                if(blockPosStack.isEmpty()){
                    this.visited.add(storageArea.getUUID());
                    setState(State.ERROR_STORAGE_FULL);
                    return;
                }
                else{
                    blockPosStack.sort(Comparator.comparing(pos -> pos.getCenter().distanceToSqr(worker.position())));
                    blockPosStack.sort(Comparator.reverseOrder());

                    chestPos = blockPosStack.pop();
                }

                setState(State.MOVE_TO_CHEST);
            }

            case MOVE_TO_CHEST -> {
                if(moveToPosition(chestPos)) return;

                setState(State.CHECK_CHEST);
            }

            case CHECK_CHEST -> {
                container = getContainer(chestPos);
                if(container == null){
                    this.storageArea.storageMap.remove(chestPos);
                    setState(State.SELECT_CHEST);
                    return;
                }
                else if(this.isContainerFull(container)){
                    chestFull = true;
                    setState(State.SELECT_CHEST);
                    return;
                }

                setState(State.OPEN_CHEST);
            }

            case OPEN_CHEST -> {
                this.interactChest(container, true);

                if(timer++ < 40){
                    return;
                }
                timer = 0;

                setState(State.DEPOSIT);
            }

            case DEPOSIT -> {
                if(depositItems()){
                    setState(State.CLOSE_CHEST_DEPOSIT_DONE);
                }
                else{
                    setState(State.CLOSE_CHEST_FULL_CHEST);
                }

            }

            case CLOSE_CHEST_DEPOSIT_DONE -> {
                this.interactChest(container, false);

                if(timer++ < 20){
                    return;
                }
                timer = 0;

                setState(State.DONE);
            }

            case CLOSE_CHEST_FULL_CHEST -> {
                this.interactChest(container, false);

                if(timer++ < 20){
                    return;
                }
                timer = 0;

                setState(State.SELECT_CHEST);
            }

            case DONE -> {
                worker.clearWorkStatus();
                worker.farmedItems = 0;
                worker.forcedDeposit = false;
                this.worker.lastStorage = storageArea.getUUID();
                worker.completeActiveCourierDelivery();
            }

            case ERROR_NO_STORAGE_FOUND -> {
                if (worker.hasActiveCourierTask()) {
                    worker.abandonActiveCourierTask(BannerModLogisticsBlockedReason.DESTINATION_CONTAINER_MISSING, "I could not reach the courier delivery storage.");
                    return;
                }
                reportBlockedReason("deposit_storage_missing", "No available storage found nearby.");
                if(!errorMessageDone){
                    errorMessageDone = true;
                }

                if(++retryTime >= 20*60){
                    retryTime = 0;
                    this.start();
                }
            }

            case ERROR_STORAGE_FULL -> {
                if (worker.hasActiveCourierTask()) {
                    worker.abandonActiveCourierTask(BannerModLogisticsBlockedReason.DESTINATION_FULL, storageArea != null ? storageArea.getName().getString() + " cannot accept the courier delivery." : "The courier delivery storage is full.");
                    return;
                }
                reportBlockedReason("deposit_storage_full", storageArea != null ? storageArea.getName().getString() + " is full." : "Storage is full.");
                if(!errorMessageDone){
                    errorMessageDone = true;
                }
                if(storageArea != null) this.visited.add(storageArea.getUUID());
                setState(State.SELECT_STORAGE);
            }

            case ERROR_STORAGE_NO_CONTAINERS -> {
                if (worker.hasActiveCourierTask()) {
                    worker.abandonActiveCourierTask(BannerModLogisticsBlockedReason.DESTINATION_CONTAINER_MISSING, storageArea != null ? storageArea.getName().getString() + " has no courier delivery containers." : "The courier delivery storage has no containers.");
                    return;
                }
                reportBlockedReason("deposit_storage_no_containers", storageArea != null ? storageArea.getName().getString() + " has no containers." : "Storage has no containers.");
                if(!errorMessageDone){
                    errorMessageDone = true;
                }

                if(storageArea != null) this.visited.add(storageArea.getUUID());
                setState(State.SELECT_STORAGE);
            }
        }
    }

    private boolean depositItems() {
        SimpleContainer inventory = worker.getInventory();

        if (container == null || this.isContainerFull(container)) {
            return false;
        }

        StorageDepositRules.DepositResult result = StorageDepositRules.depositAll(inventory, container, worker::wantsToKeep);
        boolean completed = !result.hasDepositableItemsRemaining();
        if (completed && worker.hasActiveCourierTask()) {
            worker.lastStorage = storageArea.getUUID();
            worker.completeActiveCourierDelivery();
        }
        return completed;
    }

    public void setState(State state) {
        //if(worker.getOwner() != null) worker.getOwner().sendSystemMessage(Component.literal(state.toString()));
        this.state = state;
    }

    public enum State{
        SELECT_STORAGE,
        MOVE_TO_STORAGE,
        SCAN_STORAGE,
        SELECT_CHEST,
        MOVE_TO_CHEST,
        CHECK_CHEST,
        OPEN_CHEST,
        DEPOSIT,
        CLOSE_CHEST_FULL_CHEST,
        CLOSE_CHEST_DEPOSIT_DONE,
        DONE,
        ERROR_NO_STORAGE_FOUND,
        ERROR_STORAGE_FULL,
        ERROR_STORAGE_NO_CONTAINERS

    }
}
