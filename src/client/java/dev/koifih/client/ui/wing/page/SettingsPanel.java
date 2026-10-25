package dev.koifih.client.ui.wing.page;

import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.ItemPickerSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.MultiModeSetting;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import com.utilitymod.echo.features.settings.Setting;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Scissor;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.wing.Wing;
import dev.koifih.client.ui.wing.PanelLayout;
import dev.koifih.client.ui.component.Bool;
import dev.koifih.client.ui.component.Control;
import dev.koifih.client.ui.component.Slider;
import dev.koifih.client.ui.component.popup.Dropdown;
import dev.koifih.client.ui.component.popup.Popup;
import net.minecraft.client.gui.DrawContext;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Per-module settings view inside the modules page (adin's setting overlay
 * role), bound to echo-style {@link Setting}s. Controls are registered with
 * the Wing so its normal click/drag/popup routing drives them; this class
 * only positions, clips and draws them.
 */
final class SettingsPanel {
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_STRIDE = 24;
    private static final int CONTROL_WIDTH = 120;
    private static final int TOGGLE_W = 24;
    private static final int TOGGLE_H = 12;
    private static final int FIELD_H = 16;
    private static final String[] COLOR_NAMES = {LT.L1, LT.L2, LT.L3, LT.L4, LT.L5, LT.L6, LT.L7, LT.L8, LT.L9};
    private static final int[] COLOR_RGB = {0xE53935, 0xFB8C00, 0xFDD835, 0x43A047, 0x00ACC1, 0x1E88E5, 0x8E24AA, 0xEC407A, 0xFFFFFF};

    private record Row(Setting setting, Control control, boolean wide) {}

    private final Wing gui;
    private final List<Row> rows = new ArrayList<>();
    private Feature module;
    private PanelLayout layout;
    private float scroll;
    private float scrollGoal;

    SettingsPanel(Wing gui) {
        this.gui = gui;
    }

    boolean isOpen() {
        return module != null;
    }

    Feature module() {
        return module;
    }

    void open(Feature m, PanelLayout layout) {
        close();
        this.module = m;
        this.layout = layout;
        scroll = scrollGoal = 0f;
        float s = layout.scale();
        for (Setting st : m.getSettings()) {
            Control c = create(st, s);
            if (c != null) rows.add(new Row(st, gui.add(c), !(c instanceof Bool)));
        }
    }

    void close() {
        for (Row r : rows) {
            r.control().dismiss();
            gui.remove(r.control());
        }
        rows.clear();
        module = null;
    }

    void relayout(PanelLayout layout) {
        this.layout = layout;
    }

    private Control create(Setting st, float scale) {
        var label = net.minecraft.text.Text.literal(st.getName());
        if (st instanceof BoolSetting b) {
            return new Bool(0, 0, TOGGLE_W, TOGGLE_H, scale, label, b::getValue, b::setValue);
        }
        if (st instanceof FloatSetting f) {
            float inc = f.getIncrement() > 0 ? f.getIncrement() : 0.01f;
            Slider sl = new Slider(0, 0, CONTROL_WIDTH, FIELD_H, scale, label,
                    Math.round(f.getMinValue() / inc), Math.round(f.getMaxValue() / inc),
                    () -> Math.round(f.getValue() / inc), v -> f.setValue(v * inc));
            sl.setFormat(v -> fmt(v * inc, inc));
            return sl;
        }
        if (st instanceof IntSetting i) {
            return new Slider(0, 0, CONTROL_WIDTH, FIELD_H, scale, label, i.getMinValue(), i.getMaxValue(),
                    i::getValue, i::setValue);
        }
        if (st instanceof RangeSetting r) {
            float inc = r.getIncrement() > 0 ? r.getIncrement() : 0.01f;
            Slider sl = Slider.range(0, 0, CONTROL_WIDTH, FIELD_H, scale, label,
                    Math.round(r.getLowerBound() / inc), () -> Math.round(r.getUpperBound() / inc),
                    () -> Math.round(r.getMinValue() / inc), v -> r.setMinValue(v * inc),
                    () -> Math.round(r.getMaxValue() / inc), v -> r.setMaxValue(v * inc));
            sl.setFormat(v -> fmt(v * inc, inc));
            return sl;
        }
        if (st instanceof ModeSetting mo) {
            List<CharSequence> modes = mo.getModes();
            String[] names = modes.stream().map(CharSequence::toString).toArray(String[]::new);
            return Dropdown.single(0, 0, CONTROL_WIDTH, FIELD_H, scale, label, names,
                    () -> java.util.Arrays.asList(names).indexOf(mo.getValue().toString()),
                    i -> mo.setValue(modes.get(i)));
        }
        if (st instanceof MultiModeSetting mm) {
            List<CharSequence> opts = List.copyOf(mm.getOptionsSequenceView().keySet());
            return Dropdown.multi(0, 0, CONTROL_WIDTH, FIELD_H, scale, label,
                    opts.stream().map(CharSequence::toString).toArray(String[]::new),
                    () -> new LiveSelection(mm, opts));
        }
        if (st instanceof ColorSetting c) {
            return Dropdown.single(0, 0, CONTROL_WIDTH, FIELD_H, scale, label, COLOR_NAMES,
                    () -> nearestColor(c.getARGB()),
                    i -> c.setFromARGB((c.getARGB() & 0xFF000000) | COLOR_RGB[i]));
        }
        if (st instanceof com.utilitymod.echo.features.settings.impl.KeybindSetting k) {
            // echo bind code: GLFW key, 0x80000000 | mouse button, or -1 = unbound
            dev.koifih.client.ui.component.Keybind kb = new dev.koifih.client.ui.component.Keybind(0, 0, 56, FIELD_H, scale, label,
                    () -> {
                        int code = k.getKey();
                        if (code == -1 || code == 0) return net.minecraft.client.util.InputUtil.UNKNOWN_KEY;
                        if ((code & 0x80000000) != 0) return net.minecraft.client.util.InputUtil.Type.MOUSE.createFromCode(code & 0xFF);
                        return net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(code);
                    },
                    key -> {
                        if (key == null || key.equals(net.minecraft.client.util.InputUtil.UNKNOWN_KEY)) k.setKey(-1);
                        else if (key.getCategory() == net.minecraft.client.util.InputUtil.Type.MOUSE) k.setKey(0x80000000 | key.getCode());
                        else k.setKey(key.getCode());
                    });
            kb.setClearable(true);
            return kb;
        }
        if (st instanceof ItemPickerSetting) {
            return null; // Item whitelist editor not ported yet: empty = any item.
        }
        return null;
    }

    private static String fmt(float v, float inc) {
        if (inc >= 1f) return Integer.toString(Math.round(v));
        int decimals = inc >= 0.1f ? 1 : 2;
        return String.format(Locale.ROOT, LT.L10 + decimals + LT.L11, v);
    }

    private static int nearestColor(int argb) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < COLOR_RGB.length; i++) {
            int a = argb & 0xFFFFFF, b = COLOR_RGB[i];
            long dr = ((a >> 16) & 255) - ((b >> 16) & 255), dg = ((a >> 8) & 255) - ((b >> 8) & 255), db = (a & 255) - (b & 255);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Write-through Set view of a MultiModeSetting for Dropdown.multi. */
    private static final class LiveSelection extends AbstractSet<Integer> {
        private final MultiModeSetting setting;
        private final List<CharSequence> options;

        LiveSelection(MultiModeSetting setting, List<CharSequence> options) {
            this.setting = setting;
            this.options = options;
        }

        @Override
        public Iterator<Integer> iterator() {
            List<Integer> on = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) if (setting.isEnabled(options.get(i))) on.add(i);
            return on.iterator();
        }

        @Override public int size() { int n = 0; for (CharSequence o : options) if (setting.isEnabled(o)) n++; return n; }
        @Override public boolean contains(Object o) { return o instanceof Integer i && i >= 0 && i < options.size() && setting.isEnabled(options.get(i)); }
        @Override public boolean add(Integer i) { boolean was = contains(i); setting.setOption(options.get(i), true); return !was; }
        @Override public boolean remove(Object o) { boolean was = contains(o); if (was) setting.setOption(options.get((Integer) o), false); return was; }
    }

    // ---- layout / draw -------------------------------------------------------------

    private int backSize() {
        return 16;
    }

    boolean headerBackHit(double mx, double my, int x, int y) {
        return mx >= x && mx < x + backSize() + 4 + Text.width(module.getName(), 10f) && my >= y && my < y + 20;
    }

    private List<Row> visibleRows() {
        List<Row> out = new ArrayList<>();
        for (Row r : rows) if (r.setting().isVisible()) out.add(r);
        return out;
    }

    float maxScroll(int top, int bottom) {
        int contentH = bottom - top;
        return Math.max(0f, visibleRows().size() * ROW_STRIDE - (ROW_STRIDE - ROW_HEIGHT) - contentH);
    }

    void scrollBy(double dy, int top, int bottom) {
        scrollGoal = Math.max(0f, Math.min(maxScroll(top, bottom), scrollGoal + (dy < 0 ? ROW_STRIDE * 2 : -ROW_STRIDE * 2)));
    }

    void draw(DrawContext g, int headerX, int headerY, int x, int top, int w, int bottom, int mouseX, int mouseY, float delta) {
        if (module == null) return;
        scroll += (scrollGoal - scroll) * 0.35f;
        if (Math.abs(scrollGoal - scroll) < 0.5f) scroll = scrollGoal;

        // Header: back chevron + module name (+ echo suffix).
        Draw.icon(g, 0xe5c4, headerX - 2, headerY + 2, backSize(), Theme.TEXT);
        Text.drawCentered(g, module.getName(), headerX + backSize() + 2, headerY + 10, 10f, Theme.TEXT);
        String suffix = module.concat().toString();
        if (!suffix.isEmpty()) {
            Text.drawCentered(g, suffix, headerX + backSize() + 8 + Text.width(module.getName(), 10f),
                    headerY + 10, 8f, Theme.MUTED);
        }

        List<Row> vis = visibleRows();
        for (Row r : rows) r.control().visible = false;
        List<Popup> openPopups = new ArrayList<>();
        Scissor.clip(g, x, top, w, Math.max(0, bottom - top), () -> {
            for (int i = 0; i < vis.size(); i++) {
                Row r = vis.get(i);
                int ry = top - Math.round(scroll) + i * ROW_STRIDE;
                Control c = r.control();
                boolean inView = ry + ROW_HEIGHT > top && ry < bottom;
                c.visible = inView;
                c.active = inView;
                if (!inView) continue;
                if (c instanceof Popup p) {
                    // adin row dropdown: full-width row that draws its own label, value and chevron.
                    c.setX(x);
                    c.setY(ry);
                    c.setWidth(w);
                    c.setHeight(ROW_HEIGHT);
                    p.setBottomLimit(bottom);
                } else {
                    Draw.rect(g, x, ry, w, ROW_HEIGHT, 5, Theme.ROW);
                    Text.drawCentered(g, r.setting().getName(), x + 10, ry + ROW_HEIGHT * 0.5f, 7.5f, Theme.TEXT);
                    int cw = r.wide() ? Math.min(CONTROL_WIDTH, w / 2) : TOGGLE_W;
                    int ch = r.wide() ? FIELD_H : TOGGLE_H;
                    c.setWidth(cw);
                    c.setHeight(ch);
                    c.setX(x + w - cw - 8);
                    c.setY(ry + (ROW_HEIGHT - ch) / 2);
                }
                c.draw(g, mouseX, mouseY, delta);
                if (c instanceof Popup p) openPopups.add(p);
            }
        });
        // Open dropdown lists draw last so they sit above the rows.
        // Option lists render outside the row clip, above everything (as SettingsMenu does).
        for (Popup p : openPopups) p.renderPopup(g, mouseX, mouseY);
    }

    List<Popup> popups() {
        List<Popup> out = new ArrayList<>();
        for (Row r : rows) if (r.control() instanceof Popup p) out.add(p);
        return out;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8fce,0xd1ae,0x63d0}, new char[]{0x6382,0xd1cb,0x8faa,0xf7e9}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd369,0xb078,0x67a9,0x538c,0x7951,0x2457}, new char[]{0xb037,0x53fe,0x67c8,0x793f,0x2430,0xd30c}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x988c,0x2f13,0xa0eb,0xae64,0xaad6,0xc007}, new char[]{0xaa8f,0xa08e,0xc06b,0x98e0,0x2f7c,0xae13}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1970,0x6797,0x69db,0x4c0c,0x0c01}, new char[]{0x67d0,0x1902,0x4c69,0x69be,0x0c6f}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4f81,0xa677,0x3aad,0x70a4}, new char[]{0xa634,0x70dd,0x4fe0,0x3ac3}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x905e,0x0d1d,0x1a1d,0x5686}, new char[]{0x901c,0x56ea,0x1a68,0x0d78}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0def,0x8593,0xf102,0x4b46,0xf3df,0x8c1a}, new char[]{0xf38f,0x0d9a,0x4b34,0xf172,0x8c76,0x85f6}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7c85,0x3b8e,0xa66f,0x59b9}, new char[]{0x59e9,0x7cec,0xa601,0x3be5}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5d57,0x733a,0xa7fd,0x4cb2,0x0cf4}, new char[]{0xa7aa,0x4cda,0x7353,0x5d23,0x0c91}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8f97,0x9087}, new char[]{0x8fb2,0x90a9,0x385d,0xd291}).plainString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6292}, new char[]{0x62f4,0xb33c,0xfabb,0x5c02}).plainString();
    }
}
