package com.utilitymod.echo.utils;

import com.utilitymod.echo.api.ChatCompat;
import net.minecraft.text.Text;

public class ChatUtils implements Imports {
    public static void chat(String text) {
        ChatCompat.addMessage(Text.literal(text));
    }

    public static void chat(CharSequence text) {
        ChatCompat.addMessage(Text.literal(text.toString()));
    }
}
