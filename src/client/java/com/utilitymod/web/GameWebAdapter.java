package com.utilitymod.web;

import com.google.gson.*;
import com.utilitymod.core.ModuleManager;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.settings.Setting;
import com.utilitymod.echo.features.settings.impl.*;
import com.utilitymod.anubis.AnubisBridge;
import com.utilitymod.gui.GuiConfig;
import net.minecraft.util.Identifier;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.*;

/** Adapts the existing setting objects; no duplicate module state or packet logic. */
public final class GameWebAdapter {
    private GameWebAdapter() {}
    public static WebState create() {
        WebState state=new WebState(GuiConfig::save);
        for(var m:ModuleManager.all()) {
            String raw=m.getClass().getName();
            WebState.Entry e=new WebState.Entry(WebState.opaqueId(raw),m.getName(),m.getCategory().getLabel(),m.getDescription(),
                    raw.endsWith(LT.L1),m::isEnabled,m::setEnabled);
            e.fields.put(LT.L2,key(LT.L3,()->com.utilitymod.gui.ModuleBinds.get(m.getName()),v->com.utilitymod.gui.ModuleBinds.set(m.getName(),v)));
            if(m instanceof Feature f) {
                for(Setting s:f.getSettings()) {
                    String key=fieldId(f,s);
                    WebState.Field field=echo(s);
                    field.schema.addProperty(LT.L4,LT.L5);
                    if(field!=null) e.fields.put(key,field);
                    if(s instanceof InputSimulationSetting sim) {
                        var delay=echo(sim.getInputDelay()); delay.schema.addProperty(LT.L4,s.getNameSequence().toString());
                        e.fields.put(key+LT.L6,delay);
                        for(int i=0;i<sim.getInputSlots().length;i++) {
                            var slot=echo(sim.getInputSlots()[i]);slot.schema.addProperty(LT.L4,s.getNameSequence().toString());
                            e.fields.put(key+LT.L7+i,slot);
                        }
                    }
                }
            }
            state.add(e);
        }
        for(var group:AnubisBridge.GROUPS) for(var m:group.modules()) {
            WebState.Entry e=new WebState.Entry(LT.L8+m.id(),m.name(),group.title(),m.description(),
                    m.getClass().getSimpleName().equals(LT.L9),m::isEnabled,m::setEnabled);
            e.fields.put(LT.L2,key(LT.L3,()->com.utilitymod.gui.ModuleBinds.get(m.name()),v->com.utilitymod.gui.ModuleBinds.set(m.name(),v)));
            for(var s:m.settings()) {
                WebState.Field f=anubis(s); if(f!=null) e.fields.put(s.id(),f);
            }
            state.add(e);
        }
        addClientSettings(state);
        return state;
    }
    private static WebState.Field key(String name,IntSupplier read,IntConsumer write) {
        JsonObject s=WebState.bounds(WebState.schema(name,LT.L10),Integer.MIN_VALUE,348,1);
        s.addProperty(LT.L4,LT.L11);
        return field(s,()->new JsonPrimitive(read.getAsInt()),v->write.accept(v.getAsInt()),()->true);
    }
    private static WebState.Field bool(String name,BooleanSupplier read,Consumer<Boolean> write) {
        return field(WebState.schema(name,LT.L12),()->new JsonPrimitive(read.getAsBoolean()),v->write.accept(v.getAsBoolean()),()->true);
    }
    private static WebState.Field numeric(String name,double min,double max,double step,DoubleSupplier read,DoubleConsumer write) {
        var s=WebState.bounds(WebState.schema(name,LT.L13),min,max,step);
        if(step==1)s.addProperty(LT.L14,true);
        return field(s,()->new JsonPrimitive(read.getAsDouble()),v->write.accept(v.getAsDouble()),()->true);
    }
    private static <T extends Enum<T>> WebState.Field enumeration(String name,T[] values,Supplier<T> read,Consumer<T> write) {
        var s=WebState.schema(name,LT.L15);s.add(LT.L16,options(Arrays.stream(values).map(v->carrier(v.name())).toList()));
        return field(s,()->new JsonPrimitive(carrier(read.get().name())),v->{
            String incoming=plain(v.getAsString());
            for(T t:values) if(t.name().equals(incoming)) {write.accept(t);return;}
        },()->true);
    }
    private static void addClientSettings(WebState state) {
        var e=new WebState.Entry(LT.L17,LT.L18,LT.L19,LT.L20,false,()->true,v->{});
        e.toggleable=false;
        e.fields.put(LT.L21,enumeration(LT.L22,dev.koifih.client.ui.Theme.Mode.values(),dev.koifih.client.ui.Theme::mode,dev.koifih.client.ui.Theme::setMode));
        e.fields.put(LT.L23,field(WebState.schema(LT.L24,LT.L25),()->new JsonPrimitive(String.format(LT.L26,dev.koifih.client.ui.Theme.accentRgb()&0xffffff)),v->dev.koifih.client.ui.Theme.setAccent((int)Long.parseLong(v.getAsString(),16)&0xffffff),()->true));
        e.fields.put(LT.L27,enumeration(LT.L28,dev.koifih.client.ui.UiScale.values(),dev.koifih.client.ui.UiScale::current,dev.koifih.client.ui.UiScale::set));
        e.fields.put(LT.L29,enumeration(LT.L30,dev.koifih.client.util.Lang.Language.values(),dev.koifih.client.util.Lang::current,dev.koifih.client.util.Lang::set));
        e.fields.put(LT.L31,enumeration(LT.L32,dev.koifih.client.ui.Units.values(),dev.koifih.client.ui.Units::current,dev.koifih.client.ui.Units::set));
        e.fields.put(LT.L33,enumeration(LT.L34,dev.koifih.client.ui.Tooltips.values(),dev.koifih.client.ui.Tooltips::current,dev.koifih.client.ui.Tooltips::set));
        e.fields.put(LT.L35,enumeration(LT.L36,com.utilitymod.gui.FrameManager.Style.values(),()->com.utilitymod.gui.FrameManager.style,v->com.utilitymod.gui.FrameManager.style=v));
        e.fields.put(LT.L37,numeric(LT.L38,32,348,1,dev.koifih.client.input.Keybinds::openKeyCode,v->dev.koifih.client.input.Keybinds.setOpenKeyCode((int)v)));
        e.fields.put(LT.L39,bool(LT.L40,()->com.utilitymod.gui.FrameManager.animatedBg,v->com.utilitymod.gui.FrameManager.animatedBg=v));
        e.fields.put(LT.L41,numeric(LT.L42,0,10,1,()->net.minecraft.client.MinecraftClient.getInstance().options.getMenuBackgroundBlurriness().getValue(),v->{var options=net.minecraft.client.MinecraftClient.getInstance().options;options.getMenuBackgroundBlurriness().setValue((int)v);options.write();}));
        e.fields.put(LT.L43,bool(LT.L44,()->com.utilitymod.gui.UiSounds.enabled,v->com.utilitymod.gui.UiSounds.enabled=v));
        e.fields.put(LT.L45,bool(LT.L46,()->com.utilitymod.gui.ToggleSounds.enabled,v->com.utilitymod.gui.ToggleSounds.enabled=v));
        e.fields.put(LT.L47,bool(LT.L48,()->com.utilitymod.gui.ToggleSounds.soupPack,v->com.utilitymod.gui.ToggleSounds.soupPack=v));
        e.fields.put(LT.L49,bool(LT.L50,()->com.utilitymod.echo.hud.HudManager.liquidGlass,v->com.utilitymod.echo.hud.HudManager.liquidGlass=v));
        state.add(e);
        for(var hud:com.utilitymod.echo.hud.HudManager.elements()) {
            var h=new WebState.Entry(LT.L51+hud.id,hud.name,LT.L52,LT.L53,false,()->hud.visible,v->hud.visible=v);
            h.fields.put(LT2.L1,numeric(LT.L54,0,1,0.01,()->hud.x,v->hud.x=(float)v));
            h.fields.put(LT2.L2,numeric(LT.L55,0,1,0.01,()->hud.y,v->hud.y=(float)v));
            h.fields.put(LT.L27,numeric(LT.L56,0.25,4,0.05,()->hud.scale,v->hud.scale=(float)v));
            state.add(h);
        }
    }
    static String plain(CharSequence text) {
        if(text==null) return "";
        StringBuilder out=new StringBuilder();
        for(int i=0;i<text.length();i++) { char c=text.charAt(i); out.append(c>=0xe100&&c<=0xe1ff?(char)(c&255):c); }
        return out.toString();
    }
    private static String carrier(String text) {
        return new com.utilitymod.echo.utils.EncryptedString(text).toString();
    }
    private static String fieldId(Feature f,Setting s) {
        for(Class<?> c=f.getClass();c!=null&&c!=Feature.class;c=c.getSuperclass()) for(var field:c.getDeclaredFields()) {
            if(!Setting.class.isAssignableFrom(field.getType())) continue;
            try { field.setAccessible(true); if(field.get(Modifier.isStatic(field.getModifiers())?null:f)==s) return WebState.opaqueId(c.getName()+'#'+field.getName()); }
            catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
        }
        return WebState.opaqueId(LT.L57+f.getSettings().indexOf(s));
    }
    private static JsonArray strings(Collection<?> values) {
        JsonArray a=new JsonArray(); values.forEach(x->a.add(x.toString())); return a;
    }
    private static JsonArray options(Collection<? extends CharSequence> values) {
        JsonArray a=new JsonArray(); for(var value:values) { JsonObject o=new JsonObject(); o.addProperty(LT.L58,value.toString()); o.addProperty(LT.L59,value.toString()); a.add(o); } return a;
    }
    private static WebState.Field field(JsonObject schema,Supplier<JsonElement> read,Consumer<JsonElement> write,BooleanSupplier visible) {
        return new WebState.Field(schema,read,write,visible);
    }
    private static WebState.Field echo(Setting s) {
        String name=s.getNameSequence().toString();
        JsonObject d=WebState.schema(name,LT.L60);
        d.addProperty(LT.L61,s.getDescription().toString());
        BooleanSupplier visible=s::isVisible;
        if(s instanceof BoolSetting b) {
            d.addProperty(LT.L62,LT.L12); return field(d,()->new JsonPrimitive(b.getValue()),v->b.setValue(v.getAsBoolean()),visible);
        }
        if(s instanceof IntSetting n) {
            d.addProperty(LT.L62,LT.L13); d.addProperty(LT.L14,true); WebState.bounds(d,n.getMinValue(),n.getMaxValue(),1);
            return field(d,()->new JsonPrimitive(n.getValue()),v->n.setValue(v.getAsInt()),visible);
        }
        if(s instanceof FloatSetting n) {
            d.addProperty(LT.L62,LT.L13); WebState.bounds(d,n.getMinValue(),n.getMaxValue(),n.getIncrement());
            return field(d,()->new JsonPrimitive(n.getValue()),v->n.setValue(v.getAsFloat()),visible);
        }
        if(s instanceof RangeSetting r) {
            d.addProperty(LT.L62,LT.L63); WebState.bounds(d,r.getLowerBound(),r.getUpperBound(),r.getIncrement());
            return field(d,()-> { JsonArray a=new JsonArray(); a.add(r.getMinValue()); a.add(r.getMaxValue()); return a; },v->{
                r.setMinValue(v.getAsJsonArray().get(0).getAsFloat()); r.setMaxValue(v.getAsJsonArray().get(1).getAsFloat()); r.notifyChange();
            },visible);
        }
        if(s instanceof ModeSetting m) {
            d.addProperty(LT.L62,LT.L15); d.add(LT.L16,options(m.getModes()));
            return field(d,()->new JsonPrimitive(m.getValue().toString()),v->m.setValue(v.getAsString()),visible);
        }
        if(s instanceof MultiModeSetting m) {
            d.addProperty(LT.L62,LT.L64); d.add(LT.L16,options(m.getOptionsSequenceView().keySet()));
            return field(d,()-> { JsonArray a=new JsonArray(); m.getOptionsSequenceView().forEach((k,on)->{if(on)a.add(k.toString());}); return a; },v->{
                Set<String> selected=new HashSet<>(); v.getAsJsonArray().forEach(x->selected.add(x.getAsString()));
                for(var key:List.copyOf(m.getOptionsSequenceView().keySet())) m.setOption(key,selected.contains(key));
            },visible);
        }
        if(s instanceof KeybindSetting k) {
            d.addProperty(LT.L62,LT.L10); d.addProperty(LT.L14,true); WebState.bounds(d,Integer.MIN_VALUE,348,1);
            return field(d,()->new JsonPrimitive(k.getKey()),v->{k.setKey(v.getAsInt()); k.notifyChange();},visible);
        }
        if(s instanceof ColorSetting c) {
            d.addProperty(LT.L62,LT.L25); return field(d,()->new JsonPrimitive(String.format(LT2.L4,c.getARGB())),v->c.setFromARGB((int)Long.parseLong(v.getAsString(),16)),visible);
        }
        if(s instanceof HotbarSelectionSetting h) {
            d.addProperty(LT.L62,LT.L65); return field(d,()->{JsonArray a=new JsonArray();for(int i=0;i<9;i++)a.add(h.isSelected(i));return a;},v->{
                for(int i=0;i<9;i++)h.setSelected(i,v.getAsJsonArray().get(i).getAsBoolean());
            },visible);
        }
        if(s instanceof RegistryPickerSetting<?> r) {
            d.addProperty(LT.L62,LT.L66); return field(d,()->strings(r.getSelectedIds().stream().sorted().toList()),v->{
                List<Identifier> ids=new ArrayList<>();
                for(JsonElement x:v.getAsJsonArray()) {
                    Identifier id=Identifier.tryParse(x.getAsString());
                    if(id==null || !r.getRegistry().containsId(id)) throw new IllegalArgumentException(LT.L67);
                    ids.add(id);
                }
                r.setSelectedIds(ids);
            },visible);
        }
        if(s instanceof StringSetting t) {
            d.addProperty(LT.L62,t.isAllowMultiple()?LT.L68:LT.L69); d.addProperty(LT.L70,t.getMaxLength());
            return field(d,()->t.isAllowMultiple()?strings(t.getEntries()):new JsonPrimitive(t.getValue()),v->{
                if(t.isAllowMultiple()) {
                    List<String> next=new ArrayList<>();v.getAsJsonArray().forEach(x->next.add(x.getAsString().trim()));
                    for(String old:List.copyOf(t.getEntries()))if(!next.contains(old))t.removeEntry(old);
                    for(String item:next)if(!t.hasEntry(item))t.addEntry(item);
                } else t.setValue(v.getAsString());
                t.notifyChange();
            },visible);
        }
        return field(d,()->new JsonPrimitive(LT.L71),v->{throw new IllegalArgumentException(LT.L72);},visible);
    }
    private static WebState.Field anubis(com.utilitymod.anubis.module.setting.Setting s) {
        JsonObject d=WebState.schema(s.name(),LT.L60);
        Supplier<JsonElement> read=s::serialize;
        Consumer<JsonElement> write=s::deserialize;
        if(s instanceof com.utilitymod.anubis.module.setting.BooleanSetting) d.addProperty(LT.L62,LT.L12);
        else if(s instanceof com.utilitymod.anubis.module.setting.NumberSetting n) {
            d.addProperty(LT.L62,LT.L13); WebState.bounds(d,n.min(),n.max(),n.step()); write=v->n.set(v.getAsFloat());
        } else if(s instanceof com.utilitymod.anubis.module.setting.ModeSetting m) {
            d.addProperty(LT.L62,LT.L15); d.add(LT.L16,options(m.modes()));
        } else if(s instanceof com.utilitymod.anubis.module.setting.ColorSetting c) {
            d.addProperty(LT.L62,LT.L25); read=()->new JsonPrimitive(String.format(LT2.L4,c.argb())); write=v->c.setArgb((int)Long.parseLong(v.getAsString(),16));
        } else if(s instanceof com.utilitymod.anubis.module.setting.StringListSetting) {
            d.addProperty(LT.L62,LT.L68); d.addProperty(LT.L73,64); d.addProperty(LT.L70,255);
        } else if(s instanceof com.utilitymod.anubis.module.setting.StringSetting) {
            d.addProperty(LT.L62,LT.L69); d.addProperty(LT.L70,511);
        }
        if(s.isPrivate()) { d.addProperty(LT.L74,true); read=()->JsonNull.INSTANCE; }
        return field(d,read,write,s::isVisible);
    }


    // cloak literals - runtime PUA carriers (cloak) or functional plain (LT)
    private static final class LT {
            static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x227d, 0xd138, 0xb160, 0x0b29, 0xbc07, 0x37da, 0xa8a7, 0x82d9}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad}).plainString();
            static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0844,0xa140,0x5808,0x6469,0x60e9,0x91de,0x9b34,0x671e,0x5179,0xcfe7,0x02df,0x9da5,0xfa57,0xbf99}, new char[]{0x91bc,0xcf8e,0x5866,0x640d,0x6080,0xbff7,0xa127,0x086a,0x9b40,0x9dca,0xfa30,0x02b8,0x6772,0x511c}).plainString();
            static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x79a5,0xcd30,0xc9ab,0x4eb4,0x1aa9,0xb0c8,0x8231,0xf6ca,0x94cd,0xd7dd,0xdbcc,0x197d,0xa7ff,0xbcfc,0x2ce9,0xf80f,0x904f}, new char[]{0xa7b2,0x1ac6,0xcd54,0x4ec1,0x2c85,0x1918,0xf6ea,0x903b,0xdba3,0xd7ba,0xf868,0xb0a4,0xbc99,0x8211,0xc9c0,0x94a8,0x79dc}).plainString();
            static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x37d9,0xc449,0x156e,0xcf4c,0x58b8}, new char[]{0x1509,0xcf3e,0xc426,0x37ac,0x58c8}).plainString();
            static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x958e,0x2eee,0xf57c,0x3dad,0xdc7c,0xf58f,0x7ecf,0xe941,0xc88c,0xc90c,0x97ad,0x2eab,0xcf90,0x4cbd,0x9aa9}, new char[]{0xf5c2,0x97c2,0x4cd9,0x7eba,0xc960,0x3dc8,0xf55c,0xcfe3,0x2e8b,0xdc08,0x2edf,0xe928,0x9ac7,0x95e9,0xc8ff}).plainString();
            static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xeda4,0x2142,0x13e3,0xd5e9,0x912c,0xee3c}, new char[]{0x216c,0x9148,0xee59,0x138f,0xd588,0xeddd}).plainString();
            static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaa8a,0x811a,0x5e49,0xa1b9,0x0ab6,0x43a4}, new char[]{0x438a,0xaae3,0x0ad8,0xa1c9,0x5e3c,0x816e}).plainString();
            static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb370,0xa71d,0x91db,0x5432,0x7b7d,0xab86,0x2ea9}, new char[]{0x2ec8,0x91b5,0xa768,0x7b1f,0xb319,0xabf5,0x541c}).plainString();
            static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf3ed,0x6797,0xbf6a,0x5291,0x0c86,0xdc81,0x0af1,0xb9c3,0x5060,0xdd8d,0x6666,0x6a38,0x2d69,0x2280}, new char[]{0x6a68,0x5001,0xbf04,0x22e9,0xb9a0,0x6636,0x0ce7,0xdcf8,0x52dc,0x2d06,0xf389,0xddf8,0x67fb,0x0a94}).plainString();
            static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd370,0x64fc,0x0c81}, new char[]{0x0cea,0xd315,0x6485,0x7fbe}).plainString();
            static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x91bd,0xb3ed,0xe854,0xd51c,0x3565,0x3513,0xf255,0x6177}, new char[]{0xb3a6,0xd579,0x356a,0xe836,0x91d4,0xf23b,0x3501,0x6104}).plainString();
            static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0de8,0x2ab2,0x0e3c,0x59f0,0x889b,0xe21d,0xf470}, new char[]{0x88f9,0x0d87,0x599f,0xf41c,0xe278,0x2ad3,0x0e52}).plainString();
            static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa298,0xb278,0xcf29,0x0376,0x9046,0xd0bd}, new char[]{0x0318,0xb20d,0xcf44,0xd0df,0xa2fd,0x9034}).plainString();
            static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa568,0xe247,0xbc76,0x00f8,0x27ef,0x1fdc,0xb499}, new char[]{0xe22e,0x2781,0xbc02,0x1fb9,0xb4fe,0x009d,0xa51a}).plainString();
            static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7480,0xe7ce,0x3a5c,0x390c,0x9c62,0x26bf}, new char[]{0x3a2f,0x9c07,0x74ec,0x26da,0xe7ad,0x3978}).plainString();
            static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6056,0x4939,0x0cf6,0x5f55,0x3a9c,0x745d,0x40ac}, new char[]{0x6039,0x5f25,0x7429,0x3af5,0x0c99,0x40c2,0x494a}).plainString();
            static final String L17 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x910a,0xb1cb,0x82ae,0xdb05,0x6308,0x8690,0x5502,0x60d4,0x0548,0xcb3c,0xee30,0x0f74,0xc63f,0x90c4,0x6e20,0x5f31,0xc463}, new char[]{0x5f52,0x0f18,0x6361,0x5567,0x0526,0x90b0,0xc44d,0x6e41,0x82de,0xb1bb,0x86f5,0x916b,0xdb77,0xee51,0x60ba,0xcb5f,0xc65a}).plainString();
            static final String L18 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1d7b,0x1c63,0x81ea,0x4b20,0xc17b,0x77a3,0xf3fa,0xbd00,0x3a61,0x404c,0x07c7,0x693e,0x5faf,0x14ad,0x5bad,0x51a2,0x7b2a}, new char[]{0x51e1,0x7b46,0x77ca,0x4b45,0x1c0d,0x4038,0xbd20,0x818b,0x07b7,0x1d0b,0x695b,0x14cc,0xf388,0x3a00,0x5bc3,0x5fcc,0xc11e}).plainString();
            static final String L19 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8935,0x3d8b,0x1ceb,0xd718,0xfdcb,0x4c48}, new char[]{0x3dc8,0x1c87,0xd771,0x8950,0x4c26,0xfdbf}).plainString();
            static final String L20 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7bc8,0x54b0,0xb30a,0x68d8,0x6362,0x996f,0xceb7,0x49c7,0xf031,0xa34f,0x1899,0x54ca,0x3e63,0x6904,0x058b,0x53d1,0xc11c,0x2df6,0x0310,0x64d6,0x914b,0x4773,0x6957,0xf009,0xb2a9,0x0d99,0x5487,0x76c9,0x921d,0x00ad,0xbed3,0x1664,0xae73,0x1c1a,0x63d2,0xb855,0x909e,0x5fd7,0x02ed,0x0b87,0x9335,0xd9e2,0xee6c,0x1036,0x242f,0xddb1,0x5e7f,0x8d2d,0xccfc,0x8f5b,0x4857,0xbf28,0x31c2,0xb632,0xb7e3,0x421f,0x705e}, new char[]{0xcefe,0x4839,0x9318,0x2d91,0x927c,0x2442,0x0be2,0x90be,0x54ab,0x913b,0x6927,0x18fc,0x1057,0x00df,0x0df8,0x54de,0xb836,0xb786,0x76e5,0xb612,0xc175,0x63bc,0x49b3,0x1601,0x5e0d,0x53b7,0x6965,0x7bab,0xf054,0x31ee,0x707e,0xcc8f,0x64b9,0xee19,0xb364,0xf06d,0x05ab,0x990e,0xa321,0x8d49,0xbef3,0xddf9,0x4726,0x8f1f,0x0330,0x3e13,0xb2db,0xbf4d,0x6304,0xae16,0x1c68,0x0288,0xd98c,0x427c,0x68bd,0x54f4,0x5ff9}).plainString();
            static final String L21 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6ab9,0xa58e,0x78b5,0xb8bd,0xf62f}, new char[]{0x78c1,0xb8d5,0xa5eb,0xf642,0x6adc}).plainString();
            static final String L22 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4364,0x7672,0x5af6,0x9bd8,0x4784}, new char[]{0x5aa2,0x9bb0,0x4301,0x47e9,0x7617}).plainString();
            static final String L23 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5b94,0xb102,0x5443,0x1461,0x380c,0x5673}, new char[]{0x5422,0x386f,0xb161,0x5bf1,0x140f,0x5607}).plainString();
            static final String L24 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0159,0x784c,0x4fac,0xeebe,0x7af0,0x9aec,0xc977,0xf56d,0xbc64,0xe5aa,0x2bf6,0x690c}, new char[]{0x780d,0x696f,0xf50e,0xeedb,0x0137,0x9a98,0x2bd6,0xe5c9,0x7a9f,0xc91b,0x4fc3,0xbc16}).plainString();
            static final String L25 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2158,0x2072,0xf08b,0x49d4,0x4f22}, new char[]{0x213b,0xf0e4,0x4f4e,0x49bb,0x2000}).plainString();
            static final String L26 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x83c1,0x2702,0xf939,0xcf0b,0x8d99,0xe78e}, new char[]{0x2764,0xcf6d,0xf91c,0xe7be,0x8daf,0x83b9}).plainString();
            static final String L27 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x99d1,0xb150,0x8389,0x13df,0xf446}, new char[]{0x83fa,0xf425,0xb131,0x99bd,0x13ba}).plainString();
            static final String L28 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0f48,0x9c4b,0x5f33,0xdadd,0xc983,0x48f9,0xb126,0x357d,0x8ff3,0x375f,0x27bf,0x873a,0x57fa,0xbf33,0x6ac6}, new char[]{0x57b3,0xb148,0x9c3f,0x8f96,0x5f41,0xc9e5,0x875b,0x0f2b,0x373a,0x48d9,0xbf40,0xdabe,0x6aa7,0x27d3,0x3518}).plainString();
            static final String L29 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xde81,0x328e,0xc73e,0x1516,0xeb0b,0x7819,0xe4cf,0xac22}, new char[]{0xeb67,0xdee0,0xe4a1,0xc759,0x1563,0x7878,0xac45,0x32eb}).plainString();
            static final String L30 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa9fc,0xc350,0x8e1c,0x76a0,0xc863,0xa02f,0x83db,0x7f76}, new char[]{0x8e50,0xa99d,0x7f18,0x83bc,0x76d5,0xc331,0xa048,0xc806}).plainString();
            static final String L31 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2779,0x748e,0x3461,0x0258,0x24fe}, new char[]{0x74fb,0x2717,0x2497,0x3415,0x022b}).plainString();
            static final String L32 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd5da,0x47a6,0xe127,0xc481,0x655f,0x9cc1,0x2310,0xd51e,0xf061,0x9733,0x1a7a,0x8742,0xea00,0xc547}, new char[]{0x651b,0x1a13,0xd5a9,0xe153,0x8723,0xf00f,0xc4e2,0x47c3,0xea20,0x9746,0xd570,0x9ca8,0xc533,0x2363}).plainString();
            static final String L33 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0f18,0x3657,0xc671,0x6081,0xa525,0x9729,0xcf51,0x002a}, new char[]{0xcf25,0xa54a,0x9746,0x0f74,0x3623,0x0043,0xc601,0x60f2}).plainString();
            static final String L34 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4242,0x8cb0,0xa043,0xacb9,0xe093,0x71e0,0x00d6,0x0200}, new char[]{0x71b4,0x026f,0xa02c,0x00ba,0x4236,0xe0fa,0x8cc0,0xacca}).plainString();
            static final String L35 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa610,0x9897,0xb19d,0xb7a8,0x7d13}, new char[]{0xb1ee,0xb7dc,0x98ee,0xa67c,0x7d76}).plainString();
            static final String L36 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x11f5, 0xa8ae, 0xbc1a, 0x226a, 0x1c8b, 0x82d9, 0x0b2f, 0xd12c, 0xb12f, 0x962f, 0x37d1}, new char[]{0xd17a, 0xa8cb, 0x2218, 0xbc69, 0x37be, 0xb10f, 0x0b5c, 0x82ad, 0x1cf2, 0x9643, 0x1190}).plainString();
            static final String L37 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8d62,0x6c11,0x8e90,0xd2ef,0xc0bc,0xfb0e,0xec24}, new char[]{0x8eff,0x6c61,0xd28a,0xec4a,0xfb45,0x8d07,0xc0c5}).plainString();
            static final String L38 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcdce,0x5590,0xfb85,0xc7fc,0xabce,0x74c5,0x609e,0xcbd6,0x04b3,0x16f2,0x3832,0x4346,0xb0e9,0x1e3e,0x9c34,0xda82,0x9b95,0x1c94,0xf292}, new char[]{0xc7b3,0x1ce4,0xf2f7,0x74ab,0x9c14,0x9bd2,0x60cb,0x16bb,0xcbf6,0xfbee,0x4323,0xb090,0xcdee,0x381a,0x55d7,0xdace,0x04f5,0xab99,0x1e17}).plainString();
            static final String L39 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4564,0x9e86,0xc98f,0x3562,0xa219,0x50fb,0x8643,0x1375}, new char[]{0x509a,0x862d,0x131c,0x350f,0x4505,0x9ef2,0xc9ea,0xa27d}).plainString();
            static final String L40 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x064f,0xcd16,0x26cb,0xc2e8,0x8936,0xeaf4,0xd631,0x4fe8,0xec7d,0x5996,0x5a98,0x4576,0xa202,0x3310,0x709a,0x56de,0x53f2,0x0076,0x6844}, new char[]{0x0037,0x70f4,0xd658,0x337d,0x062e,0x5aec,0x6821,0x4512,0x4fc8,0x26a9,0x56bf,0xc28b,0xcd7d,0x5395,0xa270,0xea9b,0x59e3,0x8958,0xec19}).plainString();
            static final String L41 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbe97,0xe9a4,0x6fd5,0x127a}, new char[]{0xbef5,0x1216,0xe9d1,0x6fa7}).plainString();
            static final String L42 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb10b,0x5cf7,0xa729,0xc622,0xbf83,0xc0c0,0xfbfc,0xc05b,0xf778,0xa84b,0x834b,0xae27,0xd328,0x96cd,0x43ae,0x4a7a,0x7f7c,0x0c93,0xa876,0x55e3}, new char[]{0xb146,0xc03e,0x5c99,0xc0b5,0xf758,0x8329,0x4a1b,0xd34b,0xa820,0xfb9b,0xc650,0x96a2,0xbff6,0x0cfd,0x7f18,0xa856,0x5581,0xa745,0xae52,0x43dc}).plainString();
            static final String L43 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5ebc,0xec6d,0x19dd,0x92e3,0x74b3,0xecf8,0x16bf,0xa457}, new char[]{0xec8d,0x5ed5,0xec3e,0xa438,0x16ca,0x928d,0x74d7,0x19ae}).plainString();
            static final String L44 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6e43,0x4135,0x5a63,0xb21e,0xf6ab,0xde22,0xf3d8,0x38e6,0xbb1d}, new char[]{0xf6fe,0x38af,0x4115,0xf3ab,0xb271,0x5a16,0x6e2d,0xbb79,0xde51}).plainString();
            static final String L45 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x31ef,0xd40c,0x154a,0x4ed5,0x48ba,0xaf7a,0xcc50,0xb0ca,0x7c0e,0x517f,0xaf05,0x5e32}, new char[]{0xaf0e,0xd463,0x48dd,0x4eb2,0xcc3c,0x511a,0x7c5d,0xb0a5,0x5e47,0x3181,0x152e,0xaf76}).plainString();
            static final String L46 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa2dd,0x861e,0xcce5,0xfdd7,0x5854,0x93f9,0xa722,0xfbf9,0x7097,0x430b,0x9cd7,0x6b4a,0xe533}, new char[]{0xfbad,0x9cb8,0x6b2d,0x8679,0x70fb,0xa747,0x5874,0x4378,0x9396,0xa2a8,0xcc8b,0xe557,0xfda4}).plainString();
            static final String L47 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa9b3,0xadb5,0xf589,0x3f52,0xf3f8,0x2b57,0x6e3f,0xf84b,0xe1e1,0x7793}, new char[]{0xe192,0xf824,0xf38d,0xf5f9,0xade6,0xa9dc,0x6e4a,0x77fd,0x3f36,0x2b24}).plainString();
            static final String L48 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xef93,0xf932,0xf78e,0x9a8a,0xa66f,0x096b,0xa44b,0x91c3,0x2a74,0x113e,0x9129,0x9c3c,0x9d78,0x6511,0xed1c}, new char[]{0x9ad9,0x9146,0xa43e,0xefe3,0xa64f,0x2a07,0x0904,0x91b6,0x9d16,0x9c58,0xed3c,0xf7fe,0xf953,0x115d,0x657a}).plainString();
            static final String L49 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcbce,0x5a8d,0xc855,0x09fd,0xbd72,0x7649,0xac9e,0x3ae3,0xa5e5,0xc6d1,0x8c18}, new char[]{0x7625,0x3a8a,0xacef,0x8c6d,0xcba7,0xc6b5,0x5aca,0xa589,0xc834,0xbd01,0x098e}).plainString();
            static final String L50 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7a16,0x05e9,0x001b,0xdac8,0x386d,0x73fc,0x447b,0xdce6,0x57dd,0x53bf,0x66c0,0x7376}, new char[]{0xdcaa,0x731f,0xdab9,0x7389,0x57b4,0x7a72,0x05c9,0x53d8,0x0077,0x66a1,0x4408,0x381e}).plainString();
            static final String L51 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe998,0x3aa0,0x67a1,0x1b35}, new char[]{0x1b5d,0x67d4,0xe9fc,0x3a8e}).plainString();
            static final String L52 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8364,0x5e19,0xb742,0x438a,0x191e,0x0a2c,0xae67,0xaa38,0xde5b,0x36f5}, new char[]{0x5e51,0xb717,0xaa7c,0xae47,0xde37,0x8305,0x1967,0x43e5,0x3680,0x0a58}).plainString();
            static final String L53 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbc06,0x9eef,0x684c,0x4938,0x963d,0x5a11,0x5946,0x2476,0x6675,0x8c9a,0x6e3d,0x8a44,0x45f7,0xdb50,0xd718,0xbeef,0xd82b,0xeaf4,0x4501,0x509b,0x2c56,0x15d2,0x6c3c,0xf49a,0xce66,0x06ae,0xcef2,0xb6b4,0xc0be,0x0db8,0x2b26,0xff56,0x9cf1,0xd3bc,0xf354,0x42be,0xa3b6,0x590d,0xa3e0,0x2522,0xc376,0x48b8,0xa283,0x1711,0x63e0,0xc81a,0x6d5f,0x575c,0xbfe2,0x9269}, new char[]{0x4551,0x9652,0xa3c5,0x661c,0xf4ee,0x254b,0x4598,0xce9c,0xead8,0xd80b,0x5935,0x8a2d,0xdb2a,0xbf87,0x6c1c,0x15b3,0xce08,0x2412,0x2b06,0xd76e,0x42d7,0x6e4e,0x6825,0x495a,0x06c7,0x5730,0x1778,0x2c22,0x6d26,0xb694,0xff39,0xc0d8,0x50bb,0x0dcc,0x6388,0x9200,0x9c82,0x8cba,0xbea7,0xbc53,0x9eab,0xd39c,0xc87f,0x5a7d,0x48dd,0xc31b,0x5968,0xa38e,0xf320,0xa2ad}).plainString();
            static final String L54 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0afe,0xc0c7,0xea94,0xe897,0x61df,0xfb7e,0x6821,0x5218,0x3392,0x9570,0xaf9a,0xee37,0x9f06,0x7300,0x0a13,0x6fda,0x37c4,0xe52e,0x1ec7}, new char[]{0x33da,0xeafb,0x9f74,0x1eae,0xe554,0x684e,0xe8f9,0x526c,0x0a72,0xc0ab,0x61ff,0x6faa,0x736f,0x0a8d,0x9519,0xfb0a,0x37ad,0xee58,0xaff4}).plainString();
            static final String L55 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x30b4,0x5605,0x2d51,0xdc0c,0x000e,0xcbfa,0xa084,0xacdf,0xde2a,0xf89a,0xdb0f,0x0011,0x5afe,0x3c73,0x68fb,0xbb35,0xf762}, new char[]{0xf734,0xacba,0xcb88,0x5671,0xbb5c,0x6898,0xa0e5,0x5a92,0x2d71,0xdc7c,0x30db,0xdb7c,0x3c1a,0x0065,0xde43,0xf8f5,0x0060}).plainString();
            static final String L56 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc89a,0xc1d3,0x8336,0x4697,0x8850,0xf8ee,0x661c,0x59a4,0xf82e,0x3f19,0x780d,0x2916,0x5bcd}, new char[]{0x3f5c,0xc1bf,0x6679,0x883d,0xc8ff,0x59ca,0x8342,0xf8ce,0x46e4,0x2975,0xf84f,0x5ba1,0x7868}).plainString();
            static final String L57 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xf815,0x144f,0xf16f,0xea7e,0x4926,0x78cf,0xd6d2,0x3e8d}, new char[]{0x143c,0xf10a,0xea0a,0xd6a6,0xf87c,0x78a1,0x3eea,0x4908}).plainString();
            static final String L58 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1efe,0xff31,0xc388,0x064e,0xa21e}, new char[]{0x1e88,0xa27f,0x0622,0xc3fd,0xff54}).plainString();
            static final String L59 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7fa9,0x99c0,0x1a24,0xd02a,0xbbf0}, new char[]{0xd046,0xbb91,0x1a46,0x99a5,0x7fc5}).plainString();
            static final String L60 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x91d2,0x3b2c,0xedb6,0xd1a7,0x4876,0xa285,0x67d2,0xfebc}, new char[]{0xd1d5,0x91b7,0xfedd,0xedd2,0x67bd,0x4818,0xa2e9,0x3b55}).plainString();
            static final String L61 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4edf,0x20dd,0xc01c,0xfd31,0x57ac,0x58fa,0x5458,0x70cd,0x4e6c,0x7c4a,0xae93}, new char[]{0x20b9,0x543d,0x57df,0x4ebc,0x4e1e,0xc075,0x588a,0x7c3e,0x70a4,0xaefc,0xfd5f}).plainString();
            static final String L62 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xde7a,0x4020,0x8b69,0x8ed2}, new char[]{0xde0e,0x8eab,0x8b19,0x4045}).plainString();
            static final String L63 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6d30,0x1d29,0x6641,0x9cad,0xbf32}, new char[]{0x1d5b,0x9ccc,0x6d5e,0xbf55,0x6624}).plainString();
            static final String L64 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc7bf,0x528f,0x87f2,0xd28c,0x4034}, new char[]{0x52e2,0xd2f9,0xc7d3,0x4040,0x879b}).plainString();
            static final String L65 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xe8b7,0x6275,0xa91e,0xfd8c,0x67b2}, new char[]{0x67c1,0xe8db,0xa971,0x6201,0xfdff}).plainString();
            static final String L66 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb131,0xc8e6,0x937d,0xf6f6,0xac6a,0x8e3f,0xcff9,0x04b5}, new char[]{0xf684,0x04d0,0x8e58,0xac03,0xc895,0xb145,0x930f,0xcf80}).plainString();
            static final String L67 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xa6de,0x4ddc,0x4c38,0xf1eb,0xc5b2,0x4957,0x108f,0x7571,0xaea2,0x9f78,0xb1bb,0x7aaa,0xe54a,0x1ae8,0x1ed8,0x9b77,0x0bb0,0xcbe1,0xd00a,0x7c23,0x4b9a,0x7c62,0xfb78,0xc6d4,0xc813,0xbef2,0xd9e0}, new char[]{0x7aff,0x4c56,0xc5d9,0x4bf4,0xcb8e,0x10f8,0xe524,0x1ef8,0x9b05,0x4db9,0xc874,0xb1d2,0xc6a7,0xfb0c,0x1a9a,0xf192,0xae82,0xd989,0xbe96,0x9f1d,0x4939,0xd07e,0x7c0b,0x0bd6,0x7518,0xa6bb,0x7c51}).plainString();
            static final String L68 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2200,0xaf92,0x213c,0xc02c}, new char[]{0xaffe,0xc045,0x214f,0x2274}).plainString();
            static final String L69 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x92ab,0xf452,0x1445,0x4291}, new char[]{0x1431,0x42f4,0xf42a,0x92df}).plainString();
            static final String L70 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5f2a,0xc56f,0x65d5,0xdc5f,0x29fa,0x9784,0x068a,0xc0b3,0x0064}, new char[]{0xc0de,0x06eb,0x2982,0x6599,0x0001,0xc501,0x97e3,0x5f5e,0xdc37}).plainString();
            static final String L71 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1d08,0xbd5f,0x0966,0x89fc,0xd289,0x0996,0xfd7b,0x4574,0x40d4,0xd875,0xb9d6,0x162e,0x4db2,0x8a81,0x85f1,0xe83c,0x75a7}, new char[]{0x89b2,0xd2e6,0x8aa1,0x75c2,0x164a,0x1d61,0xfd0f,0x8590,0x40b6,0x090a,0xb9b3,0xd855,0xe84a,0xbd3e,0x09fa,0x4dc7,0x4511}).plainString();
            static final String L72 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd0e5,0xa4d5,0x6b7f,0xb0fa,0xde21,0xc60a,0x0e8e,0x2024,0xd93c,0x0ce4,0x409a,0xd03c,0x13a0,0xbfd5,0xabb2,0xe215,0xf60f}, new char[]{0xb0a8,0xa4b0,0xd95d,0x0c80,0xe238,0xd08a,0x0ee0,0x13cc,0xf676,0x2004,0xbfa6,0xc66f,0x40ee,0xabc6,0xde48,0x6b11,0xd05b}).plainString();
            static final String L73 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xed4a,0xf2d3,0x68dd,0x3c19,0x9015,0xd2ff,0x0689,0xf740}, new char[]{0x9078,0xf721,0x68a5,0x06c0,0xf2a7,0xed2f,0xd292,0x3c6a}).plainString();
            static final String L74 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xffd7,0xabe7,0x3656,0xd72b,0x935e,0xe71d,0x6e91}, new char[]{0x3626,0x932c,0xffbe,0xab91,0x6ef0,0xd75f,0xe778}).plainString();
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT2 {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xfcab}, new char[]{0xfcd3,0xd446,0x3f58,0xb884}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd223}, new char[]{0xd25a,0xa789,0xc001,0x8441}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x230f}, new char[]{0x2321,0x2564,0x7ae5,0x2b9d}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x76ef,0x1c93,0xd20f,0x7e2f}, new char[]{0xd22a,0x1ca3,0x7e17,0x7697}).plainString();
    }
}
