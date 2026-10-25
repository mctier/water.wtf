package dev.koifih.client.render;

import dev.koifih.client.util.Colors;

public record Style(int fill, int stroke, float strokeWidth, int outline, float outlineWidth, Edges edges) {
    public static final Style EMPTY = new Style(0, 0, 0f, 0, 0f, Edges.FULL);

    public static Style fill(int argb) {
        return new Style(argb, 0, 0f, 0, 0f, Edges.FULL);
    }

    public static Style stroke(int argb, float width) {
        return new Style(0, argb, width, 0, 0f, Edges.FULL);
    }

    public Style withFill(int argb) {
        return new Style(argb, stroke, strokeWidth, outline, outlineWidth, edges);
    }

    public Style withOutline(int argb, float width) {
        return new Style(fill, stroke, strokeWidth, argb, width, edges);
    }

    public Style withEdges(Edges style) {
        return new Style(fill, stroke, strokeWidth, outline, outlineWidth, style);
    }

    public Style scaled(float factor) {
        return new Style(fill, stroke, strokeWidth * factor, outline, outlineWidth * factor, edges);
    }

    public boolean hasFill() {
        return !Colors.transparent(fill);
    }

    public boolean hasStroke() {
        return !Colors.transparent(stroke) && strokeWidth > 0f;
    }

    public boolean hasOutline() {
        return hasStroke() && !Colors.transparent(outline) && outlineWidth > strokeWidth;
    }

    public float widestStroke() {
        return hasOutline() ? outlineWidth : hasStroke() ? strokeWidth : 0f;
    }
}
