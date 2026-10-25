package com.utilitymod.echo.api;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.SlimeEntity;

public final class SlimeCompat {
    private SlimeCompat() {}

    public static boolean isTinySlime(Entity entity) {
        return entity instanceof SlimeEntity slime && slime.isSmall();
    }
}
