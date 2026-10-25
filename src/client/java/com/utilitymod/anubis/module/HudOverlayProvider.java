package com.utilitymod.anubis.module;

import com.utilitymod.anubis.imgui.ImDrawList;

public interface HudOverlayProvider {
   boolean isHudOverlayEnabled();

   void renderHudOverlay(ImDrawList var1, float var2, float var3);
}
