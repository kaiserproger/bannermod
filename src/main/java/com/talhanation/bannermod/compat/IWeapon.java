package com.talhanation.bannermod.compat;
import com.talhanation.bannermod.entity.military.AbstractRecruitEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public interface IWeapon {
    Item getWeapon();
    double getMoveSpeedAmp();
    int getAttackCooldown();
    int getWeaponLoadTime();
    float getProjectileSpeed();
    AbstractHurtingProjectile getProjectile(LivingEntity shooter);
    AbstractArrow getProjectileArrow(LivingEntity shooter);
    AbstractHurtingProjectile shoot(LivingEntity shooter, AbstractHurtingProjectile projectile, double x, double y, double z);
    AbstractArrow shootArrow(LivingEntity shooter, AbstractArrow projectile, double x, double y, double z);

    SoundEvent getShootSound();
    SoundEvent getLoadSound();
    boolean isGun();
    boolean canMelee();
    boolean isBow();
    boolean isCrossBow();
    void performRangedAttackIWeapon(AbstractRecruitEntity shooter, double x, double y, double z, float projectileSpeed);

    static boolean isMusketModWeapon(ItemStack stack){
        return MedievalBoomsticksCompat.isSupportedRecruitFirearm(stack);
    }

    boolean isLoaded(ItemStack stack);

    void setLoaded(ItemStack stack, boolean loaded);
}
