package com.utilitymod.anubis.module.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import com.utilitymod.anubis.module.ModuleRevision;
import com.utilitymod.anubis.module.StableId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Setting {
   private static final Logger LOGGER = LoggerFactory.getLogger(LT.L1);
   private final String name;
   private String id;
   private boolean idBound;
   private long revision;
   private final List<Runnable> changeListeners = new ArrayList<>(1);
   private BooleanSupplier visibility;
   private boolean privateValue;
   private boolean sessionOnly;
   private JsonElement defaultValue;
   private long defaultCheckedRevision = -1L;
   private boolean defaultCheckResult = true;

   protected Setting(String name) {
      this.name = name;
      // Some displayed names are PUA-cloaked (U+E100..U+E1FF) and have no ASCII
      // alphanums for StableId.legacy to slug — those settings must be registered
      // via ClientModule.addSetting(explicitId, setting), which calls bindId() to
      // supply the real id. Fall back to a placeholder here so field init on a
      // PUA-named setting doesn't throw before bindId has a chance to run.
      String slug;
      try {
         slug = StableId.legacy(name);
      } catch (IllegalArgumentException unsluggable) {
         slug = null;
      }
      this.id = slug;
   }

   public final String id() {
      return this.id;
   }

   public final long revision() {
      return this.revision;
   }

   public final void bindId(String id) {
      if (this.idBound) {
         throw new IllegalStateException(LT.L2);
      }

      this.id = StableId.validate(id);
      this.idBound = true;
   }

   public final String name() {
      return this.name;
   }

   public final Setting onChange(Runnable listener) {
      this.changeListeners.add(listener);
      return this;
   }

   public final void notifyChanged() {
      this.revision++;

      for (int i = 0; i < this.changeListeners.size(); i++) {
         Runnable listener = this.changeListeners.get(i);

         try {
            if (listener == ModuleRevision.CHANGED) {
               ModuleRevision.changed();
            } else {
               listener.run();
            }
         } catch (Throwable t) {
            LOGGER.error(LT.L3, this.name, t);
         }
      }
   }

   public final Setting visibleWhen(BooleanSupplier condition) {
      this.visibility = condition;
      return this;
   }

   public final boolean isVisible() {
      return this.visibility == null || this.visibility.getAsBoolean();
   }

   public final Setting privateValue() {
      this.privateValue = true;
      return this;
   }

   public final boolean isPrivate() {
      return this.privateValue;
   }

   public final Setting sessionOnly() {
      this.sessionOnly = true;
      this.privateValue = true;
      return this;
   }

   public final boolean isSessionOnly() {
      return this.sessionOnly;
   }

   public final void captureDefault() {
      if (this.defaultValue == null) {
         this.defaultValue = maskTree(this.serialize());
      }
   }

   public final void adoptDefault(JsonElement value) {
      this.deserialize(value);
      this.defaultValue = maskTree(this.serialize());
      this.defaultCheckedRevision = -1L;
   }

   public final boolean isDefault() {
      if (this.defaultValue == null) {
         return true;
      }

      if (this.defaultCheckedRevision != this.revision) {
         this.defaultCheckResult = unmaskTree(this.defaultValue).equals(unmaskTree(this.serialize()));
         this.defaultCheckedRevision = this.revision;
      }

      return this.defaultCheckResult;
   }

   public final boolean isDefault(JsonElement value) {
      return this.defaultValue != null && sameValue(unmaskTree(this.defaultValue), unmaskTree(value));
   }

   public static boolean sameValue(JsonElement a, JsonElement b) {
      if (a == null || b == null) {
         return a == b;
      }

      if (a.isJsonPrimitive() && b.isJsonPrimitive()) {
         JsonPrimitive left = a.getAsJsonPrimitive();
         JsonPrimitive right = b.getAsJsonPrimitive();
         if (left.isNumber() && right.isNumber()) {
            try {
               return left.getAsBigDecimal().compareTo(right.getAsBigDecimal()) == 0;
            } catch (NumberFormatException e) {
               return false;
            }
         } else {
            return left.equals(right);
         }
      } else if (a.isJsonArray() && b.isJsonArray()) {
         JsonArray left = a.getAsJsonArray();
         JsonArray right = b.getAsJsonArray();
         if (left.size() != right.size()) {
            return false;
         }

         for (int i = 0; i < left.size(); i++) {
            if (!sameValue(left.get(i), right.get(i))) {
               return false;
            }
         }

         return true;
      } else if (a.isJsonObject() && b.isJsonObject()) {
         JsonObject left = a.getAsJsonObject();
         JsonObject right = b.getAsJsonObject();
         if (!left.keySet().equals(right.keySet())) {
            return false;
         }

         for (String key : left.keySet()) {
            if (!sameValue(left.get(key), right.get(key))) {
               return false;
            }
         }

         return true;
      } else {
         return a.equals(b);
      }
   }

   public final void resetToDefault() {
      if (!this.isDefault()) {
         this.deserialize(unmaskTree(this.defaultValue));
      }
   }

   public JsonElement compact(JsonElement value) {
      return value;
   }

   protected final JsonElement defaultJson() {
      return unmaskTree(this.defaultValue);
   }

   // defaultValue lives PUA-masked at rest: mode/text defaults are scanner-dictionary
   // phrases, so the plain JsonElement must never sit in a heap field (see TextMask).
   private static JsonElement maskTree(JsonElement e) {
      if (e == null || e.isJsonNull()) return e;
      if (e.isJsonPrimitive()) {
         JsonPrimitive p = e.getAsJsonPrimitive();
         return p.isString() ? new JsonPrimitive(com.utilitymod.anubis.util.TextMask.mask(p.getAsString())) : p;
      }
      if (e.isJsonArray()) {
         JsonArray src = e.getAsJsonArray(), out = new JsonArray();
         for (JsonElement x : src) out.add(maskTree(x));
         return out;
      }
      JsonObject src = e.getAsJsonObject(), out = new JsonObject();
      for (String k : src.keySet()) out.add(k, maskTree(src.get(k)));
      return out;
   }

   private static JsonElement unmaskTree(JsonElement e) {
      if (e == null || e.isJsonNull()) return e;
      if (e.isJsonPrimitive()) {
         JsonPrimitive p = e.getAsJsonPrimitive();
         return p.isString() ? new JsonPrimitive(com.utilitymod.anubis.util.TextMask.plain(p.getAsString())) : p;
      }
      if (e.isJsonArray()) {
         JsonArray src = e.getAsJsonArray(), out = new JsonArray();
         for (JsonElement x : src) out.add(unmaskTree(x));
         return out;
      }
      JsonObject src = e.getAsJsonObject(), out = new JsonObject();
      for (String k : src.keySet()) out.add(k, unmaskTree(src.get(k)));
      return out;
   }

   public abstract JsonElement serialize();

   public final void deserialize(JsonElement element) {
      this.deserializeValue(element);
      this.notifyChanged();
   }

   protected abstract void deserializeValue(JsonElement var1);


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xea65,0x90d9,0x4a74,0x653d,0x80a4,0x86f5}, new char[]{0x9098,0x4a1a,0x80d1,0xea07,0x6554,0x8686}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xec06,0x227b,0xb67d,0x2e12,0xbb2f,0xbbd1,0x5fef,0x6b6a,0x4d58,0xb5f2,0xaf75,0x4bed,0x2acf,0xba86,0xc86c,0xcdfb,0x2417,0xbbc5,0xf49d,0xdb21,0x24d9,0xd902,0xcd76,0xe887}, new char[]{0x6b39,0xec63,0xb586,0xd976,0x2212,0xe8e9,0xcd9c,0xdb01,0x2e7b,0xbb4b,0x5fcf,0x24b8,0xbaea,0xbba3,0x2472,0xaf14,0xbba1,0x4b94,0xcd56,0xf4ff,0x2aa0,0x4d2d,0xc802,0xb619}).toString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x73a5,0x776e,0x6422,0x107e,0x3c05,0xce7c,0xd466,0x3e09,0x3dc7,0x8ca2,0xab62,0xaa1d,0x096f,0x5629,0x9663,0x9ff6,0xfb52,0xf6b0,0x18b9,0xa107,0x8177,0xd8c3,0xbbea,0x91c8,0xc9bb,0x4b54,0x4b00,0xdfd8,0x8530,0x2101,0x2264,0x4082,0x75c9,0x499d,0xe7c0,0xc0af,0x6bc6,0x214b,0xaac8}, new char[]{0x919b,0x3da2,0xf6c4,0xd8b7,0x5640,0x49f3,0xdfbf,0xbbca,0x090c,0x73cd,0x4b35,0x644c,0x8cc5,0x3c60,0xc08f,0x960f,0x6baf,0x3e7a,0xab16,0xe7a5,0x7700,0x18dc,0x75bb,0x2121,0xa161,0x8116,0x9f9f,0xaaa4,0x40e7,0xd402,0xfb72,0x4b66,0xaa72,0xc9c9,0xce5c,0x2243,0x2130,0x1003,0x8517}).plainString();
    }
}
