package com.talhanation.bannermod.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import com.talhanation.bannermod.bootstrap.BannerModMain;
import com.talhanation.bannermod.entity.military.CaptainEntity;
import com.talhanation.bannermod.entity.military.IRangedRecruit;
import com.talhanation.bannermod.util.Kalkuel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

import static com.talhanation.bannermod.util.Kalkuel.*;

public class SmallShips {

    private static final String SHIP_CLASS_NAME = "com.talhanation.smallships.world.entity.ship.Ship";
    private static final String SAILABLE_CLASS_NAME = "com.talhanation.smallships.world.entity.ship.abilities.Sailable";
    private static final String CANNONABLE_CLASS_NAME = "com.talhanation.smallships.world.entity.ship.abilities.Cannonable";
    private static final ReflectiveCompatAccess COMPAT_ACCESS = new ReflectiveCompatAccess();

    @FunctionalInterface
    interface ReflectiveClassResolver {
        Class<?> resolve(String className) throws ClassNotFoundException;
    }

    private final Boat boat;
    private final CaptainEntity captain;

    public SmallShips(Boat boat, CaptainEntity captain) {
        this.boat = boat;
        this.captain = captain;
    }

    public Boat getBoat(){
        return boat;
    }

    public static boolean isSmallShip(Entity entity) {
        return isSmallShipEntity(entity, Class::forName);
    }

    static boolean isSmallShipEntity(@Nullable Object entity, ReflectiveClassResolver classResolver) {
        if (entity == null) return false;
        ReflectiveCompatAccess access = new ReflectiveCompatAccess(classResolver::resolve);
        return access.findClass(SHIP_CLASS_NAME).filter(shipClass -> shipClass.isInstance(entity)).isPresent();
    }

    static boolean hasSmallShipEntityClass(ReflectiveClassResolver classResolver) {
        ReflectiveCompatAccess access = new ReflectiveCompatAccess(classResolver::resolve);
        return access.findClass(SHIP_CLASS_NAME).isPresent();
    }

    public boolean isCaptainDriver(){
        List<Entity> passengers = boat.getPassengers();
        return !passengers.isEmpty() && passengers.get(0).equals(captain);
    }

    public float getShipSpeed() {
        return shipObject().flatMap(ship -> invokeFloat(shipClass(), ship, "getSpeed")).orElse(0F);
    }

    public void setSailState(int state) {
        Optional<Field> coolDownField = shipField("sailStateCooldown");
        Optional<Integer> coolDown = coolDownField.flatMap(field -> COMPAT_ACCESS.getInt(field, boat));
        if (coolDown.isEmpty() || coolDown.get() != 0) return;

        Optional<Object> sailable = sailableObject();
        if (sailable.isEmpty()) return;

        int configCoolDown = invokeInt(sailableClass(), sailable.get(), "getSailStateCooldown").orElse(0);
        byte currentSail = invokeByte(sailableClass(), sailable.get(), "getSailState").orElse((byte) state);
        if (currentSail != (byte) state) {
            invoke(sailableClass(), sailable.get(), "setSailState", new Class[]{byte.class}, (byte) state);
        }
        coolDownField.ifPresent(field -> COMPAT_ACCESS.setInt(field, boat, configCoolDown));
    }

    /*


    public void steerLeft() {
        try {
            Class<?> shipClass = Class.forName("com.talhanation.smallships.world.entity.ship.Ship");
            if (shipClass.isInstance(boat)) {
                Object ship = shipClass.cast(boat);

                Method setRight = shipClass.getDeclaredMethod("setRight", boolean.class);
                Method setLeft = shipClass.getDeclaredMethod("setLeft", boolean.class);
                setRight.invoke(ship, false);
                setLeft.invoke(ship, true);
            }
        }
        catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            BannerModMain.LOGGER.info("shipClass was not found");
        }
    }
     */

    /*
    public void steerRight() {
        try {
            Class<?> shipClass = Class.forName("com.talhanation.smallships.world.entity.ship.Ship");
            if (shipClass.isInstance(boat)) {
                Object ship = shipClass.cast(boat);

                Method setRight = shipClass.getDeclaredMethod("setRight", boolean.class);
                Method setLeft = shipClass.getDeclaredMethod("setLeft", boolean.class);
                setRight.invoke(ship, true);
                setLeft.invoke(ship, false);
            }
        }
        catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            BannerModMain.LOGGER.info("shipClass was not found");
        }
    }

     */

    public void rotateShip(boolean inputLeft, boolean inputRight) {
        float maxRotSp = 2.0F;
        float rotAcceleration = 0.35F;
        Optional<Object> ship = shipObject();
        if (ship.isEmpty()) return;

        float boatRotSpeed = invokeFloat(shipClass(), ship.get(), "getRotSpeed").orElse(0F);
        invoke(shipClass(), ship.get(), "updateControls", new Class[]{boolean.class, boolean.class, boolean.class, boolean.class, Player.class}, false, false, inputLeft, inputRight, null);

        float rotationSpeed = subtractToZero(boatRotSpeed, getVelocityResistance() * 2.5F);
        if (inputRight && rotationSpeed < maxRotSp) {
            rotationSpeed = Math.min(rotationSpeed + rotAcceleration / 8, maxRotSp);
        }
        if (inputLeft && rotationSpeed > -maxRotSp) {
            rotationSpeed = Math.max(rotationSpeed - rotAcceleration / 8, -maxRotSp);
        }
        boat.deltaRotation = rotationSpeed;
        boat.setYRot(boat.getYRot() + boat.deltaRotation);
        invoke(shipClass(), ship.get(), "setRotSpeed", new Class[]{float.class}, rotationSpeed);
    }

    // Hilfsmethode, um den Rotationswert langsam in Richtung 0 zu ziehen
    private static float subtractToZero(float value, float amount) {
        if (value > 0) {
            return Math.max(value - amount, 0);
        } else if (value < 0) {
            return Math.min(value + amount, 0);
        }
        return 0;
    }

    private static float getVelocityResistance() {
        return 0.007F;
    }

    public void updateBoatControl(double posX, double posZ, double speedFactor, double turnFactor) {
        // Beispielhafte Logik:
        // - Falls der Kapitän in Slot 0 sitzt, wird weiter gemacht.
        if (boat.getPassengers().get(0).equals(captain)) {
            String id = boat.getEncodeId();
            if (BannerModMain.isSmallShipsLoaded && BannerModMain.isSmallShipsCompatible && id.contains("smallships")) {
                //updateSmallShipsRotation(posX, posZ);
            } else {
                updateVanillaBoatControl(posX, posZ, speedFactor, turnFactor);
            }
        }
    }
    public void updateControl() {
        Optional<Object> ship = shipObject();
        if (ship.isEmpty()) return;

        shipClass().flatMap(type -> COMPAT_ACCESS.findMethod(type, "controlBoat")).ifPresent(method -> COMPAT_ACCESS.invoke(method, ship.get()));
    }
    public void updateSmallShipsControl(boolean inputLeft, boolean inputRight, int state) {
        Optional<Object> ship = shipObject();
        if (ship.isEmpty()) return;

        boolean isLeashed = invokeBoolean(shipClass(), ship.get(), "isShipLeashed").orElse(false);
        if(!boat.isInWater() || isLeashed) return;

        rotateShip(inputLeft, inputRight);
        setSailState(state);
    }
    public void updateSmallShipsControl(double posX, double posZ, int state) {
        Vec3 forward = boat.getForward().yRot(-90).normalize();
        Vec3 target = new Vec3(posX, 0, posZ);
        Vec3 toTarget = boat.position().subtract(target);

        double phi = Kalkuel.horizontalAngleBetweenVectors(forward, toTarget);
        double ref = 63.334F;
        boolean inputLeft = (phi < ref);
        boolean inputRight = (phi > ref);

        double deviation = Math.abs(phi - ref);
        double stopThreshold = ref * 0.80F;
        double slowThreshold = ref * 0.35F;

        if (deviation > stopThreshold) {
            state = 0;
        } else if (deviation > slowThreshold) {
            state = 1;
        }

        Optional<Object> ship = shipObject();
        if (ship.isEmpty()) return;

        boolean isLeashed = invokeBoolean(shipClass(), ship.get(), "isShipLeashed").orElse(false);
        if(!boat.isInWater() || isLeashed) return;

        rotateShip(inputLeft, inputRight);
        setSailState(state);
    }
    public static void rotateSmallShip(Boat boat, boolean inputLeft, boolean inputRight){
        float maxRotSp = 2.0F;
        float boatRotSpeed = 0;
        float rotAcceleration = 0.35F;
        Optional<Object> ship = castBoat(boat, shipClass());
        if (ship.isEmpty()) return;

        boatRotSpeed = invokeFloat(shipClass(), ship.get(), "getRotSpeed").orElse(0F);
        invoke(shipClass(), ship.get(), "updateControls", new Class[]{boolean.class, boolean.class, boolean.class, boolean.class, Player.class}, false, false, inputLeft, inputRight, null);

        float rotationSpeed = subtractToZero(boatRotSpeed, getVelocityResistance() * 2.5F);
        if (inputRight && rotationSpeed < maxRotSp) {
            rotationSpeed = Math.min(rotationSpeed + rotAcceleration * 1 / 8, maxRotSp);
        }

        if (inputLeft && rotationSpeed > -maxRotSp) {
            rotationSpeed = Math.max(rotationSpeed - rotAcceleration * 1 / 8, -maxRotSp);
        }

        boat.deltaRotation = rotationSpeed;
        boat.setYRot(boat.getYRot() + boat.deltaRotation);

        invoke(shipClass(), ship.get(), "setRotSpeed", new Class[]{float.class}, rotationSpeed);
    }

    static boolean rotateShipTowardsPos(Boat boat, Vec3 targetVec){
        boolean rotated = false;

        if(targetVec != null) {
            Vec3 forward = boat.getForward().normalize();
            Vec3 target = new Vec3(targetVec.x, 0, targetVec.y);
            Vec3 toTarget = boat.position().subtract(target).normalize();

            double phi = Kalkuel.horizontalAngleBetweenVectors(forward, toTarget);
            double ref = 63.334F;
            boolean inputLeft = (phi < ref);
            boolean inputRight = (phi > ref);

            rotateSmallShip(boat, inputLeft, inputRight);

            if (Math.abs(phi - ref) <= ref * 0.35F) {
                rotated = true;
            }
        }
        return rotated;
    }

    private void setSmallShipsSailState(Boat boat, int state){
        Optional<Field> coolDownField = shipField("sailStateCooldown");
        Optional<Integer> coolDown = coolDownField.flatMap(field -> COMPAT_ACCESS.getInt(field, boat));
        if (coolDown.isEmpty() || coolDown.get() != 0) return;

        Optional<Object> sailable = castBoat(boat, sailableClass());
        if (sailable.isEmpty()) return;

        int configCoolDown = invokeInt(sailableClass(), sailable.get(), "getSailStateCooldown").orElse(0);
        byte currentSail = invokeByte(sailableClass(), sailable.get(), "getSailState").orElse((byte) state);
        if(currentSail != (byte) state) {
            invoke(sailableClass(), sailable.get(), "setSailState", new Class[]{byte.class}, (byte) state);
        }
        coolDownField.ifPresent(field -> COMPAT_ACCESS.setInt(field, boat, configCoolDown));
    }

    // Standardsteuerung für Vanilla-Boote
    private void updateVanillaBoatControl(double posX, double posZ, double speedFactor, double turnFactor) {
        boat.setDeltaMovement(calculateMotionX((float) speedFactor, boat.getYRot()),
                boat.getDeltaMovement().y,
                calculateMotionZ((float) speedFactor, boat.getYRot()));
    }


    public boolean hasCannons() {
        return cannonableObject()
                .flatMap(cannonAble -> invoke(cannonableClass(), cannonAble, "getCannons", new Class[]{}))
                .filter(List.class::isInstance)
                .map(List.class::cast)
                .map(list -> !list.isEmpty())
                .orElse(false);
    }
    public boolean canShootCannons() {
        return canShootCannons(boat);
    }
    public static boolean canShootCannons(Entity vehicle) {
        if(vehicle instanceof Boat boat) {
            return castBoat(boat, cannonableClass())
                    .flatMap(cannonAble -> invokeBoolean(cannonableClass(), cannonAble, "canShoot"))
                    .orElse(false);
        }
        return false;
    }


    public static void shootCannonsSmallShip(CaptainEntity driver, Boat boat, Entity target, boolean leftSide){
        if(boat == null || target == null || driver == null) return;

        double distanceToTarget = driver.distanceToSqr(target);
        //BannerModMain.LOGGER.info("Distance: " + distanceToTarget);
        double speed = 3.2F;
        double accuracy = 2F;// 0 = 100% left right accuracy
        float rotation = leftSide ? (3.14F / 2) : -(3.14F / 2);

        Vec3 shootVec = boat.getForward().yRot(rotation).normalize();
        double heightDiff = target.getY() - driver.getY();
        double angle = IRangedRecruit.getCannonAngleDistanceModifier(distanceToTarget, 2) + IRangedRecruit.getCannonAngleHeightModifier(distanceToTarget, heightDiff)/ 100;
        double yShootVec = shootVec.y() + angle;
        castBoat(boat, cannonableClass()).ifPresent(cannonAble ->
                invoke(cannonableClass(), cannonAble, "triggerCannons", new Class[]{Vec3.class, double.class, LivingEntity.class, double.class, double.class}, shootVec, yShootVec, driver, speed, accuracy)
        );
    }

    @Nullable
    public static ItemStack getSmallShipsItem() {
        return ForgeRegistries.ITEMS.getDelegateOrThrow(ResourceLocation.tryParse("smallships:oak_cog")).get().getDefaultInstance();
    }

    public void repairShip(CaptainEntity captain) {
        int amount = (10 + captain.getCommandSenderWorld().random.nextInt(5));
        if (BannerModMain.isSmallShipsLoaded && BannerModMain.isSmallShipsCompatible && boat.getEncodeId().contains("smallships")) {
            shipObject().ifPresent(ship -> {
                float damage = invokeFloat(shipClass(), ship, "getDamage").orElse(0F);
                if(damage > 5) {
                    invoke(shipClass(), ship, "repairShip", new Class[]{int.class}, amount);

                    for (int i = 0; i < captain.getInventory().getContainerSize(); ++i) {
                        ItemStack itemStack = captain.getInventory().getItem(i);
                        if (itemStack.is(ItemTags.PLANKS)) {
                            itemStack.shrink(1);
                            break;
                        }
                    }

                    for (int i = 0; i < captain.getInventory().getContainerSize(); ++i) {
                        ItemStack itemStack = captain.getInventory().getItem(i);
                        if (itemStack.is(Items.IRON_NUGGET)) {
                            itemStack.shrink(1);
                            break;
                        }
                    }
                }
            });
        }
    }

    public float getDamage() {
        return shipObject().flatMap(ship -> invokeFloat(shipClass(), ship, "getDamage")).orElse(0F);
    }


    public boolean isGalley() {
        return boat.getEncodeId().contains("galley");
    }

    private static Optional<Class<?>> shipClass() {
        return COMPAT_ACCESS.findClass(SHIP_CLASS_NAME);
    }

    private static Optional<Class<?>> sailableClass() {
        return COMPAT_ACCESS.findClass(SAILABLE_CLASS_NAME);
    }

    private static Optional<Class<?>> cannonableClass() {
        return COMPAT_ACCESS.findClass(CANNONABLE_CLASS_NAME);
    }

    private Optional<Object> shipObject() {
        return castBoat(boat, shipClass());
    }

    private Optional<Object> sailableObject() {
        return castBoat(boat, sailableClass());
    }

    private Optional<Object> cannonableObject() {
        return castBoat(boat, cannonableClass());
    }

    private static Optional<Object> castBoat(Boat boat, Optional<Class<?>> type) {
        return type.filter(clazz -> clazz.isInstance(boat)).map(clazz -> clazz.cast(boat));
    }

    private static Optional<Field> shipField(String fieldName) {
        return shipClass().flatMap(type -> COMPAT_ACCESS.findField(type, fieldName));
    }

    private static Optional<Object> invoke(Optional<Class<?>> ownerType, Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        return ownerType
                .flatMap(type -> COMPAT_ACCESS.findMethod(type, methodName, parameterTypes))
                .flatMap(method -> COMPAT_ACCESS.invoke(method, target, args));
    }

    private static Optional<Boolean> invokeBoolean(Optional<Class<?>> ownerType, Object target, String methodName, Class<?>... parameterTypes) {
        return invoke(ownerType, target, methodName, parameterTypes)
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::cast);
    }

    private static Optional<Integer> invokeInt(Optional<Class<?>> ownerType, Object target, String methodName, Class<?>... parameterTypes) {
        return invoke(ownerType, target, methodName, parameterTypes)
                .filter(Integer.class::isInstance)
                .map(Integer.class::cast);
    }

    private static Optional<Float> invokeFloat(Optional<Class<?>> ownerType, Object target, String methodName, Class<?>... parameterTypes) {
        return invoke(ownerType, target, methodName, parameterTypes)
                .filter(Float.class::isInstance)
                .map(Float.class::cast);
    }

    private static Optional<Byte> invokeByte(Optional<Class<?>> ownerType, Object target, String methodName, Class<?>... parameterTypes) {
        return invoke(ownerType, target, methodName, parameterTypes)
                .filter(Byte.class::isInstance)
                .map(Byte.class::cast);
    }
}
