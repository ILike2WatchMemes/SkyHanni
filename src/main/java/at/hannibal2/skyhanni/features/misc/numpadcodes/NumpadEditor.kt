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
