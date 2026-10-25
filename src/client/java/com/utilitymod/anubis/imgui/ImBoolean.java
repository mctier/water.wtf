package com.utilitymod.anubis.imgui;

/** Stand-in for imgui's ImBoolean value holder (no imgui in this client). */
public final class ImBoolean {
    private boolean value;

    public ImBoolean(boolean value) { this.value = value; }

    public boolean get() { return value; }

    public void set(boolean value) { this.value = value; }
}
