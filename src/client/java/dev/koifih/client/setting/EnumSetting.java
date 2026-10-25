package dev.koifih.client.setting;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.koifih.client.render.AdinIcon;
import net.minecraft.util.Identifier;
import java.util.List;
public final class EnumSetting extends Setting<Integer> {
    private final String[] options;
    private List<AdinIcon> icons = List.of();
    private List<Identifier> images = List.of();

    public EnumSetting(String id, int defaultIndex, String... options) {
        super(id, defaultIndex);
        this.options = options;
    }

    public EnumSetting withArt(List<AdinIcon> icons, List<Identifier> images) {
        if (icons.size() != options.length || images.size() != options.length) {
            throw new IllegalArgumentException(LT.L1 + id() + LT.L2);
        }
        this.icons = List.copyOf(icons);
        this.images = List.copyOf(images);
        return this;
    }

    public String selected() {
        return options[value];
    }

    @Override
    public void set(Integer value) {
        super.set(Math.clamp(value, 0, options.length - 1));
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(options[value]);
    }

    @Override
    public void load(JsonElement json) {
        if (json == null || !json.isJsonPrimitive()) return;
        String name = json.getAsString();
        for (int i = 0; i < options.length; i++) if (options[i].equals(name)) set(i);
    }

    public final String[] options() { return options; }

    public List<AdinIcon> icons() { return icons; }

    public List<Identifier> images() { return images; }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0cb5,0x5d28,0x47a1,0x2e2f,0x6e17,0xa921,0x230c,0x6588,0x83bf,0x1475,0x6cb4,0xf27d,0x49a9,0x88bd,0x4c01,0x6164}, new char[]{0x65cd,0x0cc3,0x4c64,0x5d5a,0x6ccd,0x4989,0x141a,0xa951,0x88c9,0x83d6,0x2363,0x47cf,0x2e0f,0x6e78,0xf21b,0x6144}).toString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x07bb,0x0431,0xbc1a,0x665a,0xce84,0x2be0,0x3b74,0x17dd,0x03f0,0x9883,0x2520,0x402e,0x7d57,0x669e,0x8794,0xb12b,0x79e9,0x9db7,0x59b5,0x40b5,0x7376,0x258f,0x4f48,0x13a2,0xe546,0xa546,0x76c3}, new char[]{0x2500,0x7987,0xe523,0x9dd2,0x66fa,0x07c8,0x3b54,0x2b81,0x59db,0x25af,0xb142,0x98e0,0xa529,0x039e,0x4095,0x13c3,0x045f,0x17b9,0xbc3a,0xcee5,0x6634,0x87b4,0x4f21,0x76ae,0x7317,0x7d30,0x404b}).plainString();
    }
}
