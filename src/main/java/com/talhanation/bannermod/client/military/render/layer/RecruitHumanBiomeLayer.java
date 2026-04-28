package com.talhanation.bannermod.client.military.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.client.military.render.RecruitRenderProfiling;
import com.talhanation.bannermod.client.military.render.RecruitRenderLod;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import net.minecraft.client.model.HumanoidModel;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class RecruitHumanBiomeLayer extends RenderLayer<AbstractRecruitEntity, HumanoidModel<AbstractRecruitEntity>> {

    private static final ResourceLocation[] TEXTURE = {
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/biome/human_desert.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/biome/human_jungle.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/biome/human_plains.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/biome/human_savanna.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/biome/human_snowy_tundra.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/biome/human_swamp.png"),
            new ResourceLocation(BannerModMain.MOD_ID,"textures/entity/human/biome/human_taiga.png"),
    };
    public RecruitHumanBiomeLayer(LivingEntityRenderer<AbstractRecruitEntity, HumanoidModel<AbstractRecruitEntity>> renderer) {
        super(renderer);
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int p_117722_, AbstractRecruitEntity recruit, float p_117724_, float p_117725_, float p_117726_, float p_117727_, float p_117728_, float p_117729_) {
        if(recruit.isInvisible() || !RecruitRenderLod.shouldRenderCosmeticModelLayer(recruit)){
            RecruitRenderProfiling.layerSkipped("biome");
            return;
        }
        RecruitRenderProfiling.textureStateSwitch("biome");
        long start = RecruitRenderProfiling.start();
        this.getParentModel().renderToBuffer(poseStack, bufferSource.getBuffer(RenderType.entityCutout(TEXTURE[recruit.getBiome()])), p_117722_, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        RecruitRenderProfiling.layerDuration("biome", start);
    }
}
