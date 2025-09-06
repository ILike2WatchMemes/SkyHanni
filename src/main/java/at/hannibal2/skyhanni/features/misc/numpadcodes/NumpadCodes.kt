package at.hannibal2.skyhanni.features.misc.numpadcodes

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.commands.CommandsRegistry
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.events.minecraft.KeyPressEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import net.minecraft.client.Minecraft
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.iterator
import at.hannibal2.skyhanni.config.ConfigFileType

// Numpad codes: structured actions, per-action delay, client/server execution, island-aware, threaded overlay

// Single action with optional per-action delay and client-side flag
data class NumpadAction(
    val command: String,
    val delaySeconds: Double,
)

// now a code holds structured actions and allowed islands (IslandType)
data class NumpadCode(
    val code: String,
    val actions: List<NumpadAction> = emptyList(),
    val commandDelaySeconds: Double = 0.0, // default interval between actions for this code
    val allowedIslands: Set<IslandType> = setOf(IslandType.ANY),
    val allowOutsideSkyBlock: Boolean = false,
) {
    val isDeveloperCode: Boolean get() = code.firstOrNull() == '0'
}

data class NumpadSettings(
    var enabled: Boolean = false,
    var inputTimeoutSeconds: Double = 10.0,
    var defaultCommandDelaySeconds: Double = 1.0,
    var allowOtherIslands: Boolean = true,
)

object IslandManager {
    private val knownIslands = mutableSetOf(IslandType.ANY)
    private val allowedIslandsByCode = mutableMapOf<String, MutableSet<IslandType>>()
    var allowOtherIslandsByUser: Boolean = true

    fun addIsland(island: IslandType) {
        knownIslands.add(island)
    }

    fun getKnownIslands(): Set<IslandType> = knownIslands.toSet()
    fun registerCodeAllowedIslands(code: String, islands: Set<IslandType>) {
        allowedIslandsByCode[code] = islands.toMutableSet()
    }

    fun getAllowedIslandsFor(code: String): Set<IslandType> = allowedIslandsByCode[code] ?: setOf(IslandType.ANY)

    fun isAllowedOnIsland(code: String, island: IslandType): Boolean {
        val allowed = getAllowedIslandsFor(code)
        if (allowed.contains(IslandType.ANY)) return true
        if (allowed.contains(island)) return true
        if (allowed.contains(IslandType.UNKNOWN) && allowOtherIslandsByUser && !knownIslands.contains(island)) return true
        return false
    }
}

// Overlay data with MC color code string for UI convenience
data class OverlaySuggestion(
    val code: String,
    val mcColorCode: String,
    val remainingPresses: Int? = null,
)

data class OverlayState(
    val currentInput: String,
    val suggestions: List<OverlaySuggestion>,
    val highlightedCode: String? = null,
    val currentMcColorCode: String = "",
    val currentRemainingPresses: Int? = null,
)

@SkyHanniModule
object NumpadCodes {
    // Mapping to Minecraft formatting codes (section sign + code)
    private const val MC_RED = "§c"
    private const val MC_DARK_RED = "§4"
    private const val MC_LIGHT_GREEN = "§a"
    private const val MC_DARK_GREEN = "§2"
    private const val MC_BLUE = "§9"
    private const val MC_LIGHT_BLUE = "§3"
    private const val MC_GOLD = "§6"

    // keybinds read from config on load
    private var key_numpad0 = 0
    private var key_numpad1 = 0
    private var key_numpad2 = 0
    private var key_numpad3 = 0
    private var key_numpad4 = 0
    private var key_numpad5 = 0
    private var key_numpad6 = 0
    private var key_numpad7 = 0
    private var key_numpad8 = 0
    private var key_numpad9 = 0
    private var key_enter = 0
    private var key_deleteLast = 0
    private var key_clear = 0

    private val backgroundExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val commandExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var clearFuture: ScheduledFuture<*>? = null

    val settings = NumpadSettings()
    private val codes = mutableListOf<NumpadCode>()

    private val inputBuilder = StringBuilder()
    private val lastInputMillis = AtomicLong(0)
    private val lastOverlayComputeMillis = AtomicLong(0)

    var overlayListener: ((OverlayState) -> Unit)? = null
    val currentIsland get() = HypixelData.skyBlockIsland


    @Volatile
    private var lastOverlayState: OverlayState? = null

    // Provide read access for rendering code to display the latest overlay state
    fun getLastOverlayState(): OverlayState? = lastOverlayState

    init {
        // ensure there's a default listener that stores the state so the overlay can be rendered
        overlayListener = { state ->
            lastOverlayState = state
        }
    }

    fun register(code: NumpadCode) {
        val normalized = normalizeAllowed(code)
        synchronized(codes) {
            codes.removeAll { it.code == normalized.code }
            codes.add(normalized)
            IslandManager.registerCodeAllowedIslands(normalized.code, normalized.allowedIslands)
        }
        computeAndSendOverlay()
        try { ChatUtils.debug("[NumpadCodes] Registered code: ${normalized.code}") } catch (_: Throwable) {}
        try { ChatUtils.chat("Registered numpad code: ${normalized.code}", prefix = true) } catch (_: Throwable) {}
        try {
            val saved = synchronized(codes) { codes.map { toSavedNumpadCode(it) }.toMutableList() }
            SkyHanniMod.feature.numpad.savedCodes = saved
            SkyHanniMod.launchCoroutine { SkyHanniMod.configManager.saveConfig(ConfigFileType.FEATURES, "Updated numpad codes") }
        } catch (_: Throwable) {}
    }

    fun unregister(codeString: String) {
        synchronized(codes) { codes.removeAll { it.code == codeString } }
        computeAndSendOverlay()
        try {
            ChatUtils.debug("[NumpadCodes] Unregistered code: $codeString")
        } catch (_: Throwable) {
        }
        try {
            ChatUtils.chat("Unregistered numpad code: $codeString", prefix = true)
        } catch (_: Throwable) {
        }
        // persist removal to FEATURES config
        try {
            val saved = synchronized(codes) { codes.map { toSavedNumpadCode(it) }.toMutableList() }
            SkyHanniMod.feature.numpad.savedCodes = saved
            SkyHanniMod.launchCoroutine { SkyHanniMod.configManager.saveConfig(ConfigFileType.FEATURES, "Updated numpad codes") }
        } catch (_: Throwable) {
        }
    }

    fun allCodes(): List<NumpadCode> = synchronized(codes) { codes.toList() }

    // helpers to convert between runtime NumpadCode and serializable SavedNumpadCode stored in FEATURES config
    private fun toSavedNumpadCode(c: NumpadCode): at.hannibal2.skyhanni.config.features.misc.SavedNumpadCode {
        val actions = c.actions.map { at.hannibal2.skyhanni.config.features.misc.SavedNumpadAction(it.command, it.delaySeconds) }.toMutableList()
        return at.hannibal2.skyhanni.config.features.misc.SavedNumpadCode(c.code, actions, c.commandDelaySeconds, c.allowedIslands.toMutableSet(), c.allowOutsideSkyBlock)
    }

    fun processKey(key: String) {
        if (!settings.enabled) return
        when (key) {
            "ENTER" -> attemptActivate()
            "+" -> deleteLast()
            "-" -> clear()
            else -> if (key.length == 1 && key[0].isDigit()) appendDigit(key[0])
        }
    }

    private fun fromSavedNumpadCode(s: at.hannibal2.skyhanni.config.features.misc.SavedNumpadCode): NumpadCode {
        val actions = s.actions.map { NumpadAction(it.command, it.delaySeconds) }
        return NumpadCode(s.code, actions, s.commandDelaySeconds, s.allowedIslands.toSet(), s.allowOutsideSkyBlock)
    }


    @HandleEvent
    fun onKeyPress(event: KeyPressEvent) {
        if (!settings.enabled) return
        if (Minecraft.getMinecraft().currentScreen != null) return

        val mapped = when (event.keyCode) {
            key_numpad0 -> "0"
            key_numpad1 -> "1"
            key_numpad2 -> "2"
            key_numpad3 -> "3"
            key_numpad4 -> "4"
            key_numpad5 -> "5"
            key_numpad6 -> "6"
            key_numpad7 -> "7"
            key_numpad8 -> "8"
            key_numpad9 -> "9"
            key_enter -> "ENTER"
            key_deleteLast -> "+"
            key_clear -> "-"
            else -> null
        }

        if (mapped == null) return
        backgroundExecutor.submit { processKey(mapped) }
    }

    // load saved codes from FEATURES config without triggering additional saves
    private fun loadSavedCodesFromConfig() {
        try {
            val saved = SkyHanniMod.feature.numpad.savedCodes ?: return
            synchronized(codes) {
                codes.clear()
                IslandType.entries.filter { it.isValidIsland() }.toSet()
                for (s in saved) {
                    try {
                        val nc = fromSavedNumpadCode(s)
                        var normalized = normalizeAllowed(nc)
                        // Do not expand UNKNOWN; it remains a fallback for future islands
                        // Ensure all explicit islands are valid (filter out NONE/ANY artifacts)
                        normalized = normalized.copy(allowedIslands = normalized.allowedIslands.filter { it.isValidIsland() || it == IslandType.UNKNOWN }.toSet())
                        codes.add(normalized)
                        IslandManager.registerCodeAllowedIslands(normalized.code, normalized.allowedIslands)
                    } catch (_: Throwable) {}
                }
            }
            computeAndSendOverlay()
        } catch (_: Throwable) {}
    }

    private fun scheduleClear() {
        clearFuture?.cancel(false)
        val timeoutMs = (settings.inputTimeoutSeconds * 1000.0).toLong().coerceAtLeast(0L)
        clearFuture = backgroundExecutor.schedule({ clear() }, timeoutMs, TimeUnit.MILLISECONDS)
        lastInputMillis.set(System.currentTimeMillis())
    }

    private fun appendDigit(d: Char) {
        synchronized(inputBuilder) { inputBuilder.append(d) }
        scheduleClear()
        computeAndSendOverlay()
    }

    private fun deleteLast() {
        synchronized(inputBuilder) { if (inputBuilder.isNotEmpty()) inputBuilder.deleteCharAt(inputBuilder.length - 1) }
        scheduleClear()
        computeAndSendOverlay()
    }

    fun clear() {
        extraEnterCount = 0
        synchronized(inputBuilder) { inputBuilder.clear() }
        clearFuture?.cancel(false)
        computeAndSendOverlay()
    }

    private fun currentInput(): String = synchronized(inputBuilder) { inputBuilder.toString() }

    private fun exactMatchingCodes(input: String): List<NumpadCode> =
        allCodes().filter { it.code == input && isAllowedNow(it) }

    private fun computeAndSendOverlay() {
        val input = currentInput()
        val codesSnapshot = allCodes()
        backgroundExecutor.submit {
            val prefixMatches =
                codesSnapshot.filter { it.code.startsWith(input) && isAllowedNow(it) }
            val exactMatches = codesSnapshot.filter { it.code == input && isAllowedNow(it) }

            val baseSuggestions = if (input.isEmpty()) codesSnapshot.filter { isAllowedNow(it) }
            else codesSnapshot.filter { it.code.startsWith(input) && isAllowedNow(it) }

            val suggestions = mutableListOf<OverlaySuggestion>()
            for (c in baseSuggestions) {
                val codeLen = c.code.length
                val actionsCount = c.actions.size
                val isExact = input == c.code
                var mcCode: String
                var remaining: Int? = null
                if (isExact && actionsCount > codeLen + 1) {
                    val extraNeededTotal = actionsCount - (codeLen + 1)
                    val extraPressed = if (pendingExtraCode == c.code) extraEnterCount else 0
                    val extraRemaining = (extraNeededTotal - extraPressed).coerceAtLeast(0)
                    if (extraRemaining > 0) {
                        mcCode = MC_GOLD
                        remaining = extraRemaining
                    } else {
                        mcCode = if (c.isDeveloperCode) MC_BLUE else MC_DARK_GREEN
                    }
                } else {
                    mcCode = when {
                        isExact && c.isDeveloperCode -> MC_BLUE
                        isExact -> MC_DARK_GREEN
                        c.code.startsWith(input) && input.isNotEmpty() && c.isDeveloperCode -> MC_LIGHT_BLUE
                        c.code.startsWith(input) && input.isNotEmpty() -> MC_RED
                        else -> MC_DARK_RED
                    }
                }
                suggestions += OverlaySuggestion(c.code, mcCode, remaining)
            }

            val highlighted = when {
                exactMatches.isNotEmpty() -> exactMatches.first().code
                prefixMatches.isNotEmpty() -> prefixMatches.first().code
                else -> null
            }

            var currentRemaining: Int? = null
            val currentMcCode = if (input.isEmpty()) {
                ""
            } else {
                val exactCode = codesSnapshot.firstOrNull { it.code == input && isAllowedNow(it) }
                if (exactCode != null) {
                    val codeLen = exactCode.code.length
                    val actionsCount = exactCode.actions.size
                    if (actionsCount > codeLen + 1) {
                        val extraTotal = actionsCount - (codeLen + 1)
                        val pressed = if (pendingExtraCode == exactCode.code) extraEnterCount else 0
                        val remain = (extraTotal - pressed).coerceAtLeast(0)
                        if (remain > 0) {
                            currentRemaining = remain
                            MC_GOLD
                        } else if (exactCode.isDeveloperCode) MC_BLUE else MC_DARK_GREEN
                    } else if (exactCode.isDeveloperCode) MC_BLUE else MC_DARK_GREEN
                } else {
                    val anyStarts = codesSnapshot.any { it.code.startsWith(input) && isAllowedNow(it) }
                    val useBlue = input.startsWith('0')
                    when {
                        anyStarts && useBlue -> MC_LIGHT_BLUE
                        anyStarts -> MC_RED
                        else -> MC_DARK_RED
                    }
                }
            }


            val state = OverlayState(input, suggestions, highlighted, currentMcCode, currentRemaining)
            DelayedRun.onThread.execute { overlayListener?.invoke(state) }
            lastOverlayComputeMillis.set(System.currentTimeMillis())
        }
    }

    @HandleEvent
    fun onTick() {
        val now = System.currentTimeMillis()
        val last = lastOverlayComputeMillis.get()
        if (now - last > 500L) {
            if (lastOverlayComputeMillis.compareAndSet(last, now)) {
                computeAndSendOverlay()
            }
        }
    }

    private var pendingExtraCode: String = ""
    private var extraEnterCount: Int = 0 // number of extra ENTER presses AFTER the initial one

    private fun attemptActivate() {
        val input = currentInput()
        if (input.isEmpty()) return
        val exact = exactMatchingCodes(input)
        if (exact.isEmpty()) return
        val candidate = exact.firstOrNull { isAllowedNow(it) } ?: exact.first()
        val codeLen = candidate.code.length
        val actionsCount = candidate.actions.size
        if (actionsCount <= codeLen + 1) {
            // No extra enters needed
            SkyHanniMod.launchCoroutine { executeCode(candidate) }
            clear()
            return
        }
        val extraNeededTotal = actionsCount - (codeLen + 1)
        if (pendingExtraCode != candidate.code) {
            // initial ENTER: start gating phase, do NOT count yet
            pendingExtraCode = candidate.code
            extraEnterCount = 0
            computeAndSendOverlay()
            return
        }
        // subsequent ENTERs increment count
        extraEnterCount += 1
        val remaining = extraNeededTotal - extraEnterCount
        if (remaining <= 0) {
            clear()
            SkyHanniMod.launchCoroutine { executeCode(candidate) }
        } else computeAndSendOverlay()
    }

    // schedule each NumpadAction individually using its own delaySeconds or defaults
    private fun executeCode(code: NumpadCode) {
        var accumulatedMs = 0L
        for (action in code.actions) {
            val interval = action.delaySeconds
            accumulatedMs += (interval * 1000.0).toLong()
            commandExecutor.schedule(
                {
                    try {
                        executeAction(action)
                    } catch (_: Exception) {
                    }
                },
                accumulatedMs, TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun executeAction(action: NumpadAction) {
        val raw = action.command.trim()
        if (raw.isEmpty()) return

        try {
            CommandsRegistry.execAutomaticCommand(raw)
        } catch (e: Exception) {
            println("[NumpadCodes] Failed to execute action: ${action.command} -> ${e.message}")
        }
    }

    private fun autoPersistNewIslands() {
        // Add any newly introduced valid islands into codes that opted into UNKNOWN after config load
        val validIslands = IslandType.entries.filter { it.isValidIsland() }
        var changed = false
        synchronized(codes) {
            for (i in codes.indices) {
                val current = codes[i]
                if (IslandType.UNKNOWN in current.allowedIslands) {
                    val missing = validIslands.filter { it !in current.allowedIslands }
                    if (missing.isNotEmpty()) {
                        val updatedAllowed = current.allowedIslands + missing
                        val updated = current.copy(allowedIslands = updatedAllowed)
                        codes[i] = updated
                        IslandManager.registerCodeAllowedIslands(updated.code, updated.allowedIslands)
                        changed = true
                    }
                }
            }
        }
        if (changed) {
            try {
                val saved = synchronized(codes) { codes.map { toSavedNumpadCode(it) }.toMutableList() }
                SkyHanniMod.feature.numpad.savedCodes = saved
                SkyHanniMod.launchCoroutine { SkyHanniMod.configManager.saveConfig(ConfigFileType.FEATURES, "Auto-persisted new islands for UNKNOWN numpad codes") }
                ChatUtils.debug("[NumpadCodes] Auto-persisted new islands into codes using UNKNOWN sentinel")
            } catch (_: Throwable) {}
            computeAndSendOverlay()
        }
    }

    @HandleEvent
    fun onConfigLoad(@Suppress("UNUSED_PARAMETER") event: ConfigLoadEvent) {
        try {
            val cfg = SkyHanniMod.feature.numpad
            settings.enabled = cfg.enabled
            settings.inputTimeoutSeconds = cfg.inputTimeoutSeconds
            settings.defaultCommandDelaySeconds = 1.0

            key_numpad0 = cfg.key_numpad0
            key_numpad1 = cfg.key_numpad1
            key_numpad2 = cfg.key_numpad2
            key_numpad3 = cfg.key_numpad3
            key_numpad4 = cfg.key_numpad4
            key_numpad5 = cfg.key_numpad5
            key_numpad6 = cfg.key_numpad6
            key_numpad7 = cfg.key_numpad7
            key_numpad8 = cfg.key_numpad8
            key_numpad9 = cfg.key_numpad9
            key_enter = cfg.key_enter
            key_deleteLast = cfg.key_deleteLast
            key_clear = cfg.key_clear
            // load persisted saved codes
            loadSavedCodesFromConfig()
            // After loading, ensure we propagate any newly added islands to UNKNOWN-enabled codes
            autoPersistNewIslands()
        } catch (e: Exception) {
            ChatUtils.debug("Failed to sync numpad config: ${e.message}")
        }
    }

    @HandleEvent
    fun onCommandRegistration(event: CommandRegistrationEvent) {
        event.registerBrigadier("shnumpad") {
            category = CommandCategory.USERS_ACTIVE
            description = "Manage numpad codes"
            simpleCallback {
                ChatUtils.chat("Attempting to open Numpad editor")
                SkyHanniMod.screenToOpen = NumpadEditorGui()
            }

        }
    }

    fun validateNoDuplicate(): List<Pair<String, List<String>>> {
        val grouped = allCodes().groupBy { it.code }
        val conflicts = mutableListOf<Pair<String, List<String>>>()
        for ((codeStr, list) in grouped) {
            if (list.size <= 1) continue
            for (i in 0 until list.size) for (j in i + 1 until list.size) {
                val a = list[i]
                val b = list[j]
                val intersect = intersectIslands(a.allowedIslands, b.allowedIslands)
                if (intersect.isNotEmpty()) conflicts += Pair(codeStr, intersect.map { it.displayName })
            }
        }
        return conflicts
    }

    private fun intersectIslands(a: Set<IslandType>, b: Set<IslandType>): Set<IslandType> {
        if (a.contains(IslandType.ANY) && b.contains(IslandType.ANY)) return IslandManager.getKnownIslands()
        if (a.contains(IslandType.ANY)) return b
        if (b.contains(IslandType.ANY)) return a
        val intersection = a.intersect(b)
        if (a.contains(IslandType.UNKNOWN) && b.contains(IslandType.UNKNOWN) && IslandManager.allowOtherIslandsByUser) return IslandManager.getKnownIslands()
        return intersection
    }

    fun clearAllCodes() {
        synchronized(codes) { codes.clear() }
        computeAndSendOverlay()
    }

    private fun normalizeAllowed(code: NumpadCode): NumpadCode {
        if (IslandType.ANY !in code.allowedIslands) return code
        val keepUnknown = IslandType.UNKNOWN in code.allowedIslands
        val explicit = IslandType.entries.filter { it.isValidIsland() }.toMutableSet()
        if (keepUnknown) explicit += IslandType.UNKNOWN
        return code.copy(allowedIslands = explicit)
    }

    private fun isAllowedNow(code: NumpadCode): Boolean {
        val island = currentIsland
        if (island == IslandType.NONE) return code.allowOutsideSkyBlock
        val allowed = code.allowedIslands
        if (island in allowed) return true
        // UNKNOWN acts as future island fallback (island not explicitly listed)
        if (IslandType.UNKNOWN in allowed && island !in allowed) return true
        return false
    }
}

// minimal config GUI stub unchanged
class NumpadConfigGui {
    private val pendingCodes = mutableListOf<NumpadCode>()
    fun loadFromManager() {
        pendingCodes.clear(); pendingCodes.addAll(NumpadCodes.allCodes())
    }

    fun addOrUpdate(code: NumpadCode) {
        pendingCodes.removeAll { it.code == code.code }; pendingCodes.add(code)
    }

    fun remove(codeString: String) {
        pendingCodes.removeAll { it.code == codeString }
    }

    fun save(): List<String> {
        val original = NumpadCodes.allCodes(); NumpadCodes.clearAllCodes(); for (c in pendingCodes) NumpadCodes.register(c)
        val conflicts =
            NumpadCodes.validateNoDuplicate(); NumpadCodes.clearAllCodes(); for (c in original) NumpadCodes.register(c); return conflicts.map {
            "Code '${it.first}' conflicts on islands: ${
                it.second.joinToString(
                    ",",
                )
            }"
        }
    }
}
