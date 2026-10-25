
package com.utilitymod.echo.features.settings;

import com.utilitymod.echo.Echo;
import com.utilitymod.echo.utils.strings.I18nText;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Predicate;

@Getter
public class Setting {
    private final CharSequence nameSequence;
    @Setter
    private Predicate<Object> dependency;
    @Setter
    private Runnable changeListener;
    private CharSequence description = "";

    public Setting(CharSequence nameSequence) {
        this.nameSequence = I18nText.of(nameSequence);
        this.dependency = null;
    }

    public Setting(CharSequence nameSequence, Predicate<Object> dependency) {
        this.nameSequence = I18nText.of(nameSequence);
        this.dependency = dependency;
    }

    public Setting(String name) {
        this.nameSequence = I18nText.of(name);
        this.dependency = null;
    }

    public String getName() {
        return nameSequence.toString();
    }

    public CharSequence getNameSequence() {
        return nameSequence;
    }

    public CharSequence getSuffixSequence() {
        return "";
    }

    public String getTypeId() {
        return "";
    }

    public boolean isVisible() {
        return dependency == null || dependency.test(null);
    }

    public Setting onChanged(Runnable listener) {
        this.changeListener = listener;
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T extends Setting> T describedBy(CharSequence description) {
        this.description = I18nText.of(description);
        return (T) this;
    }

    public void notifyChange() {
        if (changeListener != null) {
            changeListener.run();
        }
        if (Echo.featureConfig != null && !Echo.featureConfig.loading) {
            Echo.featureConfig.saveProfile(LT.L1);
        }
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x4860,0x31b0,0x171b,0x2bb6,0xb818,0x0064,0x9a85,0x53cb,0x73a6}, new char[]{0x31ef,0x2bd7,0x9af0,0xb86c,0x53a4,0x4813,0x177a,0x0012,0x73c3}).plainString();
    }
}
