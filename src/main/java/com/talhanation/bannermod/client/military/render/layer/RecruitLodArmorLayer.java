package com.talhanation.bannermod.client.military.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.talhanation.bannermod.client.military.render.RecruitRenderProfiling;
import com.talhanation.bannermod.client.military.render.RecruitRenderLod;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.model.ModelManager;

public class RecruitLodArmorLayer extends HumanoidArmorLayer<AbstractRecruitEntity, HumanoidModel<AbstractRecruitEntity>, HumanoidModel<AbstractRecruitEntity>> {
    public RecruitLodArmorLayer(RenderLayerParent<AbstractRecruitEntity, HumanoidModel<AbstractRecruitEntity>> renderer,
                                HumanoidModel<AbstractRecruitEntity> innerModel,
                                HumanoidModel<AbstractRecruitEntity> outerModel,
                                ModelManager modelManager) {
        super(renderer, innerModel, outerModel, modelManager);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, AbstractRecruitEntity recruit, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!RecruitRenderLod.shouldRenderArmor(recruit)) {
            RecruitRenderProfiling.layerSkipped("armor");
            return;
        }
        long start = RecruitRenderProfiling.start();
        super.render(poseStack, bufferSource, packedLight, recruit, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        RecruitRenderProfiling.layerDuration("armor", start);
    }
}
