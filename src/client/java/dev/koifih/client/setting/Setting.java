package dev.koifih.client.setting;
import com.google.gson.JsonElement;
import dev.koifih.client.util.Lang;
import java.util.function.BooleanSupplier;
public abstract class Setting<T> {
    private final String id;
    private final T defaultValue;
    private String moduleId = "";
    private BooleanSupplier visible = () -> true;
    private boolean described;
    private final Lang.Localized name = new Lang.Localized(() -> Lang.getOrDefault(LT.L1 + moduleId + LT.L2 + id(),
            Lang.getOrDefault(LT.L1 + id(), Lang.capitalize(id()))));
    private final Lang.Localized description = new Lang.Localized(() -> Lang.getOrDefault(LT.L1 + moduleId + LT.L2 + id() + LT.L3, ""));
    protected T value;

    protected Setting(String id, T defaultValue) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public void attach(String moduleId) {
        this.moduleId = moduleId;
        name.invalidate();
        description.invalidate();
    }

    public void visibleWhen(BooleanSupplier condition) {
        visible = condition;
    }

    public boolean isVisible() {
        return visible.getAsBoolean();
    }

    public void describe() {
        described = true;
    }

    public boolean described() {
        return described;
    }

    public String description() {
        return description.get();
    }

    public String name() {
        return name.get();
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public void reset() {
        value = defaultValue;
    }

    public abstract JsonElement save();

    public abstract void load(JsonElement json);

    public final String id() { return id; }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x2b5a,0x95c0,0xa8ab,0xf37d,0x0bd6,0x96ef,0x2175,0x28cf}, new char[]{0x95b3,0xa8ce,0xf309,0x2101,0x9686,0x28a1,0x0bb1,0x2b74}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x6f75}, new char[]{0x6f5b,0x43b6,0xd7e1,0x9888}).plainString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x71f8,0x751b,0xfc03,0xa9ea,0xe59f}, new char[]{0xfc2d,0xe5f7,0x719d,0x7577,0xa99a}).plainString();
    }
}
