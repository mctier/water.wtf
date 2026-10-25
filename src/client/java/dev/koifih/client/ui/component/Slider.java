package dev.koifih.client.ui.component;

import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

public final class Slider extends Control {
    private final int min;
    private final IntSupplier max;
    private final IntSupplier[] gets;
    private final IntConsumer[] sets;
    private final Transition[] positions;
    private IntFunction<String> format = Integer::toString;
    private float valueColumn;
    private int knob;
    private boolean dragging;

    public Slider(int x, int y, int width, int height, float scale, net.minecraft.text.Text label, int min, int max,
                  IntSupplier get, IntConsumer set) {
        this(x, y, width, height, scale, label, min, () -> max, new IntSupplier[] {get}, new IntConsumer[] {set});
    }

    private Slider(int x, int y, int width, int height, float scale, net.minecraft.text.Text label, int min, IntSupplier max,
                   IntSupplier[] gets, IntConsumer[] sets) {
        super(x, y, width, height, scale, label);
        this.min = min;
        this.max = max;
        this.gets = gets;
        this.sets = sets;
        this.positions = new Transition[gets.length];
        for (int i = 0; i < gets.length; i++) positions[i] = new Transition(gets[i].getAsInt(), 120);
    }

    public static Slider range(int x, int y, int width, int height, float scale, net.minecraft.text.Text label, int min, IntSupplier max,
                               IntSupplier lowGet, IntConsumer lowSet, IntSupplier highGet, IntConsumer highSet) {
        return new Slider(x, y, width, height, scale, label, min, max,
                new IntSupplier[] {lowGet, highGet}, new IntConsumer[] {lowSet, highSet});
    }

    private int max() {
        return max.getAsInt();
    }

    private boolean ranged() {
        return gets.length == 2;
    }

    private int value(int index) {
        return gets[index].getAsInt();
    }

    private String valueText(String separator) {
        return ranged() ? format.apply(value(0)) + separator + format.apply(value(1)) : format.apply(value(0));
    }

    public float naturalValueWidth() {
        float size = px(7);
        float widest = ranged()
                ? Text.width(format.apply(max()) + LT.L1 + format.apply(max()), size)
                : Math.max(Text.width(format.apply(min), size), Text.width(format.apply(max()), size));
        return Math.max(px(18), widest + px(8));
    }

    /** Card GUI look: full-width track, accent fill, square white thumb; value is drawn by the sheet. */
    public boolean cardStyle;

    public String shownValue() { return valueText(LT.L1); }

    private float valueWidth() {
        if (cardStyle) return 0f;
        return valueColumn > 0 ? valueColumn : naturalValueWidth();
    }

    private float knobWidth() {
        return px(10);
    }

    private float travel() {
        return Math.max(1f, getWidth() - valueWidth() - knobWidth());
    }

    private float knobX(float value) {
        return getX() + travel() * (value - min) / (max() - min);
    }

    @Override
    public void draw(DrawContext graphics, int mouseX, int mouseY, float delta) {
        for (int i = 0; i < positions.length; i++) {
            if (dragging && knob == i) positions[i].snap(value(i));
            else positions[i].set(value(i));
        }
        if (cardStyle) { drawCard(graphics); return; }
        float trackHeight = px(3);
        float trackY = centerY() - trackHeight / 2;
        float knobHeight = px(6);
        float fillFrom = ranged() ? knobX(positions[0].value()) + knobWidth() / 2 : getX();
        float fillTo = knobX(positions[positions.length - 1].value()) + knobWidth() / 2;
        rect(graphics, getX(), trackY, getWidth() - valueWidth(), trackHeight, trackHeight / 2, Theme.CONTROL);
        rect(graphics, fillFrom, trackY, fillTo - fillFrom, trackHeight, trackHeight / 2, Theme.ACCENT);
        for (Transition position : positions) {
            rect(graphics, knobX(position.value()), centerY() - knobHeight / 2, knobWidth(), knobHeight, px(2), Theme.ACCENT);
        }
        text(graphics, valueText(LT.L1), getRight() - valueWidth() + px(6), centerY(), px(7), Theme.TEXT);
    }

    private void drawCard(DrawContext graphics) {
        int accent = com.utilitymod.gui.CardGuiStyle.accent;
        float th = px(4), ty = centerY() - th / 2;
        float fillFrom = ranged() ? knobX(positions[0].value()) + knobWidth() / 2 : getX();
        float fillTo = knobX(positions[positions.length - 1].value()) + knobWidth() / 2;
        int end = com.utilitymod.gui.CardGuiStyle.background;
        rect(graphics, getX(), ty, getWidth(), th, th / 2, 0x33FFFFFF);
        // gradient fill (accent -> theme second colour), drawn as strips inside the filled span
        float span = Math.max(th, fillTo - fillFrom);
        int strips = Math.max(4, Math.round(span / 2));
        rect(graphics, fillFrom, ty, Math.min(th, span), th, th / 2, accent);
        for (int i = 0; i < strips; i++) {
            float x0 = fillFrom + th / 2 + (span - th) * i / strips, x1 = fillFrom + th / 2 + (span - th) * (i + 1) / strips;
            float t = (x0 - getX()) / Math.max(1, getWidth());
            graphics.fill(Math.round(x0), Math.round(ty), Math.round(x1), Math.round(ty + th), dev.koifih.client.util.Colors.lerp(accent, end, t));
        }
        for (Transition position : positions) {
            float kx = knobX(position.value()) + knobWidth() / 2;
            int d = Math.max(4, Math.round(px(10)));
            int ix = Math.round(kx - d / 2f), iy = Math.round(centerY() - d / 2f);
            com.utilitymod.gui.Rounded.fill(graphics, ix, iy + 1, d, d, d / 2f, 0x40000000);
            com.utilitymod.gui.Rounded.fill(graphics, ix, iy, d, d, d / 2f, 0xFFFFFFFF);
        }
    }

    private int valueAt(double mouseX) {
        float fraction = (float) ((mouseX - getX() - knobWidth() / 2) / travel());
        return Math.clamp(Math.round(min + fraction * (max() - min)), min, max());
    }

    private void move(int value) {
        value = Math.clamp(value, min, max());
        if (!ranged()) {
            sets[0].accept(value);
            return;
        }
        int low = value(0);
        int high = value(1);
        if (knob == 1 && value < low) {
            sets[0].accept(value);
            sets[1].accept(low);
            knob = 0;
        } else if (knob == 0 && value > high) {
            sets[1].accept(value);
            sets[0].accept(high);
            knob = 1;
        } else {
            sets[knob].accept(value);
        }
    }

    @Override
    public void onClick(Click event, boolean doubleClick) {
        int value = valueAt(event.x());
        if (ranged()) knob = value > value(1) || Math.abs(value - value(1)) < Math.abs(value - value(0)) ? 1 : 0;
        dragging = true;
        move(value);
    }

    @Override
    public void onDrag(Click event, double dx, double dy) {
        if (dragging) move(valueAt(event.x()));
    }

    @Override
    public void onRelease(Click event) {
        dragging = false;
    }

    @Override
    public boolean keyPressed(KeyInput event) {
        if (!active || !isFocused()) return false;
        if (ranged() && ((event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) || (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN))) {
            knob = 1 - knob;
            return true;
        }
        if ((event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) || (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT)) {
            int step = ((event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) ? 1 : -1) * (((event.modifiers() & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) ? 10 : 1);
            move(value(knob) + step);
            return true;
        }
        return super.keyPressed(event);
    }


    public void setFormat(IntFunction<String> format) { this.format = format; }

    public void setValueColumn(float valueColumn) { this.valueColumn = valueColumn; }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf3c9}, new char[]{0xf3e4,0x9979,0x271a,0x196b}).plainString();
    }
}
