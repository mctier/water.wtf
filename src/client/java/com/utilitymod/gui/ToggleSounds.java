package com.utilitymod.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Per-category module toggle sounds (vanilla sounds, so they follow the master volume). */
public final class ToggleSounds {
    public static volatile boolean enabled = true;
    /** Use SoupAPI's on.ogg / off.ogg instead of the per-category vanilla sounds. */
    public static volatile boolean soupPack = false;

    private ToggleSounds() {}

    public static void play(Category category, boolean on) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || mc.player == null || mc.getSoundManager() == null) return;
        if (soupPack) {
            com.utilitymod.echo.render.SoupAssets.play(on ? LT.L1 : LT.L2, 0.6f);
            return;
        }
        SoundEvent sound;
        float pitch, volume;
        if (category == null) category = Category.MISC;
        switch (category) {
            case COMBAT -> { sound = SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(); pitch = on ? 1.7f : 1.0f; volume = 0.5f; }
            case MOVEMENT -> { sound = SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(); pitch = on ? 1.4f : 0.9f; volume = 0.45f; }
            case VISUAL -> { sound = SoundEvents.UI_BUTTON_CLICK.value(); pitch = on ? 1.5f : 1.1f; volume = 0.2f; }
            default -> { sound = SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(); pitch = on ? 1.5f : 1.0f; volume = 0.35f; }
        }
        mc.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7bc3,0x3674}, new char[]{0x361b,0x7bad,0xad64,0xf065}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1778,0xc6db,0xdf3c}, new char[]{0xdf53,0x171e,0xc6bd,0x2745}).plainString();
    }
}
