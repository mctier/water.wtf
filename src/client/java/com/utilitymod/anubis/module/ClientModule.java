package com.utilitymod.anubis.module;

import com.utilitymod.anubis.event.Event;
import com.utilitymod.anubis.event.EventHandler;
import com.utilitymod.anubis.event.EventPriority;
import com.utilitymod.anubis.event.EventType;
import com.utilitymod.anubis.event.ListenerHandle;
import com.utilitymod.anubis.event.Stage;
import com.utilitymod.anubis.module.setting.BooleanSetting;
import com.utilitymod.anubis.module.setting.Setting;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Anubis' module base (GPL-3, see LICENSE-Anubis-GPL3.txt), trimmed for utilitymod: the
 * lambda-factory EventBus / ModuleRegistry / toggle toasts are replaced by reflective
 * {@link EventHandler} registration straight onto each event's static {@code TYPE}.
 */
public class ClientModule {
   private static final Logger LOGGER = LoggerFactory.getLogger(LT.L1);
   public static final String SHOW_IN_HUD_SETTING = LT.L2;
   private final String id;
   private final String name;
   private final String description;
   private final ModuleCategory category;
   private boolean enabled;
   private boolean enabling;
   private boolean disabling;
   private boolean disableRequested;
   private final List<Setting> settings = new ArrayList<>();
   private final List<Setting> settingsView = Collections.unmodifiableList(this.settings);
   private BooleanSetting notifications;
   private final List<ListenerHandle<?>> ownedListeners = new ArrayList<>();
   private int keybind = -1;
   private boolean holdMode;

   protected ClientModule(String name, String description, ModuleCategory category) {
      this(StableId.legacy(name), name, description, category);
   }

   protected ClientModule(String id, String name, String description, ModuleCategory category) {
      this.id = StableId.validate(id);
      this.name = name;
      this.description = description;
      this.category = Objects.requireNonNull(category, LT.L3);
   }

   protected final <S extends Setting> S addSetting(S setting) {
      return this.addSetting(setting.id(), setting);
   }

   protected final <S extends Setting> S addSetting(String id, S setting) {
      for (Setting existing : this.settings) {
         if (existing.id().equals(id)) {
            throw new IllegalArgumentException(LT.L4 + this.id + LT2.L1 + id);
         }
      }
      setting.bindId(id);
      setting.onChange(ModuleRevision.CHANGED);
      this.settings.add(setting);
      return setting;
   }

   protected final BooleanSetting usesNotifications() {
      if (this.notifications == null) {
         this.notifications = this.addSetting(LT.L5, new BooleanSetting(LT.L6, true));
      }
      return this.notifications;
   }

   public final boolean notificationsOn() {
      return this.notifications == null || this.notifications.get();
   }

   public final boolean toggleMessageOn() {
      return false;
   }

   public final String id() { return this.id; }
   public final String name() { return this.name; }
   public final String description() { return this.description; }
   public final ModuleCategory category() { return this.category; }
   public final List<Setting> settings() { return this.settingsView; }

   public final void captureDefaults() {
      for (Setting setting : this.settings) setting.captureDefault();
   }

   public final void resetSettings() {
      for (Setting setting : this.settings) setting.resetToDefault();
   }

   public final BooleanSetting showInHud() { return null; }
   public boolean persistEnabled() { return true; }
   public boolean enabledByDefault() { return false; }
   public boolean inConfigs() { return true; }
   public int settingsVersion() { return 0; }
   public void migrateSettings(int savedVersion) {}
   public boolean isAction() { return false; }
   public void onClick() {}

   public final boolean isEnabled() {
      return this.enabled;
   }

   public final void setEnabled(boolean value) {
      if (this.enabled == value || this.disabling) return;
      this.enabled = value;
      ModuleRevision.changed();
      try {
         com.utilitymod.echo.features.impl.render.Notifications.onToggle(this.name, value);
         if (!com.utilitymod.echo.features.impl.render.Notifications.muted) {
            com.utilitymod.gui.ToggleSounds.play(com.utilitymod.gui.Category.MISC, value);
         }
      } catch (Throwable ignored) {
      }
      if (value) {
         this.enabling = true;
         this.disableRequested = false;
         try {
            this.registerHandlers();
            this.onEnable();
            this.enabling = false;
            if (this.disableRequested) {
               this.disableRequested = false;
               this.setEnabled(false);
            }
         } catch (Throwable failure) {
            this.enabling = false;
            this.disableRequested = false;
            LOGGER.error(LT.L7, this.name, failure);
            this.setEnabled(false);
         }
      } else {
         this.disabling = true;
         try {
            this.onDisable();
         } catch (RuntimeException failure) {
            LOGGER.error(LT.L8, this.name, failure);
         } finally {
            this.ownedListeners.forEach(ListenerHandle::unregister);
            this.ownedListeners.clear();
            this.disabling = false;
         }
      }
   }

   public final void toggle() {
      this.setEnabled(!this.enabled);
   }

   protected final void requestDisable() {
      if (this.enabling) this.disableRequested = true;
      else this.setEnabled(false);
   }

   public final int keybind() { return this.keybind; }
   public final void setKeybind(int glfwKey) { this.keybind = glfwKey; }
   public final boolean isHoldMode() { return this.holdMode; }
   public final void setHoldMode(boolean value) { this.holdMode = value; }

   protected void onEnable() {}
   protected void onDisable() {}

   protected <E extends Event> void on(EventType<E> type, Consumer<E> callback) {
      this.on(type, Stage.PRE, EventPriority.NORMAL, callback);
   }

   protected <E extends Event> void on(EventType<E> type, Stage stage, Consumer<E> callback) {
      this.on(type, stage, EventPriority.NORMAL, callback);
   }

   protected <E extends Event> void on(EventType<E> type, Stage stage, EventPriority priority, Consumer<E> callback) {
      if (!this.enabled) {
         throw new IllegalStateException(LT.L9 + this.name);
      }
      this.ownedListeners.add(type.listen(this, stage, priority, callback));
   }

   /** Every {@code @EventHandler void x(SomeEvent e)} in the class hierarchy -> SomeEvent.TYPE.listen. */
   @SuppressWarnings({"unchecked", "rawtypes"})
   private void registerHandlers() throws Exception {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      for (Class<?> c = this.getClass(); c != null && c != ClientModule.class; c = c.getSuperclass()) {
         for (Method m : c.getDeclaredMethods()) {
            EventHandler h = m.getAnnotation(EventHandler.class);
            if (h == null || m.getParameterCount() != 1 || Modifier.isStatic(m.getModifiers())) continue;
            Class<?> eventClass = m.getParameterTypes()[0];
            Field typeField = eventClass.getField(LT.L10);
            EventType type = (EventType) typeField.get(null);
            m.setAccessible(true);
            MethodHandle mh = lookup.unreflect(m).bindTo(this);
            Consumer consumer = event -> {
               try {
                  mh.invoke(event);
               } catch (Throwable t) {
                  LOGGER.error(LT.L11, this.name, m.getName(), t);
               }
            };
            this.ownedListeners.add(type.listen(this, h.stage(), h.priority(), consumer));
         }
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4eff,0x9db6,0x6485,0x3d2c,0xae86,0x0387,0x1661,0x4dbf,0xace4,0x9810,0x736c,0x0406,0x5579,0x7881,0x3dd0,0x874a,0x59b9}, new char[]{0xac91,0x4e8b,0x4dd6,0x64e9,0x8723,0x3da4,0x9869,0x03ea,0x5516,0x7308,0x5996,0x3d4d,0xaee8,0x0473,0x1603,0x9ddf,0x78f2}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x931f,0x9e58,0x80a9,0x5e6c,0x04aa,0x309a,0x3438,0xf62b,0x26be,0x93c8,0x29ac}, new char[]{0x934c,0xf643,0x93a7,0x04dd,0x5e4c,0x80c0,0x29c2,0x30ba,0x26f6,0x9e0d,0x347c}).toString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4b6c,0xe549,0x426c,0x1b59,0x1fe3,0x0870,0x56da,0x6ac3}, new char[]{0x1b3a,0x56bb,0x0804,0x1f86,0xe52e,0x4203,0x4b1e,0x6aba}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7ce3,0xa2d7,0x669c,0x2d2c,0xefc3,0x2173,0x31fa,0x73be,0x4ab1,0x2fd7,0x2cb2,0x4720,0x2830,0x6268,0x3c94,0x3d3f,0xeac4,0x37d8,0x2046,0x57d3,0x1a5d,0x5a94}, new char[]{0x3d7b,0x37ad,0x2103,0x285c,0xeaad,0x2cd1,0x4741,0x73ca,0xa2b2,0x2ff7,0x3ce7,0x2d49,0x621c,0xefb7,0x7c8a,0x5afa,0x1a3a,0x2066,0x4ad8,0x57b7,0x31c0,0x66bc}).toString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x78b3,0xba71,0x0c5e,0xe038,0xba0f,0x48fe,0x8567,0x8ff7,0xe753,0x4d8c,0x060b,0x3ab6,0xba52}, new char[]{0x0c30,0x0664,0xba05,0xba3b,0x8f91,0xe73a,0xba6c,0x3ad7,0x8513,0xe051,0x78dc,0x4890,0x4dff}).plainString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2f9c,0x2711,0x1601,0x8d06,0xb21a,0x9b07,0x0432,0xce4a,0x96b4,0xb501,0x6c42,0x376b,0x7e2a}, new char[]{0x3725,0x8d69,0xce3e,0x96dd,0x7e4c,0x2778,0x2fff,0xb560,0x9b73,0x045b,0x166e,0x6c2c,0xb269}).toString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4a6d,0x6a81,0xbc01,0xa959,0x7dc6,0x0989,0x1b97,0x8b01,0x568b,0xd3e1,0x292e,0xbadc,0xb979,0x5630,0x212a,0xa66b,0xcc89,0x3e0a,0x5703,0xd409,0x721a,0xda3e,0xdf0c,0x894a,0x488f,0x0bf0,0xe0c5,0xfc14,0x79e9,0x13d2,0x6515,0x50c4,0x4a15,0xce15,0xe695,0x3396,0x6134,0x2cb9,0x156d,0x6ee1,0x7b68,0x097a,0x7b9a,0xb62a,0x6e89,0x75d9,0x8f42,0x5cd0,0x8f37,0x8a5c,0x9187,0x2827}, new char[]{0x4a53,0xa60a,0x1bfe,0xbc6d,0x091f,0x8f53,0x2807,0xd395,0x7bf5,0xcca9,0x798c,0x7da8,0x56ea,0x6156,0xbab0,0x5655,0x48af,0xda53,0xb645,0x13b6,0x75ac,0x576f,0x8f27,0x210a,0x6eae,0xa922,0x1510,0x50e3,0xe6ae,0xfc34,0x09e8,0x8b62,0x0b84,0x5cb9,0x893c,0x3e6b,0x8a28,0xdf65,0x6aee,0xce7b,0xe0e5,0x91f5,0x33f9,0xb915,0x6e8d,0x6570,0x2cdd,0x4a4d,0x7b0a,0x294f,0xd46a,0x7271}).plainString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa6c9,0x3f2f,0x75e6,0xbdfb,0xba46,0xfc7f,0xfda0,0x63c4,0xdacc,0xb756,0x84e3,0xbdb1,0x2dee,0x5400,0x9ce4,0xc15a,0x37c7,0x6aa6,0x2757,0xd31f,0xd0b9,0x8ac0,0xf7ab,0xb568,0x5c0b,0xc484,0x3fc2,0xf719,0x8eab,0x6a74,0x389b,0xc0fe}, new char[]{0x5446,0xc13b,0xb73f,0xc092,0xf77c,0x9c80,0x38bb,0xbdc5,0x7589,0xbddb,0x5c6f,0xfdc5,0x6a15,0x2734,0x63b0,0x848a,0x6ad0,0x8eca,0x3f5b,0xd37a,0xfc5f,0xdaa1,0xc4eb,0xf7cf,0x37b2,0xba2a,0x3fa7,0x8ae0,0xb54f,0xa6b2,0xd0c4,0x2dc9}).plainString();
            static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x88a6,0xae0d,0x5c61,0x03fb,0x83a0,0x4150,0xf4a4,0xc32e,0x64ff,0x9e47,0x3200,0xe298,0x6839,0x4093,0x1dc4,0xcb38,0xc7ec,0xc2b9,0x6d22,0x99cd,0x6cc8,0xce2f,0xcdd3,0xeddc,0xf357,0xf16e,0x5244,0xb2f4,0x519e,0x0e5a,0x8f8f,0x8299,0x03c3,0x1cb5,0x1c58,0x3e4e,0xaf45,0x52bd,0xd3fb,0x4b9d,0x532f,0xdad4,0xfee8,0xd906,0x918a,0x62ff,0x284b}, new char[]{0x1d87,0xf336,0xfe86,0x6d4c,0x1cda,0xd38f,0xce0f,0x684b,0x8fea,0x9e20,0x2822,0x1c2b,0x88d2,0x4135,0x99bf,0xb2d4,0x0e3b,0x5c41,0x83cc,0xaf2c,0x82ea,0x628b,0x52d8,0x6ca6,0x5221,0x0389,0xc7cc,0x03ac,0x91e4,0xd926,0xcdb7,0xae64,0xcb4b,0xdab5,0xc2db,0xedb0,0x4bf8,0x3e2a,0x3220,0x6492,0xc341,0xf4c0,0x535a,0xf102,0xe2fd,0x51a4,0x40b3}).toString();
            static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5915,0xbfba,0x8d30,0x168d}, new char[]{0x8d64,0xbfe3,0x5945,0x16c8}).plainString();
            static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd669,0xa6d6,0x677c,0x37ec,0x63ec,0xd11d,0x972a,0xec61,0x28a2,0x2beb,0x9c3c,0xc237,0x3cec,0x950f,0x534d,0x8443,0x2b44,0x5253,0x5ffa,0x2d99,0x175b}, new char[]{0x63a0,0x9566,0x9c4f,0x975e,0xa6b3,0x2b2a,0x8426,0x533f,0x675c,0x5228,0x28df,0x1775,0xc24c,0xd614,0xec41,0xd17b,0x3c8d,0x5f93,0x2b87,0x2dfc,0x3788}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc954}, new char[]{0xc97b,0xdb7c,0x1004,0x31f4}).plainString();
    }
}
