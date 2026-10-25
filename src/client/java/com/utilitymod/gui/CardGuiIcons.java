package com.utilitymod.gui;

/** Small, font-independent outline glyphs for the Cards GUI. Coordinates use a 16-unit grid. */
public final class CardGuiIcons {
    private CardGuiIcons() {}

    public static void draw(net.minecraft.client.gui.DrawContext g, String name,
                            float x, float y, float size, int color) {
        if (size <= 0 || !Float.isFinite(size)) return;
        var m = g.getMatrices();
        m.pushMatrix();
        try {
            m.translate(x, y);
            m.scale(size / 16f, size / 16f);
            switch (name) {
                case "close", "x" -> { line(g,3,3,13,13,color); line(g,13,3,3,13,color); }
                case "search" -> { ring(g, 6.5f, 6.5f, 4.5f, color); line(g,10,10,14,14,color); }
                case "bell" -> {
                    path(g,color,3,11,4,9,4,6,5,3.5f,8,2.5f,11,3.5f,12,6,12,9,13,11,3,11);
                    path(g,color,6,13,7,14,9,14,10,13); line(g,8,1,8,2.5f,color);
                }
                case "sliders", "settings", "options" -> {
                    line(g,2,4,5,4,color);line(g,8,4,14,4,color);ring(g,6.5f,4,1.5f,color);
                    line(g,2,11,9,11,color);line(g,12,11,14,11,color);ring(g,10.5f,11,1.5f,color);
                }
                case "combat" -> {
                    path(g,color,3,2,5,2,12,9,10,11,3,4,3,2);
                    line(g,9,10,13,14,color);line(g,9,13,13,9,color);
                    path(g,color,13,2,11,2,8.5f,4.5f);path(g,color,3,14,6,11,5,10,3,12);
                    line(g,3,9,7,13,color);line(g,10,6,13,3,color);
                }
                case "layers", "modules" -> {
                    path(g,color,1,5,8,1.5f,15,5,8,8.5f,1,5);
                    path(g,color,1,8.5f,8,12,15,8.5f);path(g,color,1,12,8,15.5f,15,12);
                }
                case "eye", "visual" -> {
                    path(g,color,1,8,3.5f,4.5f,8,3,12.5f,4.5f,15,8,12.5f,11.5f,8,13,3.5f,11.5f,1,8);
                    ring(g,8,8,2.5f,color);
                }
                case "folder", "configs" -> path(g,color,1.5f,4,1.5f,13,14.5f,13,14.5f,5,8,5,6.5f,3,1.5f,3,1.5f,4);
                case "monitor", "client" -> {
                    box(g,1,2,14,9,color);line(g,8,11,8,14,color);line(g,4.5f,14,11.5f,14,color);
                }
                case "palette", "themes" -> {
                    path(g,color,8,1,4,2,1.5f,5,1,9,3,12.5f,6.5f,14,9,14,10,12.5f,9,11,10,9.5f,13,9.5f,15,8,14,4,11,1.5f,8,1);
                    dot(g,5,4,color);dot(g,9,3.5f,color);dot(g,12,6,color);dot(g,4,8,color);
                }
                case "keyboard", "keybinds", "macros" -> {
                    box(g,1,3,14,10,color);line(g,4,10.5f,12,10.5f,color);
                    for(int row=0;row<2;row++)for(int col=0;col<4;col++)dot(g,3.5f+col*3,5.5f+row*2.5f,color);
                }
                case "info" -> {ring(g,8,8,6.5f,color);dot(g,8,4.5f,color);line(g,8,7,8,11.5f,color);}
                case "shield", "donut" -> path(g,color,8,1,2,3.5f,2.5f,9,4.5f,12,8,15,11.5f,12,13.5f,9,14,3.5f,8,1);
                case "compass", "finder" -> {ring(g,8,8,6.5f,color);path(g,color,5,11,7,6,11,4,9,10,5,11);}
                case "movement" -> {
                    ring(g,10,2.5f,1.5f,color);path(g,color,3,6,6,4.5f,10,6,13,7);
                    path(g,color,8,5.5f,6.5f,9,10,11,10,15);path(g,color,6.5f,9,4.5f,12,1.5f,12);
                }
                default -> {ring(g,8,8,5.5f,color);line(g,5,8,11,8,color);line(g,8,5,8,11,color);}
            }
        } finally { m.popMatrix(); }
    }

    private static void path(net.minecraft.client.gui.DrawContext g,int color,float... points) {
        for(int i=2;i<points.length;i+=2)line(g,points[i-2],points[i-1],points[i],points[i+1],color);
    }
    private static void box(net.minecraft.client.gui.DrawContext g,float x,float y,float w,float h,int color) {
        path(g,color,x,y,x+w,y,x+w,y+h,x,y+h,x,y);
    }
    private static void dot(net.minecraft.client.gui.DrawContext g,float x,float y,int color) {
        line(g,x-.45f,y,x+.45f,y,color);
    }
    private static void ring(net.minecraft.client.gui.DrawContext g,float x,float y,float r,int color) {
        for(int i=0;i<16;i++) {
            double a=i*Math.PI/8,b=(i+1)*Math.PI/8;
            line(g,x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r,x+(float)Math.cos(b)*r,y+(float)Math.sin(b)*r,color);
        }
    }
    private static void line(net.minecraft.client.gui.DrawContext g,float x1,float y1,float x2,float y2,int color) {
        float dx=x2-x1,dy=y2-y1,length=(float)Math.sqrt(dx*dx+dy*dy);
        if(length<.001f)return;
        var m=g.getMatrices();m.pushMatrix();
        try {
            m.translate(x1,y1);m.rotate((float)Math.atan2(dy,dx));m.translate(0,-.55f);
            m.scale(length,1.1f);g.fill(0,0,1,1,color);
        } finally {m.popMatrix();}
    }
}
