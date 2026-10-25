package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BlockPickerSetting;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.util.Colors;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Highlights the blocks you pick. Loaded chunks around you are scanned a few per tick in the
 * background (sections without any picked block are skipped via the palette), results are
 * cached per chunk and refreshed every couple of seconds.
 */
public class BlockFill extends Feature {
    private final BlockPickerSetting blocks = new BlockPickerSetting(LT.L1,
            LT.L2, LT.L3, LT.L4, LT.L5);
    private final IntSetting range = new IntSetting(LT.L6, 6, 1, 16);
    private final ModeSetting colorMode = new ModeSetting(LT.L7, LT.L8, LT.L8, LT.L9, LT.L10);
    private final ColorSetting color = new ColorSetting(LT.L11, 0xFF60A5FA, o -> colorMode.is(LT.L10));
    private final ModeSetting renderMode = new ModeSetting(LT.L12, LT.L13, LT.L14, LT.L15, LT.L13);
    private final ModeSetting shaderStyle = new ModeSetting(LT.L16, LT.L17, o -> !renderMode.is(LT.L14),
            LT.L17, LT.L18, LT.L19, LT.L20, LT.L21, LT.L22, LT.L23, LT.L24, LT.L25);
    private final FloatSetting glowSize = new FloatSetting(LT.L26, 8f, 1f, 24f, 0.5f, o -> !renderMode.is(LT.L14));
    private final FloatSetting shaderFill = new FloatSetting(LT.L27, 0.2f, 0f, 1f, 0.05f, o -> !renderMode.is(LT.L14));
    private final BoolSetting fill = new BoolSetting(LT.L28, true, o -> !renderMode.is(LT.L15));
    private final FloatSetting fillAlpha = new FloatSetting(LT.L29, 0.2f, 0f, 0.6f, 0.02f, o -> fill.getValue());
    private final BoolSetting tracers = new BoolSetting(LT.L30, false);
    private final IntSetting maxBlocks = new IntSetting(LT.L31, 1500, 100, 10000);

    private record Found(BlockPos pos, int color) {}

    private final Map<Long, List<Found>> byChunk = new HashMap<>();
    private final Map<Long, Long> scannedAt = new HashMap<>();
    private Set<Identifier> lastSelection = Set.of();
    /** Selected blocks as an identity set: no registry lookup per block while scanning. */
    private Set<net.minecraft.block.Block> selected = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    /** Nearest-first flat list rebuilt on tick, so render frames only walk an array. */
    private Found[] visible = new Found[0];
    private Map<Integer, List<BlockPos>> shaderGroups = Map.of();
    private boolean dirty;

    public BlockFill() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b19,0x1ca2,0x82fe,0x37d5,0xbc0a,0xa8a7,0xb12f,0x2277,0xd138}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2}).toString(), LT.L32, Category.RENDER));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        byChunk.clear();
        scannedAt.clear();
        visible = new Found[0];
        shaderGroups = Map.of();
        lastSelection = Set.of();
    }

    private int colorFor(BlockState state, BlockPos pos) {
        return switch (colorMode.getValue().toString()) {
            case "Accent" -> Theme.ACCENT | 0xFF000000;
            case "Custom" -> color.getARGB() | 0xFF000000;
            default -> textureColor(state);
        };
    }

    /** Block -> colour from its own texture (cached; the resource pack's texture if one is active). */
    private static final Map<net.minecraft.block.Block, Integer> TEXTURE_COLORS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Reads the block's particle texture PNG and returns its characteristic colour: pixels are
     * weighted by saturation, so ores take the colour of their specks (diamond -> cyan) instead
     * of the grey stone around them; plain grey/white blocks fall back to their average.
     */
    private int textureColor(BlockState state) {
        return TEXTURE_COLORS.computeIfAbsent(state.getBlock(), b -> {
            try {
                var sprite = mc.getBlockRenderManager().getModels().getModelParticleSprite(state);
                Identifier sid = sprite.getContents().getId();
                Identifier file = Identifier.of(sid.getNamespace(), LT.L33 + sid.getPath() + LT.L34);
                var res = mc.getResourceManager().getResource(file);
                if (res.isEmpty()) return fallback(state);
                try (var in = res.get().getInputStream(); var img = net.minecraft.client.texture.NativeImage.read(in)) {
                    double wr = 0, wg = 0, wb = 0, wsum = 0, ar = 0, ag = 0, ab = 0;
                    int n = 0, h = Math.min(img.getHeight(), img.getWidth()); // first frame of animated textures
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < img.getWidth(); x++) {
                            int c = img.getColorArgb(x, y);
                            if (((c >>> 24) & 255) < 128) continue;
                            int r = (c >> 16) & 255, g = (c >> 8) & 255, bl = c & 255;
                            float[] hsb = java.awt.Color.RGBtoHSB(r, g, bl, null);
                            double w = hsb[1] * hsb[1] * (0.3 + hsb[2]);
                            wr += r * w; wg += g * w; wb += bl * w; wsum += w;
                            ar += r; ag += g; ab += bl; n++;
                        }
                    }
                    if (n == 0) return fallback(state);
                    int r, g, bl;
                    if (wsum > n * 0.02) { // the texture has real colour: use the coloured pixels
                        r = (int) (wr / wsum); g = (int) (wg / wsum); bl = (int) (wb / wsum);
                    } else {
                        r = (int) (ar / n); g = (int) (ag / n); bl = (int) (ab / n);
                    }
                    // lift dark colours a little so outlines stay visible
                    float[] hsb = java.awt.Color.RGBtoHSB(r, g, bl, null);
                    return 0xFF000000 | (java.awt.Color.HSBtoRGB(hsb[0], Math.min(1f, hsb[1] * 1.15f), Math.max(0.55f, hsb[2])) & 0xFFFFFF);
                }
            } catch (Throwable t) {
                return fallback(state);
            }
        });
    }

    private int fallback(BlockState state) {
        int rgb = state.getMapColor(mc.world, BlockPos.ORIGIN).color;
        return 0xFF000000 | (rgb == 0 ? 0xFFFFFF : rgb);
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (isNull()) return;
        // selection changed -> rescan everything
        Set<Identifier> sel = Set.copyOf(blocks.getSelectedIds());
        if (!sel.equals(lastSelection)) {
            lastSelection = sel;
            byChunk.clear();
            scannedAt.clear();
            Set<net.minecraft.block.Block> set = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (Identifier id : sel) Registries.BLOCK.getOptionalValue(id).ifPresent(set::add);
            selected = set;
            dirty = true;
        }
        if (sel.isEmpty()) {
            visible = new Found[0];
            shaderGroups = Map.of();
            return;
        }

        ChunkPos center = mc.player.getChunkPos();
        int r = range.getValue();
        long now = System.currentTimeMillis();
        // forget chunks that are now out of range
        if (byChunk.keySet().removeIf(k -> Math.abs(ChunkPos.getPackedX(k) - center.x) > r || Math.abs(ChunkPos.getPackedZ(k) - center.z) > r)) dirty = true;
        scannedAt.keySet().retainAll(byChunk.keySet());

        // scan the stalest chunks first, a handful per tick (nearest first on ties)
        int budget = 4;
        for (int ring = 0; ring <= r && budget > 0; ring++) {
            for (int dx = -ring; dx <= ring && budget > 0; dx++) {
                for (int dz = -ring; dz <= ring && budget > 0; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int cx = center.x + dx, cz = center.z + dz;
                    long key = ChunkPos.toLong(cx, cz);
                    Long at = scannedAt.get(key);
                    if (at != null && now - at < 6000) continue;
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                    if (chunk == null) continue;
                    List<Found> found = scan(chunk);
                    List<Found> old = byChunk.put(key, found);
                    if (old == null ? !found.isEmpty() : !old.equals(found)) dirty = true;
                    scannedAt.put(key, now);
                    budget--;
                }
            }
        }
        // re-sort nearest-first every few ticks (or when results change) so Max Shown keeps the closest
        if (dirty || mc.player.age % 10 == 0) rebuild();
    }

    private void rebuild() {
        dirty = false;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        List<Found> all = new ArrayList<>();
        for (List<Found> l : byChunk.values()) all.addAll(l);
        all.sort(java.util.Comparator.comparingDouble(f -> f.pos().getSquaredDistance(px, py, pz)));
        int n = Math.min(all.size(), maxBlocks.getValue());
        Found[] arr = all.subList(0, n).toArray(new Found[0]);
        Map<Integer, List<BlockPos>> groups = new java.util.LinkedHashMap<>();
        for (Found f : arr) {
            List<BlockPos> g = groups.get(f.color());
            if (g == null) {
                if (groups.size() >= com.utilitymod.echo.render.BlockShaderRenderer.MAX_GROUPS) continue;
                groups.put(f.color(), g = new ArrayList<>());
            }
            g.add(f.pos());
        }
        visible = arr;
        shaderGroups = groups;
    }

    private List<Found> scan(WorldChunk chunk) {
        List<Found> out = new ArrayList<>();
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = chunk.getBottomSectionCoord();
        int baseX = chunk.getPos().getStartX(), baseZ = chunk.getPos().getStartZ();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection s = sections[i];
            if (s == null || s.isEmpty()) continue;
            Set<net.minecraft.block.Block> sel = selected;
            if (!s.hasAny(st -> sel.contains(st.getBlock()))) continue;
            int baseY = (bottom + i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState st = s.getBlockState(x, y, z);
                        if (!sel.contains(st.getBlock())) continue;
                        m.set(baseX + x, baseY + y, baseZ + z);
                        out.add(new Found(m.toImmutable(), colorFor(st, m)));
                    }
                }
            }
        }
        return out;
    }

    /** Shader mode: one style pass per colour group (max 6), drawn over the screen. */
    @EventSubscribe
    private void onRender2D(com.utilitymod.echo.event.impl.EventRender2DGui event) {
        if (renderMode.is(LT.L14) || visible.length == 0 || mc.player == null) return;
        Map<Integer, List<BlockPos>> groups = shaderGroups;
        var style = com.utilitymod.echo.render.ShaderEsp.Style.valueOf(shaderStyle.getValue().toString().toUpperCase(java.util.Locale.ROOT));
        com.utilitymod.echo.render.BlockShaderRenderer.render(event.getContext(), com.utilitymod.echo.EchoEvents.lastFrame(),
                groups, style, glowSize.getValue(), shaderFill.getValue());
    }

    @EventSubscribe
    private void onRender(EventRender3D event) {
        if (renderMode.is(LT.L15) || visible.length == 0 || mc.player == null) return;
        var d = event.getDraw3D().getCustomTarget();
        float fa = fillAlpha.getValue();
        boolean doFill = fill.getValue(), doTracers = tracers.getValue();
        var eye = mc.player.getEyePos();
        var look = mc.player.getRotationVec(1f);
        for (Found f : visible) {
            double x = f.pos().getX(), y = f.pos().getY(), z = f.pos().getZ();
            // skip boxes behind the camera (tracers still draw so you can turn to them)
            double vx = x + 0.5 - eye.x, vy = y + 0.5 - eye.y, vz = z + 0.5 - eye.z;
            boolean behind = vx * look.x + vy * look.y + vz * look.z < -1.0;
            int c = f.color();
            if (!behind) {
                int line = Colors.withAlpha(c, 0.9f);
                if (doFill) d.box(x, y, z, 1, 1, 1, Colors.withAlpha(c, fa));
                d.line(x, y, z, x + 1, y, z, line); d.line(x + 1, y, z, x + 1, y, z + 1, line);
                d.line(x + 1, y, z + 1, x, y, z + 1, line); d.line(x, y, z + 1, x, y, z, line);
                d.line(x, y + 1, z, x + 1, y + 1, z, line); d.line(x + 1, y + 1, z, x + 1, y + 1, z + 1, line);
                d.line(x + 1, y + 1, z + 1, x, y + 1, z + 1, line); d.line(x, y + 1, z + 1, x, y + 1, z, line);
                d.line(x, y, z, x, y + 1, z, line); d.line(x + 1, y, z, x + 1, y + 1, z, line);
                d.line(x + 1, y, z + 1, x + 1, y + 1, z + 1, line); d.line(x, y, z + 1, x, y + 1, z + 1, line);
            }
            if (doTracers) d.line(eye.x, eye.y - 0.2, eye.z, x + 0.5, y + 0.5, z + 0.5, Colors.withAlpha(c, 0.6f));
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe384,0x2282,0xe4ef,0xde3b,0x7dc4,0xa234}, new char[]{0xe3c6,0xa258,0x22ed,0xde58,0xe484,0x7db7}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2d54,0xd4db,0xb93b,0xaf6e,0x3ed4,0x11b1,0x22c9,0xeec6,0x1bf4,0x3e74,0x3600,0xa4b8,0x522e,0xae44,0xf766,0x05f4,0x8f99,0x45ac,0x9166,0x3d7d,0xce9a}, new char[]{0xeeab,0xcef3,0xf708,0x524b,0x3eb7,0xb949,0x3d1c,0xa4de,0x1b80,0x05ce,0x9102,0x3e1d,0xaf0f,0x22a4,0x45c3,0xae2a,0x2d30,0x11ee,0xd4b4,0x3672,0x8ffc}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8f5d,0xcc6a,0x390c,0xf5d5,0xf1ac,0xb85c,0x7a98,0x0946,0x1515,0x0656,0xa52f,0xd7ae,0x7a51,0xc2ba,0x9296,0x52da,0x8bdc,0xabbe,0xe32d,0x7722,0xf4c8,0x0b85,0x4598,0x6778,0x42fe,0x9df4,0x10e6,0x87e6,0x2930,0x6102,0xf9f4}, new char[]{0xabd3,0x9d9d,0x45f6,0xa54a,0x396f,0x1567,0x7af9,0xc2dc,0xb828,0xcc50,0x1082,0xf5b0,0xf4ad,0x8f2d,0x428d,0x8bb0,0x0637,0xd7da,0x671d,0xf1f3,0x6166,0xf99d,0x52bb,0xe340,0x92f9,0x0beb,0x2954,0x7a0e,0x0929,0x8794,0x7747}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbdf9,0x01a7,0x1457,0xce9f,0x9434,0x2f5e,0x5fcd,0xd1ac,0x461c,0x4dbf,0x57d2,0x47d5,0x1891,0x35a4,0x84d3,0x94de,0x04a6,0x00a5,0x720d,0x5094,0xd4e2,0xf6c7,0x108d,0xa9a0}, new char[]{0x7260,0x47bc,0x5fa3,0x04c3,0x35c7,0x466e,0xa9c1,0x4dd9,0xd1d8,0xbdc3,0x10ec,0x50fa,0xd481,0x18f8,0x01c2,0x945a,0x1423,0xcec0,0x00c1,0x2f3b,0xf6a5,0x57a0,0x94b7,0x84a0}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1dd0,0x8181,0x57b9,0x522f,0x72a3,0xa547,0xea90,0x1aea,0xe61c,0x753d,0xfbe7,0x9fb9,0x545a,0x7ef7,0xcdbc,0xf975,0x3d86}, new char[]{0xfb8a,0x9fd0,0x57d7,0xe679,0xf916,0x81f3,0xeaf1,0x7e91,0x1a9e,0x7299,0x1da3,0x3df6,0x524e,0xcdcb,0x7553,0xa522,0x5428}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x51dc,0x91ce,0x262f,0x5acc,0x0551,0x0bb0,0x748d,0xddfc,0x84d2,0xeeb9,0xe413,0xfeb2,0x28d3,0x64b2}, new char[]{0x8480,0x28b2,0x51b2,0xfed5,0x74e8,0x260f,0x0579,0x0bd3,0xdd94,0x91bb,0xe47d,0x5aa7,0x64c1,0xee90}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3d4a,0xf537,0x7385,0xbe98,0x6e86}, new char[]{0x6ec5,0xbef7,0x73e9,0xf558,0x3d38}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0d31,0x108e,0xacee,0xe59e,0x32e6}, new char[]{0x10cc,0xac82,0x0d5e,0x3285,0xe5f5}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3d6a,0x0da1,0x60c9,0xcd8b,0xfff6,0x06d7}, new char[]{0xcdca,0x0dc2,0x60aa,0x06b2,0x3d04,0xff82}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf087,0x6e89,0x10a4,0x8978,0x643b,0x812f}, new char[]{0x6eca,0x890d,0x815c,0xf0f3,0x10cb,0x6456}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5a6a,0x3805,0x76d5,0xf15c,0x726b,0x371e,0xb0eb,0x8e48,0x8e8f,0x7f64,0xd815,0x2fad}, new char[]{0x7228,0xd860,0x8e3b,0x376a,0xf133,0xb086,0x76f5,0x8ecc,0x7f0b,0x5a06,0x2fc2,0x3877}).toString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe2f9,0x227d,0xcd20,0x98dc,0xddf5,0x4abd}, new char[]{0xdda7,0x98b9,0xe297,0x4ad9,0x2218,0xcd52}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0020,0xa127,0x1f03,0x934f}, new char[]{0x1f41,0x9320,0xa153,0x0048}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe45d,0x94c4,0x2554,0x6eaa,0x4dc5}, new char[]{0x6ee8,0xe432,0x4dbd,0x2531,0x94b7}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8ce4,0xe67d,0x0308,0x6d81,0xe4fc,0xfa55}, new char[]{0xe4af,0x0360,0xe61c,0x6de5,0xfa30,0x8c96}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcad7,0xcc46,0xe95f,0xce26,0xbf56,0xad6a,0x67d8,0x82ad,0x2084,0x1545,0x10fa,0x092f}, new char[]{0x20d7,0xe937,0xad0b,0x094b,0xcab2,0x67aa,0x828d,0x10a9,0xce52,0xcc3f,0xbf3a,0x1520}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4454,0x235d,0x871a,0x8e21}, new char[]{0x231a,0x8e4d,0x443b,0x876d}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc4c1,0x6a12,0xe4cd,0x1200,0x337b,0x430c,0xe8aa}, new char[]{0x3334,0x1275,0xe4b9,0xc4ad,0x4365,0xe8c4,0x6a77}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb96c,0x4219,0xe5af,0xbf76}, new char[]{0xe5ee,0x426c,0xb91e,0xbf17}).plainString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbc9b,0xe370,0x8e0b,0x65c5,0x0f2d,0xc6b1,0xac3d}, new char[]{0xac6f,0xbcfa,0xe319,0x0f43,0x65a7,0x8e64,0xc6c6}).plainString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb175,0x3130,0x3c42,0x2c3a,0xce78,0xe1a2}, new char[]{0xe1e1,0xb11d,0x3c30,0x2c55,0x315d,0xce19}).plainString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xda67,0x040d,0x99fb,0x04cc,0x8d2d}, new char[]{0x045d,0x998e,0x8d41,0xda14,0x04a9}).plainString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xad6b,0x40d2,0x65d4,0x4ae6,0x36a8,0x9b7b,0xa7ae,0x80f2}, new char[]{0x409a,0x65bb,0x4a8a,0x36c7,0x8095,0x9b09,0xa7cf,0xad06}).plainString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x72b0,0x259a,0x6e2d,0x353c,0x2d10,0x1579,0x2ee0,0xe8b0,0xeb33}, new char[]{0xe8e3,0x2d64,0x355d,0x25e8,0x72d6,0x2e89,0x6e48,0xeb5f,0x151d}).plainString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcf7b,0x6778,0xebc4,0x2dce,0x4acc}, new char[]{0xeb87,0x6710,0xcf1a,0x2da3,0x4abf}).plainString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x690d,0xb1ab,0xb073,0x7356,0xe6de,0x654b,0x4eb4,0x70da,0x3fca}, new char[]{0x709d,0xb1c7,0x4edb,0x697a,0xb053,0x6518,0x3fa3,0xe6a4,0x7333}).toString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7978,0xd29f,0x87fe,0x1117,0x2bb0,0xda9d,0xacce,0xe400,0xe5fa,0xe8ff,0xdd00}, new char[]{0xe8ac,0xdaf5,0xe461,0xdd64,0xe59f,0x878c,0x7958,0xac88,0x2bd9,0xd2f3,0x117b}).plainString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0d4f,0xc625,0xdfde,0x05ce}, new char[]{0xc663,0x0d26,0xdfb2,0x05a2}).plainString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7c23,0xd958,0xd334,0x524a,0x4097,0x6d26,0xa500,0xd7f3,0x0e1e,0x6cf7,0x7fd4,0x9a82}, new char[]{0x40d1,0xd79a,0x9aee,0x6d4a,0x7c03,0x7f9b,0xa570,0xd355,0x6c94,0xd931,0x0e6a,0x5233}).toString();
        static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4a1a,0xd698,0xa88b,0xb598,0xa03f,0x7a39,0xc9b9}, new char[]{0x4a4e,0x7a4b,0xd6f9,0xa05c,0xa8ee,0xc9cb,0xb5eb}).plainString();
        static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x057c,0x609a,0xffdd,0x30cd,0xe4b6,0x4d56,0x142f,0x8a79,0x1fca}, new char[]{0x8a34,0xffbc,0x1457,0x30ed,0x4d05,0x0514,0x1fa5,0xe4c1,0x60f4}).toString();
        static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x345d,0x09aa,0x7e1a,0x3617,0x30b8,0x1111,0x104e,0x2eb0,0x785b,0x3086,0xe7a3,0x3034,0x188e,0x9371,0xed1b,0x074b,0x0c56,0xe8c9,0x8c91,0x2a18,0x3cf3,0xb0eb,0xca21,0xeac9,0xce19,0x79f4,0xc931,0x8ac7,0x10da,0x684f,0x286f,0x5917,0xcacf,0x8e0e,0x0adc,0x50e2,0xde88,0x194c,0x1957,0x0fc9,0x7b96,0xf7dd,0x9048,0x15fe,0xd41a,0xe215,0xa5f5,0x042c,0xd13a,0x8b44,0xb460,0xb7c1,0x8fe1,0xde27,0x277f}, new char[]{0x79bc,0xde4e,0x1176,0xb083,0x8e62,0xa59c,0x902f,0x8cf9,0x782f,0xeaba,0x040c,0x158a,0x2717,0xce7c,0x686f,0xdeea,0xe279,0x2edf,0x1934,0xc95a,0x7e69,0x8b64,0xb419,0x1021,0x5097,0x3637,0x18fe,0x0ab5,0x3c90,0x8f8a,0xed3b,0x7bbe,0x8aa4,0x1920,0x3434,0x3057,0x597c,0x076b,0xca63,0x0c3a,0xd475,0xf7be,0x30ed,0x2a6b,0xcaef,0x0fbd,0x10b5,0xe783,0xb7a2,0x30d0,0x09c5,0x2800,0x9302,0xe8ac,0xd113}).toString();
        static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf6d4,0x2348,0x31de,0x918e,0xd086,0xefc7,0x8039,0x59c6,0xb805}, new char[]{0xefb3,0x805c,0x91f6,0x31aa,0xb870,0xd0f4,0xf6b1,0x233b,0x59e9}).plainString();
        static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe1bc,0x1a43,0x89ad,0xebbf}, new char[]{0x1a6d,0xebcf,0x89c3,0xe1db}).plainString();
    }
}
