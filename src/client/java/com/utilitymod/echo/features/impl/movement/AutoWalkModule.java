package com.utilitymod.echo.features.impl.movement;

import com.utilitymod.echo.event.EventSubscribe;
import com.utilitymod.echo.event.impl.EventMovementInput;
import com.utilitymod.echo.features.Category;
import com.utilitymod.echo.features.Feature;
import com.utilitymod.echo.features.FeatureInfo;
import com.utilitymod.echo.features.settings.impl.BoolSetting;
import com.utilitymod.echo.utils.strings.Concat;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class AutoWalkModule extends Feature {

    public AutoWalkModule() {
        super(new FeatureInfo(
            Concat.of(new com.utilitymod.echo.utils.EncryptedString(new char[]{0x0b3d,0x1c99,0x82c1,0x379e,0xbc06,0xa8be,0xb158,0x226c,0xd13b}, new char[]{0xd17a,0xa8cb,0x2218,0xbc69,0x37be,0xb10f,0x0b5c,0x82ad,0x1cf2}).toString()),
            Concat.of(LT.L1),
            Category.MOVEMENT,
            false
        ));
    }

    private final BoolSetting autoWalk = new BoolSetting(Concat.of(LT.L2), false);
//    private final BoolSetting autoFly = new BoolSetting(Concat.of("Auto fly"), false);

    @EventSubscribe
    public void onMovement(EventMovementInput e) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isDead()) return;
//        if (com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return;
        if (autoWalk.getValue()) e.forward = true; e.forceMovement = true;

//        if (autoFly.getValue() && shouldStartElytra()) {
//            e.jump = true;
//        }
    }
    
    private boolean shouldStartElytra() {
        if (mc.player == null || mc.world == null) return false;
        if (com.utilitymod.echo.api.MinecraftCompat.getScreen() != null) return false;
        if (mc.player.isDead()) return false;
        
        if (mc.player.isGliding()) return false;
        if (mc.player.getAbilities().flying) return false;
        if (mc.player.hasVehicle()) return false;
        if (mc.player.isClimbing()) return false;
        
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isOf(Items.ELYTRA)) return false;
        if (chest.getDamage() >= chest.getMaxDamage() - 1) return false;
        
        if (mc.player.isOnGround()) return false;
        if (mc.player.isTouchingWater()) return false;
        if (mc.player.getVelocity().y >= 0.0) return false;
        
        return true;
    }


    // cloak literals (cloak=PUA carrier / plain=functional decode)
    private static final class LT {
        static final String L1 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0xd5aa,0x3898,0xc9a2,0x39a2,0x34f2,0x7e7a,0x0ca7,0x76c5,0x059f,0x0fd6,0x358e,0xf67c,0x369b,0xc3ac,0x097e,0xaebc,0x2096,0xdbd9,0xc6ce,0xef82,0xdc26,0x7e21,0x8d70,0x3444,0xa1b7,0x0e8f,0xd5a4}, new char[]{0xefc3,0x35fb,0xd5de,0x0911,0x7e17,0x76a4,0x3486,0x05f6,0xdc45,0xa1d6,0xaed0,0x8d1c,0xc3d5,0x38b8,0xc9d5,0x0cc6,0x20fa,0x342f,0xf60f,0xdbf9,0xc6a8,0x0ee0,0x36e9,0xd5d3,0x0fb7,0x7e53,0x39c6}).plainString();
        static final String L2 = new com.utilitymod.echo.utils.EncryptedString(new char[]{0x1414,0x4696,0xb358,0x37c7,0x255a,0x722d,0x3bba,0x01e9,0x054f}, new char[]{0x251b,0x7258,0x3bce,0x0520,0x01c9,0xb32f,0x37a6,0x1478,0x46fd}).plainString();
    }
}
