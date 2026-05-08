package com.talhanation.bannermod.registry;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.perks.PerkProgress;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BannerModMain.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PerkProgress>> PLAYER_PERKS =
            ATTACHMENT_TYPES.register("player_perks", () -> AttachmentType.serializable(PerkProgress::new).build());

    private ModAttachments() {
    }
}
