package dev.koifih.client.render;

public record Rect(float minX, float minY, float maxX, float maxY) {
    public static Rect of(float x0, float y0, float x1, float y1) {
        return new Rect(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1));
    }

    public float width() {
        return maxX - minX;
    }

    public float height() {
        return maxY - minY;
    }

    public Rect expand(float amount) {
        return new Rect(minX - amount, minY - amount, maxX + amount, maxY + amount);
    }
}
