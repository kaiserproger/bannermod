package com.talhanation.bannermod.client.military.gui.war;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.military.ClientManager;
import com.talhanation.bannermod.client.military.gui.MilitaryGuiStyle;
import com.talhanation.bannermod.client.military.gui.widgets.ScrollDropDownMenu;
import com.talhanation.bannermod.network.messages.war.MessageUpdateCoLeader;
import com.talhanation.bannermod.persistence.military.RecruitsPlayerInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class PoliticalEntityCoLeaderPickerScreen extends Screen {
    private static final int W = 292;
    private static final int H = 172;

    private final Screen parent;
    private final UUID entityId;
    private final UUID leaderUuid;
    private final List<UUID> existingCoLeaders;
    private final List<RecruitsPlayerInfo> candidates = new ArrayList<>();
    private int guiLeft;
    private int guiTop;
    private int lastOnlinePlayersVersion = -1;
    private ScrollDropDownMenu<RecruitsPlayerInfo> playerDropdown;
    private Button grantButton;
    @Nullable
    private RecruitsPlayerInfo selectedPlayer;

    public PoliticalEntityCoLeaderPickerScreen(Screen parent, UUID entityId, @Nullable UUID leaderUuid, List<UUID> existingCoLeaders) {
        super(Component.translatable("gui.bannermod.states.dialog.co_leader_add.title"));
        this.parent = parent;
        this.entityId = entityId;
        this.leaderUuid = leaderUuid;
        this.existingCoLeaders = existingCoLeaders == null ? List.of() : List.copyOf(existingCoLeaders);
    }

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - W) / 2;
        this.guiTop = Math.max(8, (this.height - H) / 2);
        rebuildCandidates();

        this.playerDropdown = new ScrollDropDownMenu<>(this.selectedPlayer, guiLeft + 16, guiTop + 72, W - 32, 20, this.candidates,
                this::displayName,
                selected -> {
                    this.selectedPlayer = selected;
                    updateButtons();
                });
        this.playerDropdown.setBgFill(FastColor.ARGB32.color(255, 66, 50, 34));
        this.playerDropdown.setBgFillHovered(FastColor.ARGB32.color(255, 104, 79, 54));
        this.playerDropdown.setBgFillSelected(FastColor.ARGB32.color(255, 50, 37, 24));
        this.playerDropdown.setDisplayColor(MilitaryGuiStyle.TEXT);
        this.playerDropdown.setOptionTextColor(MilitaryGuiStyle.TEXT);
        addRenderableWidget(this.playerDropdown);

        this.grantButton = addRenderableWidget(Button.builder(Component.translatable("gui.bannermod.states.dialog.co_leader.select"), button -> submitSelected())
                .bounds(guiLeft + 16, guiTop + H - 28, 82, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.bannermod.states.dialog.co_leader.manual"), button -> openManualEntry())
                .bounds(guiLeft + 105, guiTop + H - 28, 88, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.bannermod.common.back"), button -> onClose())
                .bounds(guiLeft + W - 98, guiTop + H - 28, 82, 20)
                .build());
        updateButtons();
    }

    private void rebuildCandidates() {
        this.candidates.clear();
        this.lastOnlinePlayersVersion = ClientManager.onlinePlayersVersion;
        for (RecruitsPlayerInfo playerInfo : ClientManager.onlinePlayers) {
            if (playerInfo == null || playerInfo.getUUID() == null) {
                continue;
            }
            if (playerInfo.getUUID().equals(this.leaderUuid) || this.existingCoLeaders.contains(playerInfo.getUUID())) {
                continue;
            }
            this.candidates.add(copyInfo(playerInfo));
        }
        this.candidates.sort(Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER));
        if (this.selectedPlayer != null && this.selectedPlayer.getUUID() != null) {
            this.selectedPlayer = this.candidates.stream()
                    .filter(candidate -> candidate.getUUID().equals(this.selectedPlayer.getUUID()))
                    .findFirst()
                    .orElse(null);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.lastOnlinePlayersVersion != ClientManager.onlinePlayersVersion) {
            this.init();
        }
    }

    private void submitSelected() {
        if (this.selectedPlayer == null || this.selectedPlayer.getUUID() == null) {
            return;
        }
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageUpdateCoLeader(this.entityId, this.selectedPlayer.getUUID().toString(), true));
        this.minecraft.setScreen(this.parent);
    }

    private void openManualEntry() {
        this.minecraft.setScreen(new PoliticalEntityNameInputScreen(
                this.parent,
                Component.translatable("gui.bannermod.states.dialog.co_leader_add.title"),
                Component.translatable("gui.bannermod.states.dialog.co_leader.prompt"),
                "",
                value -> BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageUpdateCoLeader(this.entityId, value, true)),
                36,
                false
        ));
    }

    private void updateButtons() {
        if (this.grantButton != null) {
            boolean hasSelection = this.selectedPlayer != null && this.selectedPlayer.getUUID() != null;
            this.grantButton.active = hasSelection;
            this.grantButton.setTooltip(hasSelection ? net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.bannermod.states.dialog.co_leader.select.tooltip"))
                    : net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.bannermod.states.dialog.co_leader.empty")));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);
        MilitaryGuiStyle.parchmentPanel(graphics, guiLeft, guiTop, W, H);
        MilitaryGuiStyle.titleStrip(graphics, guiLeft + 8, guiTop + 8, W - 16, 14);
        MilitaryGuiStyle.parchmentInset(graphics, guiLeft + 10, guiTop + 24, W - 20, 34);
        MilitaryGuiStyle.insetPanel(graphics, guiLeft + 14, guiTop + 70, W - 28, 24);

        MilitaryGuiStyle.drawCenteredTitle(graphics, font, title, guiLeft, guiTop + 11, W);
        drawWrapped(graphics, Component.translatable("gui.bannermod.states.dialog.co_leader.subtitle"), guiLeft + 16, guiTop + 32, W - 32, MilitaryGuiStyle.TEXT_DARK);
        graphics.drawString(font, Component.translatable("gui.bannermod.states.dialog.co_leader.online"), guiLeft + 16, guiTop + 60, MilitaryGuiStyle.TEXT_MUTED, false);
        drawWrapped(graphics,
                candidates.isEmpty()
                        ? Component.translatable("gui.bannermod.states.dialog.co_leader.empty")
                        : Component.translatable("gui.bannermod.states.dialog.co_leader.selected", displayName(this.selectedPlayer)),
                guiLeft + 16,
                guiTop + 104,
                W - 32,
                MilitaryGuiStyle.TEXT_MUTED);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(text, width);
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.playerDropdown != null && this.playerDropdown.isMouseOver(mouseX, mouseY)) {
            this.playerDropdown.onMouseClick(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        if (this.playerDropdown != null) {
            this.playerDropdown.onMouseMove(mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        if (this.playerDropdown != null && this.playerDropdown.isMouseOver(mouseX, mouseY) && this.playerDropdown.mouseScrolled(mouseX, mouseY, scrollX, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.playerDropdown != null && this.playerDropdown.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private String displayName(@Nullable RecruitsPlayerInfo playerInfo) {
        if (playerInfo == null) {
            return Component.translatable("gui.bannermod.common.none").getString();
        }
        if (playerInfo.getName() != null && !playerInfo.getName().isBlank()) {
            return playerInfo.getName();
        }
        return playerInfo.getUUID() == null ? Component.translatable("gui.bannermod.common.none").getString() : playerInfo.getUUID().toString();
    }

    private RecruitsPlayerInfo copyInfo(RecruitsPlayerInfo playerInfo) {
        RecruitsPlayerInfo copy = new RecruitsPlayerInfo(playerInfo.getUUID(), playerInfo.getName());
        copy.setOnline(playerInfo.isOnline());
        return copy;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
