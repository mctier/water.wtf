package dev.koifih.client.ui.component;

import dev.koifih.client.render.AdinIcon;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class Segmented extends Control {
    public record Segment(AdinIcon icon, String label) {
        public static Segment of(String label) {
            return new Segment(null, label);
        }

        public static Segment of(AdinIcon icon) {
            return new Segment(icon, null);
        }

        public static Segment of(AdinIcon icon, String label) {
            return new Segment(icon, label);
        }
    }

    private static final float TEXT_SIZE = 7f;
    private static final float ICON_SIZE = 8f;
    private static final float ICON_GAP = 3f;
    private static final float SEGMENT_PADDING = 12f;

    private static final float STRETCH = 0.18f;

    private final Segment[] segments;
    private final IntSupplier get;
    private final IntConsumer set;
    private final Transition highlight;

    public Segmented(int x, int y, int width, int height, float scale, net.minecraft.text.Text label,
                     Segment[] segments, IntSupplier get, IntConsumer set) {
        super(x, y, width, height, scale, label);
        this.segments = segments;
        this.get = get;
        this.set = set;
        this.highlight = new Transition(get.getAsInt(), 190, Transition.Easing.EASE_OUT_EXPO);
    }

    public static int preferredWidth(Segment[] segments, float scale) {
        float widest = 0;
        for (Segment segment : segments) widest = Math.max(widest, contentWidth(segment, segment.label(), scale));
        return (int) Math.ceil(segments.length * (widest + SEGMENT_PADDING * scale));
    }

    private static float contentWidth(Segment segment, String label, float scale) {
        float width = 0;
        if (segment.icon() != null) width += ICON_SIZE * scale;
        if (segment.icon() != null && label != null) width += ICON_GAP * scale;
        if (label != null) width += Text.width(label, TEXT_SIZE * scale);
        return width;
    }

    private float segmentWidth() {
        return getWidth() / (float) segments.length;
    }

    @Override
    public void draw(DrawContext graphics, int mouseX, int mouseY, float delta) {
        int selected = get.getAsInt();
        highlight.set(selected);
        float segment = segmentWidth();
        rect(graphics, getX(), getY(), getWidth(), getHeight(), px(4), Theme.CONTROL);
        float index = Math.clamp(highlight.value(), 0f, segments.length - 1f);
        float wide = segment * (1f + STRETCH * highlight.flight());
        float slot = Math.clamp(getX() + segment * index - (wide - segment) * 0.5f, getX(), getX() + getWidth() - wide);
        rect(graphics, slot, getY(), wide, getHeight(), px(4), Theme.CONTROL_ACTIVE);
        for (int i = 0; i < segments.length; i++) {
            Segment item = segments[i];
            int color = selected == i ? Theme.TEXT : Theme.DIM;
            float iconSpan = item.icon() != null ? px(ICON_SIZE + ICON_GAP) : 0f;
            String label = item.label() == null ? null : fit(item.label(), segment - px(6) - iconSpan, px(TEXT_SIZE));
            float x = getX() + segment * i + (segment - contentWidth(item, label, scale)) / 2;
            if (item.icon() != null) {
                float center = getX() + segment * (i + 0.5f);
                int backdrop = center >= slot && center <= slot + wide ? Theme.CONTROL_ACTIVE : Theme.CONTROL;
                Draw.icon(graphics, item.icon(), x, centerY() - px(ICON_SIZE) / 2, px(ICON_SIZE),
                        Theme.DIM, Theme.ACCENT, Theme.TEXT, backdrop);
                x += iconSpan;
            }
            if (label != null) text(graphics, label, x, centerY(), px(TEXT_SIZE), color);
        }
    }

    @Override
    public void onClick(Click event, boolean doubleClick) {
        set.accept(Math.clamp((int) ((event.x() - getX()) / segmentWidth()), 0, segments.length - 1));
    }

    @Override
    public boolean keyPressed(KeyInput event) {
        if (!active || !isFocused()) return false;
        int selected = get.getAsInt();
        if ((event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT)) set.accept(Math.max(0, selected - 1));
        else if ((event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT)) set.accept(Math.min(segments.length - 1, selected + 1));
        else if (isSelection(event)) set.accept((selected + 1) % segments.length);
        else return super.keyPressed(event);
        return true;
    }

}
