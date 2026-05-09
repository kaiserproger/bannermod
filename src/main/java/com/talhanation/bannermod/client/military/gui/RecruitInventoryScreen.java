package com.talhanation.bannermod.client.military.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.talhanation.bannermod.ai.military.CombatStance;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.civilian.input.AssignHomeTargetSelector;
import com.talhanation.bannermod.entity.military.runtime.RecruitEvents;
import com.talhanation.bannermod.client.military.ClientManager;
import com.talhanation.bannermod.client.military.gui.widgets.ActionMenuButton;
import com.talhanation.bannermod.client.military.gui.widgets.ContextMenuEntry;
import com.talhanation.bannermod.client.military.gui.widgets.ScrollDropDownMenu;
import com.talhanation.bannermod.compat.SmallShips;
import com.talhanation.bannermod.compat.workers.IVillagerWorker;
import com.talhanation.bannermod.entity.military.*;
import com.talhanation.bannermod.inventory.military.RecruitInventoryMenu;
import com.talhanation.bannermod.network.messages.military.*;
import com.talhanation.bannermod.persistence.military.RecruitsGroup;
import com.talhanation.bannermod.shared.military.BannerModRecruitFirearmStatus;
import de.maxhenkel.corelib.inventory.ScreenBase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.gui.widget.ExtendedButton;

@OnlyIn(Dist.CLIENT)
public class RecruitInventoryScreen extends ScreenBase<RecruitInventoryMenu> {
    private static final ResourceLocation RESOURCE_LOCATION = ResourceLocation.fromNamespaceAndPath(BannerModMain.MOD_ID, "textures/gui/recruit_gui.png" );

    private static final MutableComponent TEXT_HEALTH = Component.translatable("gui.recruits.inv.health");
    private static final MutableComponent TEXT_LEVEL = Component.translatable("gui.recruits.inv.level");
    private static final MutableComponent TEXT_EXP = Component.translatable("gui.recruits.inv.exp");
    private static final MutableComponent TEXT_KILLS = Component.translatable("gui.recruits.inv.kills");
    private static final MutableComponent TEXT_MORALE = Component.translatable("gui.recruits.inv.morale");
    private static final MutableComponent TEXT_HUNGER = Component.translatable("gui.recruits.inv.hunger");
    private static final MutableComponent TEXT_DISBAND = Component.translatable("gui.recruits.inv.text.disband");
    private static final MutableComponent TEXT_INFO_FOLLOW = Component.translatable("gui.recruits.inv.info.text.follow");
    private static final MutableComponent TEXT_INFO_WANDER = Component.translatable("gui.recruits.inv.info.text.wander");
    private static final MutableComponent TEXT_INFO_HOLD_POS = Component.translatable("gui.recruits.inv.info.text.hold_pos");
    private static final MutableComponent TEXT_INFO_PASSIVE = Component.translatable("gui.recruits.inv.info.text.passive");
    private static final MutableComponent TEXT_INFO_NEUTRAL = Component.translatable("gui.recruits.inv.info.text.neutral");
    private static final MutableComponent TEXT_INFO_AGGRESSIVE = Component.translatable("gui.recruits.inv.info.text.aggressive");
    private static final MutableComponent TEXT_INFO_LISTEN = Component.translatable("gui.recruits.inv.info.text.listen");
    private static final MutableComponent TEXT_INFO_IGNORE = Component.translatable("gui.recruits.inv.info.text.ignore");
    private static final MutableComponent TEXT_INFO_RAID = Component.translatable("gui.recruits.inv.info.text.raid");
    private static final MutableComponent TEXT_INFO_PROTECT = Component.translatable("gui.recruits.inv.info.text.protect");
    private static final MutableComponent TEXT_INFO_WORKING = Component.translatable("gui.recruits.inv.info.text.working");
    private static final MutableComponent TEXT_DISMOUNT = Component.translatable("gui.recruits.inv.text.dismount");
    private static final MutableComponent TEXT_BACK_TO_MOUNT = Component.translatable("gui.recruits.inv.text.backToMount");
    private static final MutableComponent TOOLTIP_DISMOUNT = Component.translatable("gui.recruits.inv.tooltip.dismount");
    private static final MutableComponent TOOLTIP_FOLLOW = Component.translatable("gui.recruits.inv.tooltip.follow");
    private static final MutableComponent TOOLTIP_WANDER = Component.translatable("gui.recruits.inv.tooltip.wander");
    private static final MutableComponent TOOLTIP_HOLD_MY_POS = Component.translatable("gui.recruits.inv.tooltip.holdMyPos");
    private static final MutableComponent TOOLTIP_HOLD_POS = Component.translatable("gui.recruits.inv.tooltip.holdPos");
    private static final MutableComponent TOOLTIP_BACK_TO_POS = Component.translatable("gui.recruits.inv.tooltip.backToPos");
    private static final MutableComponent TOOLTIP_CLEAR_TARGET = Component.translatable("gui.recruits.inv.tooltip.clearTargets");
    private static final MutableComponent TOOLTIP_MOUNT = Component.translatable("gui.recruits.inv.tooltip.mount");
    private static final MutableComponent TOOLTIP_PASSIVE = Component.translatable("gui.recruits.inv.tooltip.passive");
    private static final MutableComponent TOOLTIP_NEUTRAL = Component.translatable("gui.recruits.inv.tooltip.neutral");
    private static final MutableComponent TOOLTIP_AGGRESSIVE = Component.translatable("gui.recruits.inv.tooltip.aggressive");
    private static final MutableComponent TOOLTIP_RAID = Component.translatable("gui.recruits.inv.tooltip.raid");
    private static final MutableComponent TOOLTIP_BACK_TO_MOUNT = Component.translatable("gui.recruits.inv.tooltip.backToMount");
    private static final MutableComponent TOOLTIP_CLEAR_UPKEEP = Component.translatable("gui.recruits.inv.tooltip.clearUpkeep");
    private static final MutableComponent TOOLTIP_CLEAR_UPKEEP_DISABLED = Component.translatable("gui.recruits.inv.tooltip.clearUpkeep_disabled");
    private static final MutableComponent TOOLTIP_NOBLE_LOCKED = Component.translatable("gui.recruits.inv.tooltip.noble_locked");
    private static final MutableComponent TEXT_FOLLOW = Component.translatable("gui.recruits.inv.text.follow");
    private static final MutableComponent TEXT_WANDER = Component.translatable("gui.recruits.inv.text.wander");
    private static final MutableComponent TEXT_HOLD_MY_POS = Component.translatable("gui.recruits.inv.text.holdMyPos");
    private static final MutableComponent TEXT_HOLD_POS = Component.translatable("gui.recruits.inv.text.holdPos");
    private static final MutableComponent TEXT_BACK_TO_POS = Component.translatable("gui.recruits.inv.text.backToPos");
    private static final MutableComponent TEXT_PASSIVE = Component.translatable("gui.recruits.inv.text.passive");
    private static final MutableComponent TEXT_NEUTRAL = Component.translatable("gui.recruits.inv.text.neutral");
    private static final MutableComponent TEXT_AGGRESSIVE = Component.translatable("gui.recruits.inv.text.aggressive");
    private static final MutableComponent TEXT_RAID = Component.translatable("gui.recruits.inv.text.raid");
    private static final MutableComponent TEXT_CLEAR_TARGET = Component.translatable("gui.recruits.inv.text.clearTargets");
    private static final MutableComponent TEXT_MOUNT = Component.translatable("gui.recruits.command.text.mount");
    private static final MutableComponent TEXT_CLEAR_UPKEEP = Component.translatable("gui.recruits.inv.text.clearUpkeep");
    private static final MutableComponent TOOLTIP_STANCE = Component.translatable("gui.recruits.inv.tooltip.combat_stance");
    private static final MutableComponent TEXT_FIREARM_SUPPORTED = Component.translatable("gui.recruits.inv.info.firearm_supported");
    private static final MutableComponent TEXT_FIREARM_UNSUPPORTED = Component.translatable("gui.recruits.inv.info.firearm_unsupported");
    private static final MutableComponent TEXT_FIREARM_AMMO_MISSING = Component.translatable("gui.recruits.inv.info.firearm_ammo_missing");

    private static final MutableComponent TEXT_PROMOTE = Component.translatable("gui.recruits.inv.text.promote");
    private static final MutableComponent TEXT_SPECIAL = Component.translatable("gui.recruits.inv.text.special");
    private static final MutableComponent TOOLTIP_PROMOTE = Component.translatable("gui.recruits.inv.tooltip.promote");
    private static final MutableComponent TOOLTIP_DISABLED_PROMOTE = Component.translatable("gui.recruits.inv.tooltip.promote_disabled");
    private static final MutableComponent TOOLTIP_SPECIAL = Component.translatable("gui.recruits.inv.tooltip.special");
    private static final MutableComponent TOOLTIP_SPECIAL_DISABLED = Component.translatable("gui.recruits.inv.tooltip.special_disabled");
    private static final MutableComponent TEXT_MENU_CONVERT = Component.translatable("gui.bannermod.inv.menu.convert_type");
    private static final MutableComponent TEXT_CONVERT_SWORDSMAN = Component.translatable("gui.bannermod.inv.convert.swordsman");
    private static final MutableComponent TEXT_CONVERT_BOWMAN = Component.translatable("gui.bannermod.inv.convert.bowman");
    private static final MutableComponent TEXT_CONVERT_PIKEMAN = Component.translatable("gui.bannermod.inv.convert.pikeman");
    private static final MutableComponent TEXT_CONVERT_CROSSBOWMAN = Component.translatable("gui.bannermod.inv.convert.crossbowman");
    private static final MutableComponent TEXT_CONVERT_CAVALRY = Component.translatable("gui.bannermod.inv.convert.cavalry");
    private static final MutableComponent TOOLTIP_CONVERT = Component.translatable("gui.bannermod.inv.tooltip.convert_type");
    private static final MutableComponent TOOLTIP_CURRENT_STATE = Component.translatable("gui.recruits.inv.tooltip.current_state");
    private static final MutableComponent SECTION_DISCIPLINE = Component.translatable("gui.recruits.inv.section.discipline");
    private static final MutableComponent SECTION_ORDERS = Component.translatable("gui.recruits.inv.section.orders");
    private static final MutableComponent SECTION_DETAILS = Component.translatable("gui.recruits.inv.section.details");
    private static final MutableComponent TEXT_MENU_AGGRO = Component.translatable("gui.recruits.command.menu.aggro");
    private static final MutableComponent TEXT_MENU_ORDERS = Component.translatable("gui.recruits.inv.menu.orders");
    private static final MutableComponent TEXT_MENU_MOUNT = Component.translatable("gui.recruits.inv.menu.mount");
    private static final MutableComponent TEXT_ASSIGN_HOME = Component.translatable("bannermod.assign_home.button");
    private static final MutableComponent TOOLTIP_ASSIGN_HOME = Component.translatable("bannermod.assign_home.tooltip");
    private static final MutableComponent STATUS_READ_ONLY = Component.translatable("gui.recruits.inv.status.read_only");
    private static final MutableComponent STATUS_GROUP_UNSET = Component.translatable("gui.recruits.inv.status.group_unset");
    private static final MutableComponent STATUS_GROUP_LOCKED = Component.translatable("gui.recruits.inv.status.group_locked");
    private static final MutableComponent STATUS_FIREARM_UNSUPPORTED = Component.translatable("gui.recruits.inv.status.firearm_unsupported");
    private static final MutableComponent STATUS_FIREARM_AMMO_MISSING = Component.translatable("gui.recruits.inv.status.firearm_ammo_missing");
    private static final MutableComponent STATUS_PROMOTE_READY = Component.translatable("gui.recruits.inv.status.promote_ready");
    private static final MutableComponent STATUS_READY = Component.translatable("gui.recruits.inv.status.ready");
    private static final int fontColor = MilitaryGuiStyle.TEXT_DARK;
    private static final int firearmSupportedColor = 0x3A7A2A;
    private static final int firearmWarningColor = 0xD2A12D;
    private static final int firearmUnsupportedColor = 0xA33A2A;
    private final AbstractRecruitEntity recruit;
    private final Inventory playerInventory;
    private RecruitsGroup currentGroup;
    private int follow;
    private int aggro;
    private Button clearUpkeep;
    private boolean canPromote;
    private boolean buttonsSet;
    private Button rightListenButton;
    private Button leftListenButton;
    private Button moreButton;
    private Button stanceButton;
    private ScrollDropDownMenu<RecruitsGroup> groupSelectionDropDownMenu;
    public RecruitInventoryScreen(RecruitInventoryMenu recruitContainer, Inventory playerInventory, Component title) {
        super(RESOURCE_LOCATION, recruitContainer, playerInventory, Component.literal(""));
        this.recruit = recruitContainer.getRecruit();
        this.playerInventory = playerInventory;
        imageWidth = 412;
        imageHeight = 260;
    }

    @Override
    protected void init() {
        super.init();

        int zeroLeftPos = leftPos + 316;
        int zeroTopPos = topPos + 44;
        int topPosGab = 4;
        this.canPromote = this.recruit.getXpLevel() >= 3;

        this.clearWidgets();

        // AGGRO MENU — collapses Passive / Neutral / Aggressive / Raid / Clear Target into a single
        // dropdown trigger. Mutations remain server-authoritative; the menu only sends intent packets.
        boolean isNoble = recruit instanceof VillagerNobleEntity;
        int recruitState = recruit.getState();
        ActionMenuButton aggroMenu = new ActionMenuButton(zeroLeftPos - 270, zeroTopPos + (20 + topPosGab) * 0,
                80, 20, TEXT_MENU_AGGRO, java.util.List.of(
                new ContextMenuEntry(TEXT_PASSIVE.getString(), () -> {
                    if (recruit.getState() != 3) {
                        this.aggro = 3;
                        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageAggroGui(aggro, recruit.getUUID()));
                    }
                }, recruitState != 3),
                new ContextMenuEntry(TEXT_NEUTRAL.getString(), () -> {
                    if (recruit.getState() != 0) {
                        this.aggro = 0;
                        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageAggroGui(aggro, recruit.getUUID()));
                    }
                }, recruitState != 0),
                new ContextMenuEntry(TEXT_AGGRESSIVE.getString(), () -> {
                    if (recruit.getState() != 1) {
                        this.aggro = 1;
                        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageAggroGui(aggro, recruit.getUUID()));
                    }
                }, !isNoble && recruitState != 1),
                new ContextMenuEntry(TEXT_RAID.getString(), () -> {
                    if (recruit.getState() != 2) {
                        this.aggro = 2;
                        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageAggroGui(aggro, recruit.getUUID()));
                    }
                }, !isNoble && recruitState != 2),
                new ContextMenuEntry(TEXT_CLEAR_TARGET.getString(), () ->
                        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageClearTargetGui(playerInventory.player.getUUID(), recruit.getUUID())),
                        true)
        ));
        aggroMenu.setTooltip(Tooltip.create(TOOLTIP_PASSIVE.copy().append("\n")
                .append(TOOLTIP_NEUTRAL).append("\n")
                .append(TOOLTIP_AGGRESSIVE).append("\n")
                .append(TOOLTIP_RAID).append("\n")
                .append(TOOLTIP_CLEAR_TARGET)));
        addRenderableWidget(aggroMenu);

        //MOUNT — kept as a separate row because it triggers a different menu path (mount/dismount).
        ExtendedButton buttonMount =  new ProfileButton(zeroLeftPos - 270, zeroTopPos + (20 + topPosGab) * 5, 80, 20, TEXT_MOUNT,
            button -> {
                BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageMountEntityGui(recruit.getUUID(), false));
            }
            );
        boolean isNobleForMount = recruit instanceof VillagerNobleEntity;
        buttonMount.setTooltip(Tooltip.create(isNobleForMount ? TOOLTIP_NOBLE_LOCKED : TOOLTIP_MOUNT));
        buttonMount.active = !isNobleForMount;
        addRenderableWidget(buttonMount);


        // ORDERS MENU — collapses Wander / Follow / Hold Pos / Back to Pos / Hold My Pos under one
        // dropdown trigger. Server is source of truth for follow-state changes.
        int followState = recruit.getFollowState();
        ActionMenuButton ordersMenu = new ActionMenuButton(zeroLeftPos, zeroTopPos + (20 + topPosGab) * 0,
                80, 20, TEXT_MENU_ORDERS, java.util.List.of(
                new ContextMenuEntry(TEXT_WANDER.getString(), () -> {
                    if (recruit.getFollowState() != 0) {
                        this.follow = 0;
                        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageFollowGui(follow, recruit.getUUID()));
                    }
                }, followState != 0),
                new ContextMenuEntry(TEXT_FOLLOW.getString(), () -> {
                    if (recruit.getFollowState() != 1) {
                        this.follow = 1;
                        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageFollowGui(follow, recruit.getUUID()));
                    }
                }, !isNoble && followState != 1),
                new ContextMenuEntry(TEXT_HOLD_POS.getString(), () -> {
                    if (recruit.getFollowState() != 2) {
                        this.follow = 2;
                        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageFollowGui(follow, recruit.getUUID()));
                    }
                }, followState != 2),
                new ContextMenuEntry(TEXT_BACK_TO_POS.getString(), () -> {
                    if (recruit.getFollowState() != 3) {
                        this.follow = 3;
                        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageFollowGui(follow, recruit.getUUID()));
                    }
                }, followState != 3),
                new ContextMenuEntry(TEXT_HOLD_MY_POS.getString(), () -> {
                    if (recruit.getFollowState() != 4) {
                        this.follow = 4;
                        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageFollowGui(follow, recruit.getUUID()));
                    }
                }, !isNoble && followState != 4)
        ));
        ordersMenu.setTooltip(Tooltip.create(TOOLTIP_WANDER.copy().append("\n")
                .append(TOOLTIP_FOLLOW).append("\n")
                .append(TOOLTIP_HOLD_POS).append("\n")
                .append(TOOLTIP_BACK_TO_POS).append("\n")
                .append(TOOLTIP_HOLD_MY_POS)));
        addRenderableWidget(ordersMenu);

        // MOUNT MENU — Dismount / Back-to-Mount collapse to one trigger.
        ActionMenuButton mountMenu = new ActionMenuButton(zeroLeftPos, zeroTopPos + (20 + topPosGab) * 5,
                80, 20, TEXT_MENU_MOUNT, java.util.List.of(
                new ContextMenuEntry(TEXT_DISMOUNT.getString(), () -> {
                    if (recruit.getFollowState() != 4) {
                        this.follow = 4;
                        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageDismountGui(playerInventory.player.getUUID(), recruit.getUUID()));
                    }
                }, true),
                new ContextMenuEntry(TEXT_BACK_TO_MOUNT.getString(), () ->
                        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageMountEntityGui(recruit.getUUID(), true)),
                        !isNoble)
        ));
        mountMenu.setTooltip(Tooltip.create(TOOLTIP_DISMOUNT.copy().append("\n").append(TOOLTIP_BACK_TO_MOUNT)));
        addRenderableWidget(mountMenu);

        //CLEAR UPKEEP
        this.clearUpkeep = addRenderableWidget(new ProfileButton(zeroLeftPos - 270, zeroTopPos + (20 + topPosGab) * 6, 80, 20, TEXT_CLEAR_UPKEEP,
                button -> {
                    BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageClearUpkeepGui(recruit.getUUID()));
                    clearUpkeep.active = false;
                }
        ));
        this.clearUpkeep.active = this.recruit.hasUpkeep();
        this.clearUpkeep.setTooltip(Tooltip.create(this.clearUpkeep.active ? TOOLTIP_CLEAR_UPKEEP : TOOLTIP_CLEAR_UPKEEP_DISABLED));

        this.stanceButton = addRenderableWidget(new ProfileButton(zeroLeftPos - 270, zeroTopPos + (20 + topPosGab) * 7, 80, 20, Component.empty(),
                button -> {
                    CombatStance nextStance = nextCombatStance(this.recruit.getCombatStance());
                    BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageCombatStanceGui(recruit.getUUID(), nextStance));
                }
        ));
        this.stanceButton.active = !(recruit instanceof VillagerNobleEntity);
        this.stanceButton.setTooltip(Tooltip.create(this.stanceButton.active ? TOOLTIP_STANCE : TOOLTIP_NOBLE_LOCKED));
        updateCombatStanceButton();

        //LISTEN
         leftListenButton =  new ProfileButton(leftPos + 160, topPos + 130, 14, 14, Component.literal("<"), button -> {
            BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageListen(!recruit.getListen(), recruit.getUUID()));
         });
         leftListenButton.active = !(recruit instanceof VillagerNobleEntity);
         if (!leftListenButton.active) leftListenButton.setTooltip(Tooltip.create(TOOLTIP_NOBLE_LOCKED));
         addRenderableWidget(leftListenButton);

        rightListenButton = new ProfileButton(leftPos + 274, topPos + 130, 14, 14, Component.literal(">"), button -> {
            BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageListen(!recruit.getListen(), recruit.getUUID()));
        });
        rightListenButton.active = !(recruit instanceof VillagerNobleEntity);
        if (!rightListenButton.active) rightListenButton.setTooltip(Tooltip.create(TOOLTIP_NOBLE_LOCKED));
        addRenderableWidget(rightListenButton);

        //more
        moreButton = new ProfileButton(leftPos + 246, topPos + 24, 42, 16, Component.literal("..."),
                button -> {
                    minecraft.setScreen(new RecruitMoreScreen(this, this.recruit, this.playerInventory.player));
                }
        );
        moreButton.active = !(recruit instanceof VillagerNobleEntity);
        if (!moreButton.active) moreButton.setTooltip(Tooltip.create(TOOLTIP_NOBLE_LOCKED));
        addRenderableWidget(moreButton);

        Button assignHome = addRenderableWidget(new ProfileButton(zeroLeftPos, zeroTopPos + (20 + topPosGab) * 6,
                80, 20, TEXT_ASSIGN_HOME, button -> {
            AssignHomeTargetSelector.start(this.recruit.getUUID());
            this.onClose();
        }));
        assignHome.setTooltip(Tooltip.create(TOOLTIP_ASSIGN_HOME));

        if(recruit instanceof VillagerNobleEntity){
            return;
        }
        //promote
        if(recruit instanceof ICompanion || recruit instanceof IVillagerWorker){
            Button promoteButton = addRenderableWidget(new ProfileButton(zeroLeftPos, zeroTopPos + (20 + topPosGab) * 8, 80, 20, TEXT_SPECIAL,
                    button -> {
                        if(recruit instanceof ScoutEntity scout){
                            this.minecraft.setScreen(new ScoutScreen(scout, getMinecraft().player));
                            return;
                        }
                        else if(recruit instanceof MessengerEntity messenger){
                            this.minecraft.setScreen(new MessengerMainScreen(messenger, getMinecraft().player));
                            return;
                        }
                        else if(recruit instanceof AbstractLeaderEntity leader){
                            this.minecraft.setScreen(new PatrolLeaderScreen(leader, getMinecraft().player));
                            return;
                        }
                        else if(recruit instanceof IVillagerWorker worker && worker.hasOnlyScreen()){
                            this.minecraft.setScreen(worker.getSpecialScreen(recruit, getMinecraft().player));
                            return;
                        }
                        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageOpenSpecialScreen(this.playerInventory.player, recruit.getUUID()));
                        this.onClose();
                    }
            ));

            promoteButton.setTooltip(Tooltip.create(canPromote ? TOOLTIP_SPECIAL : TOOLTIP_SPECIAL_DISABLED));
            promoteButton.active = canPromote;

        }
        else {
            Button promoteButton = addRenderableWidget(new ProfileButton(zeroLeftPos, zeroTopPos + (20 + topPosGab) * 8, 80, 20, TEXT_PROMOTE,
                    button -> {
                        RecruitEvents.openPromoteScreen(this.playerInventory.player, this.recruit);
                        this.onClose();
                    }
            ));
            promoteButton.setTooltip(Tooltip.create(canPromote ? TOOLTIP_PROMOTE : TOOLTIP_DISABLED_PROMOTE));
            promoteButton.active = canPromote;

            // Convert-Type menu — only for plain base recruits (not companions, not noble villagers,
            // not villager-worker compat). Sends MessageConvertRecruitType; server discards old
            // entity and spawns the chosen type carrying owner/group/xp/level/inventory across.
            if (com.talhanation.bannermod.entity.military.runtime.RecruitTypeConverter.isConvertibleBaseType(this.recruit)) {
                ActionMenuButton convertMenu = new ActionMenuButton(zeroLeftPos, zeroTopPos + (20 + topPosGab) * 7,
                        80, 20, TEXT_MENU_CONVERT, java.util.List.of(
                        new ContextMenuEntry(TEXT_CONVERT_SWORDSMAN.getString(),
                                () -> sendConvertRecruit(com.talhanation.bannermod.entity.military.runtime.RecruitTypeConverter.Kind.SWORDSMAN),
                                !(recruit.getClass() == com.talhanation.bannermod.entity.military.RecruitEntity.class)),
                        new ContextMenuEntry(TEXT_CONVERT_BOWMAN.getString(),
                                () -> sendConvertRecruit(com.talhanation.bannermod.entity.military.runtime.RecruitTypeConverter.Kind.BOWMAN),
                                !(recruit.getClass() == com.talhanation.bannermod.entity.military.BowmanEntity.class)),
                        new ContextMenuEntry(TEXT_CONVERT_PIKEMAN.getString(),
                                () -> sendConvertRecruit(com.talhanation.bannermod.entity.military.runtime.RecruitTypeConverter.Kind.PIKEMAN),
                                !(recruit.getClass() == com.talhanation.bannermod.entity.military.RecruitShieldmanEntity.class)),
                        new ContextMenuEntry(TEXT_CONVERT_CROSSBOWMAN.getString(),
                                () -> sendConvertRecruit(com.talhanation.bannermod.entity.military.runtime.RecruitTypeConverter.Kind.CROSSBOWMAN),
                                !(recruit.getClass() == com.talhanation.bannermod.entity.military.CrossBowmanEntity.class)),
                        new ContextMenuEntry(TEXT_CONVERT_CAVALRY.getString(),
                                () -> sendConvertRecruit(com.talhanation.bannermod.entity.military.runtime.RecruitTypeConverter.Kind.CAVALRY),
                                !(recruit.getClass() == com.talhanation.bannermod.entity.military.HorsemanEntity.class))
                ));
                convertMenu.setTooltip(Tooltip.create(TOOLTIP_CONVERT));
                addRenderableWidget(convertMenu);
            }
        }
    }

    private void sendConvertRecruit(com.talhanation.bannermod.entity.military.runtime.RecruitTypeConverter.Kind kind) {
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageConvertRecruitType(this.recruit.getUUID(), kind));
        this.onClose();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateCombatStanceButton();
        if(ClientManager.groups != null && !ClientManager.groups.isEmpty() && !buttonsSet){
            this.currentGroup = ClientManager.getGroup(recruit.getGroup());

            groupSelectionDropDownMenu = new ScrollDropDownMenu<>(currentGroup, leftPos + 178, topPos + 130,  110, 14, ClientManager.groups,
                RecruitsGroup::getName,
                (selected) ->{
                    this.currentGroup = selected;
                    BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageGroup(currentGroup.getUUID(), recruit.getUUID()));
                }
            );
            groupSelectionDropDownMenu.setBgFillSelected(FastColor.ARGB32.color(255, 139, 139, 139));
            groupSelectionDropDownMenu.visible = Minecraft.getInstance().player.getUUID().equals(recruit.getOwnerUUID());
            RecruitsGroup group = ClientManager.getGroup(recruit.getGroup());
            groupSelectionDropDownMenu.canSelect = group == null || recruit.getGroup() == null || !recruit.getUUID().equals(group.leaderUUID);
            addRenderableWidget(groupSelectionDropDownMenu);
            this.buttonsSet = true;
        }
    }

    @Override
    public void mouseMoved(double x, double y) {
        if(groupSelectionDropDownMenu != null){
            groupSelectionDropDownMenu.onMouseMove(x,y);
        }
        super.mouseMoved(x, y);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (groupSelectionDropDownMenu != null && groupSelectionDropDownMenu.isMouseOver(mouseX, mouseY)) {
            groupSelectionDropDownMenu.onMouseClick(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double d) {
        if(groupSelectionDropDownMenu != null) groupSelectionDropDownMenu.mouseScrolled(x, y, scrollX, d);
        return super.mouseScrolled(x, y, scrollX, d);
    }
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        if (groupSelectionDropDownMenu != null) {
            groupSelectionDropDownMenu.renderWidget(guiGraphics, mouseX, mouseY, partialTicks);
        }
    }
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);
        int health = Mth.ceil(recruit.getHealth());
        int hunger = Mth.ceil(recruit.getHunger());
        int moral = Mth.ceil(recruit.getMorale());
        this.follow = recruit.getFollowState();
        this.aggro = recruit.getState();

        guiGraphics.drawCenteredString(font, recruit.getDisplayName(), this.imageWidth / 2, 12, MilitaryGuiStyle.TEXT);
        guiGraphics.drawString(font, SECTION_DISCIPLINE, 16, 28, MilitaryGuiStyle.TEXT_DARK, false);
        guiGraphics.drawString(font, SECTION_DETAILS, 210, 28, MilitaryGuiStyle.TEXT_DARK, false);
        guiGraphics.drawString(font, SECTION_ORDERS, 316, 28, MilitaryGuiStyle.TEXT_DARK, false);
        guiGraphics.drawString(font, playerInventory.getDisplayName().getVisualOrderText(), 118, 168, MilitaryGuiStyle.TEXT_DARK, false);

        guiGraphics.drawString(font, Component.literal("HP " + health + "   LV " + recruit.getXpLevel()), 214, 50, MilitaryGuiStyle.TEXT_DARK, false);
        guiGraphics.drawString(font, Component.literal("XP " + recruit.getXp() + "   KL " + recruit.getKills()), 214, 62, MilitaryGuiStyle.TEXT_DARK, false);
        guiGraphics.drawString(font, Component.literal("MR " + moral + "   HG " + hunger), 214, 74, MilitaryGuiStyle.TEXT_DARK, false);
        renderFirearmStatus(guiGraphics, 214, 88);
        MilitaryGuiStyle.drawBadge(guiGraphics, font, orderStatusLine(), 210, 120, 82, orderStatusColor());
        // command
        String follow = switch (this.follow) {
            case 0 -> TEXT_INFO_WANDER.getString();
            case 1 -> TEXT_INFO_FOLLOW.getString();
            case 2, 3, 4 -> TEXT_INFO_HOLD_POS.getString();
            case 5 -> TEXT_INFO_PROTECT.getString();
            case 6 -> TEXT_INFO_WORKING.getString();
            default -> throw new IllegalStateException("Unexpected value: " + this.follow);
        };
        guiGraphics.drawString(font, follow, 184, 126, MilitaryGuiStyle.TEXT_DARK, false);


        String aggro = switch (this.aggro) {
            case 0 -> TEXT_INFO_NEUTRAL.getString();
            case 1 -> TEXT_INFO_AGGRESSIVE.getString();
            case 2 -> TEXT_INFO_RAID.getString();
            case 3 -> TEXT_INFO_PASSIVE.getString();
            default -> throw new IllegalStateException("Unexpected value: " + this.aggro);
        };

        int fnt = this.aggro == 3 ? MilitaryGuiStyle.TEXT_WARN : MilitaryGuiStyle.TEXT_DARK;
        guiGraphics.drawString(font, aggro, 184, 138, fnt, false);

        String listen;
        if (recruit.getListen()) listen = TEXT_INFO_LISTEN.getString();
        else listen = TEXT_INFO_IGNORE.getString();

        int fnt2 = recruit.getListen() ? MilitaryGuiStyle.TEXT_DARK : MilitaryGuiStyle.TEXT_WARN;
        guiGraphics.drawCenteredString(font, Component.literal(listen), 226, 132, fnt2);

        ItemStack profItem1 = null;
        ItemStack profItem2 = null;
        if(this.recruit instanceof HorsemanEntity){
            profItem1 = Items.IRON_SWORD.getDefaultInstance();
            profItem2 = Items.SADDLE.getDefaultInstance();
        }
        else if(this.recruit instanceof NomadEntity){
            profItem1 = Items.BOW.getDefaultInstance();
            profItem2 = Items.SADDLE.getDefaultInstance();
        }
        else if(this.recruit instanceof RecruitShieldmanEntity){
            profItem1 = Items.IRON_SWORD.getDefaultInstance();
            profItem2 = Items.SHIELD.getDefaultInstance();
        }
        else if(this.recruit instanceof RecruitEntity){
            profItem1 = Items.IRON_SWORD.getDefaultInstance();
        }
        else if(this.recruit instanceof BowmanEntity){
            profItem1 = Items.BOW.getDefaultInstance();
        }
        else if(this.recruit instanceof CrossBowmanEntity){
            profItem1 = Items.CROSSBOW.getDefaultInstance();
        }
        else if(this.recruit instanceof MessengerEntity){
            profItem1 = Items.FEATHER.getDefaultInstance();
            profItem2 = Items.PAPER.getDefaultInstance();
        }
        else if(this.recruit instanceof CommanderEntity){
            profItem1 = Items.IRON_SWORD.getDefaultInstance();
            profItem2 = Items.GOAT_HORN.getDefaultInstance();
        }
        else if(this.recruit instanceof CaptainEntity){
            profItem1 = SmallShips.getSmallShipsItem();
        }
        else if (this instanceof IVillagerWorker worker){
            profItem1 = worker.getCustomProfessionItem();
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(0.8F, 0.8F, 1F);

        if(profItem2 != null){
            guiGraphics.renderFakeItem(profItem2, 268, 28);
        }

        if(profItem1 != null){
            guiGraphics.renderFakeItem(profItem1, 248, 28);
        }
        guiGraphics.pose().popPose();
    }

    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.clearColor(1.0F, 1.0F, 1.0F, 1.0F);
        int i = this.leftPos;
        int j = this.topPos;

        guiGraphics.fill(0, 0, this.width, this.height, 0x54160E08);
        MilitaryGuiStyle.parchmentPanel(guiGraphics, i, j, this.imageWidth, this.imageHeight);
        MilitaryGuiStyle.titleStrip(guiGraphics, i + 10, j + 8, this.imageWidth - 20, 16);

        MilitaryGuiStyle.parchmentPanel(guiGraphics, i + 8, j + 18, 92, 232);
        MilitaryGuiStyle.parchmentPanel(guiGraphics, i + 106, j + 18, 194, 148);
        MilitaryGuiStyle.parchmentPanel(guiGraphics, i + 306, j + 18, 98, 232);
        MilitaryGuiStyle.parchmentPanel(guiGraphics, i + 106, j + 172, 176, 82);

        MilitaryGuiStyle.insetPanel(guiGraphics, i + 114, j + 46, 18, 18 * 5);
        MilitaryGuiStyle.parchmentInset(guiGraphics, i + 136, j + 46, 68, 98);
        MilitaryGuiStyle.parchmentInset(guiGraphics, i + 210, j + 46, 82, 24);
        MilitaryGuiStyle.parchmentInset(guiGraphics, i + 210, j + 74, 82, 40);
        MilitaryGuiStyle.parchmentInset(guiGraphics, i + 210, j + 120, 82, 24);

        renderGeneratedSlotFrames(guiGraphics, i + 222, j + 74, 3, 3);
        renderGeneratedSlotFrames(guiGraphics, i + 118, j + 180, 9, 3);
        renderGeneratedSlotFrames(guiGraphics, i + 118, j + 238, 9, 1);

        renderProfileOrnaments(guiGraphics, i, j);
        InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics, i + 138, j + 52, i + 202, j + 140, 34, 0.0F,
                (float) (i + 170) - mouseX, (float) (j + 74) - mouseY, this.recruit);
    }

    private void renderGeneratedSlotFrames(GuiGraphics guiGraphics, int x, int y, int cols, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                MilitaryGuiStyle.insetPanel(guiGraphics, x + col * 18, y + row * 18, 18, 18);
            }
        }
    }

    private void renderProfileOrnaments(GuiGraphics guiGraphics, int x, int y) {
        renderWaxSeal(guiGraphics, x + 18, y + 26, 0xFF8E2E24);
        renderWaxSeal(guiGraphics, x + this.imageWidth - 30, y + 26, 0xFF2F6E2E);
        renderDividerFlourish(guiGraphics, x + 118, y + 34, 160);
        renderDividerFlourish(guiGraphics, x + 118, y + 164, 160);
    }

    private void renderWaxSeal(GuiGraphics guiGraphics, int x, int y, int wax) {
        guiGraphics.fill(x, y, x + 12, y + 12, wax);
        guiGraphics.renderOutline(x, y, 12, 12, 0xFF24150D);
        guiGraphics.fill(x + 3, y + 2, x + 9, y + 3, 0xFFE0B86A);
        guiGraphics.fill(x + 2, y + 5, x + 10, y + 6, 0xFFE0B86A);
        guiGraphics.fill(x + 4, y + 8, x + 8, y + 9, 0xFFE0B86A);
    }

    private void renderDividerFlourish(GuiGraphics guiGraphics, int x, int y, int width) {
        guiGraphics.fill(x, y, x + width, y + 1, 0xAA8A6A3A);
        guiGraphics.fill(x + width / 2 - 1, y - 2, x + width / 2 + 1, y + 3, 0xFFE0B86A);
        guiGraphics.fill(x + 10, y - 1, x + 16, y + 2, 0x665A4025);
        guiGraphics.fill(x + width - 16, y - 1, x + width - 10, y + 2, 0x665A4025);
    }

    private void updateCombatStanceButton() {
        if (this.stanceButton == null) {
            return;
        }
        CombatStance stance = this.recruit.getCombatStance();
        this.stanceButton.setMessage(stanceLabel(stance));
    }

    private static CombatStance nextCombatStance(CombatStance stance) {
        CombatStance[] stances = CombatStance.values();
        int index = stance == null ? 0 : stance.ordinal();
        return stances[(index + 1) % stances.length];
    }

    private void renderFirearmStatus(GuiGraphics guiGraphics, int x, int y) {
        if (!(this.recruit instanceof CrossBowmanEntity)) {
            return;
        }

        BannerModRecruitFirearmStatus.FirearmInspection status = BannerModRecruitFirearmStatus.inspect(this.recruit.getMainHandItem(), this.recruit.getInventory());
        if (!status.visible()) {
            return;
        }

        Component firearmValue = status.state() == BannerModRecruitFirearmStatus.FirearmState.UNSUPPORTED
                ? TEXT_FIREARM_UNSUPPORTED
                : TEXT_FIREARM_SUPPORTED;
        int firearmColor = switch (status.state()) {
            case SUPPORTED -> firearmSupportedColor;
            case MISSING_AMMO -> firearmWarningColor;
            case UNSUPPORTED -> firearmUnsupportedColor;
            case NONE -> fontColor;
        };
        guiGraphics.drawString(font, Component.translatable("gui.recruits.inv.info.firearm_status", firearmValue), x, y, firearmColor, false);

        if (status.state() == BannerModRecruitFirearmStatus.FirearmState.UNSUPPORTED) {
            return;
        }

        Component ammoValue = status.hasAmmo()
                ? Component.translatable("gui.recruits.inv.info.firearm_ammo_count", status.ammoCount())
                : TEXT_FIREARM_AMMO_MISSING;
        int ammoColor = status.hasAmmo() ? firearmSupportedColor : firearmWarningColor;
        guiGraphics.drawString(font, Component.translatable("gui.recruits.inv.info.ammo_status", ammoValue), x, y + 10, ammoColor, false);
    }

    private Component orderStatusLine() {
        if (this.playerInventory.player == null
                || this.recruit.getOwnerUUID() == null
                || !this.recruit.getOwnerUUID().equals(this.playerInventory.player.getUUID())) {
            return STATUS_READ_ONLY;
        }

        RecruitsGroup selectedGroup = selectedGroup();
        if (selectedGroup == null) {
            return STATUS_GROUP_UNSET;
        }
        if (selectedGroup.leaderUUID != null && this.recruit.getUUID().equals(selectedGroup.leaderUUID)) {
            return STATUS_GROUP_LOCKED;
        }

        BannerModRecruitFirearmStatus.FirearmInspection firearm = BannerModRecruitFirearmStatus.inspect(this.recruit.getMainHandItem(), this.recruit.getInventory());
        if (firearm.visible()) {
            if (firearm.state() == BannerModRecruitFirearmStatus.FirearmState.UNSUPPORTED) {
                return STATUS_FIREARM_UNSUPPORTED;
            }
            if (firearm.state() == BannerModRecruitFirearmStatus.FirearmState.MISSING_AMMO) {
                return STATUS_FIREARM_AMMO_MISSING;
            }
        }

        if (this.canPromote) {
            return STATUS_PROMOTE_READY;
        }
        return STATUS_READY;
    }

    private int orderStatusColor() {
        Component statusLine = orderStatusLine();
        if (statusLine == STATUS_READ_ONLY || statusLine == STATUS_FIREARM_UNSUPPORTED) {
            return MilitaryGuiStyle.TEXT_DENIED;
        }
        if (statusLine == STATUS_GROUP_UNSET || statusLine == STATUS_GROUP_LOCKED || statusLine == STATUS_FIREARM_AMMO_MISSING) {
            return MilitaryGuiStyle.TEXT_WARN;
        }
        if (statusLine == STATUS_PROMOTE_READY) {
            return MilitaryGuiStyle.TEXT_GOOD;
        }
        return MilitaryGuiStyle.TEXT;
    }

    private RecruitsGroup selectedGroup() {
        if (this.currentGroup != null) {
            return this.currentGroup;
        }
        return ClientManager.getGroup(this.recruit.getGroup());
    }

    private static Component stanceLabel(CombatStance stance) {
        return switch (stance == null ? CombatStance.LOOSE : stance) {
            case LINE_HOLD -> Component.translatable("gui.recruits.command.text.stance_line_hold");
            case SHIELD_WALL -> Component.translatable("gui.recruits.command.text.stance_shield_wall");
            case LOOSE -> Component.translatable("gui.recruits.command.text.stance_loose");
        };
    }

    private static class ProfileButton extends ExtendedButton {
        protected ProfileButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            MilitaryGuiStyle.commandButton(graphics, Minecraft.getInstance().font, mouseX, mouseY,
                    getX(), getY(), width, height, getMessage(), this.active, this.isHoveredOrFocused());
        }
    }
}
