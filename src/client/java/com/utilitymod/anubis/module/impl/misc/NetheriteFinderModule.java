package com.utilitymod.anubis.module.impl.misc;

import com.utilitymod.anubis.imgui.ImDrawList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.utilitymod.anubis.chat.ModuleChat;
import com.utilitymod.anubis.event.EventHandler;
import com.utilitymod.anubis.event.impl.Render3DEvent;
import com.utilitymod.anubis.event.impl.TickEvent;
import com.utilitymod.anubis.module.ClientModule;
import com.utilitymod.anubis.module.HudOverlayProvider;
import com.utilitymod.anubis.module.ModuleCategory;
import com.utilitymod.anubis.module.setting.BooleanSetting;
import com.utilitymod.anubis.module.setting.ColorSetting;
import com.utilitymod.anubis.module.setting.ModeSetting;
import com.utilitymod.anubis.module.setting.NumberSetting;
import com.utilitymod.anubis.util.netherite.NetheriteChunkTracker;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;

public final class NetheriteFinderModule extends ClientModule implements HudOverlayProvider {
   private static final int MAX_RENDERED_SECTIONS = 128;
   private static final long NOTIFY_COOLDOWN_MS = 2000L;
   private final ColorSetting color = this.addSetting(LT.L1, new ColorSetting(LT.L2, -34304));
   private final ColorSetting farColor = this.addSetting(LT.L3, new ColorSetting(LT.L4, -11688961));
   private final NumberSetting fade = this.addSetting(LT.L5, new NumberSetting(LT.L6, 192.0F, 32.0F, 512.0F).step(8.0F));
   private final NumberSetting range = this.addSetting(LT.L7, new NumberSetting(LT.L8, 512.0F, 64.0F, 1024.0F).step(1.0F));
   private final BooleanSetting tracers = this.addSetting(LT.L9, new BooleanSetting(LT.L10, true));
   private final NumberSetting tracerCount = this.addSetting(LT.L11, new NumberSetting(LT.L12, 3.0F, 1.0F, 8.0F).step(1.0F));
   private final BooleanSetting advanced = this.addSetting(LT.L13, new BooleanSetting(LT.L14, false));
   private final BooleanSetting showProbes = this.addSetting(LT.L15, new BooleanSetting(LT.L16, true));
   private final ModeSetting probeStyle = this.addSetting(LT.L17, new ModeSetting(LT.L18, LT.L19, LT.L19, LT.L20, LT.L21));
   private final NumberSetting missingFrom = this.addSetting(LT.L22, new NumberSetting(LT.L23, 60.0F, 30.0F, 100.0F).step(5.0F));
   private final AdvancedFinder advancedFinder = new AdvancedFinder(this);
   private final NetheriteVisuals visuals = new NetheriteVisuals();
   private int pendingCount;
   private BlockPos nearestPending;
   private double nearestPendingDistanceSq;
   private long lastNotifyAt;

   public NetheriteFinderModule() {
      super(LT.L24,
         dev.potato.spud.api.ProtectedText.fromInts(new int[]{0x50,0x4C,0x04,0xD3,0xA7,0x7F,0x3D,0xEB,0xC3,0xD1,0x7E,0x2A,0xE4,0xB1,0x79,0x55}).rawValue(),
         LT.L25, ModuleCategory.DONUT);
      this.usesNotifications().set(false);
      this.tracerCount.visibleWhen(this.tracers::get);
      this.showProbes.visibleWhen(this.advanced::get);
      this.probeStyle.visibleWhen(() -> this.advanced.get() && this.showProbes.get());
      this.missingFrom.visibleWhen(() -> this.advanced.get() && this.showProbes.get());
   }

   @Override
   protected void onEnable() {
      this.clearPending();
      this.lastNotifyAt = 0L;
      NetheriteChunkTracker.setListener(this::onCandidate);
      NetheriteChunkTracker.setRevealListener(this::onRevealed);
   }

   @Override
   protected void onDisable() {
      NetheriteChunkTracker.setListener(null);
      NetheriteChunkTracker.setRevealListener(null);
      this.advancedFinder.reset(MinecraftClient.getInstance());
      this.visuals.clear();
   }

   private void onRevealed(BlockPos pos) {
      if (this.advancedFinder.onRevealed(pos)) {
         this.visuals.burst(pos, this.color.argb());
      }
   }

   private void onCandidate(NetheriteChunkTracker.Candidate candidate) {
      if (this.notificationsOn()) {
         BlockPos pos = new BlockPos((candidate.chunkX() << 4) + 8, (candidate.sectionY() << 4) + 8, (candidate.chunkZ() << 4) + 8);
         this.pendingCount++;
         MinecraftClient mc = MinecraftClient.getInstance();
         double distanceSq = mc.player != null ? mc.player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) : 0.0;
         if (this.nearestPending == null || distanceSq < this.nearestPendingDistanceSq) {
            this.nearestPending = pos;
            this.nearestPendingDistanceSq = distanceSq;
         }
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      this.flushNotifications();
      MinecraftClient mc = MinecraftClient.getInstance();
      this.advancedFinder.maintain(mc);
      if (this.advanced.get()) {
         if (!this.advancedFinder.armed()) {
            this.advancedFinder.arm();
         }

         this.advancedFinder.tick(mc);
      } else if (this.advancedFinder.armed()) {
         this.advancedFinder.disarm(mc);
      }
   }

   private void flushNotifications() {
      if (this.pendingCount != 0) {
         if (!this.notificationsOn()) {
            this.clearPending();
         } else {
            long now = System.currentTimeMillis();
            if (now - this.lastNotifyAt >= 2000L) {
               ModuleChat.send(
                  this,
                  ModuleChat.body()
                     .value(this.pendingCount)
                     .text(LT.L26 + (this.pendingCount == 1 ? " " : LT2.L1))
                     .good(LT.L27)
                     .text(LT.L28)
                     .coords(this.nearestPending.getX(), this.nearestPending.getY(), this.nearestPending.getZ())
               );
               this.clearPending();
               this.lastNotifyAt = now;
            }
         }
      }
   }

   private void clearPending() {
      this.pendingCount = 0;
      this.nearestPending = null;
      this.nearestPendingDistanceSq = Double.MAX_VALUE;
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null && mc.player != null && NetheriteChunkTracker.isNether(mc.world)) {
         Vec3d eye = mc.player.getCameraPosVec(event.partialTick);
         double maxDistance = this.range.get();
         List<NetheriteVisuals.Section> sections = new ArrayList<>();

         for (NetheriteChunkTracker.Candidate candidate : NetheriteChunkTracker.snapshot()) {
            if (!this.advancedFinder.isDone(candidate)) {
               double x = candidate.chunkX() * 16.0;
               double y = candidate.sectionY() * 16.0;
               double z = candidate.chunkZ() * 16.0;
               Box box = new Box(x, y, z, x + 16.0, y + 16.0, z + 16.0);
               double distance = Math.sqrt(distanceSq(box, eye));
               if (distance <= maxDistance) {
                  sections.add(new NetheriteVisuals.Section(box, distance, this.advancedFinder.progress(candidate)));
               }
            }
         }

         sections.sort(Comparator.comparingDouble(NetheriteVisuals.Section::distance));
         if (sections.size() > 128) {
            sections = sections.subList(0, 128);
         }

         NetheriteVisuals.Options options = new NetheriteVisuals.Options(
            this.color.argb(),
            this.farColor.argb(),
            this.fade.get(),
            maxDistance,
            this.probeStyle.get(),
            this.missingFrom.get() / 100.0F,
            this.tracers.get() ? this.tracerCount.getInt() : 0,
            this.showProbes.get()
         );
         this.visuals.render(mc, event.partialTick, eye, sections, List.copyOf(this.advancedFinder.revealed()), this.advancedFinder, options);
      } else {
         this.visuals.clear();
      }
   }

   @Override
   public boolean isHudOverlayEnabled() {
      return this.isEnabled() && this.visuals.hasLabels();
   }

   @Override
   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      this.visuals.overlay(dl, screenW, screenH);
   }

   private static double distanceSq(Box box, Vec3d point) {
      double dx = Math.max(0.0, Math.max(box.minX - point.x, point.x - box.maxX));
      double dy = Math.max(0.0, Math.max(box.minY - point.y, point.y - box.maxY));
      double dz = Math.max(0.0, Math.max(box.minZ - point.z, point.z - box.maxZ));
      return dx * dx + dy * dy + dz * dz;
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2ba7,0x273f,0x3e00,0xe217,0x9b8a}, new char[]{0x9be9,0xe278,0x2753,0x2bc8,0x3e72}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe7f2,0x142a,0x2268,0x68b6,0x3867,0xe285,0x6bcc,0x171f,0x9a8b,0x8e28}, new char[]{0x68f8,0x144f,0x3806,0xe780,0x8e08,0x175c,0xe2ea,0x2204,0x6ba3,0x9af9}).toString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd4de,0x525d,0xb8a0,0xcb81,0x9c30,0x4fae,0x83bf,0x38d1,0x82ae}, new char[]{0x523b,0xb8c1,0x4fdc,0x38fc,0xd4bd,0xcbee,0x82c2,0x83d0,0x9c42}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x727d,0x8429,0x9c1a,0x3e7d,0x5190,0x8f8c,0x619b,0x24bb,0x5d1f}, new char[]{0x723b,0x8fed,0x24c9,0x9c3a,0x3e3e,0x8446,0x51fc,0x5d70,0x61e9}).toString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xab3a,0x074b,0x239e,0x49b3,0xe2d8,0x107d,0x1496,0xeb15,0xd539,0xf102,0x0acb,0x7f44,0xa78c}, new char[]{0xe2be,0xd558,0x7f20,0x0aae,0xf12f,0xa7e8,0x1014,0x49c0,0xab4e,0x072a,0x14f8,0x23fd,0xeb70}).plainString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x12e6,0xa480,0x2ee1,0xb319,0x81b5,0xa3f1,0x6263,0x8d92,0x42cc,0x918c,0x1142,0x47b5,0x90cd}, new char[]{0x8dd4,0x81d4,0x42a8,0x91e9,0x12c6,0x9089,0x112b,0x2e92,0x6217,0xb378,0x47db,0xa4e3,0xa394}).toString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbef8,0x007d,0x21b1,0xb0bc,0x6a09}, new char[]{0x000f,0xb0dd,0x21df,0xbe9f,0x6a6c}).plainString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbc81,0x0152,0xd841,0x05ea,0x47d5}, new char[]{0x05b8,0x47b4,0x013c,0xbce6,0xd824}).toString();
            static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2cf6,0xf400,0x0236,0x3411,0xe2c2,0x3b84,0xf611}, new char[]{0x3465,0xf472,0xf670,0x2c95,0x3be1,0x0244,0xe2b1}).plainString();
            static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x318f,0xd6ab,0x204c,0x0a9c,0x9c3e,0x8d61,0xd208,0xf95c,0xd7c2,0x0181,0xb7f2,0x39f4,0x614a}, new char[]{0xd6e8,0xd260,0x31fa,0xd7ac,0x6121,0x39d4,0x9c6a,0x0aee,0xf93d,0x01e2,0x8d04,0xb780,0x203f}).toString();
            static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0950,0xbc28,0x90de,0xceb9,0xaa32,0x0d4a,0xb627,0xb280,0xf54f,0x4c28,0x2843,0x91ea}, new char[]{0xb653,0x90ac,0xced8,0x9189,0xbc4d,0x2831,0xb2ad,0x4c4b,0x093f,0xaa47,0x0d24,0xf53b}).plainString();
            static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa29a,0x221a,0xe046,0x12cb,0x7bd3,0x3ab9,0xb400,0x0f39,0x5f1a,0xb2e8,0x9661,0xb9c8}, new char[]{0x224e,0x5f68,0x9600,0xb463,0x3adc,0xa2e8,0xb9e8,0x0f7a,0xe029,0xb29d,0x12a5,0x7ba7}).toString();
            static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x674e,0x74ff,0xe1f3,0xa1bf,0x6a41,0x96e7,0x4856,0x3700,0xa8e8,0xc381,0xf9b5,0x60ff,0x0c0e,0xd554,0x06b1}, new char[]{0xd535,0x609b,0x6738,0x749e,0x9689,0xa88b,0xe196,0xa1db,0xc3ac,0xf9d3,0x6a28,0x376e,0x06d5,0x4833,0x0c7c}).plainString();
            static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf570,0x813b,0x4e37,0x7502,0x4915,0x1c9c,0xc66e,0x095c,0x2782,0xd5a7,0xf62f,0xe7bb,0x03f2,0x82e6,0xbe16}, new char[]{0xc62f,0x815f,0x8290,0xd5c6,0x1cf2,0x4e54,0xf515,0x0938,0xbe36,0x27c4,0x756b,0x039c,0x4971,0xf64a,0xe7c9}).toString();
            static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4345,0xc215,0x227c,0x5587,0x2eed,0x0e89,0x8265,0x4043,0x1511,0x9ea9,0x0fbb}, new char[]{0x1562,0xc27d,0x402c,0x55f0,0x0f96,0x9ed9,0x2e9f,0x0ee6,0x4327,0x8200,0x220f}).plainString();
            static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6198,0x9f02,0x3aa7,0x9edc,0x2964,0x967d,0x42c0,0x704e,0x1525,0xce21,0xb196}, new char[]{0x9f51,0x42a8,0x9eb3,0x960a,0x3a87,0x2934,0x703c,0xce4e,0x61fa,0xb1f3,0x1556}).toString();
            static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6ef3,0x88e9,0x2836,0xedea,0x73e6,0x392f,0xde9a,0xb76c,0x7efe,0xd049,0xa9a2}, new char[]{0x8899,0x7e8c,0x7389,0xdef8,0xed8f,0x3902,0xd03a,0x2842,0xa9db,0x6e9f,0xb709}).plainString();
            static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1dae,0x28e0,0x6405,0xf894,0x65f0,0x693c,0x8864,0xfd61,0xd732,0xee75,0x3563}, new char[]{0x8834,0xfd13,0x646a,0xd750,0xf8f1,0x65d0,0x1dfd,0x6948,0xee0c,0x350f,0x2885}).toString();
            static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd588,0xa516,0x50f2,0xcc3e}, new char[]{0xd5cc,0x509d,0xa562,0xcc4d}).toString();
            static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3f05,0xd6c0,0xe255,0xcecd,0xdd35}, new char[]{0xdd76,0xceb8,0xd6a2,0x3f60,0xe226}).toString();
            static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcbe6,0xfbae,0x5d21,0x495d,0xd178,0xb65b}, new char[]{0xd128,0xfbdb,0x4931,0x5d52,0xcb83,0xb628}).toString();
            static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd336,0x0c1a,0x7f29,0xe245,0x6e80,0x7cf8,0x01fa,0x183e,0x77d3,0xfcaa,0x4a0e,0x42b1}, new char[]{0x42dc,0x0c73,0xd345,0x6ef3,0xfcc3,0x77bd,0x019d,0x7cd5,0x1858,0xe237,0x7f46,0x4a63}).plainString();
            static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4f77,0x3cfd,0x91c9,0x8ed0,0xcc3d,0xf2e4,0xf895,0xde77,0xf1c7,0xd71f,0x9a5d,0x497d,0x4019,0xc9a8,0x4662,0xb492,0x8d7f,0x052d,0xe942}, new char[]{0x4054,0x8d1e,0xd76d,0xde1c,0xcc1d,0x9184,0x8eb9,0xc9db,0xf1b4,0x4914,0x9a33,0xe925,0x3cdd,0xf2a2,0xf8e7,0xb4fd,0x460f,0x4f57,0x0508}).toString();
            static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x55a5,0x4252,0xc64d,0x5f0d,0x8ee9,0x12cd,0x5c91,0x44f7,0x84e4,0x6b9a,0xb747,0x8366,0xc595,0xa35f,0xa2c0,0x53a2}, new char[]{0x12a3,0x8481,0xa2b4,0x8e81,0x4492,0xc63f,0x830f,0x4226,0xb722,0x5f20,0x55c3,0x5cf8,0xc5fb,0x53c6,0x6bff,0xa32d}).plainString();
            static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x42da,0x7e0b,0xad0d,0xe433,0x4538,0xf449,0xccf7,0x31b5,0x4a50,0x2913,0x7ba0,0xfd36,0xcf98,0x2e1f,0xdcfe,0x1193,0xcc7a,0x16b2,0xb872,0x7288,0xd3e2,0x870b,0x0447,0xfe2c,0xfa78,0x9281,0xe971,0xa2eb,0x92cf,0x57ee,0xc582,0xa13a,0x25e7,0x9177,0x3f15,0x3007,0xec01,0x511e,0xdbe9,0x8e02,0xe1ff,0x7db9,0x645a,0xa3bb,0xb23b,0x5673,0x2627,0xe7c9,0x1606,0x19b8}, new char[]{0x57ac,0x72e7,0x42a2,0x8e67,0x6429,0x3027,0x8745,0x11f6,0x7dcd,0xdb81,0x3f70,0xb800,0x0467,0xcffb,0xfd5e,0xfe59,0xdc90,0xa280,0xcc5a,0xa3c8,0xe914,0xe450,0x5607,0x166f,0xb254,0xc5ec,0x2654,0xad2d,0x4a38,0xa153,0xe7ad,0xe196,0x92ef,0xf42e,0x7e2b,0x9116,0x31db,0x19db,0x297a,0xec64,0xcc99,0x2e6b,0x7b80,0xfa1c,0x92aa,0xd380,0x516c,0x16db,0x2594,0x4516}).toString();
            static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8ae8,0x0aa9,0x4466,0x6779,0x4c75,0xbc63,0x819f,0x7a00,0x10cd,0x5853,0x0684,0x16a5,0xaf85,0x2f42,0xb8f1,0x490d,0x6c9b,0xa9c4,0x2298,0x180a,0xfdec,0xb029,0xbe08,0x3883}, new char[]{0x8ac8,0x7a6e,0xb894,0xaff2,0xa9e4,0x4c1d,0xb040,0x10a9,0xfd88,0x22fd,0x1864,0x1688,0x4402,0xbc06,0x2f20,0x670b,0x583a,0x0ada,0x06a4,0xbe6b,0x81f7,0x4978,0x38ed,0x6cf0}).plainString();
            static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0131,0x0062,0xc302,0x104e,0x76c6}, new char[]{0x1028,0x76a9,0x0144,0xc36c,0x0006}).plainString();
            static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3a1f,0xf245,0xfef2,0xeeab,0xc668,0x1646,0x2b03,0x2b1a,0xd52c,0x7357,0xe2f8,0x25c2,0x4525}, new char[]{0xfede,0x1666,0x454b,0xe29d,0x3a7e,0xd55e,0xc60d,0x2b70,0x7323,0x2b3a,0xf224,0xeedf,0x25e2}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd69c,0xaf99}, new char[]{0xd6ef,0xafb9,0x80ac,0x8766}).plainString();
    }
}
