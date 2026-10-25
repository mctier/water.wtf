package com.utilitymod.echo.render;

import com.utilitymod.UtilityMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * HUD icons. Picture icons (heart, shield, potion, ham, bubble - from SoupAPI-recode, GPL-3.0,
 * see textures/gui/soup/CREDITS.txt) are read from the jar and registered as textures directly
 * (a late-injected jar is invisible to the resource manager). Module icons are vanilla items.
 */
public final class Icons {
    private static final Map<String, Identifier> LOADED = new HashMap<>();
    private static final Map<String, Integer> SIZE = new HashMap<>();

    private Icons() {}

    private static Identifier load(String name) {
        Identifier id = LOADED.get(name);
        if (id != null || LOADED.containsKey(name)) return id;
        try (InputStream in = Icons.class.getResourceAsStream(LT.L1 + name + LT.L2)) {
            if (in == null) throw new IllegalStateException(LT.L3);
            NativeImage img = NativeImage.read(in);
            id = Identifier.of(UtilityMod.MOD_ID, LT.L4 + name);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(id::toString, img));
            SIZE.put(name, img.getWidth());
        } catch (Exception e) {
            id = null;
        }
        LOADED.put(name, id);
        return id;
    }

    /** Draws a picture icon ("heart", "shield", "potion", "ham", "bubble") as a size x size square, tinted. */
    public static void draw(DrawContext ctx, String name, float x, float y, float size, int argb) {
        Identifier id = load(name);
        if (id == null) return;
        int tex = SIZE.getOrDefault(name, 512);
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(size / tex, size / tex);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, tex, tex, tex, tex, argb);
        m.popMatrix();
    }

    // ---- module icons (vanilla items, matched by name keywords) ------------------------------------

    private static final Object[][] KEYWORDS = {
            {LT.L5, Items.MACE}, {LT.L6, Items.END_CRYSTAL}, {LT.L7, Items.RESPAWN_ANCHOR},
            {LT.L8, Items.TNT_MINECART}, {LT.L9, Items.TOTEM_OF_UNDYING}, {LT.L10, Items.SHIELD},
            {LT.L11, Items.TRIDENT}, {LT.L12, Items.CROSSBOW}, {LT.L13, Items.SPYGLASS}, {LT.L14, Items.SLIME_BALL},
            {LT.L15, Items.CLOCK}, {LT.L16, Items.COBWEB}, {LT.L17, Items.COBWEB}, {LT.L18, Items.BUCKET},
            {LT.L19, Items.DIAMOND_PICKAXE}, {LT.L20, Items.ENDER_EYE}, {LT.L21, Items.FEATHER},
            {LT.L22, Items.SUGAR}, {LT.L23, Items.SUGAR}, {LT.L24, Items.RABBIT_FOOT}, {LT.L25, Items.STONE_STAIRS},
            {LT.L26, Items.STONE_STAIRS}, {LT.L27, Items.COBWEB}, {LT.L28, Items.HONEY_BOTTLE}, {LT.L29, Items.CLOCK},
            {LT.L30, Items.PISTON}, {LT.L31, Items.LEATHER_BOOTS}, {LT.L32, Items.LEATHER_BOOTS}, {LT.L33, Items.TRIDENT},
            {LT.L34, Items.IRON_SWORD}, {LT.L35, Items.BARRIER},
            {LT.L36, Items.ENDER_EYE}, {LT.L37, Items.CHEST}, {LT.L38, Items.LEAD}, {LT.L39, Items.NAME_TAG},
            {LT.L40, Items.PAINTING}, {LT.L41, Items.TARGET}, {LT.L42, Items.SPYGLASS},
            {LT.L43, Items.BLAZE_POWDER}, {LT.L44, Items.SLIME_BALL}, {LT.L45, Items.ARROW}, {LT.L46, Items.BOW},
            {LT.L47, Items.TORCH}, {LT.L48, Items.RED_DYE}, {LT.L49, Items.BARRIER}, {LT.L50, Items.IRON_SWORD},
            {LT.L51, Items.GLASS_PANE}, {LT.L52, Items.BELL}, {LT.L53, Items.COMPASS}, {LT.L54, Items.COMPASS},
            {LT.L55, Items.CHEST}, {LT.L56, Items.POTION}, {LT.L57, Items.WRITABLE_BOOK}, {LT.L58, Items.WITHER_SKELETON_SKULL},
            {LT.L59, Items.FIREWORK_STAR}, {LT.L60, Items.GLASS}, {LT.L61, Items.CLOCK},
            {LT.L62, Items.ENDER_PEARL}, {LT.L63, Items.SHULKER_BOX}, {LT.L64, Items.WHITE_BANNER},
            {LT.L65, Items.TARGET}, {LT.L66, Items.MAP}, {LT.L67, Items.OBSIDIAN}, {LT.L68, Items.ANCIENT_DEBRIS},
            {LT.L69, Items.EMERALD}, {LT.L70, Items.EMERALD}, {LT.L71, Items.SPYGLASS}, {LT.L72, Items.PAPER},
            {LT.L73, Items.ENDER_PEARL}, {LT.L74, Items.GOLD_INGOT}, {LT.L75, Items.CAKE},
    };
    private static final Map<String, ItemStack> CACHE = new HashMap<>();

    public static ItemStack moduleIcon(String moduleName) {
        return CACHE.computeIfAbsent(moduleName, n -> {
            String s = n.toLowerCase(Locale.ROOT) + " ";
            for (Object[] k : KEYWORDS) if (s.contains((String) k[0])) return new ItemStack((Item) k[1]);
            return new ItemStack(Items.BOOK);
        });
    }

    /** Item icon at (x, y), {@code size} px square. */
    public static void drawItem(DrawContext ctx, ItemStack stack, float x, float y, float size) {
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(size / 16f, size / 16f);
        ctx.drawItem(stack, 0, 0);
        m.popMatrix();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9157,0xf2a0,0x6913,0xc217,0x888f,0xd5ae,0x8a19,0x48ba,0x8e39,0x2245,0x95ba,0xcb47,0x9520,0x7931,0x9ac8,0x6e91,0x806b,0x121a,0x6e10,0x4782,0x2dd5,0x3494,0x9764,0xae35,0xa19d,0xe573,0xd64a,0xb181,0x58c5,0xf801,0xdf03,0x96d7,0x8741,0x7977,0x1d85,0xafcb,0x0251}, new char[]{0x6ebe,0x8e58,0x8732,0x96a4,0x58a0,0xc263,0xd639,0xf28f,0x9122,0x2231,0x47eb,0x1276,0xd5c7,0xdf77,0x1dfc,0x954d,0x88e0,0x95de,0x4895,0xf875,0xae50,0x0229,0xb1f5,0x9abd,0xcb35,0xe516,0x8a6a,0x7958,0xa1fa,0x2da0,0x7958,0x8044,0xafb8,0x697c,0x6e65,0x9714,0x34bb}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfdff,0x92ee,0x44f3,0x6553}, new char[]{0x92c0,0x6523,0x449d,0xfd98}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xabce,0xe86f,0xd053,0x0902,0x927b,0x0390,0xd4c4}, new char[]{0xd4a9,0x9212,0xe81c,0x03e3,0xd03a,0x096c,0xaba9}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd7c6,0x863a,0x09bf,0x1edb,0x346e,0x392e}, new char[]{0x1eb2,0x8659,0x3941,0xd7a8,0x09cc,0x3441}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd548,0x78ce,0x343a,0x8471}, new char[]{0x841c,0x345b,0xd52b,0x78ab}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x59e6,0xe1c8,0xbc8d,0x6d0a,0x412f,0xd0ce,0xa0dc}, new char[]{0x6d69,0xd0bc,0x4156,0xe1bb,0xa0a8,0x5987,0xbce1}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xba54,0x5147,0x8586,0x7377,0x3e16,0x3758}, new char[]{0x7316,0x3736,0x85e5,0x512f,0x3e79,0xba26}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x90c5,0xfe2d,0xa435,0xa377}, new char[]{0xa456,0x90a4,0xfe5f,0xa303}).plainString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb74e,0xc95d,0x88f4,0x101e,0x0ef3}, new char[]{0x0e87,0xb721,0x8880,0xc938,0x1073}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7953,0x1ef1,0xe470,0x2a87,0xb5ae,0x36d5}, new char[]{0xe403,0x1e99,0x36bc,0x2ae2,0xb5c2,0x7937}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1ece,0x5625,0x177c,0x6aed,0xfc98}, new char[]{0xfceb,0x5655,0x1719,0x6a8c,0x1ebc}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7c11,0x5a2e,0x2a95,0x940f,0xa27f,0x62bb,0x5951}, new char[]{0x5a5a,0x5923,0xa216,0x9468,0x62dc,0x7c74,0x2ae7}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x99a7,0x55e8,0x98f8}, new char[]{0x99c6,0x9891,0x5585,0x57ba}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfb08,0x9d87,0xa4f8,0xbdab,0x3289,0xf3b6}, new char[]{0x9def,0xbdc2,0xfb7c,0xf3d4,0xa497,0x32f1}).plainString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5eb5,0xe63c,0xb317,0x2a1e,0xc2ec,0x9be8,0x9105,0x2e32,0xca18}, new char[]{0xb375,0x9164,0xc28f,0x9b83,0x5ec1,0x2e40,0xe65d,0xca7b,0x2a75}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8bac,0x3d9e,0x7992,0xdfeb}, new char[]{0xdf98,0x8bd8,0x79e7,0x3df0}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdb97,0x568c,0x3506}, new char[]{0x56ef,0xdbf8,0x3564,0xfbe9}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x18d0,0x755c,0x1379,0x7b18,0xabc3}, new char[]{0xaba7,0x18a2,0x1318,0x7535,0x7b76}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x57c8,0xfa27,0x8f9a,0xc141}, new char[]{0xfa53,0xc12e,0x8ff5,0x57a4}).plainString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8207,0x7960,0x5bf4,0x5118,0x9e29,0x01ee,0x3830}, new char[]{0x3856,0x8275,0x5b91,0x018b,0x7903,0x9e48,0x5175}).plainString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbe40,0x3e18,0x9f40,0xa04d}, new char[]{0x9f26,0xa02c,0x3e74,0xbe2c}).plainString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x20f8,0x0777,0x9096,0x757c,0xce61,0xab0f}, new char[]{0x90e5,0xce11,0xab7d,0x7515,0x0719,0x208c}).plainString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf448,0xb465,0x3078,0x38c4,0xeac8}, new char[]{0x300b,0xeab8,0x38a1,0xf42d,0xb401}).plainString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x301a,0x2865,0x628e,0xf445}, new char[]{0x3070,0x2810,0xf428,0x62fe}).plainString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xebbd,0x0bd6,0x8a50,0x2f6f}, new char[]{0x2f1c,0x8a24,0xebd8,0x0ba6}).plainString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfd7b,0x4bde,0xf0b1,0x37af,0x9345,0x6e48}, new char[]{0x9336,0x6e3c,0xf0d0,0x37c6,0x4bac,0xfd08}).plainString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8e17,0x52dc,0x5230}, new char[]{0x5247,0x52b9,0x8e75,0x9faa}).plainString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xab85,0x6840,0xb34f,0x98c9}, new char[]{0x6833,0xb323,0x98a6,0xabf2}).plainString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x64cb,0xe904,0xb077,0x53e4,0xc587}, new char[]{0xc5f3,0x538d,0x64a6,0xb012,0xe976}).plainString();
        static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9596,0xd8a6,0x84db,0xa816,0x63fa,0xac04,0x2963,0x5f5d}, new char[]{0x2915,0xac61,0xa87a,0x95f9,0xd8c5,0x84b2,0x5f29,0x6383}).plainString();
        static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x250c,0xab24,0x2ba4,0x506d}, new char[]{0x257b,0x2bc5,0x5001,0xab4f}).plainString();
        static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2e55,0x77c4,0x668a,0x32ec}, new char[]{0x66e7,0x77ab,0x329a,0x2e30}).plainString();
        static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1b3a,0x2074,0x25f5,0x18ed,0x7065}, new char[]{0x2018,0x1898,0x1b54,0x7002,0x2590}).plainString();
        static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4250,0x7a36,0xebfa,0x2af7,0x476c}, new char[]{0x7a55,0x2a98,0x423d,0xeb98,0x4703}).plainString();
        static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x917c,0x4143,0x22a0,0x32f9}, new char[]{0x9112,0x4136,0x3295,0x22cc}).plainString();
        static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x591c,0x16e4,0xef6a}, new char[]{0x5979,0xef19,0x1694,0x3f7b}).plainString();
        static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf73d,0xdcb3,0xa658,0xed35,0xf983,0x1fd6,0x90bd}, new char[]{0xed46,0xa62c,0x90d2,0x1fa4,0xdcd2,0xf9e4,0xf758}).plainString();
        static final String L38 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7cd5,0xeb9e,0x132a,0x0508,0xc6f4,0x9c55}, new char[]{0xc680,0x7ca7,0x9c34,0xebfd,0x134f,0x057a}).plainString();
        static final String L39 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc28a,0x5303,0xb833,0xee9b,0x16bc,0x0c4e,0x24db}, new char[]{0x24b5,0x16dd,0xb85e,0xc2ef,0x0c3a,0x5362,0xeefc}).plainString();
        static final String L40 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1e06,0xa9e0,0x47b1}, new char[]{0xa988,0x47c4,0x1e62,0x46a5}).plainString();
        static final String L41 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1805,0xc0cd,0x0511,0x1996,0xa48f,0xcb37,0x2aa7,0xefab,0x6ea4}, new char[]{0xcb54,0x0563,0xc0a2,0x19e5,0x2ad4,0x6ecc,0xa4ee,0xefc2,0x1877}).plainString();
        static final String L42 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xddc3,0x02d7,0x8654,0x91e8}, new char[]{0xddb9,0x02b8,0x863b,0x9185}).plainString();
        static final String L43 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x92ef,0xa740,0xbc6f,0x29ec,0x20e7}, new char[]{0x2998,0x2095,0xbc0e,0x9286,0xa72c}).plainString();
        static final String L44 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5b74,0xd250,0xad59,0xef55,0xb80d,0xd1e2}, new char[]{0xd233,0xef3c,0xb87f,0x5b17,0xad35,0xd187}).plainString();
        static final String L45 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8a9c,0xa0ba,0x123a,0x6056,0xbf4b,0x5e24}, new char[]{0xa0d7,0x8afd,0x5e56,0x603d,0x125f,0xbf39}).plainString();
        static final String L46 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x030b,0x6054,0xef73,0x0d3c,0x2041,0x4dd9,0x9551,0x4915,0x6936}, new char[]{0x6942,0x4967,0x6035,0x202b,0x4dbc,0x0d5f,0x037f,0xef1c,0x9523}).plainString();
        static final String L47 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd750,0xda27,0xe5e9,0x5837,0xa638,0x28d3}, new char[]{0x28b1,0xa64a,0xda4e,0x5850,0xe581,0xd724}).plainString();
        static final String L48 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6f1d,0x657a,0xda8d,0x4ae1,0x449c,0x2f71,0x9a9e}, new char[]{0x44f4,0x650f,0x6f6f,0xdaf9,0x2f12,0x9aff,0x4a8c}).plainString();
        static final String L49 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5139,0x38ab,0x3ed9,0x0dcc,0x496a,0x4192,0xf022,0xc3fc}, new char[]{0x0da2,0x41fd,0x4918,0x515c,0xf04c,0xc398,0x38ce,0x3eab}).plainString();
        static final String L50 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9626,0x984e,0x1e26,0xd8f2,0xb4b2}, new char[]{0x1e55,0xd885,0x9827,0x9648,0xb4d5}).plainString();
        static final String L51 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb5ba,0xf2e2,0x471e,0xac48,0x2e04}, new char[]{0xac2f,0xf28e,0x477f,0x2e77,0xb5c9}).plainString();
        static final String L52 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0a85,0x90ac,0x37bb,0xa17c,0xfa58,0xad0e,0xcfbf,0x22ed,0xaad1,0x2f33,0x45e9,0xb87b}, new char[]{0x2f5d,0x2282,0x37cf,0xa115,0x0ae3,0xaab8,0x90cf,0x4588,0xad7a,0xb812,0xcfd0,0xfa36}).plainString();
        static final String L53 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x768e,0xe28c,0x1631,0x4d94,0xc51d,0x96e8,0xacba,0x6768}, new char[]{0x671f,0x4df5,0x9691,0xc56d,0xe2e3,0xacd3,0x165f,0x76fa}).plainString();
        static final String L54 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4524,0x5da0,0xff07,0x23cb,0x77b8,0x6fc9,0x6c89}, new char[]{0x6faa,0x77d7,0x4549,0x6cf9,0xff66,0x5dd3,0x23b8}).plainString();
        static final String L55 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd93c,0x30b6,0xa9b4,0x9d98,0xe072,0xc767}, new char[]{0xd954,0x9df7,0x30c2,0xa9d6,0xc706,0xe000}).plainString();
        static final String L56 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa20e,0x28a3,0x1752,0x0b25,0x6157,0x033b}, new char[]{0xa26b,0x0b43,0x035d,0x1737,0x28c0,0x6123}).plainString();
        static final String L57 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd34b,0xade6,0xa079,0xf62d,0x3624,0xf208,0x9a9f}, new char[]{0xa00a,0xf648,0x9aec,0xad95,0xd322,0x364b,0xf266}).plainString();
        static final String L58 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd020,0x26ee,0x6fac,0x4552}, new char[]{0x4539,0x2687,0xd04c,0x6fc0}).plainString();
        static final String L59 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x70bd,0x7753,0xe32c,0xecb1,0x54d0,0xec97,0x0769,0x6167}, new char[]{0x54a0,0x7732,0x70cf,0xece3,0x0700,0x6104,0xecdd,0xe349}).plainString();
        static final String L60 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4983,0xb2c0,0xc8d8,0x35bc,0x2d3a,0x08cb,0x7548}, new char[]{0x49ec,0x2d4f,0xc8ac,0x7524,0xb2a9,0x08a5,0x35d9}).plainString();
        static final String L61 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2f3f,0x1e75,0x7a21,0x4cbd}, new char[]{0x2f4b,0x1e1c,0x7a4c,0x4cd8}).plainString();
        static final String L62 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8292,0x2981,0x9fb6,0xd33a,0x54b5,0x696e,0x0363,0x3020,0x9192}, new char[]{0x9fc4,0x54d0,0xd359,0x82fd,0x030d,0x29ef,0x690b,0x3043,0x91e6}).plainString();
        static final String L63 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x067f,0x9dd1,0xea33,0x6ee9,0x0d11,0x29a0,0x10bd}, new char[]{0x6e99,0xea41,0x061a,0x0d67,0x9db8,0x29c5,0x10ca}).plainString();
        static final String L64 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x74c2,0x98fc,0xf0ac,0x87c3}, new char[]{0x87b7,0x9899,0x74a3,0xf0c1}).plainString();
        static final String L65 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1de9,0xa0b3,0x0e2a,0x4845,0x0fe5,0xec5c}, new char[]{0xec28,0x0e4b,0x1d9b,0x0f82,0xa0d6,0x4831}).plainString();
        static final String L66 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc3f0,0x5876,0x64d6,0x55fc,0xd232}, new char[]{0xc393,0x64be,0x5803,0x5592,0xd259}).plainString();
        static final String L67 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x62b7,0xe112,0xd835,0x0372}, new char[]{0x031a,0xe17d,0x62db,0xd850}).plainString();
        static final String L68 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4b70,0xc87c,0x3b3a,0x6841,0x7537,0x5c27,0xb784,0x6571,0x55f1}, new char[]{0x5c49,0xc819,0x5585,0x3b52,0x4b15,0x6503,0x755e,0x6835,0xb7e1}).plainString();
        static final String L69 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0454,0x8b7e,0x7b24,0xa1b6}, new char[]{0x0427,0x7b41,0xa1da,0x8b12}).plainString();
        static final String L70 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa87a,0xcf47,0x7108}, new char[]{0xa81b,0x7160,0xcf67,0x15b7}).plainString();
        static final String L71 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3d09,0x61d2,0x38c7,0xc851,0x798b,0x6447}, new char[]{0x3d7a,0x61bc,0x79e2,0x6437,0xc834,0x38b5}).plainString();
        static final String L72 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5c99,0x3e40,0x4b79,0x9ec8,0xefbf}, new char[]{0x5cf6,0x4b0b,0xefdb,0x3e25,0x9eba}).plainString();
        static final String L73 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x66b3,0xe01b}, new char[]{0xe06f,0x66c3,0xf91c,0x2682}).plainString();
        static final String L74 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa8f2,0xc9c3,0x72c1}, new char[]{0x72b1,0xa893,0xc9ba,0x742f}).plainString();
        static final String L75 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbc7e,0xdad9,0xb5a3,0x36d7,0x2f17}, new char[]{0x36b3,0x2f78,0xb5cd,0xdaac,0xbc0a}).plainString();
    }
}
