"""Deterministic fix-ups applied after tools/port.py on the echo tree.

Every rule here was checked against echo's source / the 1.21.11 bytecode:
  * .is(Items.X)/.is(Blocks.X) on ItemStack/BlockState -> isOf; .is(tag) -> isIn;
    attribute modifier .is(id) -> idMatches; every other .is(...) is echo's ModeSetting.is
  * distanceToSqr -> squaredDistanceTo, hit-result getDirection -> getSide
  * echo string helpers Concat/EncryptedString/I18nText keep .of(...)
  * mixin accessor casts -> com.utilitymod.echo.api.Accessors (reflection)
Re-runnable: each rule is a no-op once applied.

  python tools/postport.py
"""
import glob
import re

ROOT = "src/client/java/com/utilitymod/echo/"
M = r"/\*PORT\?[^*]*\*/"
A = "com.utilitymod.echo.api.Accessors"


def edit(path, old, new):
    s = open(ROOT + path, encoding="utf-8").read()
    if old in s:
        open(ROOT + path, "w", encoding="utf-8").write(s.replace(old, new))


def main():
    for f in glob.glob(ROOT + "**/*.java", recursive=True):
        s = open(f, encoding="utf-8").read()
        o = s
        s = re.sub(r"\.getKey" + M + r"\(", ".getKey(", s)  # echo/Java getKey, never MC here
        s = re.sub(r"\.is" + M + r"\((?=\s*(Items|Blocks)\.)", ".isOf(", s)
        s = re.sub(r"\.is" + M + r"\((?=\s*Item\.BASE_ATTACK_SPEED_MODIFIER_ID)", ".idMatches(", s)
        s = re.sub(r"\.is" + M + r"\((?=\s*tag\b)", ".isIn(", s)
        s = re.sub(r"\.is" + M + r"\(", ".is(", s)
        s = re.sub(r"\.distanceToSqr" + M, ".squaredDistanceTo", s)
        s = re.sub(r"\.getDirection" + M, ".getSide", s)
        s = re.sub(r"\.push" + M, ".push", s)
        s = re.sub(r"\b(Concat|EncryptedString|I18nText)\.(?:of|from|fromLong|create)(?:" + M + r")?\(", r"\1.of(", s)
        s = s.replace("mc.hitResult/*PORT?crosshairTarget|hit*/", "mc.crosshairTarget")
        # receiver-dependent renames the tool cannot see (checked against Yarn 1.21.11)
        for a, b in (("mc.hitResult", "mc.crosshairTarget"), (".getGameTimeDeltaPartialTick(", ".getTickProgress("),
                     (".getMin(java.util.Comparator", ".min(java.util.Comparator"),
                     ("getMouseSensitivity().get()", "getMouseSensitivity().getValue()"),
                     (".calculateDefaultBoundingBox(", ".getBoxAt("), (".getDefaultInstance()", ".getDefaultStack()"),
                     ("RotationHandler.getRotationClient(", "RotationHandler.getRotationVector("),
                     ("entity.position()", "entity.getEntityPos()"), ("RespawnAnchorBlock.CHARGE)", "RespawnAnchorBlock.CHARGES)"),
                     (".getInventory().getItem(", ".getInventory().getStack("), ("mc.level", "mc.world"),
                     ("firework.getItem()", "firework.getStack()")):
            s = s.replace(a, b)
        s = s.replace("ProjectileUtil.getEntityHitResult/*PORT?getEntityCollision|raycast*/(", "ProjectileUtil.raycast(")
        # accessor casts
        s = re.sub(r"\(\(MinecraftAccessor\)\s*mc\)\.invokeStartAttack\(\)", A + ".invokeStartAttack()", s)
        s = re.sub(r"\(\(MinecraftAccessor\)\s*mc\)\.invokeStartUseItem\(\)", A + ".invokeStartUseItem()", s)
        s = re.sub(r"\(\(KeyMappingAccessor\)\s*([\w.]+)\)\.getKey\(\)", A + r".getKey(\1)", s)
        s = re.sub(r"\(\(LivingEntityAccessor\)\s*([\w.]+)\)\.setNoJumpDelay\(", A + r".setNoJumpDelay(\1, ", s)
        s = re.sub(r"\(\(LivingEntityAccessor\)\s*([\w.]+)\)\.(getAttackStrengthTicker|getItemSwapTicker)\(\)", A + r".\2(\1)", s)
        s = re.sub(r"\(\(FireworkRocketAccessor\)\s*(\w+)\)\.echo\$getLife\(\)", A + r".fireworkLife(\1)", s)
        s = re.sub(r"\(\(FireworkRocketAccessor\)\s*(\w+)\)\.echo\$getLifetime\(\)", A + r".fireworkLifetime(\1)", s)
        s = re.sub(r"\(\(EntityAccessor\)\s*(\w+)\)\.echo\$setSharedFlag\(", A + r".setSharedFlag(\1, ", s)
        s = re.sub(r"\(\(AbstractArrowAccessor\)\s*(\w+)\)\.echo\$isInGround\(\)", A + r".arrowInGround(\1)", s)
        s = re.sub(r"^import com\.utilitymod\.echo\.mixin\.[\w.]+;\n", "", s, flags=re.M)
        # MC calls whose Mojang names are generic (kept by port.py's NEVER_RENAME), by receiver shape
        for a, b in ((".is(Items.", ".isOf(Items."), (".is(Blocks.", ".isOf(Blocks."),
                     (".getWindow().handle()", ".getWindow().getHandle()"), ("mc.getConnection()", "mc.getNetworkHandler()"),
                     ("player.connection", "player.networkHandler"), (".getMainHandItem().", ".getMainHandStack()."),
                     ("player.getMainHandItem()", "player.getMainHandStack()"),
                     (".getPlayersTeam(", ".getScoreHolderTeam("), (".getMainCamera()", ".getCamera()"),
                     (".sanitizeValue(", ".clamp("), ("target.position()", "target.getEntityPos()"),
                     ("player.onGround()", "player.isOnGround()"), (".getDescriptionId()", ".getTranslationKey()"),
                     (".getAllEffects()", ".getEffects()"), (".forEachModifier(", ".applyAttributeModifiers("),
                     ("teleportPacket.id()", "teleportPacket.entityId()"), ("inventory.getItem(", "inventory.getStack("),
                     ("modifier.is(", "modifier.idMatches("), ("Shape.max(", "Shape.getMax("),
                     ("getRegistryManager().get(", "getRegistryManager().getOptionalEntry("),
                     ("registry.get(Enchantments.", "registry.getOptionalEntry(Enchantments.")):
            s = s.replace(a, b)
        for a, b in ((".getCamera().getEntityPos()", ".getCamera().getCameraPos()"), ("BlockPos.ZERO", "BlockPos.ORIGIN"),
                     ("feet.offset(", "feet.add("), ("getDefaultKey().getValue()", "getDefaultKey().getCode()"),
                     ("stack.is(tag)", "stack.isIn(tag)"), ("input.getMoveVector()", "input.getMovementInput()"),
                     ("PlayerInput.EMPTY", "PlayerInput.DEFAULT"), (".getBlockPos().atY(", ".getBlockPos().withY("),
                     ("facing.dot(", "facing.dotProduct(")):
            s = s.replace(a, b)
        s = re.sub(r"\.getBoundingBox\(\)\.move\(", ".getBoundingBox().offset(", s)
        s = re.sub(r"\b(\w+)\.position\(\)", r"\1.getEntityPos()", s)  # Entity.position() (Yarn getEntityPos)
        s = re.sub(r"\.getValue\(((?:\w+Block|Properties|BlockStateProperties)\.[A-Z_]+)\)", r".get(\1)", s)
        s = re.sub(r"(\.(?:toggleCrouch|toggleSprint|getSneakToggled|getSprintToggled|getMouseSensitivity)\(\))\.get\(\)", r"\1.getValue()", s)
        # InputUtil.Key (Mojang InputConstants.Key): getValue -> getCode, getType -> getCategory
        keys = set(re.findall(r"InputUtil\.Key\s+(\w+)\s*[=;,)]", s))
        for k in keys:
            s = re.sub(r"\b" + k + r"\.getValue\(\)", k + ".getCode()", s)
            s = re.sub(r"\b" + k + r"\.getType\(\)", k + ".getCategory()", s)
        s = re.sub(r"(getKey\([^()]*\))\.getValue\(\)", r"\1.getCode()", s)
        s = re.sub(r"(getKey\([^()]*\))\.getType\(\)", r"\1.getCategory()", s)
        if s != o:
            open(f, "w", encoding="utf-8").write(s)

    edit("features/settings/impl/RegistryPickerSetting.java", "GroupState.KEYS_BY_ID", "GroupState.ALL")
    edit("features/settings/impl/RegistryPickerSetting.java", "key.identifier/*PORT?getValue|id*/()", "key.getValue()")
    # echo RegistryPickerSetting.EntryGroup.matches (name clash with KeyBinding.matchesKey)
    edit("features/impl/combat/MaceSwap.java", "ItemGroups.axes().matchesKey(item)", "ItemGroups.axes().matches(item)")
    edit("features/impl/combat/MaceSwap.java",
         "            KeyMappingAccessor accessor = (KeyMappingAccessor) mc.options.useKey;\n"
         "            InputUtil.Key key = accessor.getKey();",
         "            InputUtil.Key key = " + A + ".getKey(mc.options.useKey);")
    edit("features/impl/movement/combohelpers/ComboMode.java",
         "        ClientInputAccessor input = (ClientInputAccessor) mc.player.input;\n"
         "        PlayerInput keys = input.getKeyPresses();",
         "        net.minecraft.client.input.Input input = mc.player.input;\n"
         "        PlayerInput keys = input.playerInput;")
    edit("features/impl/movement/combohelpers/ComboMode.java",
         "        input.setKeyPresses(new PlayerInput(", "        input.playerInput = (new PlayerInput(")
    edit("features/impl/movement/combohelpers/ComboMode.java",
         "        input.setMoveVector(new Vec2f(movement.sidewaysAmount, movement.forwardAmount));",
         "        " + A + ".setMovementVector(input, new Vec2f(movement.sidewaysAmount, movement.forwardAmount));")
    # echo's accessor-typed getters return null under injection anyway; callers use the raw getters
    for kind, raw in (("MouseHandlerAccessor getMouseHandler", "Mouse"), ("KeyboardHandlerAccessor getKeyboardHandler", "Keyboard")):
        path = ROOT + "handlers/InputHandler.java"
        src = open(path, encoding="utf-8").read()
        i = src.find("    public static " + kind + "() {")
        if i >= 0:
            j = src.index("    }\n", i) + 6
            open(path, "w", encoding="utf-8").write(src[:i] + src[j:].lstrip("\n"))
    left = [f for f in glob.glob(ROOT + "**/*.java", recursive=True) if "PORT?" in open(f, encoding="utf-8").read()]
    print("files with unresolved markers:", [l.replace("\\", "/").split("echo/")[-1] for l in left])


if __name__ == "__main__":
    main()


def player_esp_shader():
    """Adds a 'Shader' mode to echo's PlayerESP: Icons Pack post shaders (ShaderEsp) over the
    vanilla entity-outline silhouette, styles Outline / Rainbow / Aura / Chams."""
    path = ROOT + "features/impl/render/PlayerESP.java"
    src = open(path, encoding="utf-8").read()
    if "ShaderEsp" in src:
        return
    src = src.replace(
        'private final MultiModeSetting enabledModes = new MultiModeSetting(Concat.of("Modes"), Concat.of("Box"), Concat.of("2D"));',
        'private final MultiModeSetting enabledModes = new MultiModeSetting(Concat.of("Modes"), Concat.of("Box"), Concat.of("2D"), Concat.of("Shader"));\n'
        '    private final ModeSetting shaderStyle = new ModeSetting(Concat.of("Shader Style"), Concat.of("Outline"), o -> enabledModes.isEnabled(Concat.of("Shader")),\n'
        '            Concat.of("Outline"), Concat.of("Rainbow"), Concat.of("Aura"), Concat.of("Chams"), Concat.of("Glow"), Concat.of("Liquid Glass"), Concat.of("Chroma"), Concat.of("Pulse"), Concat.of("Hologram"), Concat.of("Starfield"));\n'
        '    private final MultiModeSetting shaderTargets = new MultiModeSetting(Concat.of("Shader Targets"), o -> enabledModes.isEnabled(Concat.of("Shader")),\n'
        '            Concat.of("Players"), Concat.of("Mobs"), Concat.of("Items"));\n'
        '    private final FloatSetting shaderRadius = new FloatSetting(Concat.of("Shader Radius"), 10f, 1f, 30f, 0.5f, o -> enabledModes.isEnabled(Concat.of("Shader")));\n'
        '    private final FloatSetting shaderFill = new FloatSetting(Concat.of("Shader Fill"), 0.15f, 0f, 1f, 0.05f, o -> enabledModes.isEnabled(Concat.of("Shader")));\n'
        '    private final IntSetting shaderQuality = new IntSetting(Concat.of("Shader Quality"), 4, 1, 12, o -> enabledModes.isEnabled(Concat.of("Shader")));')
    src = src.replace(
        """                Category.RENDER
        ));
    }""",
        """                Category.RENDER
        ));
        // Shader mode (off by default): force the vanilla entity-outline silhouette (white) for
        // other players and run the selected Icons Pack shader over it (ShaderEsp).
        enabledModes.setOption(Concat.of("Shader"), false);
        java.util.function.BooleanSupplier shaderOn = () -> isEnabled() && enabledModes.isEnabled(Concat.of("Shader"));
        com.utilitymod.echo.render.ShaderEsp.activeWhen = shaderOn;
        shaderTargets.setOption(Concat.of("Mobs"), false);
        shaderTargets.setOption(Concat.of("Items"), false);
        java.util.function.Predicate<Object> shaded = e -> shaderOn.getAsBoolean() && (
                (e instanceof PlayerEntity p && p != mc.player && shaderTargets.isEnabled(Concat.of("Players")))
                || (e instanceof net.minecraft.entity.mob.MobEntity && shaderTargets.isEnabled(Concat.of("Mobs")))
                || (e instanceof net.minecraft.entity.ItemEntity && shaderTargets.isEnabled(Concat.of("Items"))));
        com.utilitymod.inject.Hooks.outlineOverride = shaded;
        com.utilitymod.inject.Hooks.outlineColor = e -> shaded.test(e) ? 0xFFFFFF : -1;
        com.utilitymod.echo.render.ShaderEsp.settings = () -> {
            com.utilitymod.echo.render.ShaderEsp.style = com.utilitymod.echo.render.ShaderEsp.Style.valueOf(
                    shaderStyle.getValue().toString().toUpperCase(java.util.Locale.ROOT).replace(' ', '_'));
            com.utilitymod.echo.render.ShaderEsp.color = colorSetting.getARGB() | 0xFF000000;
            com.utilitymod.echo.render.ShaderEsp.radius = shaderRadius.getValue();
            com.utilitymod.echo.render.ShaderEsp.fill = shaderFill.getValue();
            com.utilitymod.echo.render.ShaderEsp.quality = shaderQuality.getValue();
        };
    }""", 1)
    for imp in ("ModeSetting", "FloatSetting", "IntSetting"):
        line = "import com.utilitymod.echo.features.settings.impl." + imp + ";"
        if line not in src:
            src = src.replace("package com.utilitymod.echo.features.impl.render;",
                              "package com.utilitymod.echo.features.impl.render;\n\n" + line, 1)
    open(path, "w", encoding="utf-8").write(src)


player_esp_shader()
