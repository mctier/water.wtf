package com.utilitymod.anubis.module.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.utilitymod.anubis.imgui.ImString;
import java.util.ArrayList;
import java.util.List;

public final class StringListSetting extends Setting {
   public static final int MAX_ENTRIES = 64;
   private static final int ENTRY_CAPACITY = 256;
   private static final char LEGACY_SEPARATOR = '|';
   private final List<ImString> entries = new ArrayList<>();
   private List<String> values;
   private boolean itemPicker;

   public StringListSetting(String name, String... defaults) {
      super(name);

      for (String value : defaults) {
         this.entries.add(new ImString(value, 256));
      }
   }

   public StringListSetting itemPicker() {
      this.itemPicker = true;
      return this;
   }

   public boolean isItemPicker() {
      return this.itemPicker;
   }

   public boolean addValue(String value) {
      if (this.canAdd() && value != null && !value.isBlank()) {
         this.entries.add(new ImString(value, 256));
         this.changed();
         return true;
      } else {
         return false;
      }
   }

   public boolean removeValue(String value) {
      for (int i = 0; i < this.entries.size(); i++) {
         if (this.entries.get(i).get().trim().equalsIgnoreCase(value)) {
            this.entries.remove(i);
            this.changed();
            return true;
         }
      }

      return false;
   }

   public void clear() {
      if (!this.entries.isEmpty()) {
         this.entries.clear();
         this.changed();
      }
   }

   public List<String> get() {
      if (this.values == null) {
         List<String> out = new ArrayList<>(this.entries.size());

         for (ImString entry : this.entries) {
            String value = entry.get().trim();
            if (!value.isEmpty()) {
               out.add(value);
            }
         }

         this.values = List.copyOf(out);
      }

      return this.values;
   }

   public int size() {
      return this.entries.size();
   }

   public ImString entry(int index) {
      return this.entries.get(index);
   }

   public boolean canAdd() {
      return this.entries.size() < 64;
   }

   public void add() {
      if (this.canAdd()) {
         this.entries.add(new ImString("", 256));
         this.changed();
      }
   }

   public void remove(int index) {
      if (index >= 0 && index < this.entries.size()) {
         this.entries.remove(index);
         this.changed();
      }
   }

   public void edited() {
      this.changed();
   }

   private void changed() {
      this.values = null;
      this.notifyChanged();
   }

   @Override
   public JsonElement serialize() {
      JsonArray out = new JsonArray();

      for (ImString entry : this.entries) {
         out.add(entry.get());
      }

      return out;
   }

   @Override
   protected void deserializeValue(JsonElement element) {
      if (element != null) {
         List<String> loaded;
         if (element.isJsonArray()) {
            loaded = new ArrayList<>();

            for (JsonElement item : element.getAsJsonArray()) {
               if (item != null && item.isJsonPrimitive()) {
                  loaded.add(item.getAsString());
               }
            }
         } else {
            if (!element.isJsonPrimitive()) {
               return;
            }

            loaded = splitLegacy(element.getAsString());
         }

         this.entries.clear();

         for (int i = 0; i < loaded.size() && i < 64; i++) {
            this.entries.add(new ImString(loaded.get(i), 256));
         }

         this.values = null;
      }
   }

   private static List<String> splitLegacy(String raw) {
      List<String> out = new ArrayList<>();
      StringBuilder current = new StringBuilder();

      for (int i = 0; i < raw.length(); i++) {
         char c = raw.charAt(i);
         if (c == '\\' && i + 1 < raw.length() && raw.charAt(i + 1) == '|') {
            current.append('|');
            i++;
         } else if (c == '|') {
            addTrimmed(out, current);
            current.setLength(0);
         } else {
            current.append(c);
         }
      }

      addTrimmed(out, current);
      return out;
   }

   private static void addTrimmed(List<String> out, CharSequence entry) {
      String value = entry.toString().trim();
      if (!value.isEmpty()) {
         out.add(value);
      }
   }
}
