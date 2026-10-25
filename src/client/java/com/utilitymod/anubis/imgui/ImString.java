package com.utilitymod.anubis.imgui;

/** Stand-in for imgui's ImString value holder (no imgui in this client). */
public final class ImString {
    private String value;

    public ImString(String value, int capacity) { this.value = value == null ? "" : value; }

    public String get() { return value; }

    public void set(String value) { this.value = value == null ? "" : value; }

    @Override public String toString() { return value; }
}
