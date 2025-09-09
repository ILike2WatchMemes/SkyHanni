package at.hannibal2.skyhanni.features.misc.numpadcodes

import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.config.commands.CommandsRegistry
import net.minecraft.client.Minecraft
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.suggestion.Suggestions
import java.util.concurrent.CompletableFuture

/**
 * Backend helper for editing Numpad codes in a GUI. Keeps actions as editable rows with
 * separate command text and per-action delay (Double) and client-side checkbox.
 * Also provides command suggestions via the project's SuggestionProvider.
 */
class NumpadEditor {
    data class EditorAction(var command: String = "", var delaySeconds: Double = 1.0)
    data class EditorCode(
        var code: String = "",
        var actions: MutableList<EditorAction> = mutableListOf(),
        var defaultDelaySeconds: Double = 1.0,
        var allowedIslands: MutableSet<IslandType> = mutableSetOf(IslandType.ANY),
        var allowOutsideSkyBlock: Boolean = false,
    )

    /**
     * Full Brigadier parameter-context suggestions.
     * Strategy:
     *  - Respect cursor position inside the string (not only at end).
     *  - For leading '/' treat as chat command form; keep slash ONLY for the first token (before any space).
     *  - Parse only substring up to cursor to avoid brigadier range beyond cursor.
     *  - Merge client and server dispatcher suggestions; preserve order, remove duplicates.
     *  - If no suggestions found, fallback to parsing full input.
     */
    fun suggestFor(input: String, cursor: Int = input.length): List<String> {
        if (input.isBlank()) return emptyList()
        val mcPlayer = try {
            Minecraft.getMinecraft().thePlayer
        } catch (_: Throwable) {
            null
        } ?: return emptyList()
        val client: CommandDispatcher<Any> = try {
            CommandsRegistry.getDispatcher() as CommandDispatcher<Any>
        } catch (_: Throwable) {
            return emptyList()
        }
        val server: CommandDispatcher<Any>? = try {
            CommandsRegistry.mcServerDispatcher()
        } catch (_: Throwable) {
            null
        }

        val effectiveCursor = cursor.coerceIn(0, input.length)
        val leadingSlash = input.startsWith('/')
        val rawForParseFull = if (leadingSlash) input.drop(1) else input
        val rawForParseCursor =
            rawForParseFull.take(
                (if (leadingSlash) effectiveCursor - 1 else effectiveCursor).coerceAtLeast(0).coerceAtMost(rawForParseFull.length),
            )

        fun collect(dispatcher: CommandDispatcher<Any>, partial: String): CompletableFuture<Suggestions> {
            try {
                val parse: ParseResults<Any> = dispatcher.parse(partial, mcPlayer)
                return dispatcher.getCompletionSuggestions(parse)
            } catch (_: Throwable) {
                return Suggestions.empty()
            }
        }

        // Helper: only prefix slash when we are at first token (no space yet before cursor)
        fun applyLeadingSlash(list: Collection<String>): List<String> {
            if (!leadingSlash) return list.toList()
            val firstTokenPhase = !rawForParseCursor.contains(' ')
            return list.map { s -> if (firstTokenPhase && !s.startsWith('/')) "/$s" else s }
        }

        // First attempt: suggestions at cursor substring (partial line)
        val clientFuture = collect(client, rawForParseCursor)
        val serverFuture = server?.let { collect(it, rawForParseCursor) }

        val baseClient = runCatching { clientFuture.get().list }.getOrElse { emptyList() }
        val baseServer = runCatching { serverFuture?.get()?.list ?: emptyList() }.getOrElse { emptyList() }
        val mergedPrimary = LinkedHashSet<String>()
        for (s in baseClient) mergedPrimary += s.text
        for (s in baseServer) mergedPrimary += s.text

        if (mergedPrimary.isNotEmpty()) {
            return applyLeadingSlash(mergedPrimary)
        }

        // Fallback: parse full input (handles cases where cursor not at split point or brigadier needs full context)
        val clientFull = collect(client, rawForParseFull)
        val serverFull = server?.let { collect(it, rawForParseFull) }
        val fullClientList = runCatching { clientFull.get().list }.getOrElse { emptyList() }
        val fullServerList = runCatching { serverFull?.get()?.list ?: emptyList() }.getOrElse { emptyList() }
        val mergedFallback = LinkedHashSet<String>()
        for (s in fullClientList) mergedFallback += s.text
        for (s in fullServerList) mergedFallback += s.text

        return applyLeadingSlash(mergedFallback)
    }

    fun validate(editorCode: EditorCode): List<String> {
        val errors = mutableListOf<String>()
        if (editorCode.code.isBlank()) errors += "Code cannot be empty"
        if (editorCode.code.startsWith("0")) errors+="0 Starting Codes may be overridden by Global SH Codes used for Development / Internal use."
        if (editorCode.actions.isEmpty()) errors += "At least one action is required"
        editorCode.actions.forEachIndexed { i, act ->
            if (act.command.isBlank()) errors += "Action #${i + 1} command is empty"
            if (act.delaySeconds < 0.0) errors += "Action #${i + 1} delay must be >= 0"
        }
        return errors
    }

    fun toNumpadCode(editorCode: EditorCode): NumpadCode {
        val actions = editorCode.actions.map { NumpadAction(it.command.trim(), it.delaySeconds) }
        return NumpadCode(
            editorCode.code.trim(),
            actions,
            editorCode.defaultDelaySeconds,
            editorCode.allowedIslands.toSet(),
            editorCode.allowOutsideSkyBlock,
        )
    }

    fun save(editorCode: EditorCode): List<String> {
        val errors = validate(editorCode)
        if (errors.isNotEmpty()) return errors
        val code = toNumpadCode(editorCode)
        NumpadCodes.register(code)
        return emptyList()
    }
}
