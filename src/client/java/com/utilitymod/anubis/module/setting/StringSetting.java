package com.utilitymod.anubis.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.utilitymod.anubis.imgui.ImString;
import java.util.List;

public final class StringSetting extends Setting {
   private final ImString imString;
   private String hint;
   private boolean itemPicker;
   private List<StringSetting.Preset> presets = List.of();
   private StringSetting enchantItem;
   private StringSetting enchantSetting;

   public StringSetting(String name, String defaultValue) {
      super(name);
      this.imString = new ImString(defaultValue, 512);
   }

   public StringSetting hint(String hint) {
      this.hint = hint;
      return this;
   }

   public String hint() {
      return this.hint;
   }

   public StringSetting itemPicker() {
      this.itemPicker = true;
      return this;
   }

   public boolean isItemPicker() {
      return this.itemPicker;
   }

   public StringSetting presets(List<StringSetting.Preset> presets) {
      this.presets = List.copyOf(presets);
      return this;
   }

   public List<StringSetting.Preset> presets() {
      return this.presets;
   }

   public StringSetting enchantsFor(StringSetting item) {
      this.enchantItem = item;
      item.enchantSetting = this;
      return this;
   }

   public StringSetting enchantItem() {
      return this.enchantItem;
   }

   public StringSetting enchantSetting() {
      return this.enchantSetting;
   }

   public String get() {
      return this.imString.get();
   }

   public void set(String newValue) {
      if (!this.imString.get().equals(newValue)) {
         this.imString.set(newValue);
         this.notifyChanged();
      }
   }

   public String getValue() {
      return this.get();
   }

   public ImString getImString() {
      return this.imString;
   }

   @Override
   public JsonElement serialize() {
      return new JsonPrimitive(this.imString.get());
   }

   @Override
   protected void deserializeValue(JsonElement element) {
      if (element != null && element.isJsonPrimitive()) {
         try {
            this.imString.set(element.getAsString());
         } catch (RuntimeException var3) {
         }
      }
   }

   public record Preset(String value, String label, String icon, boolean glint) {
   }
}
