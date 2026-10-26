package com.utilitymod.echo.features.impl.render;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventRender3D;
import com.utilitymod.echo.event.impl.EventTick;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.features.settings.impl.ColorSetting;
import com.utilitymod.echo.features.settings.impl.FloatSetting;
import com.utilitymod.echo.features.settings.impl.ModeSetting;
import com.utilitymod.echo.features.settings.impl.StringSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.MathHelper;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Mob Finder: highlights mobs of a chosen type, optionally a chosen villager profession, and optionally with a
 * given name tag (e.g. a villager named "Kai Cenat"). Draws a box (+ tracer) on every match and says in chat
 * when a new one comes into range.
 */
public class MobFinder extends Feature {
    private static final String[] TYPES = {"Any", "Villager", "Wandering Trader", "Zombie Villager", "Iron Golem", "Cow",
            "Pig", "Sheep", "Chicken", "Horse", "Donkey", "Cat", "Wolf", "Fox", "Parrot", "Axolotl", "Allay", "Camel",
            "Llama", "Frog", "Turtle", "Bee", "Goat", "Panda", "Player"};
    private static final String[] PROFESSIONS = {"Any", "Unemployed", "Nitwit", "Armorer", "Butcher", "Cartographer",
            "Cleric", "Farmer", "Fisherman", "Fletcher", "Leatherworker", "Librarian", "Mason", "Shepherd", "Toolsmith",
            "Weaponsmith"};

    private final ModeSetting type = new ModeSetting("Mob", "Villager", TYPES);
    private final ModeSetting profession = new ModeSetting("Profession", "Any", p -> type.is("Villager"), PROFESSIONS);
    private final StringSetting name = new StringSetting("Name", "names, comma separated (e.g. Kai Cenat, Speed)");
    private final ModeSetting nameMode = new ModeSetting("Name Mode", "Contains", "Contains", "Exact", "Starts With", "Ends With", "Exclude");
    private final FloatSetting range = new FloatSetting("Range", 128f, 8f, 512f, 8f);
    private final BoolSetting tracers = new BoolSetting("Tracers", true);
    private final BoolSetting notify = new BoolSetting("Notify", true);
    private final ColorSetting color = new ColorSetting("Color", 120, 220, 255, 255);

    private final Set<Integer> announced = new HashSet<>();

    public MobFinder() {
        super(new FeatureInfo("Mob Finder", "Finds mobs by type, villager profession and name tag.", Category.RENDER));
        name.setMaxLength(256);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        announced.clear();
    }

    private static String typeId(String label) {
        return label.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private boolean matches(Entity e) {
        if (e == mc.player || !(e instanceof LivingEntity) || !e.isAlive()) return false;
        if (mc.player.squaredDistanceTo(e) > range.getValue() * range.getValue()) return false;
        String t = type.getValue().toString();
        if (!t.equals("Any")) {
            String id = Registries.ENTITY_TYPE.getId(e.getType()).getPath();
            if (!id.equals(typeId(t))) return false;
        }
        String prof = profession.getValue().toString();
        if (type.is("Villager") && !prof.equals("Any") && e instanceof VillagerEntity v) {
            String p = v.getVillagerData().profession().getKey().map(k -> k.getValue().getPath()).orElse("");
            if (!p.equalsIgnoreCase(prof)) return false;
        }
        String raw = name.getValue() == null ? "" : name.getValue();
        java.util.List<String> wants = new java.util.ArrayList<>();
        for (String part : raw.split("[,;|]")) if (!part.isBlank()) wants.add(part.trim().toLowerCase(Locale.ROOT));
        if (!wants.isEmpty()) {
            String has = e.getCustomName() != null || e instanceof net.minecraft.entity.player.PlayerEntity
                    ? e.getName().getString().toLowerCase(Locale.ROOT) : "";
            String mode = nameMode.getValue().toString();
            if (mode.equals("Exclude")) {
                for (String w : wants) if (!has.isEmpty() && has.contains(w)) return false;
            } else {
                if (has.isEmpty()) return false;   // only named mobs can match a name
                boolean any = false;
                for (String w : wants) {
                    any |= switch (mode) {
                        case "Exact" -> has.equals(w);
                        case "Starts With" -> has.startsWith(w);
                        case "Ends With" -> has.endsWith(w);
                        default -> has.contains(w);
                    };
                }
                if (!any) return false;
            }
        }
        return true;
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onTick(EventTick e) {
        if (isNull() || !notify.getValue()) return;
        Set<Integer> seen = new HashSet<>();
        for (Entity en : mc.world.getEntities()) {
            if (!matches(en)) continue;
            seen.add(en.getId());
            if (announced.add(en.getId())) {
                com.utilitymod.echo.utils.ChatUtils.chat(String.format("§b[Mob Finder]§r found §f%s§r at %d, %d, %d (%dm)",
                        en.getName().getString(), en.getBlockX(), en.getBlockY(), en.getBlockZ(), Math.round(mc.player.distanceTo(en))));
            }
        }
        announced.retainAll(seen);   // forget mobs that left range so they're announced again when they return
    }

    @EventSubscribe
    @SuppressWarnings("unused")
    private void onRender(EventRender3D e) {
        if (isNull()) return;
        var out = e.getDraw3D().getMinecraftTarget();
        float pt = e.getTickDelta();
        int col = color.getARGB();
        for (Entity en : mc.world.getEntities()) {
            if (!matches(en)) continue;
            double x = MathHelper.lerp(pt, en.lastRenderX, en.getX());
            double y = MathHelper.lerp(pt, en.lastRenderY, en.getY());
            double z = MathHelper.lerp(pt, en.lastRenderZ, en.getZ());
            double w = en.getWidth(), h = en.getHeight();
            out.box(x - w / 2, y, z - w / 2, w, h, w, col);
            if (tracers.getValue()) out.tracer(x, y + h / 2, z, col);
        }
    }
}
