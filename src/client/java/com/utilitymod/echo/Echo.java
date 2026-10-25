package com.utilitymod.echo;

import com.utilitymod.core.Module;
import com.utilitymod.core.ModuleManager;
import com.utilitymod.echo.render2.api.Draw2D;
import com.utilitymod.echo.render3.api.Draw3D;

/**
 * echo's Echo singleton, reduced to what ported code reads: the feature
 * lookup, friends and the projected draw targets.
 */
public final class Echo {
    public static final boolean isDestroyed = false;
    public static final FeatureManager featureManager = new FeatureManager();
    public static final FriendManager friendManager = new FriendManager();
    public static final Draw2D draw2D = new Draw2D();
    public static final Draw3D draw3d = new Draw3D();
    public static final FeatureConfig featureConfig = new FeatureConfig();

    /** echo config autosave -> this mod's config (saved when the wing closes). */
    public static final class FeatureConfig {
        public boolean loading;

        public void saveProfile(String name) {
            com.utilitymod.gui.GuiConfig.markDirty();
        }
    }

    private Echo() {}

    public static final class FeatureManager {
        public <T> T getFeatureByClass(Class<T> type) {
            for (Module m : ModuleManager.all()) if (type.isInstance(m)) return type.cast(m);
            return null;
        }
    }

    /** echo friends: name list (Friends tab of the dropdown GUI, saved by GuiConfig). */
    public static final class FriendManager {
        private final java.util.Set<String> friends = java.util.concurrent.ConcurrentHashMap.newKeySet();

        public boolean isFriend(String name) { return name != null && friends.contains(name.toLowerCase()); }

        public void add(String name) { friends.add(name.toLowerCase()); }

        public void remove(String name) { friends.remove(name.toLowerCase()); }

        public java.util.Set<String> all() { return friends; }
    }
}
