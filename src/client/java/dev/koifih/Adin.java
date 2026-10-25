package dev.koifih;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Adin implements ModInitializer {
    public static final String MOD_ID = LT.L1;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x8379,0x85c8,0xb784,0x389d}, new char[]{0x8318,0x38f9,0x85a1,0xb7ea}).plainString();
    }
}
