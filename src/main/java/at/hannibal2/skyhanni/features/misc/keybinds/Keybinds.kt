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
//#if MC < 1.21
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

    // replaced name tracking with code tracking to avoid mismatches (e.g. mouse buttons)
    private val chordBaseKeyCodes = mutableSetOf<Int>()
    private var chordPeakCombo: Keybind? = null
    private var chordPeakSize = 0

    @Volatile private var cachedVanillaKeys: Map<String, String>? = null
    @Volatile private var cachedVanillaKeyCodes: Set<Int>? = null
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
        return base.mapNotNull { k -> vanilla[k]?.let { "$it ($k)" } }
    }

    fun vanillaBoundKeyNames(): Map<String, String> {
        val now = System.currentTimeMillis()
        val cached = cachedVanillaKeys
        if (cached != null && (now - lastVanillaCacheStamp) < 5_000) return cached
        val map = mutableMapOf<String, String>()
        val codes = mutableSetOf<Int>()
        try {
            //#if MC < 1.21
            try {
                val mc = Minecraft.getMinecraft()
                for (kb in mc.gameSettings.keyBindings) {
                    try {
                        val code = kb.keyCode
                        if (code == 0) continue
                        val description = kb.keyDescription ?: "Unknown"
                        // Only include core vanilla Minecraft keys, not mod keys
                        // This ensures we get proper movement/action keys detection
                        codes += code
                        val keyName = keyName(code)
                        map[keyName] = humanizeDescription(description)
                    } catch (_: Throwable) {
                    }
                }
            } catch (_: Throwable) {
            }
            //#else
            //$$ try {
            //$$     val mc = MinecraftClient.getInstance()
            //$$     for (kb in mc.options.allKeys) {
            //$$         try {
            //$$             val code = kb.boundKey.code
            //$$             if (code == null || code == -1) continue
            //$$             val label = kb.translationKey
            //$$             codes += code
            //$$             val keyName = keyName(code)
            //$$             map[keyName] = humanizeDescription(label)
            //$$         } catch (_: Throwable) {}
            //$$     }
            //$$ } catch (_: Throwable) {}
            //#endif
        } catch (_: Throwable) {
        }
        cachedVanillaKeys = map
        cachedVanillaKeyCodes = codes
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

    private fun isVanillaKeyCode(code: Int): Boolean {
        vanillaBoundKeyNames() // refresh if needed
        return cachedVanillaKeyCodes?.contains(code) == true
    }

    fun register(b: Keybind) {
        val normalized = normalizeCombo(b.combo)
        if (duplicateExists(normalized, null, b.command)) {
            ChatUtils.userError("Combo already in use: $normalized"); return
        }
        // Show warning but don't block registration - vanilla keys will be ignored during execution
        val vanillaConflicts = vanillaKeyConflicts(normalized)
        if (vanillaConflicts.isNotEmpty()) {
            ChatUtils.chat(
                "§eWarning: Combo uses vanilla key(s): ${vanillaConflicts.joinToString(", ")} - these will be ignored when held",
                prefix = true,
            )
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
        try {
            CommandsRegistry.execAutomaticCommand(cmd)
        } catch (_: Throwable) {
            ErrorManager.skyHanniError("Keybinds: Failed to execute command: $cmd")
        }
    }

    private fun keyName(code: Int): String = try {
        KeyboardManager.getKeyName(code).uppercase()
    } catch (_: Throwable) {
        code.toString()
    }

    private fun isModifier(code: Int) = code in setOf(
        Keyboard.KEY_LCONTROL,
        Keyboard.KEY_RCONTROL,
        Keyboard.KEY_LSHIFT,
        Keyboard.KEY_RSHIFT,
        Keyboard.KEY_LMENU,
        Keyboard.KEY_RMENU,
    )

    private fun currentModifiers(): Set<String> = buildSet {
        if (Keyboard.KEY_LCONTROL.isKeyHeld() || Keyboard.KEY_RCONTROL.isKeyHeld()) add("CTRL")
        if (Keyboard.KEY_LSHIFT.isKeyHeld() || Keyboard.KEY_RSHIFT.isKeyHeld()) add("SHIFT")
        if (Keyboard.KEY_LMENU.isKeyHeld() || Keyboard.KEY_RMENU.isKeyHeld()) add("ALT")
    }

    private fun currentBaseKeyNames(): Set<String> = chordBaseKeyCodes.map { keyName(it) }.toSet()

    private fun snapshotNormalized(): String = normalizeCombo((currentModifiers() + currentBaseKeyNames()).joinToString("+"))

    private fun tryUpdatePeak() {
        if (!chordActive || chordBaseKeyCodes.isEmpty()) return
        val curMods = currentModifiers()
        val baseNames = currentBaseKeyNames()
        var best: Keybind? = chordPeakCombo
        var bestBaseSize = chordPeakSize
        var bestModsSize = if (chordPeakCombo == null) -1 else splitCombo(normalizeCombo(chordPeakCombo!!.combo)).first.size
        synchronized(binds) {
            for (b in binds) {
                val norm = normalizeCombo(b.combo)
                val (mods, base) = splitCombo(norm)
                // modifiers must be subset of currently held modifiers
                if (!mods.all { it in curMods }) continue
                // base keys must be subset of currently held custom base keys
                if (!base.all { it in baseNames }) continue
                val baseSize = base.size
                val modsSize = mods.size
                val better = when {
                    baseSize > bestBaseSize -> true
                    baseSize == bestBaseSize && modsSize > bestModsSize -> true
                    else -> false
                }
                if (better) {
                    best = b
                    bestBaseSize = baseSize
                    bestModsSize = modsSize
                }
            }
        }
        if (best != null) {
            chordPeakCombo = best
            chordPeakSize = bestBaseSize
        }
    }

    private fun maybeExecuteImmediate() {
        if (!chordActive || chordBaseKeyCodes.isEmpty()) return
        val curMods = currentModifiers()
        val baseNames = currentBaseKeyNames()
        // Find exact matches (mods and base sets exactly equal current)
        val exactMatches = synchronized(binds) {
            binds.filter { b ->
                val (mods, base) = splitCombo(normalizeCombo(b.combo))
                mods == curMods && base == baseNames
            }
        }
        if (exactMatches.isEmpty()) return
        // If there exists any other bind that could extend this (same modifiers, strictly superset base), defer.
        val hasPotentialExtension = synchronized(binds) {
            binds.any { b ->
                val (mods, base) = splitCombo(normalizeCombo(b.combo))
                mods == curMods && baseNames.all { it in base } && base.size > baseNames.size
            }
        }
        if (hasPotentialExtension) return
        // Choose longest command string (arbitrary stable choice if multiple). Could also just take first.
        val toRun = exactMatches.maxByOrNull { it.command.length }?.takeIf { isAllowedNow(it) } ?: return
        executeCommandRaw(toRun.command.trim())
        resetChord()
    }

    private fun resetChord() {
        chordActive = false
        chordBaseKeyCodes.clear()
        chordPeakCombo = null
        chordPeakSize = 0
    }

    @HandleEvent
    fun onKeyDown(e: KeyDownEvent) {
        if (binds.isEmpty()) return
        try {
            //#if MC < 1.21
            val mc = Minecraft.getMinecraft()
            //#else
            //$$ val mc = MinecraftClient.getInstance()
            //#endif
            if (mc.currentScreen != null) {
                resetChord(); return
            }
            val code = e.keyCode
            if (isModifier(code)) return
            // Ignore vanilla movement/action keys (do not consume or create base entries)
            if (isVanillaKeyCode(code)) return
            chordActive = true
            chordBaseKeyCodes += code
            tryUpdatePeak()
            maybeExecuteImmediate() // new immediate execution logic
        } catch (_: Throwable) {
        }
    }

    @HandleEvent
    fun onKeyUp(e: KeyUpEvent) {
        if (binds.isEmpty()) return
        try {
            //#if MC < 1.21
            val mc = Minecraft.getMinecraft()
            //#else
            //$$ val mc = MinecraftClient.getInstance()
            //#endif
            if (mc.currentScreen != null) {
                resetChord(); return
            }
            val code = e.keyCode
            if (isModifier(code)) return
            // Releasing an ignored vanilla key: if no custom base keys remain, execute
            if (isVanillaKeyCode(code)) {
                if (chordBaseKeyCodes.isEmpty() && chordActive) {
                    val toRun = chordPeakCombo?.takeIf { isAllowedNow(it) }
                    if (toRun != null) executeCommandRaw(toRun.command.trim())
                    resetChord()
                }
                return
            }
            chordBaseKeyCodes.remove(code)
            if (chordBaseKeyCodes.isEmpty() && chordActive) {
                val toRun = chordPeakCombo?.takeIf { isAllowedNow(it) }
                if (toRun != null) executeCommandRaw(toRun.command.trim())
                resetChord()
            } else {
                tryUpdatePeak()
            }
        } catch (_: Throwable) {
        }
    }

    @HandleEvent
    fun onKeyPress(@Suppress("UNUSED_PARAMETER") e: KeyPressEvent) {
    }

    private fun persist() {
        try {
            val list = synchronized(binds) { binds.map { toSaved(it) }.toMutableList() }
            SkyHanniMod.feature.misc.let { it.keybinds = list }
            SkyHanniMod.launchCoroutine {
                SkyHanniMod.configManager.saveConfig(
                    at.hannibal2.skyhanni.config.ConfigFileType.FEATURES,
                    "Updated keybinds",
                )
            }
        } catch (_: Throwable) {
        }
    }

    private fun loadFromConfig() {
        try {
            val cfgList = SkyHanniMod.feature.misc.keybinds
            synchronized(binds) {
                binds.clear(); binds.addAll(cfgList.mapNotNull { fromSaved(it) })
            }
        } catch (_: Throwable) {
        }
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

    @HandleEvent
    fun onConfigLoad(@Suppress("UNUSED_PARAMETER") e: ConfigLoadEvent) {
        loadFromConfig()
    }

    @HandleEvent
    fun onCommandRegistration(e: CommandRegistrationEvent) {
        e.registerBrigadier("shkeybinds") {
            description = "Manage keybinds"
            simpleCallback {
                ChatUtils.chat("Attempting to open Keybinds editor")
                SkyHanniMod.screenToOpen = KeybindEditorGui()
            }
        }
    }
}
