package dev.koifih.client.render;
import dev.koifih.client.render.font.Font;
import dev.koifih.client.render.font.Fonts;
import dev.koifih.client.util.Colors;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** adin's Text facade on the Comfortaa MSDF atlas (see {@link Font}). */
public final class Text {
    private static final String ELLIPSIS = LT.L1;
    private static final int WRAP_CACHE = 64;
    private static final Map<Wrap, List<String>> WRAPPED = new LinkedHashMap<>(WRAP_CACHE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Wrap, List<String>> eldest) {
            return size() > WRAP_CACHE;
        }
    };

    private record Wrap(String text, float width, float size, int maxLines) {}

    public record Span(String text, int color) {}

    @FunctionalInterface
    public interface ColorAt {
        int at(float x);
    }

    private static final Font FONT = Fonts.COMFORTAA_BOLD;

    /**
     * PUA → ASCII glyph routing. Codepoints in {@code U+E100..U+E1FF} are
     * ProtectedText / HeapCloak cloak chars — mask them back to {@code U+0000..U+00FF}
     * for the glyph lookup. The mask is a local {@code int} primitive, so no plaintext
     * String materializes on the heap (Rule 2 — one codepoint at a time).
     */
    private static int glyphCodepoint(int cp) {
        return (cp >= 0xE100 && cp <= 0xE1FF) ? (cp & 0xFF) : cp;
    }

    public static float width(String text, float size) {
        float advance = 0;
        int previous = -1;
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            i += Character.charCount(codepoint);
            Font.Glyph glyph = FONT.glyph(glyphCodepoint(codepoint));
            if (glyph == null) return text.length() * 0.5f * size;
            advance += FONT.kerning(previous, glyph.unicode()) + glyph.advance();
            previous = glyph.unicode();
        }
        return advance * size;
    }

    public static float centeredBaseline(String text, float size, float centerY) {
        Font.Glyph cap = FONT.glyph('H');
        if (cap == null || cap.planeBounds() == null) return centerY + size * 0.35f;
        return centerY - (cap.planeBounds().top() + cap.planeBounds().bottom()) * size * 0.5f;
    }

    public static void draw(DrawContext graphics, String text, float x, float baseline, float size, int color) {
        drawColored(graphics, text, x, baseline, size, color, null);
    }

    public static void drawCentered(DrawContext graphics, String text, float x, float centerY,
                                    float size, int color) {
        draw(graphics, text, x, centeredBaseline(text, size, centerY), size, color);
    }

    public static void drawCentered(DrawContext graphics, String text, float x, float centerY,
                                    float size, ColorAt color) {
        drawColored(graphics, text, x, centeredBaseline(text, size, centerY), size, 0xFFFFFFFF, color);
    }

    public static void drawCenteredX(DrawContext graphics, String text, float centerX, float centerY,
                                     float size, int color) {
        drawCentered(graphics, text, centerX - width(text, size) / 2, centerY, size, color);
    }

    public static String fit(String text, float width, float size) {
        if (width(text, size) <= width) return text;
        float room = width / size - width(ELLIPSIS, 1f);
        float advance = 0;
        int previous = -1;
        int end = 0;
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            Font.Glyph glyph = FONT.glyph(glyphCodepoint(codepoint));
            if (glyph == null) return text;
            advance += FONT.kerning(previous, glyph.unicode()) + glyph.advance();
            if (advance > room) break;
            previous = glyph.unicode();
            i += Character.charCount(codepoint);
            end = i;
        }
        return text.substring(0, end) + ELLIPSIS;
    }

    public static List<String> wrap(String text, float width, float size, int maxLines) {
        return WRAPPED.computeIfAbsent(new Wrap(text, width, size, maxLines),
                key -> List.copyOf(lines(key.text(), key.width(), key.size(), key.maxLines())));
    }

    private static List<String> lines(String text, float width, float size, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (line.isEmpty() || width(candidate, size) <= width) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            lines.add(line.toString());
            line.setLength(0);
            line.append(word);
            if (lines.size() == maxLines - 1) break;
        }
        if (lines.size() == maxLines - 1) {
            String rest = text.substring(Math.min(text.length(), String.join(" ", lines).length() + 1));
            lines.add(fit(rest, width, size));
        } else if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    public static void drawFaded(DrawContext graphics, String text, float x, float baseline, float size,
                                 int color, float transparentX, float opaqueX) {
        float fadeWidth = opaqueX - transparentX;
        if (fadeWidth == 0f) fadeWidth = 0.001f;
        float w = fadeWidth;
        drawColored(graphics, text, x, baseline, size, color,
                glyphX -> Colors.withAlpha(color, Math.clamp((glyphX - transparentX) / w, 0f, 1f)));
    }

    public static void draw(DrawContext graphics, List<Span> spans, float x, float baseline, float size) {
        if (!Float.isFinite(size) || size <= 0) return;
        float cursor = x;
        int previous = -1;
        for (Span span : spans) {
            cursor = append(graphics, span.text(), span.color(), cursor, previous, baseline, size, null);
            if (!span.text().isEmpty()) {
                Font.Glyph last = FONT.glyph(glyphCodepoint(span.text().codePointBefore(span.text().length())));
                if (last != null) previous = last.unicode();
            }
        }
    }

    private static void drawColored(DrawContext graphics, String text, float x, float baseline, float size,
                                    int color, ColorAt colorAt) {
        if (!Float.isFinite(size) || size <= 0) return;
        append(graphics, text, color, x, -1, baseline, size, colorAt);
    }

    private static float append(DrawContext graphics, String text, int spanColor, float cursor, int previous,
                                float baseline, float size, ColorAt colorAt) {
        int color = Opacity.apply(spanColor);
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            i += Character.charCount(codepoint);
            Font.Glyph glyph = FONT.glyph(glyphCodepoint(codepoint));
            if (glyph == null) return cursor;
            cursor += FONT.kerning(previous, glyph.unicode()) * size;
            Font.Bounds plane = glyph.planeBounds();
            if (plane != null) {
                float left = cursor + plane.left() * size;
                float right = cursor + plane.right() * size;
                int c = colorAt == null ? color : Opacity.apply(colorAt.at(left));
                if (!Colors.transparent(c)) {
                    FONT.quad(graphics, glyph, left, baseline + plane.top() * size, right,
                            baseline + plane.bottom() * size, c);
                }
            }
            cursor += glyph.advance() * size;
            previous = glyph.unicode();
        }
        return cursor;
    }

    private Text() {}


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8b1e,0xbf66,0x2c0a}, new char[]{0x2c24,0x8b30,0xbf48,0x90f5}).plainString();
    }
}
