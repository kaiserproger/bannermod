package com.talhanation.bannermod.client.military.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.military.render.RecruitRenderProfiling;
import com.talhanation.bannermod.client.military.render.RecruitRenderLod;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import com.talhanation.bannermod.entity.military.ICompanion;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class RecruitVillagerCompanionLayer extends RenderLayer<AbstractRecruitEntity, HumanoidModel<AbstractRecruitEntity>> {

    private static final ResourceLocation LOCATION = new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/villager/villager_assassin_cloth.png");
    public RecruitVillagerCompanionLayer(LivingEntityRenderer<AbstractRecruitEntity, HumanoidModel<AbstractRecruitEntity>> renderer) {
        super(renderer);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int p_117722_, AbstractRecruitEntity recruit, float p_117724_, float p_117725_, float p_117726_, float p_117727_, float p_117728_, float p_117729_) {
        if(recruit.isInvisible() || !(recruit instanceof ICompanion) || !RecruitRenderLod.shouldRenderCosmeticModelLayer(recruit)){
            RecruitRenderProfiling.layerSkipped("companion");
            return;
        }
        RecruitRenderProfiling.textureStateSwitch("companion");
        long start = RecruitRenderProfiling.start();
        VertexConsumer vertexconsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(LOCATION));
        this.getParentModel().renderToBuffer(poseStack, vertexconsumer, p_117722_, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        RecruitRenderProfiling.layerDuration("companion", start);
    }

}
