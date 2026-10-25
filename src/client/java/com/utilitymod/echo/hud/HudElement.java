package com.utilitymod.echo.hud;

import net.minecraft.client.gui.DrawContext;

/**
 * One movable HUD element. Position is stored as fractions of the free screen
 * area (0..1 for the top-left corner), so layouts survive resolution and GUI
 * scale changes; {@link #scale} multiplies its size. Elements draw at a local
 * origin (0,0) with size {@link #width()} x {@link #height()}; HudManager
 * applies position and scale and puts the background (theme panel or liquid
 * glass) behind them.
 */
public abstract class HudElement {
    public final String id;
    public final String name;
    public float x, y;
    public float scale = 1f;
    public boolean visible = true;
    private final float defX, defY;

    protected HudElement(String id, String name, float defX, float defY) {
        this.id = id;
        this.name = name;
        this.defX = this.x = defX;
        this.defY = this.y = defY;
    }

    public void reset() {
        x = defX;
        y = defY;
        scale = 1f;
        visible = true;
    }

    /** Unscaled size in GUI px (may change per frame, e.g. the module list). */
    public abstract float width();

    public abstract float height();

    /** Whether the element has something to show right now (outside the editor). */
    public boolean active() {
        return true;
    }

    /** 0..1 opacity of the whole element (for fade animations). */
    public float alpha() {
        return 1f;
    }

    /** Corner radius of its background panel. */
    public float radius() {
        return 6f;
    }

    /** Whether HudManager should draw a background panel (theme or glass) behind it. */
    public boolean wantsBackground() {
        return true;
    }

    /** Draws contents at (0,0). {@code preview} = inside the HUD editor (show sample data). */
    public abstract void render(DrawContext ctx, boolean preview);

    /** True to always draw this element's background as liquid glass. */
    public boolean forceGlass() {
        return false;
    }
}
