package com.utilitymod.anubis;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.utilitymod.anubis.event.impl.BlockUpdateEvent;
import com.utilitymod.anubis.event.impl.PacketAppliedEvent;
import com.utilitymod.anubis.event.impl.PacketReceiveNettyEvent;
import com.utilitymod.anubis.event.impl.PacketSendEvent;
import com.utilitymod.anubis.event.impl.Render3DEvent;
import com.utilitymod.anubis.event.impl.TickEvent;
import com.utilitymod.anubis.imgui.ImDrawList;
import com.utilitymod.anubis.module.ClientModule;
import com.utilitymod.anubis.module.HudOverlayProvider;
import com.utilitymod.anubis.module.setting.Setting;
import com.utilitymod.anubis.util.PerformanceBudget;
import com.utilitymod.UtilityMod;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.BlockPos;

/**
 * Hosts the modules ported from Anubis (GPL-3, see LICENSE-Anubis-GPL3.txt) and feeds their
 * event types from utilitymod's patcher hooks. Shown in the dropdown GUI's Donut tab.
 */
public final class AnubisBridge {
    public record Group(String title, List<ClientModule> modules) {}

    public static final List<Group> GROUPS = new ArrayList<>();
    private static boolean initialized;

    private AnubisBridge() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        GROUPS.add(group(LT.L1,
                () -> new com.utilitymod.anubis.module.impl.render.SusChunkScan(),
                () -> new com.utilitymod.anubis.module.impl.render.PrimeChunkScan(),
                () -> new com.utilitymod.anubis.module.impl.render.HoleEspModule(),
                () -> new com.utilitymod.anubis.module.impl.misc.NetheriteFinderModule()));
        GROUPS.add(group(LT.L2,
                () -> new com.utilitymod.anubis.module.impl.misc.DonutNoFallModule(),
                () -> new com.utilitymod.anubis.module.impl.donutmacros.Pediment(),
                () -> new com.utilitymod.anubis.module.impl.donutmacros.AhSellModule(),
                () -> new com.utilitymod.anubis.module.impl.donutmacros.AhSniperModule(),
                () -> new com.utilitymod.anubis.module.impl.donutmacros.OrderAhSellerModule(),
                () -> new com.utilitymod.anubis.module.impl.donutmacros.OrderDropperModule(),
                () -> new com.utilitymod.anubis.module.impl.donutmacros.TpMacroModule(),
                () -> new com.utilitymod.anubis.module.impl.donutmacros.PanicPayModule()));
    }

    @SafeVarargs
    private static Group group(String title, Supplier<ClientModule>... makers) {
        List<ClientModule> list = new ArrayList<>();
        for (Supplier<ClientModule> make : makers) {
            try {
                ClientModule m = make.get();
                m.captureDefaults();
                list.add(m);
            } catch (Throwable t) {
                UtilityMod.LOGGER.warn(LT.L3, t.toString());
            }
        }
        return new Group(title, list);
    }

    private static List<AnubisFeature> featureCache;

    /** Panel-GUI adapters for every module (built once, after init). */
    public static List<AnubisFeature> features() {
        if (featureCache == null) {
            List<AnubisFeature> out = new ArrayList<>();
            for (ClientModule m : all()) out.add(new AnubisFeature(m));
            featureCache = List.copyOf(out);
        }
        return featureCache;
    }

    private static List<ClientModule> allCache;

    public static List<ClientModule> all() {
        if (allCache == null) {
            List<ClientModule> out = new ArrayList<>();
            for (Group g : GROUPS) out.addAll(g.modules());
            allCache = List.copyOf(out);
        }
        return allCache;
    }

    // ---- hooks -------------------------------------------------------------------------------

    public static void tick() {
        PerformanceBudget.beginTick();
        TickEvent.dispatch();
        TickEvent.dispatchPost();
    }

    public static void render3D(float partialTick) {
        Render3DEvent.dispatch(partialTick);
    }

    /** Netty-thread receive. true = drop. Applied is dispatched after vanilla's own main-thread apply task. */
    public static boolean packetReceive(Packet<?> packet) {
        boolean cancel = PacketReceiveNettyEvent.dispatchSync(packet);
        if (!cancel && PacketAppliedEvent.TYPE.hasPreListeners()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            // vanilla queues its apply task right after this hook; queue behind it
            mc.execute(() -> mc.execute(() -> PacketAppliedEvent.dispatch(packet)));
        }
        return cancel;
    }

    public static boolean packetSend(Packet<?> packet) {
        return PacketSendEvent.dispatchSync(packet);
    }

    private static BlockPos pendingPos;
    private static BlockState pendingOld;

    /** ClientWorld.handleBlockUpdate HEAD: remember the old state. */
    public static void blockUpdateHead(Object world, Object pos, Object state) {
        if (!BlockUpdateEvent.TYPE.hasPreListeners() || !(world instanceof ClientWorld w) || !(pos instanceof BlockPos bp)) return;
        pendingPos = bp.toImmutable();
        pendingOld = w.getBlockState(bp);
    }

    /** ... RETURN: dispatch old -> new. */
    public static void blockUpdateReturn(Object world, Object pos, Object state) {
        if (pendingPos == null || !(state instanceof BlockState s)) return;
        BlockPos p = pendingPos;
        BlockState old = pendingOld;
        pendingPos = null;
        pendingOld = null;
        com.utilitymod.gui.SelfTest.blockUpdate++;
        if (old != s) BlockUpdateEvent.dispatch(p, old, s);
    }

    public static void hudOverlay(DrawContext ctx) {
        ImDrawList dl = null;
        for (ClientModule m : all()) {
            if (m.isEnabled() && m instanceof HudOverlayProvider o) {
                if (dl == null) dl = new ImDrawList(ctx);
                try {
                    o.renderHudOverlay(dl, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ---- persistence ---------------------------------------------------------------------------

    public static JsonObject save() {
        JsonObject all = new JsonObject();
        for (ClientModule m : all()) {
            JsonObject o = new JsonObject();
            o.addProperty(LT.L4, m.isEnabled());
            JsonObject s = new JsonObject();
            for (Setting st : m.settings()) {
                try {
                    s.add(st.id(), st.serialize());
                } catch (Throwable ignored) {
                }
            }
            o.add(LT.L5, s);
            all.add(m.id(), o);
        }
        return all;
    }

    public static void load(JsonObject all) {
        if (all == null) return;
        for (ClientModule m : all()) {
            if (!all.has(m.id()) || !all.get(m.id()).isJsonObject()) continue;
            JsonObject o = all.getAsJsonObject(m.id());
            if (o.has(LT.L5) && o.get(LT.L5).isJsonObject()) {
                JsonObject s = o.getAsJsonObject(LT.L5);
                for (Setting st : m.settings()) {
                    JsonElement e = s.get(st.id());
                    if (e == null) continue;
                    try {
                        st.deserialize(e);
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (o.has(LT.L4) && o.get(LT.L4).getAsBoolean() != m.isEnabled()) {
                m.setEnabled(o.get(LT.L4).getAsBoolean());
            }
        }
    }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4242,0xbf6b,0x9db5,0x8c4e,0x5d6a,0x2862,0x8f85,0xb343,0xdc46,0xf610,0xc051,0x18ca}, new char[]{0x9df7,0x18ab,0xc022,0x8fe0,0x8c6e,0xbf2d,0xdc2f,0x280c,0x4226,0xb32a,0xf67e,0x5d0d}).toString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfebc,0xbdde,0x52e9,0xe061,0x470b}, new char[]{0xfef8,0x4764,0xbdb0,0x529c,0xe015}).toString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4272,0xefc1,0x6e5d,0x3a3e,0x4ee7,0x1ba4,0x5b4a,0x66cb,0x37c3,0xc20f,0xb3c8,0x883a,0xe13b,0x6aff,0x6c9a,0xde6d,0x3265,0x152a,0x5061,0xfee3,0xec36,0xfc7e,0xa17e,0xe3a0,0x5be5,0x3602,0xbfe7,0x8538,0x177a,0xa78e,0x27e8,0x94eb,0x2ad8,0x4572,0xa4db,0x1f85,0x6a36,0x0ea2,0x5a8d,0x6017,0xf383,0x004e,0x10e0,0xaeaf,0xacad}, new char[]{0x6690,0xa7db,0x2aac,0x0027,0xa112,0x6a5f,0x4206,0x0edb,0xb385,0x108f,0x5b2e,0x5bb8,0xefe1,0xec77,0xfc10,0x4e92,0xc26d,0xfe8a,0x37b0,0x6cba,0x6a92,0x2787,0x5ae9,0xf3f6,0x8554,0xe15e,0x6037,0xbf81,0xaccc,0x3a57,0x1546,0x4517,0x3201,0x175a,0x949f,0x1bcb,0x3622,0x1fe9,0xaec0,0xa4ba,0x885e,0xde57,0x6e7d,0x501a,0xe3dd}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x688e,0xb803,0x9624,0x0c63,0x90d5,0xde18,0x2256}, new char[]{0x2233,0xb86d,0x90b4,0x68ec,0x0c0f,0x9641,0xde7c}).plainString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd461,0x5962,0x1131,0x3f14,0x6f5f,0x62dd,0xf0df,0xdc49}, new char[]{0x5911,0xf0ba,0xdc3d,0x1145,0xd408,0x6f31,0x3f73,0x62ae}).plainString();
    }
}
