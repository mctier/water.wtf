package dev.koifih.client.ui.wing;

public record PanelLayout(float scale, int x, int y, int width, int height, int sidebarWidth, int topBarHeight) {
    public static final int WIDTH = 375;
    public static final int HEIGHT = 323;
    public static final int RADIUS = 6;
    public static final int SIDEBAR_WIDTH = 78;
    public static final int TOP_BAR_HEIGHT = 20;
    public static final int PADDING = 10;
    public static final int SIDEBAR_INSET = 8;
    public static final int GAP = 6;
    public static final int SCREEN_MARGIN = 32;
    /**
     * The Minecraft GUI coordinate space can be very small at high GUI-scale
     * settings. Keep the wing comfortably inside that space instead of
     * letting its fixed desktop dimensions consume the whole screen.
     */
    public static final float MAX_VIEWPORT_FRACTION = 0.72f;

    public static PanelLayout of(int screenWidth, int screenHeight, int offsetX, int offsetY, float uiScale) {
        double widthFit = Math.max(1, screenWidth - SCREEN_MARGIN) / (double) WIDTH;
        double heightFit = Math.max(1, screenHeight - SCREEN_MARGIN) / (double) HEIGHT;
        double comfortableFit = Math.min(
                Math.max(1, screenWidth * MAX_VIEWPORT_FRACTION) / (double) WIDTH,
                Math.max(1, screenHeight * MAX_VIEWPORT_FRACTION) / (double) HEIGHT);
        double scale = Math.min(uiScale * Math.min(1.0, comfortableFit), Math.min(widthFit, heightFit));
        int width = Math.max(1, (int) Math.round(WIDTH * scale));
        int height = Math.max(1, (int) Math.round(HEIGHT * scale));
        int x = Math.clamp((screenWidth - width) / 2 + offsetX, 0, Math.max(0, screenWidth - width));
        int y = Math.clamp((screenHeight - height) / 2 + offsetY, 0, Math.max(0, screenHeight - height));
        int sidebarWidth = Math.max(1, (int) Math.round(SIDEBAR_WIDTH * scale));
        int topBarHeight = Math.max(1, (int) Math.round(TOP_BAR_HEIGHT * scale));
        return new PanelLayout((float) scale, x, y, width, height, sidebarWidth, topBarHeight);
    }

    public int scaled(float units) {
        return Math.round(units * scale);
    }

    public int atLeastOne(float units) {
        return Math.max(1, scaled(units));
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public int contentX() {
        return x + sidebarWidth;
    }

    public int contentY() {
        return y + topBarHeight;
    }

    public int contentWidth() {
        return width - sidebarWidth;
    }

    public int contentHeight() {
        return height - topBarHeight;
    }

    public int padding() {
        return atLeastOne(PADDING);
    }

    public int sidebarInset() {
        return scaled(SIDEBAR_INSET);
    }

    public int rowX() {
        return contentX() + padding();
    }

    public int rowWidth() {
        return contentWidth() - 2 * padding();
    }

    public boolean inContent(double pointX, double pointY) {
        return pointX >= contentX() && pointX < right() && pointY >= contentY() && pointY < bottom();
    }

    public boolean inTopBar(double pointX, double pointY) {
        return pointX >= x && pointX < right() && pointY >= y && pointY < y + topBarHeight;
    }
}
