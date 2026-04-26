package com.talhanation.bannermod.client.military.gui.war;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.network.messages.war.MessageCancelAllyInvite;
import com.talhanation.bannermod.network.messages.war.MessageInviteAlly;
import com.talhanation.bannermod.network.messages.war.MessageRespondAllyInvite;
import com.talhanation.bannermod.war.client.WarClientState;
import com.talhanation.bannermod.war.registry.PoliticalEntityRecord;
import com.talhanation.bannermod.war.runtime.WarAllyInviteRecord;
import com.talhanation.bannermod.war.runtime.WarDeclarationRecord;
import com.talhanation.bannermod.war.runtime.WarSide;
import com.talhanation.bannermod.war.runtime.WarState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * War Room → Allies subscreen for one selected war.
 *
 * <p>Lists current allies of each side and pending invites. The local player gets
 * Accept/Decline on invites where they lead the invited entity, Cancel on invites
 * they issued, and an "Invite ally" button if they lead one of the war's main
 * sides and the war is still pre-active.</p>
 */
public class WarAlliesScreen extends Screen {
    private static final int W = 380;
    private static final int H = 252;
    private static final int ROW_H = 16;
    private static final int LIST_VISIBLE = 9;

    private final Screen parent;
    private final UUID warId;
    private int guiLeft;
    private int guiTop;
    private int scrollOffset = 0;
    private List<Row> rows = List.of();

    private Button inviteAttackerBtn;
    private Button inviteDefenderBtn;
    private Button refreshBtn;
    private Button closeBtn;

    public WarAlliesScreen(@Nullable Screen parent, UUID warId) {
        super(Component.literal("War Allies"));
        this.parent = parent;
        this.warId = warId;
    }

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - W) / 2;
        this.guiTop = (this.height - H) / 2;

        rebuildRows();

        int btnY = guiTop + H - 28;
        inviteAttackerBtn = Button.builder(Component.literal("Invite to Attacker"),
                btn -> openInvitePicker(WarSide.ATTACKER))
                .bounds(guiLeft + 8, btnY, 110, 18).build();
        inviteDefenderBtn = Button.builder(Component.literal("Invite to Defender"),
                btn -> openInvitePicker(WarSide.DEFENDER))
                .bounds(guiLeft + 122, btnY, 110, 18).build();
        refreshBtn = Button.builder(Component.literal("Refresh"), btn -> rebuildRows())
                .bounds(guiLeft + 240, btnY, 60, 18).build();
        closeBtn = Button.builder(Component.literal("Back"), btn -> onClose())
                .bounds(guiLeft + 304, btnY, 68, 18).build();

        addRenderableWidget(inviteAttackerBtn);
        addRenderableWidget(inviteDefenderBtn);
        addRenderableWidget(refreshBtn);
        addRenderableWidget(closeBtn);

        updateInviteButtons();
    }

    private void rebuildRows() {
        WarDeclarationRecord war = currentWar();
        List<Row> rebuilt = new ArrayList<>();
        if (war == null) {
            this.rows = rebuilt;
            return;
        }
        for (UUID allyId : war.attackerAllyIds()) {
            rebuilt.add(Row.ally(WarSide.ATTACKER, allyId));
        }
        for (UUID allyId : war.defenderAllyIds()) {
            rebuilt.add(Row.ally(WarSide.DEFENDER, allyId));
        }
        for (WarAllyInviteRecord invite : WarClientState.allyInvitesForWar(warId)) {
            rebuilt.add(Row.invite(invite));
        }
        this.rows = rebuilt;
        scrollOffset = 0;
        updateInviteButtons();
    }

    private void updateInviteButtons() {
        WarDeclarationRecord war = currentWar();
        boolean preActive = war != null && war.state() == WarState.DECLARED;
        UUID local = localPlayerUuid();
        if (inviteAttackerBtn != null) {
            inviteAttackerBtn.active = preActive && war != null
                    && isLeaderOf(war.attackerPoliticalEntityId(), local);
        }
        if (inviteDefenderBtn != null) {
            inviteDefenderBtn.active = preActive && war != null
                    && isLeaderOf(war.defenderPoliticalEntityId(), local);
        }
    }

    private void openInvitePicker(WarSide side) {
        WarDeclarationRecord war = currentWar();
        if (war == null) return;
        Minecraft.getInstance().setScreen(new WarAllyInvitePickerScreen(this, war, side));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(guiLeft, guiTop, guiLeft + W, guiTop + H, 0xC0101010);
        graphics.renderOutline(guiLeft, guiTop, W, H, 0xFFFFFFFF);

        WarDeclarationRecord war = currentWar();
        String header = war == null
                ? "War not found"
                : "Allies — " + entityName(war.attackerPoliticalEntityId())
                        + " vs " + entityName(war.defenderPoliticalEntityId())
                        + "  [" + war.state().name() + "]";
        graphics.drawCenteredString(font, header, guiLeft + W / 2, guiTop + 6, 0xFFFFFF);

        renderList(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = guiLeft + 8;
        int listY = guiTop + 24;
        int listW = W - 16;
        int listH = LIST_VISIBLE * ROW_H;
        graphics.fill(listX, listY, listX + listW, listY + listH, 0x60000000);

        if (rows.isEmpty()) {
            graphics.drawCenteredString(font, "No allies, no invites.",
                    listX + listW / 2, listY + listH / 2 - 4, 0xAAAAAA);
            return;
        }

        int rendered = Math.min(LIST_VISIBLE, Math.max(0, rows.size() - scrollOffset));
        for (int i = 0; i < rendered; i++) {
            Row row = rows.get(scrollOffset + i);
            int rowY = listY + i * ROW_H;
            graphics.drawString(font,
                    font.plainSubstrByWidth(row.label(this), listW - 8),
                    listX + 4, rowY + 4, row.color(), false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            int listX = guiLeft + 8;
            int listY = guiTop + 24;
            int listW = W - 16;
            int listH = LIST_VISIBLE * ROW_H;
            if (mouseX >= listX && mouseX < listX + listW
                    && mouseY >= listY && mouseY < listY + listH) {
                int idx = scrollOffset + (int) ((mouseY - listY) / ROW_H);
                if (idx >= 0 && idx < rows.size()) {
                    Row row = rows.get(idx);
                    WarAllyInviteRecord invite = row.invite();
                    if (invite != null) {
                        if (button == 1) {
                            return declineIfAddressedToMe(invite);
                        }
                        triggerInviteAction(invite);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void triggerInviteAction(WarAllyInviteRecord invite) {
        UUID local = localPlayerUuid();
        if (isLeaderOf(invite.inviteePoliticalEntityId(), local)) {
            BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageRespondAllyInvite(invite.id(), true));
            rebuildRows();
            return;
        }
        UUID sideEntityId = currentWar() == null ? null : currentWar().mainSideEntityId(invite.side());
        if (isLeaderOf(sideEntityId, local)) {
            BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageCancelAllyInvite(invite.id()));
            rebuildRows();
        }
    }

    private boolean declineIfAddressedToMe(WarAllyInviteRecord invite) {
        if (!isLeaderOf(invite.inviteePoliticalEntityId(), localPlayerUuid())) {
            return false;
        }
        BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageRespondAllyInvite(invite.id(), false));
        rebuildRows();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 261 /* DELETE */ || key == 259 /* BACKSPACE */) {
            // shortcut: decline the topmost invite addressed to me
            UUID local = localPlayerUuid();
            for (Row row : rows) {
                WarAllyInviteRecord invite = row.invite();
                if (invite != null && isLeaderOf(invite.inviteePoliticalEntityId(), local)) {
                    BannerModMain.SIMPLE_CHANNEL.sendToServer(new MessageRespondAllyInvite(invite.id(), false));
                    rebuildRows();
                    return true;
                }
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, rows.size() - LIST_VISIBLE);
        scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public void onClose() {
        if (parent != null) {
            this.minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Nullable
    private WarDeclarationRecord currentWar() {
        for (WarDeclarationRecord war : WarClientState.wars()) {
            if (war.id().equals(warId)) return war;
        }
        return null;
    }

    private String entityName(@Nullable UUID id) {
        if (id == null) return "(unknown)";
        PoliticalEntityRecord entity = WarClientState.entityById(id);
        if (entity == null || entity.name().isBlank()) return shortId(id);
        return entity.name();
    }

    private static String shortId(UUID id) {
        if (id == null) return "?";
        String s = id.toString();
        return s.length() > 8 ? s.substring(0, 8) : s;
    }

    @Nullable
    private static UUID localPlayerUuid() {
        Player player = Minecraft.getInstance().player;
        return player == null ? null : player.getUUID();
    }

    private static boolean isLeaderOf(@Nullable UUID entityId, @Nullable UUID playerUuid) {
        if (entityId == null || playerUuid == null) return false;
        PoliticalEntityRecord entity = WarClientState.entityById(entityId);
        if (entity == null) return false;
        UUID leader = entity.leaderUuid();
        return leader != null && leader.equals(playerUuid);
    }

    /** Either a confirmed ally row or a pending invite row. */
    private record Row(WarSide side, @Nullable UUID allyEntityId, @Nullable WarAllyInviteRecord invite) {
        static Row ally(WarSide side, UUID id) {
            return new Row(side, id, null);
        }

        static Row invite(WarAllyInviteRecord record) {
            return new Row(record.side(), null, record);
        }

        String label(WarAlliesScreen screen) {
            if (invite != null) {
                UUID local = WarAlliesScreen.localPlayerUuid();
                String name = screen.entityName(invite.inviteePoliticalEntityId());
                String tag;
                if (WarAlliesScreen.isLeaderOf(invite.inviteePoliticalEntityId(), local)) {
                    tag = " (left-click: accept · right-click / DEL: decline)";
                } else if (WarAlliesScreen.isLeaderOf(
                        screen.currentWar() == null
                                ? null
                                : screen.currentWar().mainSideEntityId(side),
                        local)) {
                    tag = " (click to cancel)";
                } else {
                    tag = "";
                }
                return "[INVITE " + side.name() + "] " + name + tag;
            }
            return "[ALLY " + side.name() + "] " + screen.entityName(allyEntityId);
        }

        int color() {
            if (invite != null) return 0xFFFFD24A;
            return side == WarSide.ATTACKER ? 0xFFFF8888 : 0xFF99CCFF;
        }
    }
}
