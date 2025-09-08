package at.hannibal2.skyhanni.features.misc.keybinds

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.events.minecraft.KeyDownEvent
import at.hannibal2.skyhanni.events.minecraft.KeyUpEvent
import at.hannibal2.skyhanni.events.minecraft.KeyPressEvent
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
//#if MC < 1.21.6
import net.minecraft.client.Minecraft
//#else
//$$ import net.minecraft.client.MinecraftClient
//$$ import net.minecraft.client.option.KeyBinding
//#endif
import org.lwjgl.input.Keyboard
import at.hannibal2.skyhanni.config.commands.CommandsRegistry
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.KeyboardManager
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld

@SkyHanniModule
object Keybinds {
    data class Keybind(
        val combo: String,
        val command: String,
        val allowedIslands: Set<IslandType> = setOf(IslandType.ANY),
        val allowOutsideSkyBlock: Boolean = false,
    )

    private val binds = mutableListOf<Keybind>()
    private val modifierOrder = listOf("CTRL", "SHIFT", "ALT")

    private var chordActive = false
    private val chordBaseKeys = mutableSetOf<String>()
    private var chordPeakCombo: Keybind? = null
    private var chordPeakSize = 0

    @Volatile private var cachedVanillaKeys: Map<String, String>? = null
    private var lastVanillaCacheStamp: Long = 0L

    fun normalizeCombo(combo: String): String {
        val raw = combo.split('+').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        val mods = modifierOrder.filter { it in raw }
        val base = raw.filter { it !in modifierOrder }.toSet().toList().sorted()
        return (mods + base).joinToString("+")
    }

    private fun splitCombo(normalized: String): Pair<Set<String>, Set<String>> {
        val parts = normalized.split('+').map { it.trim() }.filter { it.isNotEmpty() }
        val mods = parts.filter { it in modifierOrder }.toSet()
        val base = parts.filter { it !in modifierOrder }.toSet()
        return mods to base
    }

    fun duplicateExists(normalized: String, original: String?, newCommand: String?): Boolean = synchronized(binds) {
        binds.any {
            val sameCombo = normalizeCombo(it.combo) == normalized
            if (!sameCombo) return@any false
            // Ignore duplicate if editing same original or same command text
            if (original != null && normalizeCombo(original) == normalized) return@any false
            if (newCommand != null && it.command.trim().equals(newCommand.trim(), true)) return@any false
            true
        }
    }

    fun vanillaKeyConflicts(normalized: String): List<String> {
        val (_, base) = splitCombo(normalized)
        if (base.isEmpty()) return emptyList()
        val vanilla = vanillaBoundKeyNames()
        return base.mapNotNull { k -> vanilla[k] ?.let { "$it ($k)" } }
    }

    fun vanillaBoundKeyNames(): Map<String, String> {
        val now = System.currentTimeMillis()
        val cached = cachedVanillaKeys
        if (cached != null && (now - lastVanillaCacheStamp) < 5_000) return cached
        val map = mutableMapOf<String, String>()
        try {
            //#if MC < 1.21.6
            try {
                val mc = Minecraft.getMinecraft()
                val arr = mc.gameSettings.keyBindings
                for (kb in arr) {
                    try {
                        val code = kb.keyCode
                        if (code == 0 || code == -100 || code == -99) continue
                        val human = humanizeDescription(kb.keyDescription ?: continue)
                        map[keyName(code)] = human
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
            //#else
            //$$ try {
            //$$     val mc = MinecraftClient.getInstance()
            //$$     val list = mc.options.allKeys
            //$$     for (kb in list) {
            //$$         try {
            //$$             val code = try {
            //$$                 val boundField = kb.javaClass.getDeclaredField("boundKey").apply { isAccessible = true }
            //$$                 val boundObj = boundField.get(kb)
            //$$                 boundObj?.javaClass?.methods?.firstOrNull { it.name in setOf("getCode","getValue","code") && it.parameterCount==0 }?.invoke(boundObj) as? Int
            //$$             } catch (_: Throwable) {
            //$$                 try {
            //$$                     val defField = kb.javaClass.getDeclaredField("defaultKey").apply { isAccessible = true }
            //$$                     val defObj = defField.get(kb)
            //$$                     defObj?.javaClass?.methods?.firstOrNull { it.name in setOf("getCode","getValue","code") && it.parameterCount==0 }?.invoke(defObj) as? Int
            //$$                 } catch (_: Throwable) { null }
            //$$             }
            //$$             if (code == null || code == 0 || code == -100 || code == -99) continue
            //$$             val label = (kb.translationKey)
            //$$             map[keyName(code)] = humanizeDescription(label)
            //$$         } catch (_: Throwable) {}
            //$$     }
            //$$ } catch (_: Throwable) {}
            //#endif
        } catch (_: Throwable) {}
        cachedVanillaKeys = map
        lastVanillaCacheStamp = now
        return map
    }

    private fun humanizeDescription(raw: String): String {
        val r = raw.removePrefix("key.").removePrefix("key.")
        val base = r.substringAfterLast('.')
            .replace('_', ' ').replace('.', ' ')
        return base.split(' ').filter { it.isNotBlank() }.joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
    }

    fun isVanillaBoundKeyName(name: String): Boolean = vanillaBoundKeyNames().containsKey(name.uppercase())

    fun register(b: Keybind) {
        val normalized = normalizeCombo(b.combo)
        if (duplicateExists(normalized, null, b.command)) {
            ChatUtils.userError("Combo already in use: $normalized"); return
        }
        if (vanillaKeyConflicts(normalized).isNotEmpty()) {
            ChatUtils.userError("Combo uses vanilla bound key(s): ${vanillaKeyConflicts(normalized).joinToString(", ")}"); return
        }
        val toAdd = b.copy(combo = normalized)
        synchronized(binds) {
            // Replace existing same combo (update command) instead of rejecting if same normalized
            val it = binds.iterator()
            while (it.hasNext()) {
                val old = it.next()
                if (normalizeCombo(old.combo) == normalized) it.remove()
            }
            binds.add(toAdd)
        }
        persist()
        ChatUtils.chat("Registered keybind: ${toAdd.combo}", prefix = true)
    }

    fun unregister(combo: String) {
        val normalized = normalizeCombo(combo)
        synchronized(binds) { binds.removeAll { normalizeCombo(it.combo) == normalized } }
        persist()
    }

    fun allBinds(): List<Keybind> = synchronized(binds) { binds.toList() }

    private fun isAllowedNow(b: Keybind): Boolean {
        val island = HypixelData.skyBlockIsland
        if (island == IslandType.NONE) return b.allowOutsideSkyBlock
        val allowed = b.allowedIslands
        if (allowed.contains(IslandType.ANY)) return true
        if (island in allowed) return true
        if (IslandType.UNKNOWN in allowed && !IslandType.entries.filter { it.isValidIsland() }.contains(island)) return true
        return false
    }

    private fun executeCommandRaw(cmd: String) {
        try { CommandsRegistry.execAutomaticCommand(cmd) } catch (_: Throwable) {
            ErrorManager.skyHanniError("Keybinds: Failed to execute command: $cmd")
        }
    }

    private fun keyName(code: Int): String = try { KeyboardManager.getKeyName(code).uppercase() } catch (_: Throwable) { code.toString() }
    private fun isModifier(code: Int) = code in setOf(Keyboard.KEY_LCONTROL, Keyboard.KEY_RCONTROL, Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT, Keyboard.KEY_LMENU, Keyboard.KEY_RMENU)
    private fun currentModifiers(): Set<String> = buildSet {
        if (Keyboard.KEY_LCONTROL.isKeyHeld() || Keyboard.KEY_RCONTROL.isKeyHeld()) add("CTRL")
        if (Keyboard.KEY_LSHIFT.isKeyHeld() || Keyboard.KEY_RSHIFT.isKeyHeld()) add("SHIFT")
        if (Keyboard.KEY_LMENU.isKeyHeld() || Keyboard.KEY_RMENU.isKeyHeld()) add("ALT")
    }

    private fun snapshotNormalized(): String = normalizeCombo((currentModifiers() + chordBaseKeys).joinToString("+"))

    private fun tryUpdatePeak() {
        if (!chordActive || chordBaseKeys.isEmpty()) return
        val norm = snapshotNormalized()
        val candidate = synchronized(binds) { binds.firstOrNull { normalizeCombo(it.combo) == norm } }
        if (candidate != null) {
            val size = splitCombo(norm).second.size
            if (size > chordPeakSize) {
                chordPeakSize = size
                chordPeakCombo = candidate
            }
        }
    }

    private fun resetChord() {
        chordActive = false
        chordBaseKeys.clear()
        chordPeakCombo = null
        chordPeakSize = 0
    }

    @HandleEvent fun onKeyDown(e: KeyDownEvent) {
        try {
            //#if MC < 1.21.6
            val mc = Minecraft.getMinecraft()
            //#else
            //$$ val mc = MinecraftClient.getInstance()
            //#endif
            if (mc.currentScreen != null) { resetChord(); return }
            if (isModifier(e.keyCode)) return
            if (e.keyCode == -100 || e.keyCode == -99) return
            val name = keyName(e.keyCode)
            if (isVanillaBoundKeyName(name)) return
            chordActive = true
            chordBaseKeys += name
            tryUpdatePeak()
        } catch (_: Throwable) {}
    }

    @HandleEvent fun onKeyUp(e: KeyUpEvent) {
        try {
            //#if MC < 1.21.6
            val mc = Minecraft.getMinecraft()
            //#else
            //$$ val mc = MinecraftClient.getInstance()
            //#endif
            if (mc.currentScreen != null) { resetChord(); return }
            if (isModifier(e.keyCode)) return
            if (e.keyCode == -100 || e.keyCode == -99) return
            val name = keyName(e.keyCode)
            chordBaseKeys.remove(name)
            if (chordBaseKeys.isEmpty() && chordActive) {
                val toRun = chordPeakCombo?.takeIf { isAllowedNow(it) }
                if (toRun != null) executeCommandRaw(toRun.command.trim())
                resetChord()
            } else {
                tryUpdatePeak()
            }
        } catch (_: Throwable) {}
    }

    @HandleEvent fun onKeyPress(@Suppress("UNUSED_PARAMETER") e: KeyPressEvent) { }

    private fun persist() {
        try {
            val list = synchronized(binds) { binds.map { toSaved(it) }.toMutableList() }
            SkyHanniMod.feature.misc.let { it.keybinds = list }
            SkyHanniMod.launchCoroutine { SkyHanniMod.configManager.saveConfig(at.hannibal2.skyhanni.config.ConfigFileType.FEATURES, "Updated keybinds") }
        } catch (_: Throwable) {}
    }

    private fun loadFromConfig() {
        try {
            val cfgList = SkyHanniMod.feature.misc.keybinds
            synchronized(binds) {
                binds.clear(); binds.addAll(cfgList.mapNotNull { fromSaved(it) })
            }
        } catch (_: Throwable) {}
    }

    private fun toSaved(k: Keybind): at.hannibal2.skyhanni.config.features.misc.SavedKeybind {
        val s = at.hannibal2.skyhanni.config.features.misc.SavedKeybind()
        s.combo = k.combo; s.command = k.command
        s.allowedIslands = k.allowedIslands.map { it.name }.toMutableList()
        s.allowOutsideSkyBlock = k.allowOutsideSkyBlock
        return s
    }

    private fun fromSaved(s: at.hannibal2.skyhanni.config.features.misc.SavedKeybind): Keybind? {
        val islands = s.allowedIslands.mapNotNull { n -> IslandType.entries.find { it.name == n } }.toMutableSet()
        return Keybind(s.combo ?: return null, s.command ?: return null, islands, s.allowOutsideSkyBlock)
    }

    @HandleEvent fun onConfigLoad(@Suppress("UNUSED_PARAMETER") e: ConfigLoadEvent) { loadFromConfig() }

    @HandleEvent fun onCommandRegistration(e: CommandRegistrationEvent) {
        e.registerBrigadier("shkeybinds") {
            description = "Manage keybinds"
            simpleCallback {
                ChatUtils.chat("Attempting to open Keybinds editor")
                SkyHanniMod.screenToOpen = KeybindEditorGui()
            }
        }
    }
}
