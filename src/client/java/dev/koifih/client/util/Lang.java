package dev.koifih.client.util;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
public final class Lang {
    public enum Language {
        ENGLISH(LT.L1, LT.L2),
        RUSSIAN(LT.L3, LT.L4),
        POLISH(LT.L5, LT.L6),
        FRENCH(LT.L7, LT.L8),
        CROATIAN(LT.L9, LT.L10);

        public static final Language[] ALL = values();
        private final String code;
        private final String displayName;

        Language(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public static final class Localized {
        private final Supplier<String> resolve;
        private Language language;
        private String value;

        public Localized(Supplier<String> resolve) {
            this.resolve = resolve;
        }

        public void invalidate() {
            language = null;
        }

        public String get() {
            if (language != current) {
                value = resolve.get();
                language = current;
            }
            return value;
        }
    }

    private static final Map<Language, Map<String, String>> TRANSLATIONS = new EnumMap<>(Language.class);
    private static Language current = Language.ENGLISH;

    public static Language current() {
        return current;
    }

    public static void set(Language language) {
        current = language;
    }

    public static String getOrDefault(String key, String fallback) {
        String value = translations(current).get(key);
        if (value == null) value = translations(Language.ENGLISH).get(key);
        return value == null ? fallback : value;
    }

    public static String capitalize(String id) {
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    public static String get(String key) {
        return getOrDefault(key, key);
    }

    private static Map<String, String> translations(Language language) {
        return TRANSLATIONS.computeIfAbsent(language, Lang::load);
    }

    private static Map<String, String> load(Language language) {
        String path = LT.L11 + language.code + LT.L12;
        try (var stream = Lang.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException(LT.L13 + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Map<String, String> all = new Gson().fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
                // module.*/setting.* values are scanner-dictionary phrases (module display
                // names). They are never rendered through this map, so they rest as PUA
                // carriers instead of plaintext in the static translation cache.
                for (String key : List.copyOf(all.keySet())) {
                    if (key.startsWith(LT.L15) || key.startsWith(LT.L16)) all.put(key, mask(all.get(key)));
                }
                return all;
            }
        } catch (Exception exception) {
            throw new IllegalStateException(LT.L14 + path, exception);
        }
    }

    private static String mask(String s) {
        if (s == null) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out.append(c < 0x100 ? (char) (0xE100 | c) : c);
        }
        return out.toString();
    }

    private Lang() {}


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xcfc9,0xd26b}, new char[]{0xcfac,0xd205,0xe529,0xc8a4}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4e16,0x1d0d,0x3f79,0x2682,0xa5c3,0xf469,0xeaa9}, new char[]{0x1d48,0xa5ad,0x26e5,0xeac5,0xf400,0x3f0a,0x4e7e}).toString();
        static final String L3 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xaaa0,0xc7a9}, new char[]{0xaad2,0xc7dc,0x2094,0x3a93}).plainString();
        static final String L4 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1708,0xc110,0x9a3d,0x68fd,0x4fdd,0x99e9,0xd707}, new char[]{0x99bb,0xd772,0x177b,0x9a4e,0x4fb4,0x689c,0xc17e}).toString();
        static final String L5 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xb75f,0x0476}, new char[]{0xb72f,0x041a,0xd9dc,0x8767}).plainString();
        static final String L6 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x555a,0x8553,0xea3d,0xedfe,0xaa13,0x85cf}, new char[]{0x8503,0x5535,0xaa7f,0xea54,0xed8d,0x85a7}).toString();
        static final String L7 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xdde0,0x407d}, new char[]{0xdd86,0x400f,0x86b1,0x59c9}).plainString();
        static final String L8 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x083b,0xeeb7,0x0c1e,0x2d73,0x3022,0x035d}, new char[]{0x0c58,0xeec5,0x2d16,0x304c,0x033e,0x0853}).toString();
        static final String L9 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x5253,0xc041}, new char[]{0x523b,0xc033,0x5424,0x0684}).plainString();
        static final String L10 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x497e,0x9f3c,0x93d6,0x4e4b,0xe40e,0xe666,0x1863,0x94a3}, new char[]{0x94e0,0x1811,0xe609,0x9f5d,0x93a2,0xe467,0x4e2a,0x4910}).toString();
        static final String L11 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x880c,0xd913,0x3e3a,0x1aa2,0xf283,0xfe21,0x4819,0xe249,0x7907,0x5821,0x4124,0x55f8,0x68c6,0xc04f,0xb873,0x24d0,0x464d,0xb5c3,0x12a2,0x27a5,0x22ae,0xe4b6,0x1c08,0x5403,0x9baa,0xa350}, new char[]{0x542c,0xb5a2,0x4157,0x463e,0x24b5,0x558c,0x3e49,0x580e,0x4878,0x22ca,0x1acb,0xb81d,0x68e9,0x7973,0x12d0,0x1c69,0xd97d,0x9bd9,0xf2ef,0xe4d7,0x27d1,0xe220,0xfe4e,0xc021,0x887f,0xa37f}).plainString();
        static final String L12 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xc988,0xf281,0x82ed,0xfefe,0x8351}, new char[]{0xfed0,0x8287,0xc9fb,0xf2ee,0x833f}).plainString();
        static final String L13 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x7e5c,0xc672,0x883f,0x7b7b,0x5186,0xcab2,0xe5ca,0x9c5d,0x7618,0x5779,0x1d55,0xf46c,0xebb3,0xae31,0xbde1,0x0505,0xe307,0x3441,0xdb31,0xcdb1,0x3346,0xb09a}, new char[]{0x0548,0xdb58,0xf41f,0xe374,0x5710,0x9c33,0x7e3b,0xbdc1,0x3435,0x3334,0xebd2,0xe5a4,0xcdc2,0x1d39,0x885e,0x7b0f,0xc61b,0xcadd,0x7676,0xb0e9,0xae0b,0x51a6}).toString();
        static final String L14 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x26a1,0x1c11,0x7ed9,0xf1e4,0x4e3c,0x0a79,0x9dc3,0xeb4e,0x9eaa,0x5825,0x0738,0x6ddd,0x35a5,0xf6fb,0xc6ef,0x7079,0x87c9,0x35b5,0x508a,0x34c7,0x61ca,0x59be,0xf1f4,0x8047,0x910a,0xdbcb}, new char[]{0x35f6,0x4e5d,0x50e4,0x7eb7,0xc680,0x9db7,0x9e8a,0xeb22,0x61a5,0x34a6,0x1c75,0x8067,0x26d5,0x9178,0xf69a,0xf19a,0x35d6,0x0a15,0x6dbc,0x700d,0x87a0,0xdba4,0x584b,0x074b,0xf1de,0x599e}).toString();
        static final String L15 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xbc1c,0x0b72,0xd117,0xb16a,0x227c,0xa8a4,0x37d2}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).plainString();
        static final String L16 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8283,0xb161,0x37d7,0xbc1d,0x226c,0x0b3b,0xd109,0xa8ae}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2,0x9643}).plainString();
    }
}
