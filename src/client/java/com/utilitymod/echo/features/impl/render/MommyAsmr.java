package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.utils.combat.KillTracker;
import net.minecraft.entity.player.PlayerEntity;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.ThreadLocalRandom;

/** Mommy ASMR: plays one of the bundled voice clips (or a random one) every time you get a kill. */
public class MommyAsmr extends Feature {
    private static final String[] CLIPS = {"m1", "m2", "m3", "m4"};

    private final ModeSetting sound = new ModeSetting("Sound", "Random", "Random", "M1", "M2", "M3", "M4");
    private final FloatSetting volume = new FloatSetting("Volume", 80f, 0f, 100f, 1f);
    private final BoolSetting playersOnly = new BoolSetting("Players Only", true);

    private static MommyAsmr instance;
    private long lastPlay;

    public MommyAsmr() {
        super(new FeatureInfo("Mommy ASMR", "Plays a voice clip when you kill someone.", Category.RENDER));
        instance = this;
        KillTracker.listen(victim -> {
            MommyAsmr m = instance;
            if (m == null || !m.isEnabled()) return;
            if (m.playersOnly.getValue() && !(victim instanceof PlayerEntity)) return;
            m.play();
        });
    }

    @Override
    public void onEnable() {
        super.onEnable();
        play();   // preview so you can hear it works
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onTick(EventTick e) {
        KillTracker.poll();
    }

    private void play() {
        long now = System.currentTimeMillis();
        if (now - lastPlay < 300) return;   // one clip per kill, even if the kill is reported twice
        lastPlay = now;
        // ModeSetting values are I18nText, so match with is() rather than toString()
        String file = CLIPS[ThreadLocalRandom.current().nextInt(CLIPS.length)];
        for (String c : CLIPS) if (sound.is(c.toUpperCase())) file = c;
        final String clipFile = file;
        float vol = volume.getValue() / 100f;
        // decode + play off the game thread so a kill never hitches a frame
        Thread t = new Thread(() -> playClip(clipFile, vol), "mommy-asmr");
        t.setDaemon(true);
        t.start();
    }

    private static void playClip(String file, float vol) {
        try (InputStream raw = MommyAsmr.class.getResourceAsStream("/assets/utilitymod/sounds/mommy/" + file + ".wav")) {
            if (raw == null) { com.utilitymod.UtilityMod.LOGGER.warn("Mommy ASMR: missing clip {}", file); return; }
            AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(raw));
            Clip clip = AudioSystem.getClip();
            clip.open(in);
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float db = vol <= 0.001f ? gain.getMinimum() : (float) (20 * Math.log10(vol));
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
            }
            clip.addLineListener(ev -> {
                if (ev.getType() == javax.sound.sampled.LineEvent.Type.STOP) clip.close();
            });
            clip.start();
        } catch (Throwable t) {
            com.utilitymod.UtilityMod.LOGGER.warn("Mommy ASMR: could not play {}: {}", file, t.toString());
        }
    }
}
