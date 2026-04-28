package com.talhanation.bannermod.client.military.render;
import com.mojang.blaze3d.vertex.PoseStack;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.military.events.ClientEvent;
import com.talhanation.bannermod.client.military.models.RecruitHumanModel;
import com.talhanation.bannermod.client.military.render.layer.RecruitHumanBiomeLayer;
import com.talhanation.bannermod.client.military.render.layer.RecruitHumanCompanionLayer;
import com.talhanation.bannermod.client.military.render.layer.RecruitHumanTeamColorLayer;
import com.talhanation.bannermod.client.military.render.layer.RecruitLodArmorLayer;
import com.talhanation.bannermod.client.military.render.layer.RecruitLodCustomHeadLayer;
import com.talhanation.bannermod.client.military.render.layer.RecruitLodItemInHandLayer;
import com.talhanation.bannermod.compat.IWeapon;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.CrossBowmanEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;

public class RecruitHumanRenderer extends MobRenderer<AbstractRecruitEntity, HumanoidModel<AbstractRecruitEntity>> {

    private static final ResourceLocation[] TEXTURE = {
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_0.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_1.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_2.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_3.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_4.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_5.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_6.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_7.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_8.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_9.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_10.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_11.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_12.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_13.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_14.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_15.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_16.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_17.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_18.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/human_19.png")
    };

    @Override
    public ResourceLocation getTextureLocation(AbstractRecruitEntity recruit) {
        RecruitRenderProfiling.textureStateSwitch("base_model");
        return TEXTURE[recruit.getVariant()];
    }

    public static ResourceLocation crowdTexture(AbstractRecruitEntity recruit) {
        return TEXTURE[recruit.getVariant()];
    }

    public RecruitHumanRenderer(EntityRendererProvider.Context mgr) {
        super(mgr, new RecruitHumanModel(mgr.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new RecruitLodArmorLayer(this, new HumanoidModel<>(mgr.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), new HumanoidModel<>(mgr.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), mgr.getModelManager()));
        this.addLayer(new RecruitHumanTeamColorLayer(this));
        this.addLayer(new RecruitHumanBiomeLayer(this));
        this.addLayer(new RecruitHumanCompanionLayer(this));
        //this.addLayer(new ArrowLayer<>(mgr, this));
        this.addLayer(new RecruitLodItemInHandLayer<>(this, mgr.getItemInHandRenderer()));
        this.addLayer(new RecruitLodCustomHeadLayer<>(this, mgr.getModelSet(), mgr.getItemInHandRenderer()));

    }


    public void render(AbstractRecruitEntity recruit, float p_117789_, float p_117790_, PoseStack p_117791_, MultiBufferSource p_117792_, int p_117793_) {
        long poseStart = RecruitRenderProfiling.start();
        this.setModelProperties(recruit);
        RecruitRenderProfiling.duration("animation_pose", poseStart);
        RecruitRenderProfiling.beginNormalRender();
        long renderStart = RecruitRenderProfiling.start();
        super.render(recruit, p_117789_, p_117790_, p_117791_, p_117792_, p_117793_);
        RecruitRenderProfiling.endNormalRender(renderStart);
    }

    @Override
    protected boolean shouldShowName(AbstractRecruitEntity recruit) {
        boolean showName = RecruitRenderLod.shouldRenderName(recruit) && super.shouldShowName(recruit);
        if (showName) {
            RecruitRenderProfiling.increment("nameplates.visible");
        } else {
            RecruitRenderProfiling.skipped("nameplates");
        }
        return showName;
    }

    @Override
    protected void renderNameTag(AbstractRecruitEntity recruit, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        long start = RecruitRenderProfiling.start();
        super.renderNameTag(recruit, displayName, poseStack, bufferSource, packedLight);
        RecruitRenderProfiling.duration("nameplates", start);
    }

    private void setModelProperties(AbstractRecruitEntity recruit) {
        HumanoidModel<AbstractRecruitEntity> model = this.getModel();

        model.setAllVisible(true);
        model.crouching = recruit.isCrouching();
        HumanoidModel.ArmPose humanoidmodel$armpose = getArmPose(recruit, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose humanoidmodel$armpose1 = getArmPose(recruit, InteractionHand.OFF_HAND);
        if (humanoidmodel$armpose.isTwoHanded()) {
            humanoidmodel$armpose1 = recruit.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        }
        if (recruit.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = humanoidmodel$armpose;
            model.leftArmPose = humanoidmodel$armpose1;
        } else {
            model.rightArmPose = humanoidmodel$armpose1;
            model.leftArmPose = humanoidmodel$armpose;
        }
    }

    private static HumanoidModel.ArmPose getArmPose(AbstractRecruitEntity recruit, InteractionHand hand) {
        ItemStack itemstack = recruit.getItemInHand(hand);
        boolean isMusket = IWeapon.isMusketModWeapon(itemstack) && (recruit instanceof CrossBowmanEntity crossBowman)  && crossBowman.isAggressive();
        if (itemstack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        } else {
            if (recruit.getUsedItemHand() == hand && recruit.getUseItemRemainingTicks() > 0) {
                UseAnim useanim = itemstack.getUseAnimation();
                if (useanim == UseAnim.BLOCK) {
                    return HumanoidModel.ArmPose.BLOCK;
                }

                if (useanim == UseAnim.BOW) {
                    return HumanoidModel.ArmPose.BOW_AND_ARROW;
                }

                if (useanim == UseAnim.SPEAR) {
                    return HumanoidModel.ArmPose.THROW_SPEAR;
                }

                if (useanim == UseAnim.CROSSBOW && hand == recruit.getUsedItemHand() || isMusket) {
                    return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
                }

                if (useanim == UseAnim.SPYGLASS) {
                    return HumanoidModel.ArmPose.SPYGLASS;
                }
            } else if (!recruit.swinging && itemstack.is(Items.CROSSBOW) && CrossbowItem.isCharged(itemstack) || isMusket) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }

            HumanoidModel.ArmPose forgeArmPose = net.minecraftforge.client.extensions.common.IClientItemExtensions.of(itemstack).getArmPose(recruit, hand, itemstack);
            if (forgeArmPose != null) return forgeArmPose;

            return HumanoidModel.ArmPose.ITEM;
        }
    }

}
