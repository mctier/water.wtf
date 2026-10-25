package com.utilitymod.echo.features;

/** echo features.FeatureInfo. */
public record FeatureInfo(CharSequence name, CharSequence description, Category category, int key, boolean visible) {
    public FeatureInfo {
        category = category != null ? category : Category.INTERNALS;
    }

    public FeatureInfo(CharSequence name, CharSequence description, Category category, int key) {
        this(name, description, category, key, true);
    }

    public FeatureInfo(CharSequence name, CharSequence description, Category category, boolean visible) {
        this(name, description, category, -1, visible);
    }

    public FeatureInfo(CharSequence name, CharSequence description, Category category) {
        this(name, description, category, -1, true);
    }
}
