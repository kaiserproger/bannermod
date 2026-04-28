package com.talhanation.bannermod.client.military.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.talhanation.bannermod.client.military.render.RecruitRenderProfiling;
import com.talhanation.bannermod.client.military.render.RecruitRenderLod;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;

public class RecruitLodItemInHandLayer<T extends AbstractRecruitEntity, M extends EntityModel<T> & ArmedModel> extends ItemInHandLayer<T, M> {
    public RecruitLodItemInHandLayer(RenderLayerParent<T, M> renderer, ItemInHandRenderer itemInHandRenderer) {
        super(renderer, itemInHandRenderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T recruit, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!RecruitRenderLod.shouldRenderHeldItems(recruit)) {
            RecruitRenderProfiling.layerSkipped("held_items");
            return;
        }
        RecruitRenderProfiling.textureStateSwitch("held_items");
        long start = RecruitRenderProfiling.start();
        super.render(poseStack, bufferSource, packedLight, recruit, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        RecruitRenderProfiling.layerDuration("held_items", start);
    }
}
