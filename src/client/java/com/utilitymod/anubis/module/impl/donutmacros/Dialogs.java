package com.utilitymod.anubis.module.impl.donutmacros;

import com.utilitymod.anubis.util.Access;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.dialog.type.ColumnsDialog;
import net.minecraft.dialog.type.Dialog;
import net.minecraft.dialog.type.DialogInput;
import net.minecraft.dialog.type.MultiActionDialog;
import net.minecraft.dialog.type.SimpleDialog;
import net.minecraft.dialog.body.DialogBody;
import net.minecraft.dialog.body.ItemDialogBody;
import net.minecraft.dialog.body.PlainMessageDialogBody;
import net.minecraft.dialog.input.BooleanInputControl;
import net.minecraft.dialog.input.TextInputControl;
import net.minecraft.client.gui.screen.dialog.DialogScreen;
import net.minecraft.dialog.DialogActionButtonData;
import net.minecraft.text.ObjectTextContent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.TextColor;
import net.minecraft.dialog.action.DialogAction.ValueGetter;

final class Dialogs {
   private Dialogs() {
   }

   static Dialogs.View current(MinecraftClient mc) {
      if (!(mc.currentScreen instanceof DialogScreen<?> screen)) {
         return null;
      } else {
         Dialog dialog = Access.dialog((DialogScreen<?>)screen);
         if (dialog == null) {
            return null;
         }

         List<DialogActionButtonData> raw = new ArrayList<>();
         if (dialog instanceof MultiActionDialog multi) {
            raw.addAll(multi.actions());
         }

         if (dialog instanceof SimpleDialog simple) {
            raw.addAll(simple.getButtons());
         }

         if (dialog instanceof ColumnsDialog list) {
            list.exitAction().ifPresent(raw::add);
         }

         List<Dialogs.Button> buttons = new ArrayList<>(raw.size());

         for (DialogActionButtonData button : raw) {
            Text label = button.data().label();
            String text = visibleText(label);
            buttons.add(new Dialogs.Button(button, ServerText.normalize(text), ServerText.key(text), label));
         }

         List<String> body = new ArrayList<>();
         List<ItemStack> items = new ArrayList<>();

         for (DialogBody part : dialog.common().body()) {
            if (part instanceof PlainMessageDialogBody message) {
               body.add(ServerText.normalize(visibleText(message.contents())));
            }

            if (part instanceof ItemDialogBody item) {
               items.add(item.item());
               item.description().ifPresent(message -> body.add(ServerText.normalize(visibleText(message.contents()))));
            }
         }

         List<String> textInputs = new ArrayList<>();

         for (DialogInput input : dialog.common().inputs()) {
            if (input.control() instanceof TextInputControl) {
               textInputs.add(input.key());
            }
         }

         return new Dialogs.View(dialog, ServerText.normalize(visibleText(dialog.common().title())), buttons, body, textInputs, items);
      }
   }

   static String visibleText(Text component) {
      StringBuilder out = new StringBuilder();
      appendVisible(component, out);
      return out.toString();
   }

   private static void appendVisible(Text component, StringBuilder out) {
      if (!(component.getContent() instanceof ObjectTextContent)) {
         component.getContent().visit(text -> {
            out.append(text);
            return Optional.empty();
         });
      }

      for (Text sibling : component.getSiblings()) {
         appendVisible(sibling, out);
      }
   }

   static boolean press(MinecraftClient mc, Dialogs.View view, Dialogs.Button button, Map<String, String> text) {
      if (mc.currentScreen instanceof DialogScreen<?> screen) {
         if (Access.dialog((DialogScreen<?>)screen) != view.dialog()) {
            return false;
         }

         Map<String, ValueGetter> values = new HashMap<>();

         for (DialogInput input : view.dialog().common().inputs()) {
            if (input.control() instanceof TextInputControl textInput) {
               values.put(input.key(), ValueGetter.of(text.getOrDefault(input.key(), textInput.initial())));
            } else if (input.control() instanceof BooleanInputControl bool) {
               values.put(input.key(), booleanValue(bool));
            }
         }

         Optional<ClickEvent> event = button.raw().action().flatMap(action -> action.createClickEvent(values));
         screen.runAction(event);
         return true;
      } else {
         return false;
      }
   }

   static void close(MinecraftClient mc) {
      if (mc.currentScreen instanceof DialogScreen<?> screen) {
         screen.close();
      }
   }

   private static ValueGetter booleanValue(BooleanInputControl input) {
      final boolean value = input.initial();
      return new ValueGetter() {
         public String get() {
            return value ? input.onTrue() : input.onFalse();
         }

         public NbtElement getAsNbt() {
            return NbtByte.of(value);
         }
      };
   }

   private static boolean isGreen(int rgb) {
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;
      return g >= 192 && r < 144 && b < 144;
   }

   record Button(DialogActionButtonData raw, String label, String key, Text component) {
      boolean green() {
         boolean[] found = new boolean[]{false};
         this.component.visit((style, text) -> {
            TextColor color = style.getColor();
            if (!text.isBlank() && color != null && Dialogs.isGreen(color.getRgb())) {
               found[0] = true;
            }

            return Optional.empty();
         }, Style.EMPTY);
         return found[0];
      }
   }

   record View(Dialog dialog, String title, List<Dialogs.Button> buttons, List<String> body, List<String> textInputs, List<ItemStack> items) {
      Dialogs.Button find(Predicate<Dialogs.Button> test) {
         for (Dialogs.Button button : this.buttons) {
            if (test.test(button)) {
               return button;
            }
         }

         return null;
      }

      Dialogs.Button labelled(String... words) {
         for (String word : words) {
            String wanted = ServerText.key(word);
            Dialogs.Button exact = this.find(button -> button.key().equals(wanted));
            if (exact != null) {
               return exact;
            }
         }

         for (String word : words) {
            String wanted = ServerText.key(word);
            Dialogs.Button partial = this.find(button -> button.key().contains(wanted));
            if (partial != null) {
               return partial;
            }
         }

         return null;
      }

      boolean titled(String text) {
         return this.title.contains(text);
      }

      String describe() {
         List<String> labels = new ArrayList<>();

         for (Dialogs.Button button : this.buttons) {
            if (!button.label().isBlank()) {
               labels.add(button.label());
            }

            if (labels.size() >= 12) {
               break;
            }
         }

         return LT2.L1 + this.title + LT.L1 + (labels.isEmpty() ? LT.L2 : String.join(LT2.L2, labels));
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7f3c,0x46ec,0x8e65,0xe796,0x348a,0x8c8b,0xb013,0xbc1c,0xe6f7,0x3832,0x28fc}, new char[]{0xb034,0xe7b6,0x7f5e,0x8cfe,0xe683,0xbc68,0x385d,0x2892,0x469f,0x34b0,0x8e45}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0cd2,0xf987,0x28d8,0xbd66,0xa3f1,0xf5aa}, new char[]{0xf582,0x0cbc,0xf9e8,0xa39f,0x28bd,0xbd4f}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2a69}, new char[]{0x2a4e,0x0041,0x7bba,0x326e}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5369,0x828b}, new char[]{0x5345,0x82ab,0x7896,0xe87d}).plainString();
    }
}
