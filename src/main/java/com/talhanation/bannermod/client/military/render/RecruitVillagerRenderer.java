package com.talhanation.bannermod.client.military.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.military.events.ClientEvent;
import com.talhanation.bannermod.client.military.models.RecruitVillagerModel;
import com.talhanation.bannermod.client.military.render.layer.RecruitVillagerBiomeLayer;
import com.talhanation.bannermod.client.military.render.layer.RecruitVillagerCompanionLayer;
import com.talhanation.bannermod.client.military.render.layer.RecruitVillagerTeamColorLayer;
import com.talhanation.bannermod.client.military.render.layer.RecruitLodArmorLayer;
import com.talhanation.bannermod.client.military.render.layer.RecruitLodItemInHandLayer;
import com.talhanation.bannermod.client.military.render.layer.VillagerRecruitCustomHeadLayer;
import com.talhanation.bannermod.compat.IWeapon;
import com.talhanation.bannermod.entity.military.AbstractInventoryEntity;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.CrossBowmanEntity;
import com.talhanation.bannermod.entity.military.VillagerNobleEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;

public class RecruitVillagerRenderer extends MobRenderer<AbstractRecruitEntity, HumanoidModel<AbstractRecruitEntity>> {

    private static final ResourceLocation[] TEXTURE = {
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/villager/villager_1.png"),
    };
    public ResourceLocation getTextureLocation(AbstractRecruitEntity recruit) {
        RecruitRenderProfiling.textureStateSwitch("base_model");
        return TEXTURE[0];
    }

    public static ResourceLocation crowdTexture(AbstractRecruitEntity recruit) {
        return TEXTURE[0];
    }

    public RecruitVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new RecruitVillagerModel(context.bakeLayer(ClientEvent.RECRUIT)), 0.5F);
        this.addLayer(new RecruitLodArmorLayer(this, new HumanoidModel<>(context.bakeLayer(ClientEvent.RECRUIT_INNER_ARMOR)), new HumanoidModel<>(context.bakeLayer(ClientEvent.RECRUIT_OUTER_ARMOR)), context.getModelManager()));
        this.addLayer(new RecruitVillagerTeamColorLayer(this));
        this.addLayer(new RecruitVillagerBiomeLayer(this));
        this.addLayer(new RecruitVillagerCompanionLayer(this));
        this.addLayer(new RecruitLodItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new VillagerRecruitCustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
    }

    @Override
    public void render(AbstractRecruitEntity entityIn, float entityYaw, float partialTicks, PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn) {
        long poseStart = RecruitRenderProfiling.start();
        this.setModelVisibilities(entityIn);
        RecruitRenderProfiling.duration("animation_pose", poseStart);
        RecruitRenderProfiling.beginNormalRender();
        long renderStart = RecruitRenderProfiling.start();
        super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
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

    //PlayerRenderer
    private void setModelVisibilities(AbstractInventoryEntity recruitEntity) {
        HumanoidModel<AbstractRecruitEntity> model = this.getModel();
        model.setAllVisible(true);
        model.crouching = recruitEntity.isCrouching();
        model.setAllVisible(true);
        model.hat.visible = true;
        HumanoidModel.ArmPose humanoidmodel$armpose = getArmPose(recruitEntity, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose humanoidmodel$armpose1 = getArmPose(recruitEntity, InteractionHand.OFF_HAND);
        if (humanoidmodel$armpose.isTwoHanded()) {
            humanoidmodel$armpose1 = recruitEntity.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        }

        if (recruitEntity.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = humanoidmodel$armpose;
            model.leftArmPose = humanoidmodel$armpose1;
        } else {
            model.rightArmPose = humanoidmodel$armpose1;
            model.leftArmPose = humanoidmodel$armpose;
        }
    }

    @Override
    protected void scale(AbstractRecruitEntity entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        matrixStackIn.scale(0.9375F, 0.9375F, 0.9375F);
    }

    private static HumanoidModel.ArmPose getArmPose(AbstractInventoryEntity recruit, InteractionHand hand) {
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
            return HumanoidModel.ArmPose.ITEM;
        }
    }
}
