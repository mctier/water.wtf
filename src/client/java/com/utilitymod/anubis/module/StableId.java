package com.utilitymod.anubis.module;

import java.util.Locale;

public final class StableId {
   private StableId() {
   }

   public static String validate(String id) {
      if (!isValid(id)) {
         throw new IllegalArgumentException(LT.L1 + id);
      } else {
         return id;
      }
   }

   public static String legacy(String label) {
      String lower = com.utilitymod.anubis.util.TextMask.plain(label).toLowerCase(Locale.ROOT);
      StringBuilder id = new StringBuilder(lower.length());
      boolean pendingDash = false;

      for (int i = 0; i < lower.length(); i++) {
         char c = lower.charAt(i);
         if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
            if (pendingDash && !id.isEmpty()) {
               id.append('-');
            }

            pendingDash = false;
            id.append(c);
         } else {
            pendingDash = true;
         }
      }

      return validate(id.toString());
   }

   private static boolean isValid(String id) {
      if (id != null && !id.isEmpty()) {
         boolean afterSeparator = true;

         for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if ((c < 'a' || c > 'z') && (c < '0' || c > '9')) {
               if (c != '.' && c != '_' && c != '-') {
                  return false;
               }

               if (afterSeparator) {
                  return false;
               }

               afterSeparator = true;
            } else {
               afterSeparator = false;
            }
         }

         return !afterSeparator;
      } else {
         return false;
      }
   }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1b66,0xc082,0x417c,0xf8cc,0x14a5,0x9654,0xdec2,0x36dc,0xce43,0xe11a,0x63b5,0xe4c4,0xce6f,0x7569,0x49b9,0x2c11,0xbf09,0xda2b,0x2076}, new char[]{0xde8b,0xbf67,0xc0f4,0x411d,0x1b0a,0xce2a,0xda4f,0x9674,0x36af,0xce1b,0xf8ad,0x49db,0xe4a8,0xe17f,0x7549,0x63dc,0x14c1,0x2c2b,0x2056}).toString();
    }
}
