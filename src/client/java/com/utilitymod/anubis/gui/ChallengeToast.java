package com.utilitymod.anubis.gui;

import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.client.toast.Toast.Visibility;

public class ChallengeToast implements Toast {
   private static final int BACKGROUND = -266921698;
   private static final int TITLE_COLOR = -986891;
   private static final int MESSAGE_COLOR = -5656902;
   private static final int COUNT_COLOR = -6643540;
   private static final int TIMER_TRACK = 654311423;
   private static final int TIMER_FILL = -1711276033;
   private static final int TEXT_X = 30;
   private static final int TEXT_PAD = 7;
   private static final int RIGHT_GAP = 3;
   private static final int MAX_WIDTH = 240;
   private static final long DISPLAY_MILLIS = 5000L;
   private final Object token;
   private final ItemStack icon;
   private final boolean sound;
   private Text title;
   private Text message;
   private int width;
   private int count = 1;
   private boolean changed;
   private boolean hiding;
   private long changedAt;
   private Visibility wanted = Visibility.HIDE;

   public ChallengeToast(ItemStack icon, Text title, Text message, boolean sound) {
      this(TYPE, icon, title, message, sound);
   }

   private ChallengeToast(Object token, ItemStack icon, Text title, Text message, boolean sound) {
      this.token = token;
      this.icon = icon.copy();
      this.title = title;
      this.message = message;
      this.sound = sound;
      this.fit();
   }

   public static void addOrUpdate(ItemStack icon, String title, Text message, boolean sound) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (!mc.isOnThread()) {
         mc.execute(() -> addOrUpdate(icon, title, message, sound));
      } else {
         ToastManager toasts = mc.getToastManager();
         ChallengeToast toast = (ChallengeToast)toasts.getToast(ChallengeToast.class, title);
         if (toast == null) {
            toasts.add(new ChallengeToast(title, icon, Text.literal(title), message, sound));
         } else {
            toast.count++;
            toast.message = message;
            toast.changed = true;
            toast.fit();
         }
      }
   }

   private String countLabel() {
      return this.count > 1 ? LT.L1 + this.count : "";
   }

   private void fit() {
      TextRenderer font = MinecraftClient.getInstance().textRenderer;
      int text = Math.max(font.getWidth(this.title) + font.getWidth(this.countLabel()), font.getWidth(this.message));
      this.width = MathHelper.clamp(30 + text + 7, 160, 240);
   }

   public Object getType() {
      return this.token;
   }

   public SoundEvent getSoundEvent() {
      return this.sound ? (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_BELL.value() : null;
   }

   public Visibility getVisibility() {
      return this.wanted;
   }

   public int getWidth() {
      return this.width;
   }

   public void update(ToastManager toasts, long visibleFor) {
      if (!this.hiding) {
         if (this.changed) {
            this.changedAt = visibleFor;
            this.changed = false;
         }

         this.hiding = visibleFor - this.changedAt >= 5000.0 * toasts.getNotificationDisplayTimeMultiplier();
         this.wanted = this.hiding ? Visibility.HIDE : Visibility.SHOW;
      }
   }

   public void onFinishedRendering() {
      if (this.changed) {
         this.hiding = false;
         MinecraftClient.getInstance().getToastManager().add(this);
      }
   }

   public void draw(DrawContext graphics, TextRenderer font, long visibleFor) {
      int right = this.width - 3;
      int bottom = 31;
      rounded(graphics, 0, 1, right, bottom, -266921698);
      this.drawTimer(graphics, visibleFor, right, bottom);
      boolean single = this.message.getString().isEmpty();
      this.drawText(graphics, font, this.title, this.countLabel(), single ? 12 : 7, -986891);
      if (!single) {
         this.drawText(graphics, font, this.message, "", 18, -5656902);
      }

      graphics.drawItemWithoutEntity(this.icon, 8, 8);
   }

   private void drawTimer(DrawContext graphics, long visibleFor, int right, int bottom) {
      double duration = 5000.0 * MinecraftClient.getInstance().getToastManager().getNotificationDisplayTimeMultiplier();
      float left = 1.0F - (float)MathHelper.clamp((visibleFor - this.changedAt) / duration, 0.0, 1.0);
      int x0 = 4;
      int x1 = right - 4;
      int y = bottom - 2;
      graphics.fill(x0, y, x1, y + 1, 654311423);
      graphics.fill(x0, y, x0 + Math.round((x1 - x0) * left), y + 1, -1711276033);
   }

   private static void rounded(DrawContext graphics, int x0, int y0, int x1, int y1, int color) {
      graphics.fill(x0 + 2, y0, x1 - 2, y0 + 1, color);
      graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, color);
      graphics.fill(x0, y0 + 2, x1, y1 - 2, color);
      graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, color);
      graphics.fill(x0 + 2, y1 - 1, x1 - 2, y1, color);
   }

   private void drawText(DrawContext graphics, TextRenderer font, Text text, String suffix, int y, int color) {
      int room = this.width - 30 - 7;
      int textWidth = font.getWidth(text) + font.getWidth(suffix);
      graphics.getMatrices().pushMatrix();
      graphics.getMatrices().translate(30.0F, y);
      if (textWidth > room) {
         graphics.getMatrices().scale((float)room / textWidth, 1.0F);
      }

      graphics.drawText(font, text, 0, 0, color, true);
      if (!suffix.isEmpty()) {
         graphics.drawText(font, suffix, font.getWidth(text), 0, -6643540, true);
      }

      graphics.getMatrices().popMatrix();
   }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9624,0x9cb5}, new char[]{0x9604,0x9ccd,0x2321,0x3032}).plainString();
    }
}
