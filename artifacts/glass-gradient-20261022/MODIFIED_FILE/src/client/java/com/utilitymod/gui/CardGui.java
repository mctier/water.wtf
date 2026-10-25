package com.utilitymod.gui;

import com.utilitymod.core.Module;
import com.utilitymod.core.ModuleManager;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.settings.Setting;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.IntSetting;
import com.utilitymod.echo.features.settings.impl.KeybindSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.MultiModeSetting;
import com.utilitymod.echo.features.settings.impl.RangeSetting;
import dev.koifih.client.render.Draw;
import dev.koifih.client.render.Scissor;
import dev.koifih.client.render.Switch;
import dev.koifih.client.render.Text;
import dev.koifih.client.ui.Theme;
import dev.koifih.client.ui.Transition;
import dev.koifih.client.ui.component.Bool;
import dev.koifih.client.ui.component.Control;
import dev.koifih.client.ui.component.Keybind;
import dev.koifih.client.ui.component.Slider;
import dev.koifih.client.ui.component.TextInput;
import dev.koifih.client.ui.component.popup.Dropdown;
import dev.koifih.client.ui.component.popup.Popup;
import dev.koifih.client.util.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

/**
 * Third GUI style ("Cards"): a wide panel with a brand/category sidebar and a
 * two-column grid of module cards (name, description, live toggle) - the
 * sigma/internalsonly look. Left-click toggles, gear / right-click opens the
 * module's settings sheet. Same module model and sounds as the other GUIs.
 */
public final class CardGui extends Screen {
    private static final int BASE_W = 610;
    private static final int BASE_H = 400;
    private static final int SIDEBAR_UNITS = 155;
    private static final int CARD_HEIGHT_UNITS = 63;
    private static final int CARD_GAP_UNITS = 10;
    private static final int CARD_RADIUS_UNITS = 11;
    private static final int COLS = 2;
    private static final int PINK = 0xFFCE52B3, INK = 0xFFF7EAF5, MUTED = 0xFFD4B8D1;
    private static final int[] NAV_ICONS = {0xe145, 0xe566, 0xe8f4, 0xe55b, 0xe8b8, 0xe8cb};
    private static final String[] CLIENT_LINKS = {"Configs", "Themes", "Keybinds", "Options"};
    private static final int SEARCH_H_UNITS = 28;

    private record Cat(String label, com.utilitymod.gui.Category category) {}
    private static final List<Cat> CATS = List.of(
            new Cat(LT.L5, com.utilitymod.gui.Category.COMBAT),
            new Cat(LT.L6, com.utilitymod.gui.Category.MOVEMENT),
            new Cat(LT.L7, com.utilitymod.gui.Category.VISUAL),
            new Cat(LT.L8, com.utilitymod.gui.Category.FINDER),
            new Cat(LT.L9, com.utilitymod.gui.Category.MISC),
            new Cat(LT.L10, com.utilitymod.gui.Category.DONUT));

    private final Transition reveal = new Transition(0f, 190, 90, Transition.Easing.EASE_OUT_SETTLE, Transition.Easing.EASE_IN_CUBIC);
    private static final MinecraftClient MCA = MinecraftClient.getInstance();
    /** Screen-level focus (Screen has none of Wing's widget routing). */
    private Control focus;
    private final Map<Module, Float> toggleShown = new HashMap<>();
    private final Map<Module, Float> cardHover = new HashMap<>();
    private TextInput search;
    private final StringBuilder query = new StringBuilder();
    private int panelX, panelY, panelW, panelH, sidebarW;
    private float scale;
    private com.utilitymod.gui.Category selected = com.utilitymod.gui.Category.COMBAT;
    private float scroll;
    private float scrollGoal;
    private boolean closing;
    private final CardGuiMotion motion = new CardGuiMotion();
    private long frameTime;
    private float frameEase = .2f;
    private boolean pointerHeld;
    private int page; // 0 modules, 1 configs, 2 themes, 3 keybinds, 4 options
    private TextInput profileInput;
    private final StringBuilder profileName = new StringBuilder();
    private List<String> profiles = List.of();
    private float pageScroll, pageMax;
    private String notice = "", pendingDelete, bindingModule;
    private boolean bindingMenu;
    private int colorEditor = -1;
    private final List<Hotspot> hotspots = new ArrayList<>();
    private Hotspot dragging;
    private record Hotspot(float x, float y, float w, float h, java.util.function.BiConsumer<Double, Double> action, boolean drag) {
        boolean contains(double mx, double my) { return mx >= x && mx < x+w && my >= y && my < y+h; }
    }


    // Settings sheet state.
    private record Row(Setting setting, Control control, boolean wide) {}
    private final List<Row> sheetRows = new ArrayList<>();
    private final List<Control> sheetControls = new ArrayList<>();
    private Feature sheetModule;
    private float sheetScroll, sheetScrollGoal;

    public CardGui() {
        super(net.minecraft.text.Text.literal(LT.L11));
    }

    // ---- panel geometry ---------------------------------------------------------

    private int pad() { return Math.round(10 * scale); }
    private int contentX() { return panelX + sidebarW; }
    private int contentY() { return panelY + pad(); }
    private int contentW() { return panelW - sidebarW; }
    private int contentH() { return panelH - 2 * pad(); }

    @Override
    protected void init() {
        com.utilitymod.gui.GuiTextures.ensureLoaded();
        scale = fitScale();
        motion.reset();
        frameTime = System.nanoTime();
        panelW = Math.max(1, Math.round(BASE_W * scale));
        panelH = Math.max(1, Math.round(BASE_H * scale));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        sidebarW = Math.round(SIDEBAR_UNITS * scale);
        reveal.set(1f);
        search = new TextInput(0, 0, 10, 10, scale, net.minecraft.text.Text.literal(LT.L12), 24,
                query::toString, s -> { query.setLength(0); query.append(s); });
        search.setFocused(false);
        search.setPlaceholder("Search modules...");
        search.setSurface(0x209F6695, INK, MUTED, 28, 36);
        profileInput = new TextInput(0, 0, 10, 10, scale, net.minecraft.text.Text.literal("New profile name..."), 48,
                profileName::toString, v -> { profileName.setLength(0); profileName.append(v); });
        profileInput.setPlaceholder("New profile name...");
        profileInput.setSurface(0x209F6695, INK, MUTED, 10, 10);
        profiles = GuiConfig.profiles();
        closeSheet();
    }

    private float fitScale() {
        float desired = Math.min(width * .64f / BASE_W, height * .74f / BASE_H) * CardGuiStyle.size / 100f;
        return Math.max(.1f, Math.min(desired, Math.min((width-16f)/BASE_W, (height-16f)/BASE_H)));
    }

    private void layoutMotion(int mx, int my) {
        long now = System.nanoTime();
        float dt = frameTime == 0 ? 1f/60f : (now-frameTime)/1_000_000_000f;
        frameTime = now;
        frameEase = 1f-(float)Math.exp(-14f*Math.min(.05f, Math.max(0,dt)));
        int bx=(width-panelW)/2, by=(height-panelH)/2;
        motion.update(mx,my,bx,by,panelW,panelH,scale,dt,CardGuiStyle.motion && CardGuiStyle.gravity>0,
                pointerHeld || sheetModule!=null || (focus!=null && focus.capturesInput()));
        panelX=bx+Math.round(motion.x()*CardGuiStyle.gravity/100f);
        panelY=by+Math.round(motion.y()*CardGuiStyle.gravity/100f);
    }

    @Override
    public void removed() {
        closeSheet();
        com.utilitymod.gui.GuiConfig.save();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---- module list ------------------------------------------------------------

    private List<Module> visible() {
        String q = query.toString().trim().toLowerCase(Locale.ROOT);
        List<Module> out = new ArrayList<>();
        Iterable<? extends Module> source = selected == com.utilitymod.gui.Category.DONUT
                ? com.utilitymod.anubis.AnubisBridge.features() : ModuleManager.all();
        for (Module m : source) {
            if (m.getCategory() != selected) continue;
            if (!q.isEmpty() && !(m.getName().toLowerCase(Locale.ROOT).contains(q)
                    || (m.getDescription() != null && m.getDescription().toLowerCase(Locale.ROOT).contains(q)))) continue;
            out.add(m);
        }
        return out;
    }

    // ---- draw -------------------------------------------------------------------

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        Theme.update();
        layoutMotion(mouseX, mouseY);
        hotspots.clear();
        if (closing && reveal.value() <= 0f) {
            MinecraftClient.getInstance().setScreen(null);
            return;
        }
        float shown = reveal.value();
        graphics.createNewRootLayer();
        if (shown < 1f) graphics.fill(0, 0, width, height, Colors.withAlpha(0xFF08080C, 0.55f * (1f - shown)));

        int radius = Math.max(1, Math.round(CardGuiStyle.radius * scale));
        for (int i = 6; i >= 1; i--) {
            int s = i * 2;
            Draw.rect(graphics, panelX - s, panelY - s + 3, panelW + 2 * s, panelH + 2 * s,
                    radius + s, Colors.withAlpha(0xFF000000, 0.04f));
        }
        if (CardGuiStyle.glass && com.utilitymod.echo.render.GlassRenderer.available()) {
            com.utilitymod.echo.render.GlassRenderer.queue(new com.utilitymod.echo.render.GlassRenderer.Panel(
                    panelX, panelY, panelW, panelH, radius, 0.92f));
            com.utilitymod.echo.render.GlassRenderer.flushCard(graphics, CardGuiStyle.background, CardGuiStyle.blur / 5f, !CardGuiStyle.motion);
        }
        boolean glassReady = CardGuiStyle.glass && com.utilitymod.echo.render.GlassRenderer.available();
        if (!glassReady) com.utilitymod.echo.render.GlassRenderer.reportCardGate(CardGuiStyle.glass);
        Draw.rect(graphics, panelX, panelY, panelW, panelH, radius, Colors.withAlpha(CardGuiStyle.back, glassReady ? .04f : .72f));
        com.utilitymod.gui.Rounded.outline(graphics, panelX, panelY, panelW, panelH, radius, 1f, 0x18F4C4EF);
        Draw.rect(graphics, panelX, panelY, sidebarW, panelH, radius, Colors.withAlpha(CardGuiStyle.background, .08f));
        graphics.fill(contentX()-1, panelY, contentX(), panelY+panelH, 0x22F0B6E5);
        graphics.fill(contentX(), panelY+Math.round(48*scale), panelX+panelW, panelY+Math.round(48*scale)+1, 0x22FFFFFF);

        drawSidebar(graphics, mouseX, mouseY);
        drawTopbar(graphics);
        drawSearch(graphics, mouseX, mouseY, delta);
        if (page == 0) {
            drawCategoryHeader(graphics);
            drawCards(graphics, mouseX, mouseY, delta);
        } else drawClientPage(graphics, mouseX, mouseY, delta);
        if (sheetModule != null) drawSheet(graphics, mouseX, mouseY, delta);
    }

    private void drawCategoryHeader(DrawContext g) {
        String label=CATS.stream().filter(c->c.category==selected).findFirst().map(Cat::label).orElse("Modules");
        List<Module> entries=visible();
        long enabled=entries.stream().filter(Module::isEnabled).count();
        CardGuiIcons.draw(g,new String[]{"combat","movement","eye","compass","sliders","shield"}[Math.max(0,CATS.indexOf(CATS.stream().filter(c->c.category==selected).findFirst().orElse(CATS.get(0))))],contentX()+15*scale,panelY+64*scale,12*scale,CardGuiStyle.accent);
        Text.draw(g,label,contentX()+34*scale,panelY+71*scale,10*scale,INK);
        Text.draw(g,entries.size()+" modules",contentX()+39*scale+Text.width(label,10*scale),panelY+71*scale,6.5f*scale,MUTED);
        String on=enabled+" on";
        float bx=panelX+panelW-47*scale;
        Draw.rect(g,bx,panelY+60*scale,35*scale,Math.round(17*scale),Math.round(3*scale),0x22FFFFFF);
        Text.draw(g,on,bx+6*scale,panelY+68.5f*scale,6.5f*scale,INK);
        g.fill(contentX()+Math.round(12*scale),panelY+Math.round(84*scale),panelX+panelW-Math.round(12*scale),panelY+Math.round(84*scale)+1,0x18FFFFFF);
    }

    private void setPage(int value) {
        page=value; dragging=null; pointerHeld=false; pageScroll=0; colorEditor=-1; pendingDelete=null; bindingModule=null; bindingMenu=false;
        closeSheet(); focus=null; search.setFocused(false); profileInput.setFocused(false);
        profiles=GuiConfig.profiles(); notice="";
        UiSounds.play(UiSounds.Sound.TAB);
    }
    private float pageX() { return contentX()+16*scale; }
    private float pageWidth() { return contentW()-32*scale; }
    private void hot(float x,float y,float w,float h,boolean drag,java.util.function.BiConsumer<Double,Double> action) {
        float top=panelY+56*scale, bottom=panelY+panelH-12*scale;
        if(y>=top && y+h<=bottom) hotspots.add(new Hotspot(x,y,w,h,action,drag));
    }
    private void button(DrawContext g,String text,float x,float y,float w,Runnable action,boolean danger) {
        Draw.rect(g,x,y,w,Math.max(1,Math.round(20*scale)),Math.round(3*scale),danger?0x804B2330:Colors.withAlpha(CardGuiStyle.accent,.64f));
        Text.drawCenteredX(g,text,x+w/2,y+10*scale,7*scale,INK);
        hot(x,y,w,20*scale,false,(mx,my)->action.run());
    }
    private void change(java.util.function.IntConsumer set,int value) { set.accept(value); GuiConfig.markDirty(); }
    private float slider(DrawContext g,String label,float y,int min,int max,int value,java.util.function.IntConsumer set) {
        float x=pageX()+14*scale,w=pageWidth()-28*scale;
        Text.draw(g,label,x,y+8*scale,8*scale,INK);
        String val=Integer.toString(value); Text.draw(g,val,x+w-Text.width(val,8*scale),y+8*scale,8*scale,INK);
        float ty=y+22*scale,t=(value-min)/(float)(max-min);
        Draw.rect(g,x,ty,w,Math.max(1,Math.round(4*scale)),0,0x446F4F69);
        Draw.rect(g,x,ty,w*t,Math.max(1,Math.round(4*scale)),0,CardGuiStyle.accent);
        Draw.rect(g,x+w*t-4*scale,ty-3*scale,8*scale,Math.max(1,Math.round(10*scale)),Math.round(2*scale),0xFF352332);
        Draw.rect(g,x+w*t-3*scale,ty-2*scale,6*scale,Math.max(1,Math.round(8*scale)),Math.round(1*scale),INK);
        hot(x-4*scale,ty-5*scale,w+8*scale,15*scale,true,(mx,my)->change(set,min+Math.round(Math.max(0,Math.min(1,(float)(mx-x)/w))*(max-min))));
        return y+38*scale;
    }
    private float toggle(DrawContext g,String label,float y,boolean value,Runnable change) {
        float x=pageX()+14*scale,w=pageWidth()-28*scale;
        Text.draw(g,label,x,y+10*scale,8*scale,INK);
        float tx=x+w-28*scale;
        Draw.rect(g,tx,y+2*scale,28*scale,Math.round(16*scale),Math.round(3*scale),value?CardGuiStyle.accent:0x665C4B60);
        Draw.rect(g,tx+(value?17:3)*scale,y+5*scale,9*scale,Math.round(10*scale),Math.round(2*scale),INK);
        hot(x,y,w,22*scale,false,(mx,my)->{change.run();GuiConfig.markDirty();});
        return y+28*scale;
    }
    private int color(int i) { return i==0?CardGuiStyle.accent:i==1?CardGuiStyle.background:CardGuiStyle.back; }
    private void color(int i,int c) {
        if(i==0)CardGuiStyle.accent=c;else if(i==1)CardGuiStyle.background=c;else CardGuiStyle.back=c;
        GuiConfig.markDirty();
    }
    private float colorRow(DrawContext g,String label,int index,float y) {
        float x=pageX()+14*scale,w=pageWidth()-28*scale;
        Text.draw(g,label,x,y+10*scale,8*scale,INK);
        Draw.rect(g,x+w-46*scale,y+3*scale,31*scale,Math.round(15*scale),Math.round(2*scale),color(index));
        Text.draw(g,colorEditor==index?"v":">",x+w-7*scale,y+10*scale,8*scale,MUTED);
        hot(x,y,w,24*scale,false,(mx,my)->colorEditor=colorEditor==index?-1:index);
        y+=27*scale;
        if(colorEditor!=index)return y;
        float[] hsv=java.awt.Color.RGBtoHSB((color(index)>>16)&255,(color(index)>>8)&255,color(index)&255,null);
        float cy=y, ch=58*scale,hy=y+64*scale;
        int hue=0xFF000000|java.awt.Color.HSBtoRGB(hsv[0],1,1);
        // Horizontal saturation and vertical value gradients, in 32 bounded strips.
        for(int j=0;j<32;j++) {
            int x0=Math.round(x+w*j/32),x1=Math.round(x+w*(j+1)/32);
            g.fillGradient(x0,Math.round(cy),x1,Math.round(cy+ch),Colors.lerp(0xFFFFFFFF,hue,j/31f),0xFF000000);
        }
        Draw.rect(g,x+hsv[1]*w-2*scale,cy+(1-hsv[2])*ch-2*scale,4*scale,Math.max(1,Math.round(4*scale)),Math.round(scale),INK);
        Draw.hueBar(g,Math.round(x),Math.round(hy),Math.round(w),Math.max(1,Math.round(8*scale)),0);
        Draw.rect(g,x+hsv[0]*w-1*scale,hy-1*scale,2*scale,Math.round(10*scale),0,INK);
        hot(x,cy,w,ch,true,(mx,my)->color(index,java.awt.Color.HSBtoRGB(hsv[0],Math.max(0,Math.min(1,(float)(mx-x)/w)),1-Math.max(0,Math.min(1,(float)(my-cy)/ch)))));
        hot(x,hy,w,8*scale,true,(mx,my)->color(index,java.awt.Color.HSBtoRGB(Math.max(0,Math.min(1,(float)(mx-x)/w)),hsv[1],hsv[2])));
        return y+82*scale;
    }
    private void drawClientPage(DrawContext g,int mx,int my,float delta) {
        float x=pageX(),w=pageWidth(),top=panelY+60*scale,bottom=panelY+panelH-14*scale;
        if(page==1) {
            profileInput.visible=true; profileInput.setX(Math.round(x));profileInput.setY(Math.round(top));
            profileInput.setWidth(Math.round(w-78*scale));profileInput.setHeight(Math.round(25*scale));
            profileInput.draw(g,mx,my,delta);
            button(g,"Save",x+w-70*scale,top+2*scale,70*scale,()->{
                notice=GuiConfig.saveProfile(profileName.toString())?"Profile saved":"Enter a valid profile name";
                profiles=GuiConfig.profiles();
            },false);
            int listTop=Math.round(top+36*scale),listBottom=Math.round(bottom-13*scale);
            pageMax=Math.max(0,profiles.size()*32*scale-(listBottom-listTop));pageScroll=Math.min(pageMax,pageScroll);
            Scissor.clip(g,Math.round(x),listTop,Math.round(w),listBottom-listTop,()->{
                for(int i=0;i<profiles.size();i++) {
                    String name=profiles.get(i);float y=listTop+i*32*scale-pageScroll;
                    if(y<listTop || y+27*scale>listBottom)continue;
                    Draw.rect(g,x,y,w,Math.round(27*scale),Math.round(3*scale),0x25FFFFFF);
                    Text.draw(g,Text.fit(name,w-130*scale,8*scale),x+13*scale,y+13*scale,8*scale,INK);
                    button(g,"Load",x+w-90*scale,y+3*scale,50*scale,()->{boolean loaded=GuiConfig.loadProfile(name); notice=loaded?"Profile loaded":"Load failed"; if(loaded)init();},false);
                    button(g,name.equals(pendingDelete)?"Yes?":"X",x+w-32*scale,y+3*scale,28*scale,()->{
                        if(name.equals(pendingDelete)){ GuiConfig.deleteProfile(name); profiles=GuiConfig.profiles();pendingDelete=null; }
                        else {pendingDelete=name;notice="Click Yes? to delete "+name;}
                    },true);
                }
                if(profiles.isEmpty())Text.draw(g,"No saved profiles yet.",x+12*scale,listTop+15*scale,8*scale,MUTED);
            });
            Text.draw(g,Text.fit(notice.isEmpty()?"Autosave keeps the current state; profiles are snapshots.":notice,w,6.5f*scale),x+3*scale,bottom-3*scale,6.5f*scale,MUTED);
            return;
        }
        int clipTop=Math.round(top),clipBottom=Math.round(bottom);
        Scissor.clip(g,Math.round(x),clipTop,Math.round(w),clipBottom-clipTop,()->{
            Draw.rect(g,x,top,w,Math.round(bottom-top),Math.round(4*scale),0x1EFFFFFF);
            float y=top+13*scale-pageScroll;
            Text.drawCenteredX(g,CLIENT_LINKS[page-1],x+w/2,y,7*scale,MUTED); y+=15*scale;
            if(page==2) {
                y=colorRow(g,"Main colour",0,y);y=colorRow(g,"Background",1,y);y=colorRow(g,"Back",2,y);
                y=slider(g,"Blur",y,0,30,CardGuiStyle.blur,v->CardGuiStyle.blur=v);
                y=slider(g,"GUI radius",y,4,60,CardGuiStyle.radius,v->CardGuiStyle.radius=v);
                y=slider(g,"Module radius",y,2,40,CardGuiStyle.cardRadius,v->CardGuiStyle.cardRadius=v);
                y=toggle(g,"Liquid glass",y,CardGuiStyle.glass,()->CardGuiStyle.glass=!CardGuiStyle.glass);
                y=toggle(g,"Hover push",y,CardGuiStyle.motion,()->CardGuiStyle.motion=!CardGuiStyle.motion);
                y=slider(g,"Push strength",y,0,100,CardGuiStyle.gravity,v->CardGuiStyle.gravity=v);
            } else if(page==4) {
                Text.draw(g,"Open menu",x+14*scale,y+10*scale,8*scale,INK);
                button(g,bindingMenu?"Press key...":dev.koifih.client.input.Keybinds.panelKey().getLocalizedText().getString(),x+w-100*scale,y,86*scale,()->bindingMenu=true,false);y+=32*scale;
                y=toggle(g,"Menu sounds",y,UiSounds.enabled,()->UiSounds.enabled=!UiSounds.enabled);
                y=slider(g,"GUI scale (%)",y,70,120,CardGuiStyle.size,v->CardGuiStyle.size=v);
                y=toggle(g,"Reduced motion",y,!CardGuiStyle.motion,()->CardGuiStyle.motion=!CardGuiStyle.motion);
                y=toggle(g,"Glass effects",y,CardGuiStyle.glass,()->CardGuiStyle.glass=!CardGuiStyle.glass);
                button(g,"Profiles folder",x+14*scale,y,w-28*scale,GuiConfig::openProfileFolder,false); y+=32*scale;
                Text.draw(g,"Escape cancels rebind. Right click anywhere here resets the open key.",x+14*scale,y,6.5f*scale,MUTED); y+=22*scale;
            } else {
                Text.draw(g,"Module toggle keys - click to bind; Delete clears.",x+14*scale,y,6.5f*scale,MUTED);y+=18*scale;
                List<Module> modules=new ArrayList<>(ModuleManager.all());modules.addAll(com.utilitymod.anubis.AnubisBridge.features());
                for(Module m:modules) {
                    if(query.length()>0 && !m.getName().toLowerCase(Locale.ROOT).contains(query.toString().toLowerCase(Locale.ROOT)))continue;
                    if(y>=top && y+23*scale<=bottom) {
                        Text.draw(g,Text.fit(m.getName(),w-120*scale,8*scale),x+14*scale,y+10*scale,8*scale,INK);
                        String key=m.getName().equals(bindingModule)?"Press key...":ModuleBinds.name(ModuleBinds.get(m.getName()));
                        button(g,key.isEmpty()?"Unbound":key,x+w-96*scale,y,82*scale,()->bindingModule=m.getName(),false);
                    }
                    y+=27*scale;
                }
            }
            pageMax=Math.max(0,y+pageScroll-bottom+10*scale);
        });
        pageScroll=Math.max(0,Math.min(pageScroll,pageMax));
        if(pageMax>0) {
            float track=bottom-top,thumb=Math.max(12*scale,track*track/(track+pageMax));
            Draw.rect(g,x+w-3*scale,top+(track-thumb)*pageScroll/pageMax,2*scale,Math.round(thumb),0,CardGuiStyle.accent);
        }
    }

    private void drawTopbar(DrawContext graphics) {
        String user = MCA.player != null ? MCA.player.getName().getString() : LT.L13;
        String date = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm", Locale.ENGLISH));
        float right=panelX+panelW-48*scale;
        Text.draw(graphics, user, right-Text.width(user,8.5f*scale), panelY+14*scale,8.5f*scale,INK);
        Text.draw(graphics, date, right-Text.width(date,6f*scale), panelY+27*scale,6f*scale,MUTED);
        Draw.rect(graphics,panelX+panelW-36*scale,panelY+5*scale,29*scale,Math.round(29*scale),Math.round(7*scale),0xFFE9A1CF);
        Draw.icon(graphics,dev.koifih.client.render.AdinIcon.USER,Math.round(panelX+panelW-30*scale),Math.round(panelY+11*scale),Math.round(17*scale),0xFFFADEEE);
        Draw.rect(graphics,panelX+panelW-140*scale,panelY+6*scale,27*scale,Math.round(27*scale),Math.round(5*scale),0x284F2449);
        CardGuiIcons.draw(graphics,"bell",Math.round(panelX+panelW-134*scale),Math.round(panelY+12*scale),Math.round(15*scale),MUTED);
    }

    private void drawSidebar(DrawContext graphics, int mouseX, int mouseY) {
        float size = scale;
        Text.draw(graphics, List.of(new Text.Span(LT.L15, INK), new Text.Span(LT.L16, INK)),
                panelX + 16 * size, panelY + 24 * size, 10 * size);
        Text.draw(graphics, LT.L17, panelX + 16 * size, panelY + 36 * size, 6.5f * size, MUTED);

        int margin = Math.round(10 * size);
        int rowH = Math.round(18 * size);
        int navX = panelX + margin;
        int navW = sidebarW - 2 * margin;
        int y = panelY + Math.round(58 * size);

        boolean modulesHov = mouseX >= navX && mouseX < navX + navW && mouseY >= y && mouseY < y + rowH;
        Draw.rect(graphics, navX, y, navW, rowH, Math.round(6 * size), 0x667F286F);
        CardGuiIcons.draw(graphics, "layers", (int) (navX + 8 * size), (int) (y + rowH / 2f - 6 * size), (int) (12 * size), INK);
        Text.draw(graphics, LT.L18, navX + 26 * size, y + rowH / 2f, 8f * size, INK);
        y += rowH + Math.round(5 * size);

        for (Cat cat : CATS) {
            boolean sel = page == 0 && cat.category == selected;
            boolean hov = mouseX >= navX && mouseX < navX + navW && mouseY >= y && mouseY < y + rowH;
            int count = 0;
            for (Module m : cat.category == com.utilitymod.gui.Category.DONUT ? com.utilitymod.anubis.AnubisBridge.features() : ModuleManager.all()) if (m.getCategory() == cat.category) count++;
            if (sel) {
                Draw.rect(graphics, navX, y, navW, rowH, Math.round(3 * size), 0x2FFFFFFF);
                Draw.rect(graphics, panelX+Math.round(3*scale), y+Math.round(4*scale), Math.max(1,Math.round(2*scale)), Math.round(10*scale), 0, CardGuiStyle.accent);
            }
            else if (hov) Draw.rect(graphics, navX, y, navW, rowH, Math.round(6 * size), Colors.withAlpha(INK, 0.05f));
            float ty = y + rowH / 2f;
            CardGuiIcons.draw(graphics, new String[]{"combat","movement","eye","compass","sliders","shield"}[CATS.indexOf(cat)], navX+Math.round(8*scale), y+Math.round(5*scale), Math.round(10*scale), MUTED);
            Text.draw(graphics, cat.label, navX + 26 * size, ty, 8f * size, sel ? INK : hov ? INK : MUTED);
            String cs = Integer.toString(count);
            Text.draw(graphics, cs, navX + navW - 8 * size - Text.width(cs, 6.5f * size), ty, 6.5f * size, MUTED);
            y += rowH + Math.round(3 * size);
        }

        Text.draw(graphics, LT.L19, panelX+16*scale, panelY+222*scale, 9*scale, INK);
        for (int i=0;i<CLIENT_LINKS.length;i++) {
            float ly=panelY+(244+i*21)*scale;
            if (page == i+1) {
                Draw.rect(graphics, panelX+Math.round(10*scale), Math.round(ly-9*scale), sidebarW-Math.round(20*scale), Math.round(19*scale), Math.round(3*scale), 0x2FFFFFFF);
                Draw.rect(graphics, panelX+Math.round(3*scale), Math.round(ly-5*scale), Math.max(1,Math.round(2*scale)), Math.round(10*scale), 0, CardGuiStyle.accent);
            }
            CardGuiIcons.draw(graphics, new String[]{"folder","palette","keyboard","sliders"}[i],
                    Math.round(panelX+23*scale),Math.round(ly-5*scale),Math.round(10*scale),MUTED);
            Text.draw(graphics,CLIENT_LINKS[i],panelX+40*scale,ly,8*scale,MUTED);
        }

        // footer warning (matches the source screenshot's warning block)
        float footY = panelY + panelH - 44 * size;
        CardGuiIcons.draw(graphics, "info", (int) (panelX + 16 * size), (int) footY, (int) (12 * size), 0xFFFFC34B);
        Text.draw(graphics, "NOTE", panelX + 34 * size, footY + 6 * size, 7.5f * size, 0xFFFFC34B);
        List<String> lines = Text.wrap("Local profiles save your setup. Right-click a module to edit its settings.",
                sidebarW - 30 * size, 6f * size, 3);
        float ly = footY + 16 * size;
        for (String line : lines) {
            Text.draw(graphics, line, panelX + 16 * size, ly, 6f * size, MUTED);
            ly += 8 * size;
        }
    }

    private void drawSearch(DrawContext graphics, int mouseX, int mouseY, float delta) {
        int h = Math.round(SEARCH_H_UNITS * scale);
        search.setWidth(Math.max(1, contentW()-Math.round(162*scale)));
        search.setHeight(h);
        search.setX(Math.round(contentX() + 12 * scale));
        search.setY(Math.round(panelY + 6 * scale));
        search.visible = sheetModule == null && page != 1;
        if (search.visible) {
            search.draw(graphics, mouseX, mouseY, delta);
            CardGuiIcons.draw(graphics,"search",search.getX()+10*scale,search.getY()+8*scale,11*scale,MUTED);
            float kx=search.getX()+search.getWidth()-32*scale;
            Draw.rect(graphics,kx,search.getY()+7*scale,25*scale,Math.round(14*scale),Math.round(2*scale),0x18FFFFFF);
            Text.drawCenteredX(graphics,"Ctrl F",kx+12.5f*scale,search.getY()+14*scale,5.5f*scale,MUTED);
        }
    }

    private int cardsTop() {
        return panelY + Math.round(92 * scale);
    }

    private void drawCards(DrawContext graphics, int mouseX, int mouseY, float delta) {
        int top = panelY + Math.round(92 * scale);
        int bottom = contentY() + contentH() - Math.round(14 * scale);
        int gap = Math.round(CARD_GAP_UNITS * scale);
        int cardH = Math.round(CARD_HEIGHT_UNITS * scale);
        int gridW = contentW() - Math.round(24 * scale);
        int cw = Math.max(1, (gridW - (COLS - 1) * gap) / COLS);
        List<Module> mods = visible();
        float max = Math.max(0f, ((mods.size() + COLS - 1) / COLS) * (float) (cardH + gap) - gap - (bottom - top));
        scrollGoal = Math.max(0f, Math.min(max, scrollGoal));
        scroll += (scrollGoal - scroll) * frameEase;
        if (Math.abs(scrollGoal - scroll) < 0.5f) scroll = scrollGoal;

        Scissor.clip(graphics, contentX(), top, contentW(), Math.max(0, bottom - top), () -> {
            for (int i = 0; i < mods.size(); i++) {
                int col = i % COLS, row = i / COLS;
                int cx = contentX() + Math.round(12 * scale) + col * (cw + gap);
                int cy = top - Math.round(scroll) + row * (cardH + gap);
                if (cy + cardH < top || cy > bottom) continue;
                drawCard(graphics, mods.get(i), cx, cy, cw, cardH, mouseX, mouseY);
            }
        });

        if (max > 1f) {
            int ch = bottom - top;
            int barH = Math.max(12, Math.round(ch * ch / (float) (ch + max)));
            int barY = top + Math.round(scroll / max * (ch - barH));
            graphics.fill(contentX() + contentW() - 3, barY, contentX() + contentW(), barY + barH,
                    Colors.withAlpha(Theme.DIM, 0.6f));
        }
    }

    private void drawCard(DrawContext g, Module m, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        float ht = approach(cardHover.getOrDefault(m, 0f), hov ? 1f : 0f);
        cardHover.put(m, ht);
        boolean on = m.isEnabled();
        float ts = approach(toggleShown.getOrDefault(m, on ? 1f : 0f), on ? 1f : 0f);
        toggleShown.put(m, ts);

        int base = Colors.lerp(0x244E1848, 0x407C366C, ht * 0.45f);
        int cardColor = on ? Colors.lerp(base, 0x507C366C, 0.22f * ts + 0.08f) : base;
        Rounded.outline(g, x, y, w, h, Math.round(CardGuiStyle.cardRadius * scale), Math.max(.5f,scale*.5f), 0x0BE3A6D8);
        Draw.rect(g, x+1, y+1, w-2, h-2, Math.round(CardGuiStyle.cardRadius * scale), cardColor);
        Draw.rect(g, x + Math.round(8 * scale), y, w - Math.round(16 * scale), 1, 0,
                Colors.withAlpha(0xFFFFFFFF, 0.05f + 0.05f * ht));

        int toggleW = Math.round(26 * scale);
        String name = Text.fit(m.getName(), w - toggleW - 42 * scale, 9f * scale);
        Text.draw(g, name, x + 12 * scale, y + 19 * scale, 9f * scale, INK);
        Text.draw(g, name, x + 12.22f * scale, y + 19 * scale, 9f * scale, INK);
        String desc = m.getDescription() == null ? "" : m.getDescription();
        if (!desc.isEmpty()) {
            List<String> lines = Text.wrap(desc.replace('\uE120', ' '), w - 28 * scale, 6.8f * scale, 2);
            float ly = y + 31 * scale;
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                Text.draw(g, Text.fit(lines.get(i), w-28*scale, 6.8f*scale), x + 12 * scale, ly, 6.8f * scale, MUTED);
                ly += 10 * scale;
            }
        }
        boolean hasCfg = m instanceof Feature f && !f.getSettings().isEmpty();
        if (hasCfg) {
            int gx = x + w - toggleW - Math.round(24 * scale);
            CardGuiIcons.draw(g, "sliders", (int) gx, (int) (y + 14 * scale), (int) (11 * scale),
                    hov ? INK : MUTED);
        }
        int tx = x + w - toggleW - Math.round(12 * scale);
        int ty = y + Math.round(11 * scale);
        int th=Math.max(1,Math.round(17*scale));
        Draw.rect(g,tx,ty,toggleW,th,Math.round(3*scale),Colors.lerp(0x665C4B60,CardGuiStyle.accent,ts));
        float thumb=11*scale, inset=3*scale;
        Draw.rect(g,tx+inset+(toggleW-thumb-2*inset)*ts,ty+inset,thumb,Math.round(thumb),Math.round(2*scale),Colors.lerp(0xFFBEC9D7,0xFFFFFFFF,ts));
    }

    private float approach(float current, float target) {
        float next = current + (target - current) * frameEase;
        return Math.abs(target - next) < 0.002f ? target : next;
    }

    // ---- settings sheet ---------------------------------------------------------

    private void openSheet(Module m) {
        if(search!=null) { search.setFocused(false); search.dismiss(); }
        focus=null;
        closeSheet();
        if (!(m instanceof Feature f) || f.getSettings().isEmpty()) return;
        sheetModule = f;
        sheetScroll = sheetScrollGoal = 0f;
        for (Setting st : f.getSettings()) {
            Control c = create(st);
            if (c == null) continue;
            sheetControls.add(c);
            sheetRows.add(new Row(st, c, !(c instanceof Bool)));
        }
    }

    private void closeSheet() {
        focus = null;
        for (Control c : sheetControls) {
            c.dismiss();
            c.visible = false;
        }
        sheetControls.clear();
        sheetRows.clear();
        sheetModule = null;
    }

    private Control create(Setting st) {
        var label = net.minecraft.text.Text.literal(st.getName());
        int fieldH = Math.max(1, Math.round(16 * scale));
        if (st instanceof BoolSetting b) {
            return new Bool(0, 0, Math.round(24 * scale), Math.max(1, Math.round(12 * scale)), scale, label, b::getValue, b::setValue);
        }
        if (st instanceof FloatSetting f) {
            float inc = f.getIncrement() > 0 ? f.getIncrement() : 0.01f;
            Slider sl = new Slider(0, 0, Math.round(120 * scale), fieldH, scale, label,
                    Math.round(f.getMinValue() / inc), Math.round(f.getMaxValue() / inc),
                    () -> Math.round(f.getValue() / inc), v -> f.setValue(v * inc));
            sl.setFormat(v -> fmt(v * inc, inc));
            return sl;
        }
        if (st instanceof IntSetting i) {
            return new Slider(0, 0, Math.round(120 * scale), fieldH, scale, label, i.getMinValue(), i.getMaxValue(),
                    i::getValue, i::setValue);
        }
        if (st instanceof RangeSetting r) {
            float inc = r.getIncrement() > 0 ? r.getIncrement() : 0.01f;
            Slider sl = Slider.range(0, 0, Math.round(120 * scale), fieldH, scale, label,
                    Math.round(r.getLowerBound() / inc), () -> Math.round(r.getUpperBound() / inc),
                    () -> Math.round(r.getMinValue() / inc), v -> r.setMinValue(v * inc),
                    () -> Math.round(r.getMaxValue() / inc), v -> r.setMaxValue(v * inc));
            sl.setFormat(v -> fmt(v * inc, inc));
            return sl;
        }
        if (st instanceof ModeSetting mo) {
            List<CharSequence> modes = mo.getModes();
            String[] names = modes.stream().map(CharSequence::toString).toArray(String[]::new);
            Dropdown d = Dropdown.single(0, 0, Math.round(120 * scale), fieldH, scale, label, names,
                    () -> java.util.Arrays.asList(names).indexOf(mo.getValue().toString()),
                    i -> mo.setValue(modes.get(i)));
            d.setBottomLimit(panelY + panelH - pad());
            return d;
        }
        if (st instanceof MultiModeSetting mm) {
            List<CharSequence> opts = List.copyOf(mm.getOptionsSequenceView().keySet());
            Dropdown d = Dropdown.multi(0, 0, Math.round(120 * scale), fieldH, scale, label,
                    opts.stream().map(CharSequence::toString).toArray(String[]::new),
                    () -> new LiveSelection(mm, opts));
            d.setBottomLimit(panelY + panelH - pad());
            return d;
        }
        if (st instanceof ColorSetting c) {
            String[] names = {LT.L22, LT.L23, LT.L24, LT.L25, LT.L26, LT.L27, LT.L28, LT.L29, LT.L30};
            int[] rgb = {0xE53935, 0xFB8C00, 0xFDD835, 0x43A047, 0x00ACC1, 0x1E88E5, 0x8E24AA, 0xEC407A, 0xFFFFFF};
            Dropdown d = Dropdown.single(0, 0, Math.round(120 * scale), fieldH, scale, label, names,
                    () -> nearestColor(c.getARGB(), rgb),
                    i -> c.setFromARGB((c.getARGB() & 0xFF000000) | rgb[i]));
            d.setBottomLimit(panelY + panelH - pad());
            return d;
        }
        if (st instanceof KeybindSetting k) {
            Keybind kb = new Keybind(0, 0, Math.round(56 * scale), fieldH, scale, label,
                    () -> {
                        int code = k.getKey();
                        if (code == -1 || code == 0) return net.minecraft.client.util.InputUtil.UNKNOWN_KEY;
                        if ((code & 0x80000000) != 0) return net.minecraft.client.util.InputUtil.Type.MOUSE.createFromCode(code & 0xFF);
                        return net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(code);
                    },
                    key -> {
                        if (key == null || key.equals(net.minecraft.client.util.InputUtil.UNKNOWN_KEY)) k.setKey(-1);
                        else if (key.getCategory() == net.minecraft.client.util.InputUtil.Type.MOUSE) k.setKey(0x80000000 | key.getCode());
                        else k.setKey(key.getCode());
                    });
            kb.setClearable(true);
            return kb;
        }
        return null;
    }

    private static String fmt(float v, float inc) {
        if (inc >= 1f) return Integer.toString(Math.round(v));
        int decimals = inc >= 0.1f ? 1 : 2;
        return String.format(Locale.ROOT, LT.L31 + decimals + LT.L32, v);
    }

    private static int nearestColor(int argb, int[] rgb) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        int a = argb & 0xFFFFFF;
        for (int i = 0; i < rgb.length; i++) {
            int b = rgb[i];
            long dr = ((a >> 16) & 255) - ((b >> 16) & 255), dg = ((a >> 8) & 255) - ((b >> 8) & 255), db = (a & 255) - (b & 255);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    /** Write-through Set view of a MultiModeSetting for Dropdown.multi (same as the panel GUI). */
    private static final class LiveSelection extends AbstractSet<Integer> {
        private final MultiModeSetting setting;
        private final List<CharSequence> options;

        LiveSelection(MultiModeSetting setting, List<CharSequence> options) {
            this.setting = setting;
            this.options = options;
        }

        @Override
        public Iterator<Integer> iterator() {
            List<Integer> on = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) if (setting.isEnabled(options.get(i))) on.add(i);
            return on.iterator();
        }

        @Override public int size() { int n = 0; for (CharSequence o : options) if (setting.isEnabled(o)) n++; return n; }
        @Override public boolean contains(Object o) { return o instanceof Integer i && i >= 0 && i < options.size() && setting.isEnabled(options.get(i)); }
        @Override public boolean add(Integer i) { boolean was = contains(i); setting.setOption(options.get(i), true); return !was; }
        @Override public boolean remove(Object o) { boolean was = contains(o); if (was) setting.setOption(options.get((Integer) o), false); return was; }
    }

    private static final int SHEET_ROW_H_UNITS = 22;

    private void drawSheet(DrawContext g, int mouseX, int mouseY, float delta) {
        int sheetW = Math.min(contentW() - 2 * pad(), Math.round(340 * scale));
        int sheetH = Math.min(contentH() - 2 * pad(), Math.round(300 * scale));
        int sx = contentX() + (contentW() - sheetW) / 2;
        int sy = contentY() + (contentH() - sheetH) / 2;
        List<Popup> openPopups = new ArrayList<>();

        int inner = pad() + 1;
        int rowStride = Math.round(SHEET_ROW_H_UNITS * scale);
        int headerH = Math.round(18 * scale);
        int top = sy + headerH + inner;
        int bottom = sy + sheetH - inner;
        int rowsH = bottom - top;
        int content = Math.max(rowStride, sheetRows.size() * rowStride);
        sheetScrollGoal = Math.max(0f, Math.min(Math.max(0f, content - rowsH), sheetScrollGoal));
        sheetScroll += (sheetScrollGoal - sheetScroll) * frameEase;
        if (Math.abs(sheetScrollGoal - sheetScroll) < 0.5f) sheetScroll = sheetScrollGoal;

        Draw.rect(g, sx, sy, sheetW, sheetH, Math.round(8 * scale), 0xF030102A);
        for (Row r : sheetRows) r.control().visible = false;
        Scissor.clip(g, sx, top, sheetW, Math.max(0, rowsH), () -> {
            for (int i = 0; i < sheetRows.size(); i++) {
                Row r = sheetRows.get(i);
                int ry = top - Math.round(sheetScroll) + i * rowStride;
                Control c = r.control();
                boolean inView = ry + rowStride > top && ry < bottom;
                c.visible = inView;
                c.active = inView;
                if (!inView) continue;
                if (c instanceof Popup p) {
                    c.setX(sx + inner);
                    c.setY(ry);
                    c.setWidth(sheetW - 2 * inner);
                    c.setHeight(rowStride);
                } else {
                    Draw.rect(g, sx + inner, ry, sheetW - 2 * inner, rowStride, 5, Theme.ROW);
                    Text.draw(g, r.setting().getName(), sx + inner + 10 * scale, ry + rowStride * 0.5f, 7.5f * scale, INK);
                    int cw = r.wide() ? Math.min(Math.round(120 * scale), (sheetW - 2 * inner) / 2)
                            : Math.round(24 * scale);
                    int ch = r.wide() ? Math.max(1, Math.round(16 * scale)) : Math.max(1, Math.round(12 * scale));
                    c.setWidth(cw);
                    c.setHeight(ch);
                    c.setX(sx + sheetW - inner - cw - Math.round(8 * scale));
                    c.setY(ry + (rowStride - ch) / 2);
                }
                c.draw(g, mouseX, mouseY, delta);
                if (c instanceof Popup p) openPopups.add(p);
            }
        });

        // header (above the sheet bg, after rows so the back chevron reads crisp)
        Draw.rect(g, sx, sy, sheetW, headerH, Math.round(8 * scale), 0xFF49113E);
        Draw.icon(g, 0xe5c4, (int) (sx + 10 * scale), (int) (sy + headerH / 2f - 7 * scale), (int) (14 * scale), INK);
        Text.draw(g, sheetModule.getName(), sx + 30 * scale, sy + headerH / 2f, 9.5f * scale, INK);
        for (int i = openPopups.size() - 1; i >= 0; i--) openPopups.get(i).renderPopup(g, mouseX, mouseY);
    }

    private boolean sheetContentHit(double mx,double my) {
        int sw=Math.min(contentW()-2*pad(),Math.round(340*scale));
        int sh=Math.min(contentH()-2*pad(),Math.round(300*scale));
        int sx=contentX()+(contentW()-sw)/2, sy=contentY()+(contentH()-sh)/2;
        return mx>=sx && mx<sx+sw && my>=sy+Math.round(18*scale)+pad()+1 && my<sy+sh-pad()-1;
    }

    private boolean sheetHeaderHit(double mx, double my) {
        if (sheetModule == null) return false;
        int sheetW = Math.min(contentW() - 2 * pad(), Math.round(340 * scale));
        int sheetH = Math.min(contentH() - 2 * pad(), Math.round(300 * scale));
        int sx = contentX() + (contentW() - sheetW) / 2;
        int sy = contentY() + (contentH() - sheetH) / 2;
        return mx >= sx && mx < sx + sheetW && my >= sy && my < sy + Math.round(18 * scale);
    }

    // ---- input ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click event, boolean doubleClick) {
        if (closing) return true;
        pointerHeld = true;
        if (sheetModule == null && page != 0) {
            if (page == 4 && event.button() == 1) {
                // reference law: right click on the options page resets the open key
                dev.koifih.client.input.Keybinds.setOpenKeyCode(GLFW.GLFW_KEY_RIGHT_SHIFT);
                GuiConfig.markDirty();
                return true;
            }
            if (page == 1 && profileInput.isMouseOver(event.x(),event.y())) {
                focus=profileInput; search.setFocused(false); profileInput.setFocused(true); profileInput.onClick(event,doubleClick); return true;
            }
            for (Hotspot h : hotspots) if (h.contains(event.x(),event.y())) {
                if (event.button()==0) { h.action.accept(event.x(),event.y()); if(h.drag) dragging=h; }
                return true;
            }
        }
        if (sheetModule != null) {
            for(Control c:sheetControls) if(c instanceof Keybind k && k.captureMouse(event)) return true;
            // open dropdown lists first, then rows/controls, then outside-click close
            for (Control c : sheetControls) {
                if (c instanceof Popup p && p.isOpen() && p.mouseClicked(event, doubleClick)) return true;
            }
            for (Control c : sheetControls) {
                if (sheetContentHit(event.x(),event.y()) && c.active && c.visible && c.isMouseOver(event.x(), event.y())) {
                    focus = c;
                    c.onClick(event, doubleClick);
                    return true;
                }
            }
            if (event.button() == 0 && sheetHeaderHit(event.x(), event.y())) {
                closeSheet();
                com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.TAB);
                return true;
            }
            if (event.button() == 0) closeSheet();
            return true;
        }
        // search field
        if (search.isMouseOver(event.x(), event.y()) && search.visible) {
            focus = search;
            profileInput.setFocused(false); search.setFocused(true);
            search.onClick(event, doubleClick);
            return true;
        }
        // sidebar navigation
        if (event.x() >= panelX && event.x() < contentX()) {
            if(event.button()==0) for(int i=0;i<CLIENT_LINKS.length;i++) {
                float ly=panelY+(235+i*21)*scale;
                if(event.y()>=ly && event.y()<ly+19*scale) {
                    setPage(i+1);
                    return true;
                }
            }
            float size = scale;
            int rowH = Math.round(18 * size);
            int y = panelY + Math.round(58 * size) + rowH + Math.round(5 * size);
            int margin = Math.round(10 * size);
            int navX = panelX + margin;
            int navW = sidebarW - 2 * margin;
            if (event.x() >= navX && event.x() < navX + navW) {
                for (Cat cat : CATS) {
                    if (event.y() >= y && event.y() < y + rowH) {
                        setPage(0);
                        selected = cat.category;
                        query.setLength(0);
                        focus = null;
                        com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.TAB);
                        scrollGoal = 0f;
                        return true;
                    }
                    y += rowH + Math.round(3 * size);
                }
            }
            return true;
        }
        if (page != 0) return true;
        // cards
        if (event.button() == 0 || event.button() == 1) {
            Module hit = cardAt(event.x(), event.y());
            if (hit != null) {
                if (event.button() == 1) {
                    openSheet(hit);
                    com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.POPUP);
                    return true;
                }
                // gear zone opens settings instead of toggling
                int gap = Math.round(CARD_GAP_UNITS * scale);
                int gridW = contentW() - Math.round(24 * scale);
                int cw = Math.max(1, (gridW - 1 * gap) / COLS);
                int cardH = Math.round(CARD_HEIGHT_UNITS * scale);
                int toggleW = Math.round(26 * scale);
                int x = cardX(hit, cw);
                int y = cardY(hit, cardH, gap);
                int gx = x + cw - toggleW - Math.round(24 * scale);
                boolean onGear = hit instanceof Feature f && !f.getSettings().isEmpty()
                        && event.x() >= gx && event.x() < x + cw - toggleW - Math.round(12 * scale)
                        && event.y() >= y + 8*scale && event.y() < y + 30*scale;
                if (onGear) {
                    openSheet(hit);
                    com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.POPUP);
                    return true;
                }
                hit.setEnabled(!hit.isEnabled());
                com.utilitymod.gui.UiSounds.play(hit.isEnabled() ? com.utilitymod.gui.UiSounds.Sound.TOGGLE_ON : com.utilitymod.gui.UiSounds.Sound.TOGGLE_OFF);
                Toasts.push(hit.isEnabled() ? Toasts.ICON_ON : Toasts.ICON_OFF,
                        hit.getName() + (hit.isEnabled() ? LT.L36 : LT.L37));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int cardX(Module target, int cardW) {
        List<Module> mods = visible();
        int gap = Math.round(CARD_GAP_UNITS * scale);
        for (int i = 0; i < mods.size(); i++) {
            if (mods.get(i) == target) {
                return contentX() + Math.round(12 * scale) + (i % COLS) * (cardW + gap);
            }
        }
        return -1;
    }

    private int cardY(Module target, int cardH, int gap) {
        int top = panelY + Math.round(92 * scale);
        List<Module> mods = visible();
        for (int i = 0; i < mods.size(); i++) {
            if (mods.get(i) == target) {
                return top - Math.round(scroll) + (i / COLS) * (cardH + gap);
            }
        }
        return -1;
    }

    private Module cardAt(double mx, double my) {
        if(mx<contentX() || mx>=panelX+panelW || my<cardsTop() || my>=contentY()+contentH()-Math.round(14*scale)) return null;
        int top = panelY + Math.round(92 * scale);
        int gap = Math.round(CARD_GAP_UNITS * scale);
        int cardH = Math.round(CARD_HEIGHT_UNITS * scale);
        int gridW = contentW() - Math.round(24 * scale);
        int cw = Math.max(1, (gridW - (COLS - 1) * gap) / COLS);
        List<Module> mods = visible();
        for (int i = 0; i < mods.size(); i++) {
            int x = contentX() + Math.round(12 * scale) + (i % COLS) * (cw + gap);
            int y = top - Math.round(scroll) + (i / COLS) * (cardH + gap);
            if (mx >= x && mx < x + cw && my >= y && my < y + cardH) return mods.get(i);
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (x < contentX() || x >= panelX+panelW || y < panelY+48*scale || y >= panelY+panelH) return false;
        // Open dropdown first: the wheel scrolls its list, never the sheet behind it.
        if (sheetModule != null) {
            for (Control c : sheetControls) {
                if (c instanceof Popup p && p.isOpen() && p.scrolledWidget(x, y, dy)) return true;
            }
            boolean popupOpen = false;
            for (Control c : sheetControls) popupOpen |= c instanceof Popup p && p.isOpen();
            if (!popupOpen) sheetScrollGoal += dy < 0 ? 24 * scale : -24 * scale;
            return true;
        }
        if (page != 0) { pageScroll=Math.max(0,Math.min(pageMax,pageScroll-(float)dy*25*scale)); return true; }
        if (x >= contentX() && x < panelX + panelW) {
            scrollGoal += dy < 0 ? 60 * scale : -60 * scale;
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public boolean mouseDragged(Click event, double dx, double dy) {
        if (dragging!=null) { dragging.action.accept(event.x(),event.y()); return true; }
        if (sheetModule != null) {
            for (Control c : sheetControls) if (c.active && c.visible) c.onDrag(event, dx, dy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click event) {
        pointerHeld=false;
        if(dragging!=null) { dragging=null; GuiConfig.markDirty(); if(Math.abs(fitScale()-scale)>.001f)init(); return true; }
        if (sheetModule != null) {
            for (Control c : sheetControls) if (c.active && c.visible) c.onRelease(event);
            return true;
        }
        return super.mouseReleased(event);
    }

    private Control focusedControl() {
        return focus;
    }

    public boolean keyPressed(KeyInput event) {
        if (closing) return true;
        if(bindingMenu || bindingModule!=null) {
            if(event.key()!=GLFW.GLFW_KEY_ESCAPE) {
                if(bindingMenu) dev.koifih.client.input.Keybinds.setOpenKeyCode(event.key());
                else ModuleBinds.set(bindingModule,event.key()==GLFW.GLFW_KEY_BACKSPACE || event.key()==GLFW.GLFW_KEY_DELETE ? -1 : event.key());
            }
            bindingMenu=false; bindingModule=null; GuiConfig.markDirty(); return true;
        }
        if(event.key()==GLFW.GLFW_KEY_F && (event.modifiers() & GLFW.GLFW_MOD_CONTROL)!=0 && search.visible) { focus=search; search.setFocused(true); return true; }
        Control focused = focusedControl();
        if (focused != null && focused.capturesInput() && focused.keyPressed(event)) return true;
        if (sheetModule != null) {
            for (Control c : sheetControls) if (c.capturesInput() && c.keyPressed(event)) return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (sheetModule != null) {
                closeSheet();
                return true;
            }
            requestClose();
            return true;
        }
        if (dev.koifih.client.input.Keybinds.matches(event)) {
            requestClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharInput event) {
        Control focused = focusedControl();
        if (focused != null && focused.capturesInput()) return focused.charTyped(event);
        if (sheetModule != null) {
            for (Control c : sheetControls) if (c.capturesInput() && c.charTyped(event)) return true;
        }
        return super.charTyped(event);
    }

    private void requestClose() {
        if (closing) return;
        closing = true;
        com.utilitymod.gui.UiSounds.play(com.utilitymod.gui.UiSounds.Sound.CLOSE);
        com.utilitymod.gui.GuiConfig.save();
        reveal.set(0f);
    }

    @Override
    public void tick() {
        if (closing && reveal.value() <= 0f) {
            MinecraftClient.getInstance().setScreen(null);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbaf4,0x59b0,0x56cc,0x1603,0x7c40,0xf406,0xfad2}, new char[]{0x568f,0x59df,0xf468,0x7c26,0x166a,0xfab5,0xba87}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdb35,0xaef8,0x16c9,0xe054,0x4a2d,0xf0d4}, new char[]{0xaeac,0xdb5d,0xf0b1,0xe039,0x4a48,0x16ba}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x56ac,0x1006,0x646a,0x67b3,0x5fe1,0x3b39}, new char[]{0x104b,0x5f80,0x3b5a,0x56de,0x6405,0x67c0}).toString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11a3,0x962b,0xa708}, new char[]{0x9663,0x11f6,0xa74c,0x8072}).plainString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x63f7,0xf9c7,0x656c,0xb795,0x0480,0xd737}, new char[]{0x04c3,0xd758,0xb7f8,0x650e,0x6396,0xf9b3}).toString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa4e1,0xb378,0xefec,0x5042,0x3997,0xba26,0x2d16,0x23fc}, new char[]{0xa4ac,0x2393,0x2d60,0xba43,0xef81,0x39f2,0xb316,0x5036}).plainString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x328a,0x6c96,0x2352,0x0f41,0xcf30,0x86e5,0xf4d6}, new char[]{0x32dc,0x233b,0xf4a5,0x6ce3,0x8684,0x0f2d,0xcf43}).toString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x24df,0xf093,0xc55e,0x696b,0xc59b,0x27f4}, new char[]{0xf0d5,0x279d,0x24b1,0xc5ff,0xc53b,0x6919}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0361,0xc1fc,0x4c79,0x634d}, new char[]{0x032c,0x6324,0xc18f,0x4c1a}).toString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf7b8,0x9b4f,0x4289,0x1554,0x8236}, new char[]{0x9b0b,0x42e6,0x8258,0x1521,0xf7cc}).toString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaa5b,0x1fb4,0x3716,0x9f4f,0xf5e6,0x3162,0x80dc,0x5260,0x7a38,0x1999,0x3d54,0xc904,0x1212,0xcc5e,0x0e50,0xdc53}, new char[]{0x8089,0xc970,0x19f0,0x127e,0x310b,0x5214,0x1fcd,0x9f02,0x7a57,0xdc37,0xf5c6,0x3d17,0xaa3a,0x0e22,0xcc3a,0x3765}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x43a5,0xc8d0,0x01d4,0x22b7,0xa346,0x53dd,0xcfbd,0x47ec,0x9ad0}, new char[]{0x43f6,0x22d2,0x9ab1,0xa334,0xc8b3,0x4784,0xcf93,0x53f3,0x01fa}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4977,0x6be8,0xca19,0x693f,0x2f73,0x1c70}, new char[]{0xca69,0x491b,0x1c11,0x6b91,0x2f16,0x694d}).plainString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf56c,0xc968,0x14a6,0x7a18,0x49eb,0xbca8,0xe42f,0xabf8,0xf2e5,0x2f51}, new char[]{0xc90c,0xe40f,0xabb5,0x14eb,0x2f1c,0xbc88,0xf290,0x499e,0xf519,0x7a6d}).toString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x53b4,0xd948,0x0e11,0xd079,0x6c32,0x3b5e,0xf78e}, new char[]{0xd02c,0x6c46,0xd921,0xf7e2,0x3b37,0x0e65,0x53cd}).toString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3f4b,0x3e63,0x1c80}, new char[]{0x3e2e,0x3f24,0x1ce4,0xe9e1}).toString();
        static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0471,0x7666,0x26cb,0xa29c,0xe333,0xaf1b,0x3443,0x9b10,0x9505}, new char[]{0x9b53,0xa2dd,0xe361,0x9541,0x7646,0xaf4d,0x0438,0x3406,0x269c}).toString();
        static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x61f0,0xa667,0xc2ea,0x5926,0x73fb,0x724c,0xacf9}, new char[]{0xacb4,0x7223,0x5942,0x6185,0xc286,0x739e,0xa614}).toString();
        static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2f63,0x2ef6,0x835f,0xdc82,0x1220,0xa0d2}, new char[]{0x1263,0x8333,0xdceb,0x2e93,0x2f0d,0xa0a6}).toString();
        static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1de2,0xdf0b,0x3468,0x3e9a,0x65d0,0xab9d,0x8714}, new char[]{0x6587,0xdf4a,0x1db0,0x3426,0x875d,0xabd3,0x3edd}).plainString();
        static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3f8a,0x35ec,0x7c32,0x7849,0x43e3,0x055d,0xcce2,0x91c6,0x06cd,0xfebc,0xf06e,0x53c2,0xafa8,0x57b5,0xffcd,0x4bda,0xe645,0x0aa1,0xa8ae,0xc616,0x6dcf,0x3205,0x9b45,0xf771,0xa05e,0x3c86,0x7025,0xc02a,0x8019,0xd2dd,0xbfee,0x82d5,0x6087,0x9faa,0x9bb3,0xa86b,0x72f5,0xfdb1,0x8a08,0x4d65,0x5efd,0xf114,0xfae0,0x75a2,0xf691,0x64b5,0x035b,0x5eb9,0xdbb7,0x3f06,0x060c,0x7bf6,0x8506,0x4285,0xc7be,0x3a63,0x7cf2,0xf27d,0xda24,0x574d,0xbe5e,0xd8fa}, new char[]{0x5381,0x57dd,0xf175,0xfa8e,0x9bd4,0x7c57,0x4d16,0xda04,0x91a7,0xc05a,0x067c,0x7049,0x5ec0,0xf6b1,0xa032,0x326c,0x4395,0x3f63,0x4bf4,0x5edd,0xf722,0xc677,0xafde,0xfed9,0x35cc,0x728c,0x0334,0xffb8,0x9fd8,0xf04e,0xd899,0xd2b2,0x7827,0xc7d8,0x0534,0x3ce1,0xbe7e,0x64d7,0x9b20,0x7c94,0x8a67,0xf20f,0x6daa,0x8526,0x3ff9,0x75d5,0xdbde,0x60f3,0x807a,0x0ac9,0x06a4,0xa8c0,0xa80c,0xe665,0x7b95,0xfddd,0x5724,0xbf8b,0xcc8c,0x42f1,0x82a6,0x3a4d}).toString();
        static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc6e5,0x9fdb,0x957b}, new char[]{0x9529,0x9fbe,0xc681,0x1ddb}).toString();
        static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x05d7,0x7a0a,0x8378,0x451c,0xfef3,0x3c89}, new char[]{0xfebc,0x3cfb,0x8319,0x7a64,0x457b,0x05b2}).toString();
        static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfe33,0x85d0,0x7f6d,0xba3a,0xe5de,0x59c3}, new char[]{0xe587,0x59a6,0xba56,0x7f01,0xfe5c,0x85a7}).toString();
        static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbeee,0x15a7,0x4639,0x3b68,0xfebe}, new char[]{0xbea9,0x15d5,0x465c,0xfedb,0x3b06}).toString();
        static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x17fc,0x6f1d,0x95e6,0x9ab7}, new char[]{0x6f5e,0x959f,0x9ad6,0x1792}).toString();
        static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xed4f,0x601b,0xbdff,0xebb3}, new char[]{0xebf1,0x6077,0xbd8a,0xed2a}).toString();
        static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1342,0x1bb8,0xeb91,0xb90b,0xeb26,0x30c8}, new char[]{0x1312,0xeb53,0xebe3,0xb97b,0x1bd4,0x30ad}).toString();
        static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x452b,0xae72,0xe12e,0xbab3}, new char[]{0xe17e,0xbada,0xae1c,0x4540}).toString();
        static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x3d73,0x07c4,0xa0e1,0x52e9,0x2eb8}, new char[]{0x0793,0xa089,0x2ed1,0x3d07,0x528c}).plainString();
        static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa407,0x83e0}, new char[]{0x83c5,0xa429,0x8bba,0x605e}).plainString();
        static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe5af}, new char[]{0xe5c9,0x6c0b,0x1f39,0xdeec}).plainString();
        static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b49,0xd2f6,0xd3be,0x2836,0xd1e1,0x0491}, new char[]{0xd3fd,0x04de,0xd2b8,0x2870,0xd1a8,0x0b0e}).plainString();
        static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa9e9,0x8ad7,0xa889,0x53a9,0x1654}, new char[]{0x8a83,0xa9a1,0xa8cc,0x53e4,0x1611}).plainString();
        static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1d91,0x578e,0x1a80,0x50f7,0xc6bd}, new char[]{0x1ac4,0xc6f2,0x50b9,0x57db,0x1dc5}).plainString();
        static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc9dc,0x6c28,0x0150,0x3abe,0x172f,0xafec,0xcd17,0xa393}, new char[]{0xafcc,0x6c4d,0xa3fd,0x3adf,0x174d,0xc9b0,0xcd72,0x0134}).plainString();
        static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x9da8,0xee44,0x91f8,0x22e1,0xdd3c,0x898b,0xef82,0xcbbd,0xe61e}, new char[]{0xdd1c,0xe67a,0x89e2,0xeff1,0xee25,0x9dca,0x9194,0x2284,0xcbd9}).plainString();
    }
}
