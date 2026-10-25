package dev.koifih.client.ui.component.popup;

import dev.koifih.client.render.AdinIcon;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Opacity;
import dev.koifih.client.render.Scissor;
import dev.koifih.client.render.Text;
import dev.koifih.client.render.Transform;
import dev.koifih.client.ui.Theme;
import net.minecraft.client.gui.DrawContext;


public abstract class RowPopup extends Popup {
    private static final int CHEVRON_ICON = 0xe5cf;
    private static final int CHECK_ICON = 0xe5ca;

    private AdinIcon icon;

    protected RowPopup(int x, int y, int width, int height, float scale, net.minecraft.text.Text label) {
        super(x, y, width, height, scale, label);
    }

    public void setIcon(AdinIcon icon) {
        this.icon = icon;
    }

    protected AdinIcon icon() {
        return icon;
    }

    protected abstract void drawRowValue(DrawContext graphics, float rightLimit, float available);

    protected void drawContents(DrawContext graphics, float x, float top, float width, float height) {
    }

    protected static void drawCheck(DrawContext graphics, float left, float center, float iconSize, float checked) {
        if (checked > 0f) {
            float size = iconSize * (0.6f + 0.4f * checked);
            Opacity.with(checked, () -> Draw.icon(graphics, CHECK_ICON, left + (iconSize - size) / 2,
                    center - size / 2, size, Theme.ACCENT));
        }
    }

    protected boolean opensBelow() {
        return getBottom() + popupHeight() <= bottomLimit();
    }

    @Override
    protected float popupY() {
        return opensBelow() ? getBottom() : getY() - popupHeight();
    }

    /** Card GUI look: no row fill, label on the left, value in a compact dark box on the right. */
    public boolean cardStyle;

    private void drawCardRow(DrawContext graphics, float shown) {
        text(graphics, getMessage().getString(), getX() + px(6), centerY(), px(8), Theme.TEXT);
        float boxW = px(70), boxH = Math.min(getHeight() - px(4), px(18));
        float bx = getRight() - px(6) - boxW, by = centerY() - boxH / 2;
        rect(graphics, bx, by, boxW, boxH, px(4), 0x80140612);
        com.utilitymod.gui.Rounded.outline(graphics, Math.round(bx), Math.round(by), Math.round(boxW), Math.round(boxH), (float) px(4), Math.max(.5f, px(.5f)), 0x14FFFFFF);
        float iconSize = px(7), iconCenterX = bx + boxW - px(9);
        Transform.rotatedAbout(graphics, (float) Math.PI * shown, iconCenterX, centerY(), () ->
                Draw.icon(graphics, CHEVRON_ICON, iconCenterX - iconSize / 2, centerY() - iconSize / 2, iconSize, Theme.DIM));
        drawRowValue(graphics, bx + boxW - px(17), boxW - px(24));
    }

    private void drawRow(DrawContext graphics, float shown) {
        if (cardStyle) { drawCardRow(graphics, shown); return; }
        float labelX = getX() + px(10);
        AdinIcon rowIcon = icon();
        if (rowIcon != null) {
            float size = px(9);
            Draw.icon(graphics, rowIcon, getX() + px(8), centerY() - size / 2, size, Theme.DIM, Theme.ACCENT, Theme.TEXT, Theme.ROW);
            labelX += px(13);
        }
        String label = getMessage().getString();
        text(graphics, label, labelX, centerY(), px(7), Theme.TEXT);
        float labelEnd = labelX + Text.width(label, px(7));
        float iconSize = px(9);
        float iconCenterX = getRight() - px(8) - iconSize / 2;
        Transform.rotatedAbout(graphics, (float) Math.PI * shown, iconCenterX, centerY(), () ->
                Draw.icon(graphics, CHEVRON_ICON, iconCenterX - iconSize / 2, centerY() - iconSize / 2, iconSize, Theme.DIM));
        rect(graphics, getRight() - px(24), centerY() - px(5), Math.max(0.5f, scale), px(10), 0, Theme.CONTROL);
        float valueRight = getRight() - px(30);
        drawRowValue(graphics, valueRight, Math.max(px(12), valueRight - labelEnd - px(4)));
    }

    @Override
    protected void drawValue(DrawContext graphics) {
        if (!cardStyle) rect(graphics, getX(), getY(), getWidth(), getHeight(), px(5), Theme.ROW);
        drawRow(graphics, revealed());
    }

    @Override
    protected void drawPopup(DrawContext graphics, float x, float y, float width, float height,
                             float shown, int mouseX, int mouseY) {
        float listHeight = height * shown;
        boolean below = opensBelow();
        float listTop = below ? getBottom() : getY() - listHeight;
        float boxTop = below ? getY() : listTop;
        float boxBottom = below ? getBottom() + listHeight : getBottom();
        rect(graphics, x, boxTop, width, boxBottom - boxTop, px(5), Theme.ROW);
        drawRow(graphics, shown);
        Scissor.clip(graphics, Math.round(x), (int) Math.floor(listTop), Math.round(width), (int) Math.ceil(listHeight),
                () -> Opacity.with(shown, () -> drawContents(graphics, x, listTop, width, height)));
    }
}
