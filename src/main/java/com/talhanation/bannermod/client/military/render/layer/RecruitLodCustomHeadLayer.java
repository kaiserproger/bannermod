package com.talhanation.bannermod.client.military.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.talhanation.bannermod.client.military.render.RecruitRenderProfiling;
import com.talhanation.bannermod.client.military.render.RecruitRenderLod;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.LivingEntity;

public class RecruitLodCustomHeadLayer<T extends LivingEntity, M extends EntityModel<T> & HeadedModel> extends CustomHeadLayer<T, M> {
    public RecruitLodCustomHeadLayer(RenderLayerParent<T, M> renderer,
                                     EntityModelSet modelSet,
                                     ItemInHandRenderer itemInHandRenderer) {
        super(renderer, modelSet, itemInHandRenderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity instanceof AbstractRecruitEntity recruit && !RecruitRenderLod.shouldRenderCustomHead(recruit)) {
            RecruitRenderProfiling.layerSkipped("custom_head");
            return;
        }
        RecruitRenderProfiling.textureStateSwitch("custom_head");
        long start = RecruitRenderProfiling.start();
        super.render(poseStack, bufferSource, packedLight, entity, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        RecruitRenderProfiling.layerDuration("custom_head", start);
    }
}
