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
import net.minecraft.client.Minecraft
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
    private val pressedBaseKeys = mutableSetOf<String>()
    private var lastTriggeredMultiCombo: Set<String>? = null
    // New: remember last fired normalized combo while its base keys are still held to avoid repeated execution
    private var activeFiredCombo: String? = null
    // Remember the exact set of base key names that fired last, to allow only one reactivation when the held set changes
    private var lastFiredBaseSet: Set<String>? = null

    // --- Normalization & validation ---
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

    /** Detect subset/superset conflicts with existing combos sharing identical modifier sets. */
    fun conflictError(normalized: String): String? {
        val (modsNew, baseNew) = splitCombo(normalized)
        synchronized(binds) {
            for (b in binds) {
                val normOld = normalizeCombo(b.combo)
                if (normOld == normalized) continue
                val (modsOld, baseOld) = splitCombo(normOld)
                if (modsOld != modsNew) continue
                if (baseOld.containsAll(baseNew) || baseNew.containsAll(baseOld)) {
                    return "Combo conflicts with existing combo '${'$'}normOld' (subset/superset)"
                }
            }
        }
        return null
    }

    // --- Registration ---
    fun register(b: Keybind) {
        val normalized = normalizeCombo(b.combo)
        conflictError(normalized)?.let {
            ChatUtils.userError(it); return
        }
        val toAdd = b.copy(combo = normalized)
        synchronized(binds) {
            binds.removeAll { normalizeCombo(it.combo) == normalized }
            binds.add(toAdd)
        }
        persist()
        try { ChatUtils.chat("Registered keybind: ${'$'}{toAdd.combo}", prefix = true) } catch (_: Throwable) {}
    }

    fun unregister(combo: String) {
        val normalized = normalizeCombo(combo)
        synchronized(binds) { binds.removeAll { normalizeCombo(it.combo) == normalized } }
        persist()
        try { ChatUtils.chat("Unregistered keybind: ${'$'}normalized", prefix = true) } catch (_: Throwable) {}
    }

    fun allBinds(): List<Keybind> = synchronized(binds) { binds.toList() }

    // --- Environment checks ---
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
            ErrorManager.skyHanniError("Keybinds: Failed to execute command: ${'$'}cmd")
        }
    }

    // --- Key helpers ---
    private fun keyName(code: Int): String = try { KeyboardManager.getKeyName(code).uppercase() } catch (_: Throwable) { code.toString() }
    private fun isModifier(code: Int) = code in setOf(Keyboard.KEY_LCONTROL, Keyboard.KEY_RCONTROL, Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT, Keyboard.KEY_LMENU, Keyboard.KEY_RMENU)
    private fun currentModifiers(): Set<String> = buildSet {
        if (Keyboard.KEY_LCONTROL.isKeyHeld() || Keyboard.KEY_RCONTROL.isKeyHeld()) add("CTRL")
        if (Keyboard.KEY_LSHIFT.isKeyHeld() || Keyboard.KEY_RSHIFT.isKeyHeld()) add("SHIFT")
        if (Keyboard.KEY_LMENU.isKeyHeld() || Keyboard.KEY_RMENU.isKeyHeld()) add("ALT")
    }

    private fun buildAttempt(mods: Set<String>, base: Set<String>) = normalizeCombo((mods + base).joinToString("+"))

    // --- Event handling (now press-based execution) ---
    @HandleEvent fun onKeyDown(e: KeyDownEvent) {
        try {
            val mc = Minecraft.getMinecraft()
            if (mc.currentScreen != null) { // if a screen is open we don't want to keep stale state
                pressedBaseKeys.clear(); activeFiredCombo = null; lastTriggeredMultiCombo = null; return
            }
            if (isModifier(e.keyCode)) return
            if (e.keyCode == -100 || e.keyCode == -99) return // ignore left/right mouse
            val name = keyName(e.keyCode)
            pressedBaseKeys += name
            val attempt = buildAttempt(currentModifiers(), pressedBaseKeys)
            if (attempt != activeFiredCombo) {
                val candidate = allBinds().firstOrNull { normalizeCombo(it.combo) == attempt }
                if (candidate != null && isAllowedNow(candidate)) {
                    executeCommandRaw(candidate.command.trim())
                    activeFiredCombo = attempt
                    lastFiredBaseSet = pressedBaseKeys.toSet()
                    lastTriggeredMultiCombo = pressedBaseKeys.toSet().takeIf { it.size > 1 }
                    try { ChatUtils.chat("Executed keybind ${candidate.combo}") } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
    }

    @HandleEvent fun onKeyUp(e: KeyUpEvent) {
        try {
            val mc = Minecraft.getMinecraft()
            if (mc.currentScreen != null) { // clear stale keys if screen opens mid combo
                pressedBaseKeys.clear(); activeFiredCombo = null; lastTriggeredMultiCombo = null; lastFiredBaseSet = null; return
            }
            if (isModifier(e.keyCode)) { // releasing a modifier changes combo context; allow re-fire next time
                activeFiredCombo = null
                lastFiredBaseSet = null
                return
            }
            if (e.keyCode == -100 || e.keyCode == -99) return // ignore left/right mouse
            pressedBaseKeys.remove(keyName(e.keyCode))
            if (pressedBaseKeys.isEmpty()) {
                // reset once all base keys released so combo can trigger again
                activeFiredCombo = null
                lastTriggeredMultiCombo = null
                lastFiredBaseSet = null
            } else {
                // If the currently held base keys form a DIFFERENT (smaller) combo than what fired, allow new fire once
                val attempt = buildAttempt(currentModifiers(), pressedBaseKeys)
                if (lastFiredBaseSet != null && pressedBaseKeys != lastFiredBaseSet) {
                    // a key was released compared to what fired -> allow re-fire
                    activeFiredCombo = null
                    lastFiredBaseSet = null
                } else if (attempt != activeFiredCombo) activeFiredCombo = null
            }
        } catch (_: Throwable) {}
    }

    // Legacy press event intentionally unused (kept for compatibility)
    @HandleEvent fun onKeyPress(@Suppress("UNUSED_PARAMETER") e: KeyPressEvent) { }

    // --- Persistence ---
    private fun persist() {
        try {
            val list = synchronized(binds) { binds.map { toSaved(it) }.toMutableList() }
            SkyHanniMod.feature.misc.let { it.keybinds = list }
            try { SkyHanniMod.launchCoroutine { SkyHanniMod.configManager.saveConfig(at.hannibal2.skyhanni.config.ConfigFileType.FEATURES, "Updated keybinds") } } catch (_: Throwable) {}
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
