package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.MultiModeSetting;
import com.utilitymod.echo.render3.api.FramebufferTarget;
import com.utilitymod.echo.utils.DrawUtils;
import com.utilitymod.echo.utils.inventory.InventoryUtils;
import com.utilitymod.echo.utils.strings.Concat;
import com.utilitymod.echo.utils.trajectory.ArrowTrajectoryUtils;
import com.utilitymod.echo.utils.trajectory.BowTrajectoryUtils;
import com.utilitymod.echo.utils.trajectory.CrossbowTrajectoryUtils;
import com.utilitymod.echo.utils.trajectory.PearlTrajectoryUtils;
import com.utilitymod.echo.utils.trajectory.PotionTrajectoryUtils;
import com.utilitymod.echo.utils.trajectory.WindChargeTrajectoryUtils;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class TrajectoryRenderModule extends Feature {

    private static final int LANDING_CIRCLE_SEGMENTS = 24;
    private static final double LANDING_CIRCLE_RADIUS = 0.4;
    private static final double LANDING_CIRCLE_SURFACE_OFFSET = 0.02;

    private final MultiModeSetting trajectories = new MultiModeSetting(
        Concat.of(LT.L1),
        Concat.of(LT.L2),
        Concat.of(LT.L3),
        Concat.of(LT.L4),
        Concat.of(LT.L5),
        Concat.of(LT.L6),
        Concat.of(LT.L7),
        Concat.of(LT.L8)
    );
    private final ColorSetting pearlColor = new ColorSetting(Concat.of(LT.L9), 102, 179, 255, 200, s -> allowsPearl(), true);
    private final ColorSetting potionColor = new ColorSetting(Concat.of(LT.L10), 124, 192, 255, 200, s -> allowsPotion(), true);
    private final ColorSetting windChargeColor = new ColorSetting(Concat.of(LT.L11), 255, 209, 102, 200, s -> allowsWindCharge(), true);
    private final ColorSetting bowColor = new ColorSetting(Concat.of(LT.L12), 148, 114, 255, 200, s -> allowsBow(), true);
    private final ColorSetting crossbowColor = new ColorSetting(Concat.of(LT.L13), 114, 216, 180, 200, s -> allowsCrossbow(), true);
    private final ColorSetting fireworkColor = new ColorSetting(Concat.of(LT.L14), 255, 196, 130, 200, s -> allowsFirework(), true);
    private final ColorSetting arrowColor = new ColorSetting(Concat.of(LT.L15), 255, 128, 128, 200, s -> allowsArrow(), true);

    public TrajectoryRenderModule() {
        super(new FeatureInfo(
            Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeb92,0x0b28,0x1c80,0x962a,0x2279,0xd12e,0x11f5,0x82c2,0xa8b9,0x37db,0xb16c,0xbc03}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1}).toString()),
            Concat.of(LT.L16),
            Category.RENDER
        ));

        trajectories.setOption(Concat.of(LT.L2), true);
        trajectories.setOption(Concat.of(LT.L3), true);
        trajectories.setOption(Concat.of(LT.L4), true);
        trajectories.setOption(Concat.of(LT.L5), true);
        trajectories.setOption(Concat.of(LT.L6), true);
        trajectories.setOption(Concat.of(LT.L7), true);
        trajectories.setOption(Concat.of(LT.L8), true);
    }

    @SuppressWarnings("unused")
    @EventSubscribe
    private void onRender3D(EventRender3D event) {
        if (isNull()) return;

        var draw = event.getDraw3D().getMinecraftTarget();
        float pt = event.getTickDelta();

        if (allowsPearl() && InventoryUtils.isHoldingAnyHand(Items.ENDER_PEARL)) {
            drawTrajectory(draw, PearlTrajectoryUtils.sample(mc.player, pt), toColor(pearlColor));
        }

        if (allowsPearl()) {
            int color = toColor(pearlColor);
            for (Entity entity : mc.world.getEntities()) {
                if (entity.getType() != EntityType.ENDER_PEARL) continue;
                drawTrajectory(draw, PearlTrajectoryUtils.sample(entity), color);
            }
        }

        if (allowsPotion() && isHoldingThrowablePotion()) {
            drawTrajectory(draw, PotionTrajectoryUtils.sample(mc.player, pt), toColor(potionColor));
        }

        if (allowsPotion()) {
            int color = toColor(potionColor);
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof PotionEntity)) continue;
                drawTrajectory(draw, PotionTrajectoryUtils.sample(entity), color);
            }
        }

        if (allowsWindCharge() && InventoryUtils.isHoldingAnyHand(Items.WIND_CHARGE)) {
            drawTrajectory(draw, WindChargeTrajectoryUtils.sample(mc.player, pt), toColor(windChargeColor));
        }

        if (allowsWindCharge()) {
            int color = toColor(windChargeColor);
            for (Entity entity : mc.world.getEntities()) {
                if (entity.getType() != EntityType.WIND_CHARGE) continue;
                drawTrajectory(draw, WindChargeTrajectoryUtils.sample(entity), color);
            }
        }

        if (allowsBow() && InventoryUtils.isHoldingAnyHand(Items.BOW)) {
            drawTrajectory(draw, BowTrajectoryUtils.sample(mc.player, getBowChargeTicks(), pt), toColor(bowColor));
        }

        if (allowsCrossbow() && InventoryUtils.isHoldingAnyHand(Items.CROSSBOW)) {
            ItemStack crossbow = getHeldCrossbow();
            if (!crossbow.isEmpty()) {
                int color = toColor(crossbowColor);
                for (List<Vec3d> points : CrossbowTrajectoryUtils.sample(mc.player, crossbow, pt)) {
                    drawTrajectory(draw, points, color);
                }
            }
        }

        if (allowsFirework()) {
            int color = toColor(fireworkColor);
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof FireworkRocketEntity firework)) continue;
                drawTrajectory(draw, CrossbowTrajectoryUtils.sampleFirework(firework), color);
            }
        }

        if (allowsArrow()) {
            int color = toColor(arrowColor);
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof PersistentProjectileEntity arrow)) continue;
                if (com.utilitymod.echo.api.Accessors.arrowInGround(arrow)) continue;
                drawTrajectory(draw, ArrowTrajectoryUtils.sample(arrow), color);
            }
        }
    }

    private int getBowChargeTicks() {
        if (!mc.player.isUsingItem()) return 20;

        ItemStack using = mc.player.getActiveItem();
        if (!(using.getItem() instanceof BowItem)) return 20;

        int charge = using.getMaxUseTime(mc.player) - mc.player.getItemUseTimeLeft();
        return MathHelper.clamp(charge, 0, 20);
    }

    private boolean allowsPearl() {
        return trajectories.isEnabled(Concat.of(LT.L2));
    }

    private boolean allowsWindCharge() {
        return trajectories.isEnabled(Concat.of(LT.L4));
    }

    private boolean allowsPotion() {
        return trajectories.isEnabled(Concat.of(LT.L3));
    }

    private boolean allowsBow() {
        return trajectories.isEnabled(Concat.of(LT.L5));
    }

    private boolean allowsCrossbow() {
        return trajectories.isEnabled(Concat.of(LT.L6));
    }

    private boolean allowsFirework() {
        return trajectories.isEnabled(Concat.of(LT.L7));
    }

    private boolean allowsArrow() {
        return trajectories.isEnabled(Concat.of(LT.L8));
    }

    private ItemStack getHeldCrossbow() {
        ItemStack main = mc.player.getMainHandStack();
        if (main.getItem() instanceof CrossbowItem) return main;

        ItemStack offhand = mc.player.getOffHandStack();
        return offhand.getItem() instanceof CrossbowItem ? offhand : ItemStack.EMPTY;
    }

    private boolean isHoldingThrowablePotion() {
        return InventoryUtils.isHoldingAnyHand(Items.SPLASH_POTION) || InventoryUtils.isHoldingAnyHand(Items.LINGERING_POTION);
    }

    private void drawTrajectory(FramebufferTarget draw, List<Vec3d> points, int color) {
        if (points.size() < 2) return;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d from = points.get(i);
            Vec3d to = points.get(i + 1);
            draw.line(from.x, from.y, from.z, to.x, to.y, to.z, color);
        }

        Vec3d previous = points.get(points.size() - 2);
        Vec3d landing = points.get(points.size() - 1);
        Vec3d normal = new Vec3d(0.0, 1.0, 0.0);

        if (mc.world != null) {
            BlockHitResult hit = mc.world.raycast(new RaycastContext(
                previous,
                landing,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
            ));

            if (hit.getType() != HitResult.Type.MISS) {
                landing = hit.getPos();
                Direction direction = hit.getSide();
                normal = new Vec3d(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ());

                Vec3d approach = previous.subtract(landing);
                if (approach.lengthSquared() > 1.0E-6 && normal.dotProduct(approach) < 0.0) {
                    normal = normal.multiply(-1.0);
                }
            }
        }

        Vec3d center = landing.add(normal.normalize().multiply(LANDING_CIRCLE_SURFACE_OFFSET));
        DrawUtils.drawOutlinedCircle(
            draw,
            center,
            normal,
            LANDING_CIRCLE_RADIUS,
            LANDING_CIRCLE_SEGMENTS,
            color
        );
    }

    private int toColor(ColorSetting color) {
        return ColorHelper.fromFloats(
            color.getAlphaNormalized(),
            color.getRedNormalized(),
            color.getGreenNormalized(),
            color.getBlueNormalized()
        );
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x34d1,0xb4a6,0x51d5,0x00f7,0x0042,0x0fc9,0xcfe2,0x76b9,0xe114,0x9f18,0x1ba8,0x57fb}, new char[]{0x3485,0xe166,0xb4c7,0x0028,0x579e,0x76da,0x51a1,0x0098,0xcf90,0x1bc1,0x9f7d,0x0fba}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x20a6,0xbd4d,0xc279,0x6c51,0x975b}, new char[]{0x20f6,0x6c34,0xbd2c,0x9729,0xc215}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdc8c,0x9a1d,0x20cb,0x8490,0xbcd7,0x000a}, new char[]{0xdcdc,0x20a4,0xbca3,0x84f9,0x9a72,0x0064}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x10d2,0x93fb,0xe069,0xbbc6,0x16e1,0xad94,0xd26f,0xc924,0x389c,0x17db,0x79d2}, new char[]{0x16b6,0x10bb,0x9395,0xbba2,0x79f2,0xd22c,0x17b3,0xc945,0x38ee,0xe00e,0xadf1}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x36e0,0xb990,0x41c0}, new char[]{0x36a2,0x41af,0xb9e7,0xf2ae}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1671,0x9002,0xb858,0x6b8c,0x20ec,0xbbab,0x084f,0xc025}, new char[]{0xc066,0xbbd9,0x906d,0x209f,0xb82b,0x6bee,0x161e,0x0838}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x48f1,0x9b10,0x2fba,0x5b73,0x2e82,0x663b,0x1eec,0xf592}, new char[]{0x48b7,0x1e85,0x9b62,0x2fdf,0x5b04,0xf5fd,0x2ef0,0x6650}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8dbd,0x43f5,0xb7ff,0xde11,0x4ef9}, new char[]{0x8dfc,0xb78d,0xde63,0x439a,0x4e8e}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xab14,0x2be7,0x20c4,0x3388,0xf287,0x737a,0xf212,0x5f66,0x5783,0x72fd,0xa43d}, new char[]{0x72ad,0x731f,0xa45c,0xf260,0x33e4,0xf2a7,0x5f25,0x57ec,0x2b8b,0xab7b,0x20b6}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x471f,0x2fc3,0x9749,0x52d1,0x6225,0x9834,0x39a5,0xbdf9,0xae28,0xe8e5,0xb2c1,0x17cf}, new char[]{0xbda9,0x9726,0xae5c,0xe88c,0x52be,0x2fad,0x17ef,0xb282,0x985b,0x39c9,0x4770,0x6257}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x48f0,0x6b6c,0xe087,0x9310,0x9a31,0x7bd3,0xd47e,0x3ff6,0xc9af,0x11f0,0x5568,0xc1a0,0x409b,0x713a,0x0a7f,0x1fd0,0x36c0}, new char[]{0x553f,0x4899,0xc9c1,0x3f92,0x1ff0,0x3683,0x7bbb,0xd41f,0x7148,0xc1c7,0x9a54,0x6b4c,0x11b3,0x937f,0xe0eb,0x40f4,0x0a0d}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6f32,0xc401,0x6f11,0x995b,0xa61c,0x61f7,0xdaa4,0xd85c,0xd6e6}, new char[]{0x9919,0x6f7e,0xd691,0xc421,0xdae7,0x6f5d,0xd830,0xa673,0x6185}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfc93,0xfafc,0xecd6,0xf04b,0xbc45,0x12d8,0x5c45,0xc6d4,0xb379,0x62cd,0x3d64,0x6d53,0x5204,0x8270}, new char[]{0x5247,0xfce1,0xbc2a,0x8203,0x5c36,0x3d06,0xfa93,0xeca1,0x62ed,0x129b,0x6d3c,0xb315,0xf024,0xc6a6}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x50bb,0xad9d,0x6ccd,0x4d69,0x23d1,0x2a64,0x28c1,0xa1a5,0x4aae,0x290f,0x3312,0xe447,0x1cda,0x5693}, new char[]{0xaddb,0x4ac7,0x23a3,0x56f6,0x4d1e,0x50d4,0x2a16,0x28aa,0x3332,0x6c8e,0xa1ca,0xe42b,0x2960,0x1ca8}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9fcc,0x3cf4,0x9b51,0xfe9e,0xe171,0x2b97,0x569d,0x9127,0xbcd2,0xdd75,0x3b44}, new char[]{0x3b05,0x3c86,0xdd07,0xe11e,0x9150,0x56bd,0x9b12,0x9fa3,0xbcbe,0x2bf8,0xfeec}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdffc,0xc71b,0x4e82,0x6400,0xb7d1,0xb3d4,0x4533,0x4ec9,0x2afd,0xe3e9,0xe2b0,0x27aa,0x8806,0x9e3a,0x9bb2,0x9b55,0x16d6,0xa953,0x6e59,0x2e71,0x6565,0x02a4,0xb8df,0xc214,0x76c8,0xc05b,0x4855,0x3ebf,0x9833,0xe7c0,0xec94,0x49bc}, new char[]{0x2aaf,0xc03e,0x3ed1,0x02c0,0x9b30,0xe2c2,0x9e49,0x16f6,0xc264,0x2e03,0xc774,0x4ee8,0xecf1,0x4836,0x6e2d,0xb3bd,0xe7ac,0x6500,0xb8ff,0x8872,0x4ebb,0xb7b0,0xa939,0x49d9,0xe38a,0x9847,0x646f,0x27d8,0x455a,0xdf99,0x9bc1,0x76e6}).plainString();
    }
}
