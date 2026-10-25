#!/usr/bin/env python3
"""
rename_modules.py — source-tree scanner. Walks .java under a root and reports
any plaintext string literal that carries a scanner-dictionary term with
word-boundary framing. With --decrypt it also decodes ProtectedText.fromInts
arrays and checks the plaintext for the same terms (in case the encrypted
name itself contains a flagged phrase).

Exits non-zero when hits > 0 so CI can gate builds.

Usage:
    python tools/rename_modules.py                       # scan ./src
    python tools/rename_modules.py path/to/src
    python tools/rename_modules.py --decrypt src         # also inspect encrypted names
    python tools/rename_modules.py --dict extra.txt src  # add custom terms
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from typing import Iterable

# Must match HeapCloakDict.BUILT_IN exactly.
BUILT_IN = [
    # ------------------------------------------------------------------
    # original list
    # ------------------------------------------------------------------
    # combat
    "Kill Aura", "Crystal Aura", "Mace Aura", "Silent Mace",
    "Aura Guide", "Silent Aim", "Bow Guide", "SnapFire",
    "Anti-Weakness", "Fake Punch", "Damage Tick", "Only Crit",
    "Static HitBoxes", "Shield Disabler", "AntiInvis", "W-Tap",
    # crystal
    "Shard Hack", "Crystal Optimizer", "CwCrystal", "Double Anchor",
    "Anchor Exploder", "Auto Dtap", "Shard Rig",
    # totem
    "Auto Totem", "Totem Offhand", "Hover Totem", "Force Totem", "Auto Retotem",
    # movement
    "Fast Bridge", "Bridge Assist", "Fast Swim", "Fast Place",
    "No Break Delay", "No Jump Delay", "Elytra Swap", "Elytra Glide",
    "Jetpack", "Auto Sprint", "InventoryMove",
    # utility
    "Auto Clicker", "Auto Pot", "Auto Eat", "Auto XP", "Auto Armor",
    "Auto Tool", "Auto Mine", "Chest Stealer", "Shulker Dropper",
    "Shard Trade", "Cord Snapper", "Key Pearl", "Auto Tpa",
    "Bed Macro", "Auto Restock", "Replace Mod",
    # esp
    "Player ESP", "Storage ESP", "Entity ESP", "X-Ray",
    "Health Indicators", "Target HUD", "Netherite Finder",
    # evasion
    "Fake Lag", "Ping Spoof", "Pack Spoof", "Stray Bypass",
    "Anti SS Tool", "String Cleaner", "Purge", "USN Journal Cleaner",

    # ------------------------------------------------------------------
    # javaw.exe - client signatures (severity: high)
    # ------------------------------------------------------------------
    "auth.nuvow@re",
    "dev.zprestige", "dev/zprestige", "zprestige",
    "Prestige-refmap.jsonPK", "prestige.mixins.jsonPK",
    "dev/zprestige/prestige/api/mixin/K.class",
    "net/onyx/", "net.onyx.",
    "client-orchard.refmap.jsonPK",
    "dev/orchard/orchard/aaaaaea.classPK",
    "dev/orchard/orchard/api/aaaaacc.classPK",
    "dev/orchard/orchard/api/aaaaado.classPK",
    "dev/krypton/", "dev.krypton.", "skid/krypton",
    "dev/lvstrng/argon/", "lvstrng/argon", "dev/lvstrng/argon/PK",
    "dev/lvstrng/argon/Argon.classPK", "dev/lvstrng/argon/Main.classPK",
    "dev/lvstrng/argon/event/PK",
    "dev/boze/loader/KnotEntryPoint.class",
    "dev/boze/loader/KnotEntryPoint.classPK",
    "dev/boze/loader/KnotEntryPoint",
    "libboze.so", "libboze.dylib",
    "Undetectable Trident Fly. Requires Rain, RipTide",
    "com.elementars.eclient.command.commands.AntiVoidCommand",
    "me.earth.earthhack.impl.commands.MacroCommand",
    "fifthcolumn.n.modules.AntiAim",
    "com/ishland/vmp/ui/settings/SliderSetting",
    "dev/kir/cubeswithoutborders/client/mixin/EndCrystalItemMixin.class",
    "dev/kir/cubeswithoutborders/client/mixin/ClientPlayerEntityMixin.class",
    "com.wms.abstractclient.mixin.mixins.MixinRenderEnderCrystal",
    "client.src.main.template.rip.api.event.events.EntityHealthEvent",
    "me.mrhakan.agalarhack.Main",
    "1.8.9-Forge.src.main.me.kiras.aimwhere.modules.combat.TPAura",
    "dev.luminous.api.events.impl.EntityVelocityUpdateEvent",
    "allah.owns.me.allahware.guiscreen.hud.ArmorDurabilityWarner",
    "me.ritomg.ananta.module.modules.combat.AutoXp",
    "DTool.command.impl.ArmorStand",
    "com.obamabob.apeclient.clickgui.windows.AuraSettings",
    "com.apollo.api.event.events.TotemPopEvent",
    "ares-fabric-1.16.src.main.dev.tigr.ares.fabric.event.player.AntiHitboxEvent",
    "arsenic.injection.accessor.IMixinS12PacketEntityVelocity",
    "blebdapleb.arsenic.arsenic.event.events.EventReach",
    "artemis.mods.Combat.Antibot",
    "me.lyrica.commands.impl.MacroCommand",
    "1.19.4.src.main.net.caffeinemc.phosphor.api.util.CrystalUtils",
    "java.astralis.mixin.accessor.network.EntityVelocityUpdateS2CPacketAccessor",
    "international.astro.events.TotemPopEvent",
    "best.reich.ingros.commands.MacrosCommand",
    "me.zeroX150.atomic.feature.command.impl.ForEach",
    "me._3000IQPlay.atrium.event.events.TotemPopEvent",
    "bbcdevelopment.addon.bbcaddon.modules.combat.AntiBed",
    "com.backdoored.hacks.client.LogoutSpots",
    "mc.bape.module.blatant.AutoArmor",
    "com.gamesense.api.event.events.TotemPopEvent",
    "me.earth.phobos.event.events.TotemPopEvent",
    "dev.niuren.events.event.TotemEvent",
    "me.bigg.module.assist.NetWorkAssist",
    "bleach.hack.event.events.TotemPopEvent",
    "me.ghosttypes.reaper.modules.chat.ArmorAlert",
    "com.bladecore.mixin.accessor.network.AccessorSPacketEntityVelocity",
    "org.bleachhack.event.events.EventReach",
    "main.zov.blessed.crack.by.laqwrt.imagesBytes.CombatGuiImageBytes",
    "me.offeex.bloomware.mixin.particle.MixinTotemParticle",
    "bossware.event.events.TotemPopEvent",
    "com.lambda.mixin.accessor.network.AccessorSPacketEntityVelocity",
    "bubby.client.irc.pircBot.NickAlreadyInUseException",
    "com.syuto.bytes.module.impl.combat.AimAssist",
    "client.me.danny125.byteutilitymod.modules.combat.AntiBots",
    "me.cum.fusion.event.events.TotemPopEvent",
    "minecraft.de.chrissx.mods.building.FastBreak",
    "cn.trollaura.mixin.IMinecraft",
    "minecraft.CakeClient.modules.combat.AutoWtap",
    "me.gavin.calypso.events.ArmorVisibilityEvent",
    "fc.client.candy.event.events.TotemPopEvent",
    "me.hero.onepop.mixins.OnePopMixinLayerArmorBase",
    "me.catmi.command.commands.MacroCommand",
    "cats.gg.CatsWare.modules.Move.NoSlow",
    "me.chuck.chuckhack.events.block.GetBlockReachDistanceEvent",
    "me.crimp.claudius.event.events.TotemPopEvent",
    "me.noat.click.Clicker",
    "io.github.itzispyder.clickcrystals.ClickCrystals",
    "coffee.client.feature.command.impl.ForEach",
    "me.bebeli555.cookieclient.events.block.GetBlockReachDistanceEvent",
    "cope.cosmos.asm.mixins.accessor.ISPacketEntityVelocity",
    "io.ace.nordclient.command.commands.Spotify",
    "dev.fxcte.creepyware.event.events.TotemPopEvent",
    "nl.snoworange.cranberry.features.module.modules.combat.AutoAnvil",
    "net.ccbluex.liquidbounce.features.module.modules.combat.HitBox",
    "minegame159.minegame159.meteorclient.events.entity.player.JumpVelocityMultiplierEvent",
    "cn.origin.cube.event.events.player.TotemPopEvent",
    "dev.logging4j.currymod.mixin.ArmorFeatureRendererMixin",
    "net.spartanb312.cursa.hud.huds.CombatInfo",
    "com.curseclient.client.extension.webhook.Webhook",
    "sb.love.hud.components.ArmorWarningComponent",
    "net.cwhack.CrystalDataTracker",
    "8rK;Zq&e\\ >i?eY", "9+]0dtEHe1", "2ElA5U#T'e'",
    "net.dent.client.module.modules.combat.Revenge",
    "client.cmd.impl.MacroCommand",
    "me.devu.features.module.modules.combat.Criticals",
    "just.monika.IQ救不了主播你因为你反编译我端子.DokiDokiLegitClient",
    "me.pignol.swift.api.mixins.MixinLayerArmorBase",
    "ru.drughack.api.event.impl.EventFireworkVelocity",
    "hack/echo/gg", "hack/echo/fG",
    "fail.mercury.client.client.modules.combat.AntiChainPop",
    "com.elysian.client.module.modules.combat.AutoArmor",
    "Client.src.main.club.eridani.epsilon.client.mixin.mixins.accessor.entity.AccessorEntityTippedArrow",
    "studio.coni.epsilon.mixin.mixins.accessor.entity.AccessorEntityTippedArrow",
    "com.europa.api.manager.event.impl.world.EventCrystalAttack",
    "dev.evangelion.client.elements.ElementArmor",
    "gg.evlieye.commands.PotionCmd",
    "cf.warriorcrystal.evo.BitcoinMiner",
    "ru.levin.events.impl.move.EventNoSlow",
    "net.ccbluex.liquidbounce.injection.forge.mixins.block.MixinBlockAnvil",
    "io.github.pranavgade20.fabrichax.automationhax.Scaffold",
    "family_fun_pack.commands.BlockAtCommand",
    "org.fenci.fencingfplus2.events.player.TotemPopEvent",
    "com.olliem5.ferox.api.util.module.CrystalPosition",
    "com.cout970.fira.coremod.mixin.MixinRenderEnderCrystal",
    "build.sources.main.dev.zprestige.fire.event.impl.RenderCrystalEvent",
    "Firework-Client.src.main.com.firework.client.Features.CommandsSystem.Commands.WebhookCommand",
    "me.dev.fiveguys.module.combat.FastBow",
    "me.flawless.api.events.impl.TotemEvent",
    "me.brownzombie.floppa.event.events.EventRenderArmorLayer",
    "com.fogclient.entity.EntityPvPBot",
    "we.devs.forever.api.event.events.player.ReachEvent",
    "dev.fiki.forgehax.api.spam.SpamTrigger",
    "me.gopro336.zenith.event.TotemPopEvent",
    "me.zopac.freemanatee.event.KamiEvent",
    "com.evaan.frostburn.module.modules.combat.AutoAnchor",
    "Method.Client.module.Onscreen.Display.Armor",
    "me.maxmods.learnfabric.features.module.impl.Categories.Combat",
    "com.gamesense.api.event.events.ReachDistanceEvent",
    "me.kambing.gardenia.Agent",
    "com.garume.Garuff.event.events.event.TotemPopEvent",
    "me.thef1xer.gateclient.modules.combat.AutoTotem",
    "me.thef1xer.gateclient.modules.combat.TpReach",
    "me.gerald.hack.event.events.TotemPopEvent",
    "dev.lambdaurora.lambdynlights.event.events.EventReach",
    "i.gishreloaded.gishcode.hack.hacks.AimBot",
    "club.megyn.megyndotclub.modules.AnvilAuraModule",
    "com.client.glowclient.mods.AntiPotion",
    "me.zane.grassware.event.events.TotemPopEvent",
    "rosegold.gumtuneclient.config.pages.GemstoneMacroAOTVRoutes",
    "life.hanabi.event.events.impl.player.EventNoSlow",
    "me.luxtix.haybale.event.events.TotemPopEvent",
    "net.grilledham.hamhacks.mixin.MixinEntityVelocityUpdateS2CPacket",
    "me.hollow.sputnik.api.mixin.mixins.render.MixinLayerBipedArmor",
    "mod.hitlerhax.event.events.HtlrEventTotemPop",
    "org.hockey.hockeyware.client.events.player.TotemPopEvent",
    "net.halalaboos.huzuni.gui.widgets.ArmorStatusWidget",
    "cf.warriorcrystal.other.Colours",
    "club.auth.hydraware.module.modules.combat.Anchor",
    "com.thealtening.auth.AltService",
    "dev.hypnotic.mixin.ChunkAccessor",
    "org.vined.ikea.utils.DiscordWebhook",
    "build.sources.main.me.alpha432.oyvey.event.events.TotemPopEvent",
    "io.github.frontear.infinity.mixins.tweaks.autoclicker.MinecraftMixin",
    "com.jhlabs.image.KaleidoscopeFilter",
    "net.invincibilityhack.mixin.HealthUpdateS2CPacketMixin",
    "sources.codes.som.anthony.koffee.BlockAssembly",
    "me.dustin.jex.event.misc.EventEntityHitbox",
    "botnet.auth.jigokusense.module.modules.combat.AutoArmor",
    "dev.dov.jobless.client.managers.spotify.Song",
    "me.ijese.jorgitohack.event.events.TotemPopEvent",
    "clientname.extras.WebhookSender",
    "me.zeroeightsix.kami.feature.module.Module",
    "ssl.wastaken.kamipp.DiscordPresence",
    "tech.mmmax.kami.api.binds.IBindable",
    "org.kamiblue.client.mixin.MixinLoaderForge",
    "cypphi.kana.EventHandler",
    "dev.slmpc.kappaclient.asm.mixin.ClientConnectionMixin",
    "cn.origin.cube.module.modules.combat.KillAura",
    "minecraft.kevin.module.modules.combat.AutoArmor",
    "com.tangykiwi.kiwiclient.gui.hudeditor.components.ArmorComponent",
    "com.thealtening.api.TheAltening",
    "build.sources.main.me.darki.konas.command.commands.MacroCommand",
    "client.dev.mlml.korppu.module.modules.AntiAim",
    "me.windyteam.kura.command.commands.web.YoutubeDownload",
    "dev.dyzjct.kura.mixin.block.MixinBlockWeb",
    "net.ccbluex.liquidbounce.features.module.modules.combat.AutoArmor",
    "com.lambda.mixin.render.ArmorFeatureRendererMixin",
    "net.ccbluex.liquidbounce.features.module.modules.player.AntiVoid",
    "server.src.main.the.kis.devs.server.websockets.AbstractWebSocket",
    "com.leave.old.Utils.NoSlowDownUtil",
    "me.dev.legacy.api.event.events.misc.TotemPopEvent",
    "me.accessmodifier364.leuxbackdoor.client.event.events.EventRenderArmorOverlay",
    "net.ccbluex.liquidbounce.injection.mixins.lithium.MixinChunkAwareBlockCollisionSweeperBlockPos",
    "net.ccbluex.liquidbounce.features.module.modules.combat.AntiFireBall",
    "net.ccbluex.liquidbounce.features.module.modules.combat.NoClickDelay",
    "me.listed.listedhack.client.event.events.EventGetBlockReachDistance",
    "livesense.livesense.module.modules.CrystalTriggerBot",
    "lmaobox.holyfuckthisismodulesfolder.categories.category.AntiBedAura",
    "dev.blackhig.zhebushigudu.lover.event.events.TotemPopEvent",
    "just.monika.LiteraruteMC.Lucky.config.ConfigManager",
    "me.alpha432.oyvey.api.util.BurrowUtil",
    "me.stormcph.lumina.module.impl.category_mng.impl.CombatCata",
    "com.based.lynx.module.combat.AutoArmor",
    "client.dev.wrrulosdev.mcpclient.client.commands.cmd.AuthMeVelocityExploitCommand",
    "mac.hack.event.events.TotemPopEvent",
    "dev.hause.mackmod.module.modules.combat.Criticals",
    "net.mommymarlow.marlowclient.module.impl.combat.AutoAnchor",
    "me.perry.mcdonalds.event.events.TotemPopEvent",
    "medusa.ware.module.combat.AimAssist",
    "client.me.ghluka.medved.mixin.ExampleMixin",
    "java.xyz.Melody.ClientLib.SkyblockArea",
    "dev.zenhao.melon.mixin.block.MixinBlockWeb",
    "jibet.zenhao.melon.mixin.client.MixinBlockWeb",
    "meteordevelopment.meteorclient.commands.arguments.MacroArgumentType",
    "nekiplay.meteorplus.features.modules.combat.AntiBotPlus",
    "meteordevelopment.meteorclient.events.entity.player.CobwebEntityCollisionEvent",
    "mint.events.CrystalAttackEvent",
    "net.melbourne.commands.impl.MacroCommand",
    "com.Mirai.impl.module.combat.AutoLog",
    "halq.misericordia.fun.executor.modules.combat.AutoWeb",
    "me.afterdarkness.moloch.event.events.render.RenderBossHealthEvent",
    "me.linus.momentum.gui.hud.element.AnchorPoint",
    "monke.modules.combat.Aimbot",
    "me.kawaiizenbo.moonlight.Config",
    "dev.moonlight.event.events.CrystalAttackEvent",
    "com.snoworange.mousse.event.listeners.TotemPopEvent",
    "myau.mixin.MixinBlockWeb",
    "com.ngames.nclient.hack.hacks.AdvClickerDelays",
    "nami-api.src.main.namidevelopment.kiriyaga.api.core.TotemCounterService",
    "dev.supermic.nanosense.mixins.accessor.entity.AccessorEntityTippedArrow",
    "me.vaimok.nekoplus.api.event.events.TotemPopEvent",
    "me.corruptionhades.vapemenu.module.impl.combat.Criticals",
    "client.nicotine.events.RenderArmorEvent",
    "net.aspw.client.features.module.impl.movement.speeds.velocity.Velocity",
    "me.ninethousand.ninehack.event.events.TotemPopEvent",
    "me.travis.wurstplus.wurstplustwo.guiscreen.hud.WurstplusArmorDurabilityWarner",
    "net.noblesix.hl32k.HL32K",
    "me.awsomemanpop1.noobhack.module.modules.combat.KillAura",
    "Norules.com.viaversion.viabackwards.VelocityPlugin",
    "me.gavin.notorious.hack.hacks.chat.PotionAlert",
    "me.nullpoint.api.events.impl.TotemEvent",
    "me.zeroeightsix.kami.KamiMod",
    "me.leeeaf.oakclient.event.events.TotemUsedEvent",
    "me.alpha432.oyvey.event.events.TotemPopEvent",
    "keystrokesmod.client.module.modules.combat.AimAssist",
    "rina.onepop.club.api.util.world.CrystalUtil",
    "net.kankantari.onigiriclient.OnigiriClient",
    "me.ghosttypes.orion.modules.chat.ArmorAlert",
    "me.finz0.osiris.command.commands.MacroCommand",
    "pw.yallxe.outrage.modules.modules.combat.AutoTotem",
    "me.muffin.oyveyplus.api.utils.CrystalUtils",
    "me.trambled.ozark.mixins.MixinBossHealth",
    "me.olliem5.past.api.util.world.CrystalUtil",
    "net.rubberduck.pastique.mixin.ClickableWidgetMixin",
    "net.daporkchop.pepsimod.gui.clickgui.window.WindowCombat",
    "net.pistonmaster.pistonclient.gui.modules.combat.AutoTotem",
    "gateway.EaglercraftXBungee.src.main.net.lax1dude.eaglercraft.v1_8.plugin.gateway_bungeecord.server.HttpWebSocketHandler",
    "me.pollos.polloshook.api.command.args.MacroArgument",
    "me.srgantmoomoo.mixin.mixins.MixinBlockWeb",
    "xyz.pub.api.events.impl.EventPopTotem",
    "org.quantumclient.qubit.mixin.MixinEntityVelocityUpdateS2CPacket",
    "me.robeart.raion.client.events.events.entity.AddEntityVelocityEvent",
    "me.pianopenguin471.mixins.IS12PacketEntityVelocity",
    "me.amfero.razmorozka.Discord",
    "build.tmp.expandedArchives.forge-1.12.2-14.23.5.2860-inject_src.jar_9dc3fbaa5ca7b66255ea5693cd3b5294.net.minecraft.advancements.critereon.package-info",
    "me.rebirthclient.api.events.impl.TotemEvent",
    "xside.reflection.modules.movement.AntiWeb",
    "me.sjnez.renosense.event.events.TotemPopEvent",
    "Rat.RAT.com.sun.jna.platform.mac.DiskArbitration",
    "club.shrimphack.swag.event.events.TotemPopEvent",
    "com.krispdev.resilience.command.commands.CmdKillAuraMode",
    "zimnycat.reznya.utils.KillAura",
    "me.rina.rocan.api.util.crystal.BlockUtil",
    "rosegoldclient.commands.ChangeVelocity",
    "dev.zprestige.ruby.manager.TotemPopManager",
    "bleach.a32k.MapPeek",
    "com.kamiskidder.shgr.SHGR",
    "net.fabricmc.smphack.Hacks.AimBot.AutoAim",
    "safepoint.two.core.event.events.CrystalAttackEvent",
    "thefellas.safepoint.impl.modules.combat.AC_KillAura",
    "com.github.satellite.features.module.combat.AntiCrystal",
    "mod.selene.api.utils.TotemPopChams",
    "me.rigamortis.seppuku.api.event.gui.EventRenderPotions",
    "host.serenity.serenity.modules.combat.AntiKnockback",
    "me.noat.sexhack.client.guiscreen.hud.WurstplusArmorDurabilityWarner",
    "ShafferHackSRC.build.sources.main.me.HHA.shafferhack.event.events.TotemPopEvent",
    "com.netease.mc.modSS.mod.mods.COMBAT.AimAssist",
    "net.shoreline.client.api.config.MacroConfig",
    "aids.dev.shrimphack.auth.WebhookInformer",
    "cc.silk.module.modules.combat.AimAssist",
    "main.skiddedclient.mixins.ChunkAccessor",
    "me.vene.skilled.modules.mods.combat.AimAssist",
    "cc.slack.features.modules.impl.combat.AntiFireball",
    "! Smok.src.main.me.sleepyfish.smok.rats.impl.blatant.Aura",
    "me.skitttyy.kami.api.binds.IBindable",
    "io.github.solclient.client.event.impl.HitboxRenderEvent",
    "Core.src.main.org.sorus.client.module.impl.chatmacros.ChatMacros",
    "splash-server-master.src.main.server.website.Website",
    "me.alpha432.stay.features.modules.combat.Anti32KTotem",
    "sudo.core.hwid.utils.Webhook",
    "io.fishermen.fpsdisplay.settings.Killaura",
    "nl.chromaticvision.sunshine.impl.module.modules.misc.SilentPearl",
    "net.sushiclient.client.command.parser.BlockArrayParser",
    "me.aidan.sydney.commands.impl.MacroCommand",
    "net.fabricmc.fabric.event.impl.EndCrystalExplosionMcPlayerEvent",
    "dlindustries.vigillant.system.mixin.EndCrystalItemMixin",
    "me.Skid.Jello.Opacity",
    "package-mod-fixes.src.main.su.mandora.tarasande_mod_fixes.injection.mixin.baritone.MixinLookBehavior",
    "com.chompchompdead.teddyware.client.module.combat.AutoArmor",
    "xyz.templecheats.templeclient.event.events.render.ArmorEvent",
    "dev.tensor.backend.mixins.ArmorFeatureRendererMixin",
    "thunder.hack.core.manager.client.MacroManager",
    "io.github.racoondog.tokyo.commands.LookAtCommand",
    "events.EventVelocity",
    "me.endr.toxic.event.events.TotemPopEvent",
    "fr.undev.transware.module.combat.Aura",
    "me.leon.trinity.events.main.EventGetBlockReachDistance",
    "me.leon.trinityplus.events.main.EventGetBlockReachDistance",
    "me.hollow.trollgod.api.mixin.mixins.render.MixinLayerBipedArmor",
    "dev.luna5ama.trollhack.mixins.accessor.entity.AccessorEntityTippedArrow",
    "zirve.loves.mans.event.events.TotemPopEvent",
    "com.oldturok.turok.module.modules.combat.AntiKnockback",
    "me.kix.uzi.api.game.impl.packet.AccessorSPacketEntityVelocity",
    "io.fishermen.fpsdisplay.settings.AutoGapple",
    "dev.urmomia.events.entity.player.JumpVelocityMultiplierEvent",
    "me.pyr0byte.vapid.modules.ModuleAura",
    "vertex.client.feature.command.impl.ForEach",
    "com.veteran.hack.command.commands.GenerateWebsiteCommand",
    "bre2el.fpsreducer.feature.module.modules.combat.AimAssist",
    "dev.rabies.vox.modules.modules.AutoClickerModule",
    "vp.client.vrpos.module.combat.CrystalAura",
    "com.vydra.death.screen.modules.impl.combat.SkullBurrow",
    "build.sources.main.me.wise.w1sehack.api.event.CrystalBlockRenderEvent",
    "net.walksy.client.events.client.GetVelocityMultiplierEvent",
    "cc.winterclient.client.event.ext.scenarios.EventArrowLoose",
    "WizClient.modules.movement.NoSlowdown",
    "me.travis.wurstplus.module.modules.combat.Aura",
    "me.travis.wurstplusthree.command.commands.BurrowBlockCommand",
    "Public Modules W-.OldAutoTotem",
    "minecraft.com.darkcart.xdolf.clickgui.windows.WindowAura",
    "me.krymz0n.xenophyre.impl.module.combat.AutoArmor",
    "xuluv1.5.2.com.elementars.eclient.command.commands.AntiVoidCommand",
    "me.alpha432.oyvey.event.events.NewTotemPopEvent",
    "dev.zspacehack.gui.ArmorHUD",
    "crystal.addons.CrystalCL",
    "cum.xiaomao.zerohack.mixins.accessor.entity.AccessorEntityTippedArrow",
    "me.сс.zerotwo.api.event.events.TotemPopEvent",
    "com.zinc.hack.api.utils.CombatUtil",
    "cat.yoink.zodiac.module.modules.combat.AutoCrystal",
    "client.src.respectful.rapist.client.mapping.mappings.S12PacketEntityVelocity",
    "me.tastybulb.asylum.api.event.events.TotemPopEvent",
    "/com/xenon/XenonClient.class",
    "com/water/mixin/AbstractClientPlayerEntityMixin.classPK",
    "/com/water/WaterClient.class",
    "LICENSE_noxx-clientPK", "assets/noxx-client/PK",
    "Calcium Client utility mod",
    "zelith-refmap.jsonPK", "com/zelith/mixin/",
    "AnchorMacro", "AutoCrystal",

    # ------------------------------------------------------------------
    # javaw.exe - module / feature strings (severity: medium)
    # ------------------------------------------------------------------
    # cleaners / self destruct
    "USN Journal Cleaner", "Delete USN Journal", "Self Destruct",
    "Generic Selfdestruct", "Saves the last modified date after self destruct",
    "Anti SS Tool", "String Cleaner", "Possible Destruct",
    # crystal / anchor
    "CwCrystal", "CwCrystalOld", "CwCrystal2", "MarlowCrystalReWrite",
    "AnchorMacroRewrite", "AnchorMacroRe", "AntiAntiCw", "AutoDtap", "AutoDtaps",
    "Auto Crystal", "Crystal Optimizer", "Generic Crystal Optimizer",
    "Crystal Clicker", "Double Crystal", "Double Anchor", "Anchor Exploder",
    "Anchor Placer", "MarlowAnchor", "Generic autoanchor", "AutoAnchor",
    "autoanchor", "HotbarRestock",
    # totem
    "Auto Totem", "AutoTotem", "Auto Inv Totem", "AutoInventoryTotem",
    "Totem Offhand", "InventoryTotemLegit", "Hover Totem",
    "Auto Hit Crystal", "AutoHitCrystal", "Auto Double Hand", "AutoDoubleHand",
    "AutoDHandReWrite", "Auto Retotem", "Autoretotem", "legit retotem",
    "Force Totem", "Auto Switch To Totem", "Auto Totem Buy",
    # combat
    "TriggerBot", "Stray Bypass", "Kill Aura", "CrystalAura", "MaceAura",
    "Silent Mace", "Mace Aim", "Mace Swap", "Mace Hit", "Aim Assist",
    "Silent Aim", "BowAimbot", "ClickAimassist", "Auto WTap", "AutoWTap",
    "W Tap", "Shield Disabler", "Auto Shield Breaker", "Generic Shieldbreaker",
    "No Miss Delay", "Static HitBoxes", "Static Hitboxes", "Totem Hit",
    "Auto Dtap", "NoShieldDelay", "Disable Shields", "Generic disable shield",
    "AntiInvis", "Attack invisibles", "Only Crit", "Only Crit Sword",
    "Only Crit Axe", "Fake Punch", "Damage Tick", "Anti-Weakness",
    "Ghost Obby", "Fake Ghost",
    # movement / misc
    "No Jump Delay", "Auto Jump Reset", "NoJumpDelay", "Fast Place",
    "No Break Delay", "FastBreak", "Fake Lag", "Ping Spoof", "Pack Spoof",
    "WebMacro", "Elytra Swap", "Elytra Glide", "Elytra Predict",
    "Auto Firework", "Firework Macro", "Fast Bridge", "BridgeAssist",
    "Fast Swim", "Auto Sprint", "Auto sneak", "No Bounce", "Anti Trap",
    "InventoryMove",
    # utility
    "AutoLootYeeter", "Auto Looter", "AutoLoot", "Chest Stealer",
    "Auto Pickaxe", "Auto Tool", "Auto Mine", "Auto Eat", "Auto Pot",
    "Auto Pot Refill", "Potion Refill", "Auto XP", "Fast EXP", "Auto Sell",
    "Shulker Dropper", "Bone Dropper", "Key Pearl", "Cord Snapper", "Auto Tpa",
    "Auto MineCart", "Auto Crafter", "Auto Web", "Auto Spawner Sell",
    "Auto Restock", "AutoPearlRestock",
    # esp / finders
    "Player ESP", "Storage ESP", "Block Esp", "Entity ESP", "Kelp ESP",
    "Trident ESP", "Arrow ESP", "Circle ESP", "Item ESP", "Netherite Finder",
    "Health Indicators", "Target HUD", "Rtp Base Finder", "RTP End Base Finder",
    "Tunnel Base Finder",
    # clickers / optimizers / bypasses
    "Auto Clicker", "Autoclicker", "Click Simulation", "Walksy Optimizer",
    "blatant mode", "Donut SMP Bypass", "Fakeplayer", "AutoHeadBob",
    "Auto HeadBob", "AutoAgro", "Auto Armor", "Autoarmor", "Bed Macro",
    "Quick Macro", "Trap Save", "Anti Bot", "Auto Loot", "Replace Mod",
    # description / setting strings
    "Bypasses stray's Anti-TriggerBot",
    "Stops while Auto Crystal is running",
    "Helps you do the air place/double anchor",
    "Checks if the player is blocking your hits with a shield",
    "Target hud will disappear after 10 seconds",
    "Key to add/remove friends",
    "Key that starts double anchoring",
    "Key to activate the chest stealer",
    "Key to enabled the module",
    "Settings for the client",
    "For servers with freeze plugins that don't let you open the GUI",
    "Enables LGBTQ mode",
    "Doesn't let you hit friends",
    "Disables aim assist for friends",
    "Tells you if you're aiming at a friend or not",
    "Automatically aims at players for you",
    "Aims at the last attacked player",
    "Stop at Target Vert", "Stop at Target Horiz",
    "Precision of the target position",
    "Automatically blows up respawn anchors for you",
    "Switch Chance", "Glowstone Delay", "Glowstone Chance", "Explode Chance",
    "Only Charge",
    "Automatically crystals fast for you",
    "Times your crystals for a perfect d-tap",
    "Predict Damage", "Predict Crystals",
    "Automatically hit-crystals for you",
    "Automatically opens and closes the inventory for you",
    "Automatically throws health potions when low on health",
    "Refills your hotbar with potions",
    "Makes your crystals disappear faster client-side so you can place crystals faster",
    "Equips a totem in your totem and offhand slots if a totem is hovered",
    "Switches to your totem slot and offhands a totem if you dont have one already",
    "Automatically hits players for you",
    "Will hit the player no matter if you're eating or blocking with a shield",
    "Automatically clicks for you",
    "Automatically throws XP bottles for you",
    "Lets you move freely around the world without actually moving",
    "Holds back packets making the server think your internet connection is bad.",
    "Prevents you from certain actions",
    "Renders players through walls",
    "Renders storage blocks through walls",
    "Donut Bypass",
    "Auto-aims at nearest player below you when flying with elytra and holding a mace.",
    "Only aim when using elytra", "Only When Flying", "Only With Mace",
    "Only aim when holding a mace",
    "Switches to a mace when attacking.",
    "Walksy Optimizer but more customizable",
    "Simulates actual mouse clicks when placing crystals",
    "Requires two keys to be pressed simultaneously to open GUI",
    "Anti Aliasing | This can impact performance if you're using tracers but gives them a smoother look |",
    "Expands a player's hitbox.",
    "Module that helps you escape Polish traps",
    "Automatically drops bones from spawner",
    "Drop delay",
    "Goes to shop buys shulkers and drops automatically",
    "It detects whenever the hungerbar/health falls a certain threshold, selects food in your hotbar, and starts eating.",
    "Switches to a firework and uses it when you press a bind.",
    "Module that allows players to automatically mine",
    "Module that automatically switches to best tool",
    "Automatically starts flying when right-clicking firework rocket while standing still",
    "Spams use action.",
    "Move freely with smooth camera",
    "Switches to an ender pearl and throws it when you press a bind",
    "Boosts you when using riptide with a trident.",
    "Out of water",
    "Highlights selected blocks through walls",
    "Highlights kelp that reaches the water surface for base finding",
    "Draws a line from your player to the other",
    "Draws a line from your player to the storage block",
    "Highlights drowned with tridents",
    "This module makes it so you can't do certain stuff if you have a player friended!",
    "Puts a totem in your hotbar as well, if enabled (Setting below will work if this is enabled)",
    "When you get close to a player your aim sticks to the player.",
    "Aim at head", "Automatically clicks.",
    "Left Click Delay", "Right Click Delay",
    "Performs critical attacks when you hit your target.",
    "Only KillAura",
    "Only performs crits when using killaura.",
    "Only attacks an entity when a specified weapon is in your hand.",
    "Prevents you from being moved by external forces.",
    "Allows movement while inventory is open",
    "Allows you to hit the player from a distance",
    "Automatically shifts on edge of block when you hold block in hand.",
    "Fly faster while using Elytra.",
    "Swim faster in water",
    "Allows you to fly",
    "Automatically places web.",
    "Seamlessly swap between an Elytra and a Chestplate with a configurable keybinding",
    "If elytra is not in hotbar it will move it from inventory to preferred slot",
    "Stops crystalling when a player dies nearby",
    "Automatically equips a totem in your offhand and main hand if empty",
    "Automatically Places 2 anchors",
    "Equips a totem in offhand and optionally hotbar when hovering over one in inventory",
    "BlockESP", "ChatSpammer", "autoweapon", "BowAssist",
    "Throw Pot", "Pot Delay", "Silent Swap",
    "Automatically crafts items when crafting table is open",
    "Target Mobs", "Target Players",
    "Reveals players who are invisible.",
    "The health at which the totem will be placed into your hand.",
    "Makes all attacks into critical strikes.",
    "Allows you to reach further.",
    "Radius that TriggerBot will trigger.",
    "Determines when AutoEat will trigger.",
    "Automatically switch to fishing rod before casting.",
    "Allows the player to walk on water.",
    "Prevents fall damage.",
    "Makes it so the user can jump very quickly.",
    "Automatically plants, fertilizes, and harvests crops.",
    "Syracruse Client", "Macro anchor", "Cw Crystal", "Fake cps",
    "Attack Delay", "Switch Delay", "Speed Multiplier", "Sword Delay",
    "Axe Delay", "Sticky Aim", "Slot selection", "Dysplays Hud",
    "Autodoublehand", "Legit Totem", "Switch Back", "Double Glowstone",
    "Height Expansion", "Width Expansion", "throw delay", "Vertical Speed",
    "Horizontal Speed", "Speed Delay", "Stop on Kill", "Equip Delay",
    "hit delay", "Auto Switch", "Attack Players", "Stop On Crystal",
    "Switches to totem if low on health", "Crystal Aura",
    "After you move your mouse aim assist will stop working for the selected amount of time",
    "Makes the CPS hud think you're legit",
    "Explode Delay", "No break Delay", "Place Delay",
    "player 3sp", "Storage 3SP",
    "Place Chance", "Break Chance", "Particle Chance", "Attack Mobs",
    "Selected Slot", "Friend Key", "Player is friend", "Anti-Attack",
    "Anti-aim", "Stop at Target Vertical", "Stop at Target Horizontal",
    "Look at Nearest", "Wait on Move", "Aim At", "X Coords", "Y Coords",
    "Hud Timeout", "In Air", "Glowstone Misplace", "Anchor on Anchor",
    "E-chest click", "Default spam message", "Enable While Moving",
    "Only Blocks", "Aimassist", "Autototem", "Anti Resource Pack",
    "Crysta lAura", "fast place", "Auto crystal", "Auto shield breaker",
    "Auto loot", "Anchor macro", "Fast break", "Auto sprint", "Click Aimassist",

    # fullwidth-unicode obfuscated variants
    "ＡｕｔｏＣｒｙｓｔａｌ", "Ａｕｔｏ Ｃｒｙｓｔａｌ", "ＡｕｔｏＨｉｔＣｒｙｓｔａｌ",
    "ＡｕｔｏＡｎｃｈｏｒ", "Ａｕｔｏ Ａｎｃｈｏｒ", "ＤｏｕｂｌｅＡｎｃｈｏｒ",
    "Ｄｏｕｂｌｅ Ａｎｃｈｏｒ", "ＳａｆｅＡｎｃｈｏｒ", "Ｓａｆｅ Ａｎｃｈｏｒ",
    "Ａｎｃｈｏｒ Ｍａｃｒｏ", "ＡｕｔｏＴｏｔｅｍ", "Ａｕｔｏ Ｔｏｔｅｍ",
    "ＨｏｖｅｒＴｏｔｅｍ", "Ｈｏｖｅｒ Ｔｏｔｅｍ", "ＩｎｖｅｎｔｏｒｙＴｏｔｅｍ",
    "Ａｕｔｏ Ｉｎｖｅｎｔｏｒｙ Ｔｏｔｅｍ", "Ａｕｔｏ Ｔｏｔｅｍ Ｈｉｔ",
    "ＡｕｔｏＰｏｔ", "Ａｕｔｏ Ｐｏｔ", "Ａｕｔｏ Ｐｏｔ Ｒｅｆｉｌｌ",
    "ＡｕｔｏＡｒｍｏｒ", "Ａｕｔｏ Ａｒｍｏｒ", "ＳｈｉｅｌｄＤｉｓａｂｌｅｒ",
    "Ｓｈｉｅｌｄ Ｄｉｓａｂｌｅｒ", "ＡｕｔｏＤｏｕｂｌｅＨａｎｄ",
    "Ａｕｔｏ Ｄｏｕｂｌｅ Ｈａｎｄ", "ＡｕｔｏＣｌｉｃｋｅｒ", "ＡｕｔｏＭａｃｅ",
    "Ａｕｔｏ Ｍａｃｅ", "ＭａｃｅＳｗａｐ", "Ｍａｃｅ Ｓｗａｐ", "Ｓｐｅａｒ Ｓｗａｐ",
    "Ａｕｔｏｍａｔｉｃａｌｌｙ ａｘｅ ａｎｄ ｍａｃｅ ｓｈｉｅｌｄｅｄ ｐｌａｙｅｒｓ",
    "Ｓｔｕｎ Ｓｌａｍ", "ＡｉｍＡｓｓｉｓｔ", "Ａｉｍ Ａｓｓｉｓｔ", "ＴｒｉｇｇｅｒＢｏｔ",
    "Ｔｒｉｇｇｅｒ Ｂｏｔ", "Ｓｉｌｅｎｔ Ｒｏｔａｔｉｏｎｓ", "ＦａｋｅＬａｇ",
    "Ｆａｋｅ Ｌａｇ", "Ｆａｋｅ Ｐｕｎｃｈ", "ＥｌｙｔｒａＳｗａｐ", "Ｅｌｙｔｒａ Ｓｗａｐ",
    "Ｗａｌｋｓｙ Ｏｐｔｉｍｉｚｅｒ", "ＫｅｙＰｅａｒｌ", "Ｋｅｙ Ｐｅａｒｌ",
    "Ｌｏｏｔ Ｙｅｅｔｅｒ", "Ｆａｓｔ Ｐｌａｃｅ", "Ｐｌａｃｅ ｂｌｏｃｋｓ ｆａｓｔｅｒ",
    "Ａｕｔｏ Ｂｒｅａｃｈ", "Ｆｒｅｅｃａｍ", "Ｍｏｖｅ ｆｒｅｅｌｙ ｔｈｒｏｕｇｈ ｗａｌｌｓ",
    "Ｎｏ Ｃｌｉｐ", "Ｆｒｅｅｚｅ Ｐｌａｙｅｒ", "ＬＷＦＨ Ｃｒｙｓｔａｌ",
    "Ａｃｔｉｖａｔｅ Ｋｅｙ", "Ｃｌｉｃｋ Ｓｉｍｕｌａｔｉｏｎ", "Ｏｎ ＲＭＢ",
    "Ｎｏ Ｃｏｕｎｔ Ｇｌｉｔｃｈ", "Ｎｏ Ｂｏｕｎｃｅ", "ＮｏＢｏｕｎｃｅ",
    "Ｒｅｍｏｖｅｓ ｔｈｅ ｃｒｙｓｔａｌ ｂｏｕｎｃｅ ａｎｉｍａｔｉｏｎ",
    "Ｐｌａｃｅ Ｄｅｌａｙ", "Ｂｒｅａｋ Ｄｅｌａｙ", "Ｆａｓｔ Ｍｏｄｅ",
    "Ｐｌａｃｅ Ｃｈａｎｃｅ", "Ｂｒｅａｋ Ｃｈａｎｃｅ", "Ｓｔｏｐ Ｏｎ Ｋｉｌｌ",
    "Ｄａｍａｇｅ Ｔｉｃｋ", "Ａｎｔｉ Ｗｅａｋｎｅｓｓ", "Ｐａｒｔｉｃｌｅ Ｃｈａｎｃｅ",
    "Ｔｒｉｇｇｅｒ Ｋｅｙ", "Ｓｗｉｔｃｈ Ｄｅｌａｙ", "Ｔｏｔｅｍ Ｓｌｏｔ",
    "Ｓｍｏｏｔｈ Ｒｏｔａｔｉｏｎｓ", "Ｒｏｔａｔｉｏｎ Ｓｐｅｅｄ", "Ｕｓｅ Ｅａｓｉｎｇ",
    "Ｅａｓｉｎｇ Ｓｔｒｅｎｇｔｈ", "Ｗｈｉｌｅ Ｕｓｅ", "Ｓｔｏｐ ｏｎ Ｋｉｌｌ",
    "Ｇｌｏｗｓｔｏｎｅ Ｄｅｌａｙ", "Ｇｌｏｗｓｔｏｎｅ Ｃｈａｎｃｅ",
    "Ｅｘｐｌｏｄｅ Ｄｅｌａｙ", "Ｅｘｐｌｏｄｅ Ｃｈａｎｃｅ", "Ｅｘｐｌｏｄｅ Ｓｌｏｔ",
    "Ｏｎｌｙ Ｃｈａｒｇｅ", "Ｒｅａｃｈ Ｄｉｓｔａｎｃｅ", "Ｍｉｎ Ｈｅｉｇｈｔ",
    "Ｍｉｎ Ｆａｌｌ Ｓｐｅｅｄ", "Ａｔｔａｃｋ Ｄｅｌａｙ", "Ｂｒｅａｃｈ Ｄｅｌａｙ",
    "Ｒｅｑｕｉｒｅ Ｅｌｙｔｒａ", "Ａｕｔｏ Ｓｗｉｔｃｈ Ｂａｃｋ",
    "Ｃｈｅｃｋ Ｌｉｎｅ ｏｆ Ｓｉｇｈｔ", "Ｏｎｌｙ Ｗｈｅｎ Ｆａｌｌｉｎｇ",
    "Ｒｅｑｕｉｒｅ Ｃｒｉｔ", "Ｓｈｏｗ Ｓｔａｔｕｓ Ｄｉｓｐｌａｙ",
    "Ｓｔｏｐ Ｏｎ Ｃｒｙｓｔａｌ", "Ｃｈｅｃｋ Ｓｈｉｅｌｄ", "Ｏｎ Ｐｏｐ",
    "Ｐｒｅｄｉｃｔ Ｄａｍａｇｅ", "Ｏｎ Ｇｒｏｕｎｄ", "Ｃｈｅｃｋ Ｐｌａｙｅｒｓ",
    "Ｐｒｅｄｉｃｔ Ｃｒｙｓｔａｌｓ", "Ｃｈｅｃｋ Ａｉｍ", "Ｃｈｅｃｋ Ｉｔｅｍｓ",
    "Ａｃｔｉｖａｔｅｓ Ａｂｏｖｅ", "Ｂｌａｔａｎｔ", "Ｆｏｒｃｅ Ｔｏｔｅｍ",
    "Ｓｔａｙ Ｏｐｅｎ Ｆｏｒ", "Ｏｎｌｙ Ｏｎ Ｐｏｐ", "Ｖｅｒｔｉｃａｌ Ｓｐｅｅｄ",
    "Ｓｗａｐ Ｓｐｅｅｄ", "Ｓｔｒｉｃｔ Ｏｎｅ－Ｔｉｃｋ", "Ｍａｃｅ Ｐｒｉｏｒｉｔｙ",
    "Ｍｉｎ Ｔｏｔｅｍｓ", "Ｍｉｎ Ｐｅａｒｌｓ", "Ｔｏｔｅｍ Ｆｉｒｓｔ",
    "Ｄｒｏｐ Ｉｎｔｅｒｖａｌ", "Ｒａｎｄｏｍ Ｐａｔｔｅｒｎ",
    "Ｈｏｒｉｚｏｎｔａｌ Ａｉｍ Ｓｐｅｅｄ", "Ｖｅｒｔｉｃａｌ Ａｉｍ Ｓｐｅｅｄ",
    "Ｉｎｃｌｕｄｅ Ｈｅａｄ", "Ｗｅｂ Ｄｅｌａｙ", "Ｈｏｌｄｉｎｇ Ｗｅｂ",
    "Ｎｏｔ Ｗｈｅｎ Ａｆｆｅｃｔｓ Ｐｌａｙｅｒ", "Ｈｉｔ Ｄｅｌａｙ",
    "Ｓｗｉｔｃｈ Ｂａｃｋ", "Ｒｅｑｕｉｒｅ Ｈｏｌｄ Ａｘｅ",
    "ｐｌａｃｅＩｎｔｅｒｖａｌ", "ｂｒｅａｋＩｎｔｅｒｖａｌ", "ｓｔｏｐＯｎＫｉｌｌ",
    "ａｃｔｉｖａｔｅＯｎＲｉｇｈｔＣｌｉｃｋ", "ｄａｍａｇｅｔｉｃｋ",
    "ｈｏｌｄＣｒｙｓｔａｌ", "ｆａｋｅＰｕｎｃｈ",
    "Ｒｅｆｉｌｌｓ ｙｏｕｒ ｈｏｔｂａｒ ｗｉｔｈ ｐｏｔｉｏｎｓ",
    "Ｋｅｐｓ ｙｏｕ ｓｐｒｉｎｔｉｎｇ ａｔ ａｌｌ ｔｉｍｅｓ",
    "Ｐｌａｃｅｓ ａｎｃｈｏｒ， ｃｈａｒｇｅｓ ｉｔ， ｐｒｏｔｅｃｔｓ ｙｏｕ， ａｎｄ ｅｘｐｌｏｄｅｓ",
    "Ａｕｔｏ ｓｗａｐ ｔｏ ｓｐｅａｒ ｏｎ ａｔｔａｃｋ", "Ａｎｔｉ Ｗｅｂ",
    "ＡｕｔｏＷｅｂ", "Ｐｌａｃｅｓ Ｗｅｂｓ Ｏｎ Ｅｎｅｍｉｅｓ", "Ｍａｃｒｏ Ｋｅｙ",

    # more medium strings
    "Auto Cart", "XP Macro", "Auto LavaingsMix", "Cart Trap", "Safe Cart",
    "LegitTridentFly", "AntiTrap", "AutoTreeFarmer", "AuctionSnipercla",
    "RTPBaseFinder",
    "Auto manages both hands for combat efficiency",
    "Standard placement macro", "REOFFHAND_TOTEM",
    "Attacks instantly when enemy pops a totem",
    "Advanced placement macro",
    "Expands entity hitboxes to make them easier to hit.",
    "Jumps when you take damage to reduce knockback.",
    "Breaks all spawners around you when players are nearby and dumps your inventory in an e-chest",
    "FakeScoreboard",
    "Undetectable Trident Fly. Requires Rain, RipTide",
    "Helps you escape traps by removing certain entities.",
    "Automatically sells items from your hotbar.",
    "AFK farms 2x2 spruce podzol patches with auto refill",
    "Automatically sells all items in your inventory.",
    "Snipes items on auction house for cheap",
    "Finds bases by digging down",
    "Automatically buys items from the common crate",
    "Automatically buys/sells shulkers and shulker shells with player targeting",
    "Automatically buys selected items from PVP shop category",
    "Target Bone", "Switch Target Key",
    "Prevents you from being kicked for being AFK by rotating your view",
    "AutoBoneOrder", "SilentHomeSetter", "Panic close the GUI instantly",
    "TunnelBaseFinder", "Amethyst ESP", "Mob ESP", "Sus ESP", "Hole ESP",
    "Anchor Macro", "Anchor Macroà",
    "Triggerbot", "SpearSwap", "ChunkFinder", "Shield Breaker",
    "COMBAT/Shield Breaker/enabled", "Single anchor", "tuff chunk finder",
    "Pearl ESP", "automine", "sus chunk finder", "Invtotem", "Piston ESP",
    "Swing Speed", "Manual tunnel", "breach swap", "swingspeed", "Base ESP",
    "Chunkfinder", "AH Sniper", "Bedrock hole ESP", "Light ESP",
    "Fake Pay", "Fake Payments", "Fake Roles", "Spawner Finder", "Stash Finder",
]

# keep first occurrence, drop exact duplicates
BUILT_IN = list(dict.fromkeys(BUILT_IN))

STRING_LITERAL = re.compile(r'"(?:[^"\\]|\\.)*"')
PROTECTED_TEXT_CALL = re.compile(
    r'ProtectedText\.fromInts\(new\s+int\s*\[\s*\]\s*\{([^}]+)\}\)',
    re.MULTILINE,
)

KEY_A = 0x1E
KEY_STEP = 0x37


def key(i: int) -> int:
    return (KEY_A ^ (i * KEY_STEP)) & 0xFF


def decrypt(ints: Iterable[int]) -> str:
    out = bytearray()
    for i, b in enumerate(ints):
        out.append((b ^ key(i)) & 0xFF)
    return out.decode("utf-8", errors="replace")


def compile_pattern(terms: list[str]) -> re.Pattern[str]:
    terms = sorted({t.strip() for t in terms if t and t.strip()}, key=len, reverse=True)
    body = "|".join(re.escape(t) for t in terms)
    return re.compile(r"(?<![A-Za-z0-9])(" + body + r")(?![A-Za-z0-9])", re.IGNORECASE)


def line_of(src: str, offset: int) -> int:
    return src.count("\n", 0, offset) + 1


def scan_file(path: str, pattern: re.Pattern[str], decrypt_arrays: bool) -> list[tuple[str, int, str, str]]:
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            src = f.read()
    except OSError:
        return []
    hits: list[tuple[str, int, str, str]] = []
    for m in STRING_LITERAL.finditer(src):
        body = m.group(0)[1:-1]
        for hit in pattern.finditer(body):
            hits.append((path, line_of(src, m.start()), hit.group(1), m.group(0)))
    if decrypt_arrays:
        for m in PROTECTED_TEXT_CALL.finditer(src):
            raw_ints = m.group(1)
            try:
                ints = [int(x.strip(), 0) & 0xFF for x in raw_ints.split(",") if x.strip()]
            except ValueError:
                continue
            plain = decrypt(ints)
            for hit in pattern.finditer(plain):
                hits.append((
                    path,
                    line_of(src, m.start()),
                    hit.group(1),
                    f'[decrypted] "{plain}"',
                ))
    return hits


def main() -> int:
    ap = argparse.ArgumentParser(description="Scan a source tree for plaintext scanner-dictionary hits.")
    ap.add_argument("root", nargs="?", default="src", help="source root to walk (default: src)")
    ap.add_argument("--decrypt", action="store_true",
                    help="also decode ProtectedText.fromInts arrays and re-check")
    ap.add_argument("--dict", default=None, help="path to extra dictionary file (one term per line)")
    ap.add_argument("--json", action="store_true", help="emit findings as JSON")
    args = ap.parse_args()

    terms = list(BUILT_IN)
    if args.dict and os.path.exists(args.dict):
        with open(args.dict, "r", encoding="utf-8") as f:
            for line in f:
                s = line.strip()
                if s and not s.startswith("#"):
                    terms.append(s)
    pattern = compile_pattern(terms)

    if not os.path.exists(args.root):
        print(f"error: root does not exist: {args.root}", file=sys.stderr)
        return 2

    all_hits: list[tuple[str, int, str, str]] = []
    for dirpath, _, filenames in os.walk(args.root):
        for fn in filenames:
            if fn.endswith(".java"):
                all_hits.extend(scan_file(os.path.join(dirpath, fn), pattern, args.decrypt))

    if args.json:
        import json
        print(json.dumps([
            {"file": p, "line": l, "term": t, "context": c[:200]}
            for p, l, t, c in all_hits
        ], indent=2))
    else:
        for path, line, term, ctx in all_hits:
            snippet = ctx if len(ctx) <= 120 else ctx[:117] + "..."
            print(f"[FLAGGED] {path}:{line}  term={term!r}  in {snippet}")
        print()
        if all_hits:
            print(f"{len(all_hits)} flagged plaintext occurrence(s).")
        else:
            print("[ CLEAN ] no plaintext dictionary hits under " + args.root)
    return 1 if all_hits else 0


if __name__ == "__main__":
    raise SystemExit(main())
