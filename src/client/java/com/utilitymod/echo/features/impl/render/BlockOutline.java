package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;

/** Accent-coloured outline + soft fill on the block you look at, gliding between blocks. */
public class BlockOutline extends Feature {
    private final BoolSetting fill = new BoolSetting(LT.L1, true);
    private final FloatSetting fillAlpha = new FloatSetting(LT.L2, 0.18f, 0f, 0.6f, 0.02f, o -> fill.getValue());
    private final FloatSetting speed = new FloatSetting(LT.L3, 0.35f, 0.05f, 1f, 0.05f);

    private double[] cur; // minX minY minZ maxX maxY maxZ
    private float shown;

    public BlockOutline() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2277,0x962f,0x11f9,0xeb8f,0x82d8,0x0b13,0xa8a7,0xbc0a,0x1c86,0xd138,0x37d5,0x6553,0xb12f}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3b79,0xf893,0x93a6,0xc5cf,0x4a1f,0x3ff2,0x11e7,0x76f4,0x962c,0x5f64,0xd930,0x82ca,0x3313,0x2650,0xa46c,0xd2a0,0x15c8,0x50c0,0x1857,0x2277,0xb98c,0x9e43,0xd129,0xcde7,0xf04a,0x8af4,0x4780,0xbc06,0x37ca,0xef58,0xb167,0x9bfc,0x6558,0xc149,0xa8a6,0xac46,0x1c9e,0x0cdf,0x866c,0x7ec8,0xeb88,0x0024,0xe735,0x04b9,0x66fa,0x7269,0x6d79,0xb508,0x2a9a,0x58aa,0xa1ad,0x0b7c,0xfce0}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e}).toString(), Category.RENDER));
    }

    @EventSubscribe
    private void onRender(EventRender3D event) {
        if (mc.world == null) return;
        Box target = null;
        if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            var state = mc.world.getBlockState(hit.getBlockPos());
            var shape = state.getOutlineShape(mc.world, hit.getBlockPos());
            if (!shape.isEmpty()) target = shape.getBoundingBox().offset(hit.getBlockPos());
        }
        shown += ((target != null ? 1f : 0f) - shown) * 0.3f;
        if (target != null) {
            double[] t = {target.minX, target.minY, target.minZ, target.maxX, target.maxY, target.maxZ};
            if (cur == null || shown < 0.05f) cur = t.clone();
            float k = speed.getValue();
            for (int i = 0; i < 6; i++) cur[i] += (t[i] - cur[i]) * k;
        }
        if (cur == null || shown < 0.02f) return;

        var d = event.getDraw3D().getCustomTarget();
        double e = 0.002; // avoid z-fighting
        double x0 = cur[0] - e, y0 = cur[1] - e, z0 = cur[2] - e, x1 = cur[3] + e, y1 = cur[4] + e, z1 = cur[5] + e;
        int line = Colors.withAlpha(Theme.ACCENT, shown);
        if (fill.getValue()) d.box(x0, y0, z0, x1 - x0, y1 - y0, z1 - z0, Colors.withAlpha(Theme.ACCENT, fillAlpha.getValue() * shown));
        d.line(x0, y0, z0, x1, y0, z0, line); d.line(x1, y0, z0, x1, y0, z1, line);
        d.line(x1, y0, z1, x0, y0, z1, line); d.line(x0, y0, z1, x0, y0, z0, line);
        d.line(x0, y1, z0, x1, y1, z0, line); d.line(x1, y1, z0, x1, y1, z1, line);
        d.line(x1, y1, z1, x0, y1, z1, line); d.line(x0, y1, z1, x0, y1, z0, line);
        d.line(x0, y0, z0, x0, y1, z0, line); d.line(x1, y0, z0, x1, y1, z0, line);
        d.line(x1, y0, z1, x1, y1, z1, line); d.line(x0, y0, z1, x0, y1, z1, line);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b88,0x8637,0xfaf6,0x0286}, new char[]{0xfab0,0x02ef,0x865b,0x0be4}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb99d,0x13e7,0x3c64,0x21bd,0x7bf5,0x3342,0x91a6,0x7058,0x992e,0x5c47,0x253a,0x5c89}, new char[]{0x3304,0x5c2e,0x7b99,0x7034,0xb9bd,0x5cc6,0x3c14,0x1386,0x2559,0x21d4,0x995a,0x91df}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b27,0xccc0,0x7ea8,0x1983,0x04d7,0x619b,0x4db1,0x6b08,0x74d1,0x9e9a,0xe39f}, new char[]{0x0b60,0x4ddd,0x74b8,0xe3fb,0x04b2,0x19a3,0x7efb,0x9eea,0x61fe,0xcca5,0x6b6c}).toString();
    }
}
