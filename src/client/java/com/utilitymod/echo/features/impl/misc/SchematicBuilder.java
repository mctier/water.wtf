package com.utilitymod.echo.features.impl.misc;

import com.utilitymod.UtilityMod;
import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventMove;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.impl.render.Notifications;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.utils.rotation.RotationUtils;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Builds a Litematica schematic (.litematic in config/utilitymod-schematics) at a spot you pick:
 * shows the missing blocks as a ghost, and places the ones within reach from your inventory,
 * bottom layer first, against an existing neighbour. You walk around; it places.
 */
public class SchematicBuilder extends Feature {
    private final IntSetting file = new IntSetting(LT.L1, 1, 1, 50);
    private final BoolSetting openFolder = new BoolSetting(LT.L2, false);
    private final BoolSetting setOrigin = new BoolSetting(LT.L3, false);
    private final BoolSetting build = new BoolSetting(LT.L4, true);
    private final IntSetting perTick = new IntSetting(LT.L5, 1, 1, 4);
    private final IntSetting delay = new IntSetting(LT.L6, 1, 0, 10);
    private final FloatSetting range = new FloatSetting(LT.L7, 4.5f, 2f, 5.5f, 0.1f);
    private final BoolSetting sAim = new BoolSetting(dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x4D,0x40,0x1C,0xDE,0xAC,0x79,0x74,0xDE,0xCF,0x9C}), true);
    private final BoolSetting ghost = new BoolSetting(LT.L8, true);

    private record Entry(BlockPos rel, BlockState state) {}

    private List<Entry> blocks = List.of();
    private String loaded;
    private BlockPos origin;
    private int wait;
    private BlockPos pendingPos;
    private Direction pendingSide;
    private BlockPos pendingNeighbour;

    public SchematicBuilder() {
        super(new FeatureInfo(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4a18,0x76b0,0xf040,0xb16e,0x2270,0xd129,0x0b28,0xeb94,0x82c4,0x1c91,0x9663,0x11d2,0xa8a8,0x37d3,0xfceb,0x655f,0xbc0c}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a}).toString(), new com.utilitymod.echo.utils.EncryptedString(new char[]{0x93a3,0x76b9,0x58e0,0x114d,0xf044,0x8668,0x11fc,0xac47,0x6271,0x1581,0x966d,0xfce2,0x6d3a,0x2271,0x479b,0x4b29,0xa1b2,0xef5a,0x3350,0x181e,0x7edf,0x3ff0,0xd979,0xe72f,0x3b75,0xe0ff,0x4305,0x9e40,0x82cc,0xda91,0x9779,0xf891,0xf19f,0x77c6,0x1cd2,0xbc05,0x5f6b,0x2e38,0xf415,0x2a8d,0x9bef,0x0b7c,0xa9d1,0xe8dd,0x6542,0xd138,0x34ee,0xc145,0x7225,0xb50f,0xfc39,0xd2bd,0x0809,0x8aef,0x4a1e,0xc5d2,0xbd49,0xb17c,0xa8be,0x820a,0x0cd1,0x37da,0x1da7,0x66fd,0xcdf7,0xc961,0xb9c2,0xa423,0x547f,0x50c8,0x4fa7,0x8eac,0x23d5,0x7a5b,0x04bf,0x002b,0x265d,0xeb88}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643,0x1190,0xebe1,0x6536,0xfc87,0x76d4,0xf025,0x4a6a,0xc5bb,0x5f08,0xd959,0x50ae,0x2aff,0xa44c,0x3f9d,0xb9e2,0x3333,0x8a80,0x04d1,0x9e26,0x1877,0x93c4,0x6d15,0xe75a,0x7eab,0xf8f8,0x7249,0xcd9e,0x47ef,0xc13c,0x588d,0xd2d2,0xac23,0x2670,0xa1c1,0x3b16,0xb567,0x0cb4,0x8605,0x004a,0x9b9b,0x15e8,0xef39,0x668e,0xe0df,0x7a2c,0xf47d,0x4fc2,0xc913,0x4360,0xdab1,0x5406,0x2e57,0xa9a4,0x23f5,0xbd3a,0x348b,0x8ed8,0x0829,0x827e,0x1dcf,0x971c,0x116d,0xe8b2,0x6203,0xfc50,0x77a1,0xf1f6,0x4b47}).toString(), Category.UTILITY));
    }

    private static Path folder() {
        return net.minecraft.client.MinecraftClient.getInstance().runDirectory.toPath().resolve(LT.L9).resolve(LT.L10);
    }

    private List<Path> files() {
        List<Path> out = new ArrayList<>();
        try {
            Files.createDirectories(folder());
            try (var s = Files.list(folder())) {
                s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(LT.L11)).sorted().forEach(out::add);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    @Override
    public CharSequence concat() {
        if (loaded == null) return LT.L12;
        return Path.of(loaded).getFileName().toString().replace(LT.L11, "") + " " + remaining() + LT.L13;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        load();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        pendingPos = null;
        RotationUtils.cleanup(this, sAim.getValue());
    }

    private int remaining() {
        if (origin == null || mc.world == null) return blocks.size();
        int n = 0;
        for (Entry e : blocks) if (mc.world.getBlockState(origin.add(e.rel())) != e.state()) n++;
        return n;
    }

    private void load() {
        List<Path> list = files();
        if (list.isEmpty()) {
            blocks = List.of();
            loaded = null;
            return;
        }
        Path f = list.get(Math.min(list.size(), file.getValue()) - 1);
        if (f.toString().equals(loaded)) return;
        try {
            blocks = readLitematic(f);
            loaded = f.toString();
            Notifications.push(LT.L14, LT.L15 + f.getFileName() + LT.L16 + blocks.size() + LT.L17, Notifications.Type.INFO);
        } catch (Exception e) {
            blocks = List.of();
            loaded = null;
            UtilityMod.LOGGER.warn(LT.L18, f.getFileName(), e.toString());
            Notifications.push(LT.L14, LT.L19 + f.getFileName(), Notifications.Type.DISABLED);
        }
    }

    /** Litematica format: Regions -> {Position, Size, BlockStatePalette, BlockStates (packed longs)}. */
    private static List<Entry> readLitematic(Path f) throws Exception {
        NbtCompound root = NbtIo.readCompressed(f, NbtSizeTracker.ofUnlimitedBytes());
        NbtCompound regions = root.getCompoundOrEmpty(LT.L20);
        List<Entry> out = new ArrayList<>();
        for (String name : regions.getKeys()) {
            NbtCompound r = regions.getCompoundOrEmpty(name);
            NbtCompound pos = r.getCompoundOrEmpty(LT.L21), size = r.getCompoundOrEmpty(LT.L22);
            int sx = size.getInt(LT.L23, 0), sy = size.getInt(LT.L24, 0), sz = size.getInt(LT.L25, 0);
            int ox = pos.getInt(LT.L23, 0) + (sx < 0 ? sx + 1 : 0);
            int oy = pos.getInt(LT.L24, 0) + (sy < 0 ? sy + 1 : 0);
            int oz = pos.getInt(LT.L25, 0) + (sz < 0 ? sz + 1 : 0);
            sx = Math.abs(sx); sy = Math.abs(sy); sz = Math.abs(sz);
            NbtList pal = r.getListOrEmpty(LT.L26);
            BlockState[] palette = new BlockState[pal.size()];
            for (int i = 0; i < pal.size(); i++) {
                NbtElement el = pal.get(i);
                palette[i] = NbtHelper.toBlockState(Registries.BLOCK, (NbtCompound) el);
            }
            long[] data = r.getLongArray(LT.L27).orElse(new long[0]);
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.length - 1)));
            long mask = (1L << bits) - 1;
            long total = (long) sx * sy * sz;
            for (long i = 0; i < total; i++) {
                long bit = i * bits;
                int li = (int) (bit >>> 6), off = (int) (bit & 63);
                if (li >= data.length) break;
                long v = data[li] >>> off;
                if (off + bits > 64 && li + 1 < data.length) v |= data[li + 1] << (64 - off);
                int id = (int) (v & mask);
                if (id <= 0 || id >= palette.length) continue; // 0 = air
                BlockState st = palette[id];
                if (st.isAir()) continue;
                int x = (int) (i % sx), z = (int) ((i / sx) % sz), y = (int) (i / ((long) sx * sz));
                out.add(new Entry(new BlockPos(ox + x, oy + y, oz + z), st));
            }
        }
        out.sort(Comparator.comparingInt((Entry e) -> e.rel().getY()).thenComparingInt(e -> e.rel().getX()).thenComparingInt(e -> e.rel().getZ()));
        return out;
    }

    @EventSubscribe
    private void onTick(EventTick event) {
        if (openFolder.getValue()) {
            openFolder.setValue(false);
            try {
                Files.createDirectories(folder());
                net.minecraft.util.Util.getOperatingSystem().open(folder());
            } catch (Exception ignored) {
            }
        }
        if (isNull()) return;
        load();
        if (setOrigin.getValue()) {
            setOrigin.setValue(false);
            origin = mc.player.getBlockPos();
            Notifications.push(LT.L14, LT.L28 + origin.toShortString(), Notifications.Type.INFO);
        }
        if (origin == null || blocks.isEmpty() || !build.getValue() || mc.currentScreen != null) return;

        // finish a placement we were aiming at
        if (pendingPos != null) {
            boolean ok = RotationUtils.aim(this).priority(EventSubscribe.Priority.NORMAL).silent(sAim.getValue())
                    .speed(360f).aimType(RotationUtils.AimType.BLATANT).to(hitVec(pendingNeighbour, pendingSide));
            if (!ok) return;
            place();
            pendingPos = null;
            wait = delay.getValue();
            return;
        }
        if (wait-- > 0) return;

        int placed = 0;
        Vec3d eye = mc.player.getEyePos();
        double r2 = range.getValue() * range.getValue();
        for (Entry e : blocks) {
            if (placed >= perTick.getValue()) break;
            BlockPos p = origin.add(e.rel());
            if (eye.squaredDistanceTo(Vec3d.ofCenter(p)) > r2) continue;
            BlockState cur = mc.world.getBlockState(p);
            if (cur == e.state() || !cur.isReplaceable()) continue;
            if (mc.player.getBoundingBox().intersects(new net.minecraft.util.math.Box(p))) continue;
            Direction side = null;
            BlockPos neighbour = null;
            for (Direction d : Direction.values()) {
                BlockPos n = p.offset(d);
                if (!mc.world.getBlockState(n).isReplaceable()) {
                    neighbour = n;
                    side = d.getOpposite();
                    break;
                }
            }
            if (neighbour == null) continue; // nothing to place against yet (lower layers first)
            if (!selectItem(e.state().getBlock().asItem())) continue;
            pendingPos = p;
            pendingNeighbour = neighbour;
            pendingSide = side;
            placed++;
            if (!sAim.getValue() || perTick.getValue() > 1) { // place right away
                place();
                pendingPos = null;
            } else {
                return; // aim this tick, place next tick
            }
        }
        if (placed > 0) wait = delay.getValue();
    }

    private static Vec3d hitVec(BlockPos neighbour, Direction side) {
        return Vec3d.ofCenter(neighbour).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
    }

    private void place() {
        BlockHitResult hit = new BlockHitResult(hitVec(pendingNeighbour, pendingSide), pendingSide, pendingNeighbour, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    /** Hold the item: hotbar slot if there, else swap it from the inventory into the current slot. */
    private boolean selectItem(Item item) {
        if (!(item instanceof BlockItem)) return false;
        var inv = mc.player.getInventory();
        if (inv.getSelectedStack().isOf(item)) return true;
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(item)) {
                inv.setSelectedSlot(i);
                return true;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (inv.getStack(i).isOf(item)) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, inv.getSelectedSlot(), SlotActionType.SWAP, mc.player);
                return true;
            }
        }
        return false;
    }

    @EventSubscribe
    private void onMove(EventMove.Pre event) {
        if (!sAim.getValue() || pendingPos == null) return;
        if (!RotationUtils.isControlledBy(this) || !RotationUtils.hasSilentRotation()) return;
        event.setYaw(RotationUtils.getSilentYaw());
        event.setPitch(RotationUtils.getSilentPitch());
    }

    @EventSubscribe
    private void onRender(EventRender3D event) {
        if (!ghost.getValue() || origin == null || blocks.isEmpty() || mc.world == null) return;
        var d = event.getDraw3D().getCustomTarget();
        int shown = 0;
        Vec3d eye = mc.player.getEyePos();
        for (Entry e : blocks) {
            BlockPos p = origin.add(e.rel());
            if (eye.squaredDistanceTo(Vec3d.ofCenter(p)) > 48 * 48) continue;
            BlockState cur = mc.world.getBlockState(p);
            if (cur == e.state()) continue;
            boolean wrong = !cur.isAir() && !cur.isReplaceable();
            int c = wrong ? 0x60FF4040 : 0x3040A0FF;
            d.box(p.getX() + 0.1, p.getY() + 0.1, p.getZ() + 0.1, 0.8, 0.8, 0.8, c);
            if (++shown > 3000) break;
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7b8f,0xc329,0xfa6d,0x0d03,0xb67c,0x078a,0xa384,0xd875,0xad4f,0x39e6,0x98a6}, new char[]{0xad1c,0xa3e7,0xfa05,0x3983,0xd818,0x0d62,0x07fe,0x7be6,0xb61f,0xc309,0x9885}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf288,0x3b28,0xc3f0,0xc101,0x8017,0xb57c,0x4347,0x46bf,0xe8bb,0xd292,0x7824}, new char[]{0xc14e,0x8067,0x4322,0xb512,0xd2b2,0xe8fd,0x784b,0x3b44,0xf2ec,0x46da,0xc382}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1260,0x9c76,0x7622,0xdcfd,0xa2c4,0xec06,0x8aa3,0x1059,0x3bd8,0xfd00,0x560f,0x03be,0xa40b,0xb175,0xecd8}, new char[]{0xa297,0xb110,0x1214,0x039e,0x766d,0x102b,0xa462,0xecbf,0x9c1f,0x3bb6,0xdcdd,0x8aeb,0xec63,0x567d,0xfd65}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2abc,0x3cfd,0x20df,0x0141,0x050a}, new char[]{0x209d,0x0134,0x0563,0x3c91,0x2ad8}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x972d,0x8ed8,0xd1ef,0x7c30,0x4cac,0x1fe7,0x45e8,0x2ca1,0x8fb2,0x016c,0x366e,0x5ac5,0x53a5}, new char[]{0x5a87,0x8fde,0x9742,0x7c53,0xd184,0x361d,0x5385,0x1fc8,0x8ef8,0x45bc,0x0105,0x4ccf,0x2cca}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x014f,0x7cb5,0x7dac,0x5046,0x9107,0x185c,0x05ef,0xbba8,0x26d1,0xd0f8}, new char[]{0x9153,0xbbc1,0x7dcf,0x7cde,0xd0d8,0x5002,0x058a,0x1830,0x26b0,0x0136}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4d8a,0x8563,0x2451,0x0780,0x13c4}, new char[]{0x4dd8,0x07e1,0x13aa,0x2436,0x8506}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7c3f,0x6b68,0x0eb9,0x2bae,0x8df9,0x300b,0xc82e,0x01ba,0x8bea,0xd27a}, new char[]{0xd229,0x7c57,0x8d96,0x01cd,0x302b,0xc869,0x0ed1,0x8b85,0x2bdd,0x6b1c}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbbe7,0xd8ef,0x77c3,0xbddb,0xf6dc,0xc28a}, new char[]{0xd88c,0xbb88,0x77ad,0xf6ba,0xc2e3,0xbdbc}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xba9b,0xcced,0xcd27,0x4665,0xdede,0x513d,0xe899,0x0119,0xa503,0xb620,0x20d8,0x7783,0xf8c3,0xcfe1,0x16f0,0x9012,0x7197,0x029e,0xdb5b,0x9b8e,0xfde8}, new char[]{0x016c,0xb654,0xe8f0,0x169c,0x460c,0x9066,0xcf98,0x9be3,0xdb34,0xcc89,0x02b3,0xfd9b,0xcd44,0x77eb,0xbafe,0xa56e,0x515c,0x71e3,0x20b1,0xf8a0,0xdead}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd44e,0x36ff,0x16e7,0x1fd8,0xf4f7,0x6bca,0xce03,0x672e,0x9089,0x8020}, new char[]{0x16c9,0xf49b,0x90e0,0x675a,0x8045,0xce6e,0xd42f,0x6bbe,0x1fb1,0x369c}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3640,0xf84f,0x38a3,0xbabf,0x7c7f,0x3f7f,0xf5f0,0xfe84,0x99b4,0xde60,0xfb7d,0xe464}, new char[]{0x38cd,0xfb12,0xf5d0,0xe417,0xf82c,0x3628,0x7c1a,0x3f12,0xfee5,0xbacb,0xde09,0x99d7}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0d02,0xebcc,0xedea,0xa25c,0xdebf}, new char[]{0xde9f,0xeba0,0xed8f,0x0d64,0xa228}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1d15,0xe835,0xc9d1,0xa931,0xbe9c,0x71b2,0x2c8b,0xdb7e,0x7960,0xf56c,0x41c7,0xe43a,0x09a6,0x617b,0xdbd2,0xe0f1,0xf632}, new char[]{0x4194,0xbeff,0x2ce3,0xf657,0x790d,0x1d74,0xdb0a,0x6112,0x09c5,0xf54c,0xe0b3,0xe44f,0x71db,0xa95d,0xe851,0xc9b4,0xdba0}).toString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x09a7,0x957a,0xbd64,0x2b07,0x6f54,0xf0cd,0xf173}, new char[]{0xf081,0x09c8,0xf112,0xbd00,0x2b62,0x6f30,0x955a}).toString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4148,0xbf98}, new char[]{0xbfb8,0x4160,0x15c4,0x1da5}).plainString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9138,0x2e55,0x651a,0x18a7,0xb73c,0xd39b,0x3fbd,0xd337}, new char[]{0x9118,0x18c5,0x6576,0xb753,0xd354,0x2e3e,0xd3e8,0x3f94}).plainString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3d52,0x58e3,0x8199,0xec40,0xa83f,0x75b8,0x66c4,0x8a38,0x29cd,0x3e9c,0xf190,0x21af,0x14cf,0xa1de,0x6f4f,0x1488,0xa310,0xadc4,0x8d25,0x3938,0x8079,0x5ae7,0xd32a,0x465b,0x3371,0x40fb,0x211f,0x4c83,0x05ad,0x8130,0x8311,0x61f7,0xa645,0x6441,0x348f,0xe56c,0x318f,0x4cba,0x788f,0x6174}, new char[]{0x669f,0xa86a,0xec34,0x8159,0xe500,0x34e6,0x408f,0x6f36,0x5aaa,0x8d4a,0x217b,0x4cde,0x058d,0xa616,0x801a,0x75d0,0x31ea,0xd347,0x8a59,0x5897,0x14a6,0xa373,0x3918,0x4cc1,0x4626,0x61d7,0x3d27,0x642f,0x3303,0x6111,0x14e9,0x78eb,0xf1f1,0x21cd,0x3ef0,0xada1,0xa1e4,0x29ed,0x836a,0x81e4}).plainString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaaed,0x370d,0x0b78,0x51f4,0x5a71,0x5a7a,0x3100,0x1c8e,0x717b,0xf026,0x5244}, new char[]{0x7138,0x376c,0x316e,0x1ca9,0x5180,0x5a51,0x5236,0x0b1d,0x5a1b,0xf042,0xaacd}).toString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd959,0x3df7,0xc06f,0x25c5,0x80b2,0x20f5,0x788a}, new char[]{0x20a7,0x25a0,0xd93e,0x80db,0x3d98,0x78e4,0xc01c}).toString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9ea8,0x8ff8,0xb831,0x543e,0x3c42,0xa294,0xf450,0x294c}, new char[]{0xf400,0x3c2d,0x544d,0x9ec1,0xb845,0xa2fd,0x2923,0x8f96}).plainString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x55ec,0x3456,0xa62e,0x5f51}, new char[]{0xa67d,0x5f38,0x342c,0x5589}).plainString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa884}, new char[]{0xa8fc,0xdc20,0x5600,0xa40c}).plainString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x46b2}, new char[]{0x46cb,0xa487,0xc8ba,0xf2e9}).plainString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb147}, new char[]{0xb13d,0x16e2,0xbbf9,0xbc7d}).plainString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9b98,0x136c,0xbdc0,0xb5f6,0x1094,0xc56f,0x1a47,0x43ff,0xcbfd,0xf901,0x88fe,0xcfd2,0x7be2,0x3ad4,0x5fe0,0x551e,0x7f60}, new char[]{0x555c,0x7b8e,0xb599,0xcfb1,0x1307,0xc53c,0xbdb4,0x3ab5,0x7f14,0x1a22,0x9bc8,0xf960,0xcb91,0x439a,0x5f94,0x10e0,0x889b}).toString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xee76,0xd685,0xc97a,0xb000,0xd6b4,0x6c02,0x2e38,0x7bb4,0xf970,0x0981,0x930e}, new char[]{0x2e7a,0xf91c,0xd6ea,0x6c61,0x7bdf,0xee25,0xb074,0x09e0,0x937a,0xc91f,0xd6c7}).toString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0732,0x5c1f,0x165e,0x093e,0xbd5e,0x7ee5,0xb8c5,0x2eca,0x8408,0x670b,0x724b,0xfbe4,0x1bfb,0x0b13}, new char[]{0xfbab,0x1b89,0x6762,0x722c,0x7e8c,0xbd30,0x2eea,0x0b60,0x163b,0xb8b1,0x091e,0x847c,0x075d,0x5c3f}).toString();
    }
}
