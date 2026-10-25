package dev.koifih.client.ui.component.popup;

import dev.koifih.client.render.AdinIcon;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Text;
import dev.koifih.client.render.Transform;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.util.Colors;
import dev.koifih.client.util.Lang;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;

import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class Dropdown extends RowPopup {
    private static final float OPTION_HEIGHT = 15f;
    private static final float ART_OPTION_HEIGHT = 19f;
    private static final float IMAGE_ASPECT = 12f;
    private static final float IMAGE_SPAN = 0.72f;
    private static final float IMAGE_FADE = 0.55f;
    private static final float OPTION_MIN_ZOOM = 0.9f;

    private final String[] options;
    private final IntPredicate chosen;
    private final IntConsumer choose;
    private final boolean multi;
    private final Transition[] checks;
    private final Transition[] reveals;
    private IntPredicate optionVisible = option -> true;
    private List<AdinIcon> icons = List.of();
    private List<Identifier> images = List.of();
    private boolean synced;
    private int highlighted;
    /** Inner smooth scroll (content space, px): eases toward listScrollGoal each frame. */
    private float listScroll, listScrollGoal;

    private Dropdown(int x, int y, int width, int height, float scale, net.minecraft.text.Text label, String[] options,
                     IntPredicate chosen, IntConsumer choose, boolean multi) {
        super(x, y, width, height, scale, label);
        this.options = options;
        this.chosen = chosen;
        this.choose = choose;
        this.multi = multi;
        this.checks = new Transition[options.length];
        this.reveals = new Transition[options.length];
        for (int i = 0; i < options.length; i++) {
            checks[i] = new Transition(0f, 120);
            reveals[i] = new Transition(1f, 150);
        }
    }

    public static Dropdown single(int x, int y, int width, int height, float scale, net.minecraft.text.Text label,
                                  String[] options, IntSupplier get, IntConsumer set) {
        return new Dropdown(x, y, width, height, scale, label, options, option -> get.getAsInt() == option, set, false);
    }

    public static Dropdown multi(int x, int y, int width, int height, float scale, net.minecraft.text.Text label,
                                 String[] options, Supplier<Set<Integer>> selected) {
        return new Dropdown(x, y, width, height, scale, label, options, option -> selected.get().contains(option), option -> {
            Set<Integer> set = selected.get();
            if (!set.remove(option)) set.add(option);
        }, true);
    }

    public void setOptionVisible(IntPredicate visible) {
        optionVisible = visible;
    }

    public void setArt(List<AdinIcon> icons, List<Identifier> images) {
        this.icons = icons;
        this.images = images;
    }

    private boolean hasArt() {
        return !icons.isEmpty();
    }

    private float optionHeight() {
        return hasArt() ? ART_OPTION_HEIGHT : OPTION_HEIGHT;
    }

    @Override
    protected AdinIcon icon() {
        if (multi || !hasArt()) return super.icon();
        for (int i = 0; i < options.length; i++) if (chosen.test(i)) return icons.get(i);
        return super.icon();
    }

    private List<Integer> visibleOptions() {
        List<Integer> visible = new ArrayList<>();
        for (int i = 0; i < options.length; i++) if (optionVisible.test(i)) visible.add(i);
        return visible;
    }

    private String value() {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < options.length; i++) {
            if (!chosen.test(i) || !optionVisible.test(i)) continue;
            if (!names.isEmpty()) names.append(LT.L1);
            names.append(options[i]);
        }
        return names.isEmpty() ? Lang.get(LT.L2) : names.toString();
    }

    private void select(int option) {
        choose.accept(option);
        if (!multi) dismiss();
    }

    @Override
    protected void onOpen() {
        highlighted = 0;
        listScroll = listScrollGoal = 0f;
        if (!multi) for (int i = 0; i < options.length; i++) if (chosen.test(i)) highlighted = i;
    }

    private void syncReveals() {
        for (int i = 0; i < options.length; i++) {
            float target = optionVisible.test(i) ? 1f : 0f;
            if (synced) reveals[i].set(target);
            else reveals[i].snap(target);
        }
    }

    private float rowSpan() {
        float span = 0f;
        for (Transition reveal : reveals) span += reveal.value();
        return span;
    }

    @Override
    protected float popupHeight() {
        // Clamp full-height lists to the space below/above the anchor so overflow
        // scrolls inside the popup instead of spilling past bottomLimit. Opens-below
        // is recomputed against the NATURAL height to avoid ping-ponging with
        // RowPopup.opensBelow (which itself consults popupHeight()).
        float natural = px(4 + rowSpan() * optionHeight());
        boolean below = getBottom() + natural <= bottomLimit();
        float avail = below ? Math.max(0f, bottomLimit() - getBottom()) : Math.max(0f, getY());
        return Math.max(px(optionHeight() * 2), Math.min(natural, avail));
    }

    /** Content rows that live outside the clamped popup box. */
    private float maxListScroll() {
        float content = rowSpan() * optionHeight() + px(2);
        return Math.max(0f, content - (popupHeight() - px(4)));
    }

    private void easeListScroll() {
        listScrollGoal = Math.max(0f, Math.min(maxListScroll(), listScrollGoal));
        listScroll += (listScrollGoal - listScroll) * 0.35f;
        if (Math.abs(listScrollGoal - listScroll) < 0.5f) listScroll = listScrollGoal;
    }

    /** Accumulated content-space offset of an option (respecting reveal spans). */
    private float contentPos(int option) {
        float pos = 0f;
        for (int i = 0; i < option && i < options.length; i++) pos += reveals[i].value();
        return pos;
    }

    @Override
    protected boolean popupScrolled(double x, double y, double dy) {
        if (maxListScroll() <= 0f) return false;
        listScrollGoal += dy < 0 ? px(optionHeight()) : -px(optionHeight());
        listScrollGoal = Math.max(0f, Math.min(maxListScroll(), listScrollGoal));
        return true;
    }

    private int optionAt(double y) {
        float scrolled = (float) ((y - popupY() - px(2) + listScroll) / px(optionHeight()));
        if (scrolled < 0f) return -1;
        for (int i = 0; i < options.length; i++) {
            float span = reveals[i].value();
            if (scrolled < span) return span >= 1f && optionVisible.test(i) ? i : -1;
            scrolled -= span;
        }
        return -1;
    }

    @Override
    protected void drawRowValue(DrawContext graphics, float rightLimit, float available) {
        float size = px(7);
        // The fade shader is not active in the late-injected renderer. A
        // measured truncation keeps a long value inside its value column.
        if (available < px(18)) return;
        String label = value();
        String shown = Text.fit(label, available, size);
        float labelWidth = Math.min(available, Text.width(shown, size));
        float labelX = rightLimit - labelWidth;
        text(graphics, shown, labelX, centerY(), size, Theme.TEXT);
    }

    @Override
    protected void drawContents(DrawContext graphics, float x, float top, float width, float height) {
        syncReveals();
        easeListScroll();
        if (!synced) {
            for (int i = 0; i < options.length; i++) checks[i].snap(chosen.test(i) ? 1f : 0f);
            synced = true;
        } else if (isOpen()) {
            for (int i = 0; i < options.length; i++) checks[i].set(chosen.test(i) ? 1f : 0f);
        }
        float offset = 0f;
        for (int i = 0; i < options.length; i++) {
            float shown = reveals[i].value();
            if (shown > 0f) drawOption(graphics, i, x, top + px(2) - listScroll + px(optionHeight()) * (offset + shown * 0.5f), width, shown);
            offset += shown;
        }
    }

    private void drawOption(DrawContext graphics, int option, float x, float center, float width, float shown) {
        float iconSize = px(8);
        float checked = checks[option].value();
        Transform.popIn(graphics, x + width * 0.5f, center, shown, OPTION_MIN_ZOOM, () -> {
            if (hasArt()) {
                drawArt(graphics, option, x, center, width, checked);
                return;
            }
            drawCheck(graphics, x + px(9), center, iconSize, checked);
            text(graphics, fit(options[option], width - px(30), px(7)), x + px(22), center, px(7), Colors.lerp(Theme.DIM, Theme.TEXT, checked));
        });
    }

    private void drawArt(DrawContext graphics, int option, float x, float center, float width, float checked) {
        float imageHeight = px(ART_OPTION_HEIGHT - 5);
        float imageRight = x + width - px(4);
        float imageWidth = Math.min(imageHeight * IMAGE_ASPECT, width * IMAGE_SPAN);
        float imageX = imageRight - imageWidth;
        Draw.fadedTexture(graphics, images.get(option), imageX, center - imageHeight / 2, imageWidth, imageHeight,
                1f - imageWidth / (imageHeight * IMAGE_ASPECT), IMAGE_FADE);
        float iconSize = px(9);
        Draw.icon(graphics, icons.get(option), x + px(8), center - iconSize / 2, iconSize, Theme.DIM,
                Colors.lerp(Theme.DIM, Theme.ACCENT, checked), Colors.lerp(Theme.DIM, Theme.TEXT, checked));
        float textX = x + px(22);
        text(graphics, fit(options[option], Math.max(imageX + imageWidth * IMAGE_FADE * 0.4f, imageX) - textX, px(7)),
                textX, center, px(7), Colors.lerp(Theme.DIM, Theme.TEXT, checked));
    }

    @Override
    protected void clickPopup(double x, double y) {
        int option = optionAt(y);
        if (option < 0) return;
        highlighted = option;
        select(option);
    }

    @Override
    protected void popupKeyPressed(KeyInput event) {
        List<Integer> visible = visibleOptions();
        if (visible.isEmpty()) return;
        int row = Math.max(0, visible.indexOf(highlighted));
        if ((event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN)) highlighted = visible.get((row + 1) % visible.size());
        else if ((event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_UP)) highlighted = visible.get((row + visible.size() - 1) % visible.size());
        else if (isSelection(event) && optionVisible.test(highlighted)) select(highlighted);
        // Scroll the keyboard cursor into view (content-space coordinates).
        float oh = px(optionHeight());
        float rowTop = px(2) + contentPos(highlighted) * oh;
        if (rowTop < listScrollGoal + px(2)) listScrollGoal = Math.max(0f, rowTop - px(2));
        else if (rowTop + oh > listScrollGoal + popupHeight()) listScrollGoal = Math.max(0f, rowTop + oh - popupHeight());
    }



    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x97cf,0x7e18}, new char[]{0x7e34,0x97ef,0x2944,0x49d3}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xac87,0xaff8,0xcadb,0x2871}, new char[]{0xace9,0xcab4,0x281f,0xaf9d}).plainString();
    }
}
