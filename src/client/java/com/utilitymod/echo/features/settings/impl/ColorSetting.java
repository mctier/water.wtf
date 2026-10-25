package com.utilitymod.echo.features.settings.impl;

import lombok.Getter;
import lombok.Setter;

import java.util.function.Predicate;

import com.utilitymod.echo.features.settings.Setting;

@Getter
@Setter
public class ColorSetting extends Setting {
    private int red;
    private int green;
    private int blue;
    private int alpha;
    private float hue;
    private float saturation;
    private float brightness;
    private boolean showAlpha = true;

    public ColorSetting(CharSequence name, int red, int green, int blue, int alpha) {
        super(name);
        this.red = clamp(red);
        this.green = clamp(green);
        this.blue = clamp(blue);
        this.alpha = clamp(alpha);
        updateHSBFromRGB();
    }

    public ColorSetting(CharSequence name, int red, int green, int blue, int alpha, Predicate<Object> dependency) {
        super(name, dependency);
        this.red = clamp(red);
        this.green = clamp(green);
        this.blue = clamp(blue);
        this.alpha = clamp(alpha);
        updateHSBFromRGB();
    }

    public ColorSetting(CharSequence name, int red, int green, int blue, int alpha, boolean showAlpha, Predicate<Object> dependency) {
        super(name, dependency);
        this.red = clamp(red);
        this.green = clamp(green);
        this.blue = clamp(blue);
        this.alpha = clamp(alpha);
        this.showAlpha = showAlpha;
        updateHSBFromRGB();
    }

    public ColorSetting(String name, int red, int green, int blue, int alpha) {
        this((CharSequence) name, red, green, blue, alpha);
    }

    public ColorSetting(String name, int red, int green, int blue, int alpha, Predicate<Object> dependency) {
        this((CharSequence) name, red, green, blue, alpha, dependency);
    }

    // Constructor with ARGB int
    public ColorSetting(CharSequence name, int argb) {
        super(name);
        setFromARGB(argb);
    }

    public ColorSetting(CharSequence name, int argb, Predicate<Object> dependency) {
        super(name, dependency);
        setFromARGB(argb);
    }

    public ColorSetting(String name, int argb) {
        this((CharSequence) name, argb);
    }

    public ColorSetting(String name, int argb, Predicate<Object> dependency) {
        this((CharSequence) name, argb, dependency);
    }

    public ColorSetting(CharSequence name, int red, int green, int blue, int alpha, boolean showAlpha) {
        super(name);
        this.red = clamp(red);
        this.green = clamp(green);
        this.blue = clamp(blue);
        this.alpha = clamp(alpha);
        this.showAlpha = showAlpha;
        updateHSBFromRGB();
    }

    public ColorSetting(CharSequence name, int red, int green, int blue, int alpha, Predicate<Object> dependency,
            boolean showAlpha) {
        super(name, dependency);
        this.red = clamp(red);
        this.green = clamp(green);
        this.blue = clamp(blue);
        this.alpha = clamp(alpha);
        this.showAlpha = showAlpha;
        updateHSBFromRGB();
    }

    public ColorSetting(String name, int red, int green, int blue, int alpha, boolean showAlpha) {
        this((CharSequence) name, red, green, blue, alpha, showAlpha);
    }

    public ColorSetting(String name, int red, int green, int blue, int alpha, Predicate<Object> dependency,
            boolean showAlpha) {
        this((CharSequence) name, red, green, blue, alpha, dependency, showAlpha);
    }

    public ColorSetting(CharSequence name, int argb, boolean showAlpha) {
        super(name);
        this.showAlpha = showAlpha;
        setFromARGB(argb);
    }

    public ColorSetting(CharSequence name, int argb, Predicate<Object> dependency, boolean showAlpha) {
        super(name, dependency);
        this.showAlpha = showAlpha;
        setFromARGB(argb);
    }

    public ColorSetting(String name, int argb, boolean showAlpha) {
        this((CharSequence) name, argb, showAlpha);
    }

    public ColorSetting(String name, int argb, Predicate<Object> dependency, boolean showAlpha) {
        this((CharSequence) name, argb, dependency, showAlpha);
    }

    @Override
    public String getTypeId() { return LT.L1; }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public void setRed(int red) {
        this.red = clamp(red);
        updateHSBFromRGB();
        notifyChange();
    }

    public void setRedNoNotify(int red) {
        this.red = clamp(red);
        updateHSBFromRGB();
    }

    public void setGreen(int green) {
        this.green = clamp(green);
        updateHSBFromRGB();
        notifyChange();
    }

    public void setGreenNoNotify(int green) {
        this.green = clamp(green);
        updateHSBFromRGB();
    }

    public void setBlue(int blue) {
        this.blue = clamp(blue);
        updateHSBFromRGB();
        notifyChange();
    }

    public void setBlueNoNotify(int blue) {
        this.blue = clamp(blue);
        updateHSBFromRGB();
    }

    public void setAlpha(int alpha) {
        this.alpha = clamp(alpha);
        notifyChange();
    }

    public void setAlphaNoNotify(int alpha) {
        this.alpha = clamp(alpha);
    }

    public void setFromARGB(int argb) {
        this.alpha = (argb >> 24) & 0xFF;
        this.red = (argb >> 16) & 0xFF;
        this.green = (argb >> 8) & 0xFF;
        this.blue = argb & 0xFF;
        updateHSBFromRGB();
        notifyChange();
    }

    public void setFromRGB(int rgb) {
        this.red = (rgb >> 16) & 0xFF;
        this.green = (rgb >> 8) & 0xFF;
        this.blue = rgb & 0xFF;
        updateHSBFromRGB();
        notifyChange();
    }

    public int getRGB() {
        return (red << 16) | (green << 8) | blue;
    }

    public int getARGB() {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    // HSB conversion helpers
    public float[] getHSB() {
        return new float[] { hue, saturation, brightness };
    }

    public void setFromHSB(float hue, float saturation, float brightness) {
        this.hue = normalizeHue(hue);
        this.saturation = clamp01(saturation);
        this.brightness = clamp01(brightness);
        int rgb = hsbToRgb(this.hue, this.saturation, this.brightness);
        this.red = (rgb >> 16) & 0xFF;
        this.green = (rgb >> 8) & 0xFF;
        this.blue = rgb & 0xFF;
        notifyChange();
    }

    public void setFromHSBNoNotify(float hue, float saturation, float brightness) {
        this.hue = normalizeHue(hue);
        this.saturation = clamp01(saturation);
        this.brightness = clamp01(brightness);
        int rgb = hsbToRgb(this.hue, this.saturation, this.brightness);
        this.red = (rgb >> 16) & 0xFF;
        this.green = (rgb >> 8) & 0xFF;
        this.blue = rgb & 0xFF;
    }

    public float getHue() {
        return hue;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getBrightness() {
        return brightness;
    }

    public void setHue(float hue) {
        setFromHSB(hue, this.saturation, this.brightness);
        notifyChange();
    }

    public void setSaturation(float saturation) {
        setFromHSB(this.hue, saturation, this.brightness);
        notifyChange();
    }

    public void setBrightness(float brightness) {
        setFromHSB(this.hue, this.saturation, brightness);
        notifyChange();
    }

    public float getRedNormalized() {
        return red / 255.0f;
    }

    public float getGreenNormalized() {
        return green / 255.0f;
    }

    public float getBlueNormalized() {
        return blue / 255.0f;
    }

    public float getAlphaNormalized() {
        return alpha / 255.0f;
    }

    private void updateHSBFromRGB() {
        float[] hsb = rgbToHsb(red, green, blue);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private float normalizeHue(float hue) {
        if (Float.isNaN(hue))
            return 0.0f;
        float result = hue % 1.0f;
        if (result < 0.0f)
            result += 1.0f;
        if (result == 0.0f && hue > 0.0f) {
            return Math.nextDown(1.0f);
        }
        if (result >= 1.0f) {
            return Math.nextDown(1.0f);
        }
        return result;
    }

    // HSB conversion methods
    private static float[] rgbToHsb(int r, int g, int b) {
        float rf = r / 255.0f;
        float gf = g / 255.0f;
        float bf = b / 255.0f;

        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;

        float hue = 0.0f;
        float saturation = max == 0.0f ? 0.0f : delta / max;
        float brightness = max;

        if (delta != 0.0f) {
            if (max == rf) {
                hue = (gf - bf) / delta + (gf < bf ? 6.0f : 0.0f);
            } else if (max == gf) {
                hue = (bf - rf) / delta + 2.0f;
            } else {
                hue = (rf - gf) / delta + 4.0f;
            }
            hue /= 6.0f;
        }

        return new float[] { hue, saturation, brightness };
    }

    private static int hsbToRgb(float hue, float saturation, float brightness) {
        int r = 0, g = 0, b = 0;
        if (saturation == 0.0f) {
            r = g = b = (int) (brightness * 255.0f + 0.5f);
        } else {
            // Clamp hue to [0.0, 1.0) to prevent wrapping at 1.0
            float clampedHue = hue >= 1.0f ? 0.9999f : (hue < 0.0f ? 0.0f : hue);
            float h = (clampedHue - (float) Math.floor(clampedHue)) * 6.0f;
            float f = h - (float) Math.floor(h);
            float p = brightness * (1.0f - saturation);
            float q = brightness * (1.0f - saturation * f);
            float t = brightness * (1.0f - (saturation * (1.0f - f)));
            switch ((int) h) {
                case 0:
                    r = (int) (brightness * 255.0f + 0.5f);
                    g = (int) (t * 255.0f + 0.5f);
                    b = (int) (p * 255.0f + 0.5f);
                    break;
                case 1:
                    r = (int) (q * 255.0f + 0.5f);
                    g = (int) (brightness * 255.0f + 0.5f);
                    b = (int) (p * 255.0f + 0.5f);
                    break;
                case 2:
                    r = (int) (p * 255.0f + 0.5f);
                    g = (int) (brightness * 255.0f + 0.5f);
                    b = (int) (t * 255.0f + 0.5f);
                    break;
                case 3:
                    r = (int) (p * 255.0f + 0.5f);
                    g = (int) (q * 255.0f + 0.5f);
                    b = (int) (brightness * 255.0f + 0.5f);
                    break;
                case 4:
                    r = (int) (t * 255.0f + 0.5f);
                    g = (int) (p * 255.0f + 0.5f);
                    b = (int) (brightness * 255.0f + 0.5f);
                    break;
                case 5:
                    r = (int) (brightness * 255.0f + 0.5f);
                    g = (int) (p * 255.0f + 0.5f);
                    b = (int) (q * 255.0f + 0.5f);
                    break;
            }
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1174,0x70c1,0xaaf9}, new char[]{0x1117,0xaa95,0x70b3,0xd844}).plainString();
    }
}
