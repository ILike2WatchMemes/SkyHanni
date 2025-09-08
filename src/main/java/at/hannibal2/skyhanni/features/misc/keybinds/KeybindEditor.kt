package at.hannibal2.skyhanni.features.misc.keybinds

import at.hannibal2.skyhanni.data.IslandType

class KeybindEditor {
    data class EditorKeybind(
        var combo: String = "",
        var command: String = "",
        var allowedIslands: MutableSet<IslandType> = mutableSetOf(IslandType.ANY),
        var allowOutsideSkyBlock: Boolean = false,
    )

    /**
     * Validate a keybind edit. If [original] is provided and equals the normalized combo, duplicate reuse warnings are suppressed.
     */
    fun validate(e: EditorKeybind, original: String? = null): List<String> {
        val errors = mutableListOf<String>()
        if (e.combo.isBlank()) errors += "Key combo cannot be empty"
        if (e.command.isBlank()) errors += "Command cannot be empty"
        if (errors.isNotEmpty()) return errors
        val normalized = Keybinds.normalizeCombo(e.combo)
        // Subset/superset allowed now; only block true duplicate from different command context
        if (Keybinds.duplicateExists(normalized, original, e.command)) errors += "Reuse: combo already bound"
        // Vanilla (Minecraft) bound key usage
        val vanilla = Keybinds.vanillaKeyConflicts(normalized)
        if (vanilla.isNotEmpty()) errors += "Uses Minecraft bound key(s): ${vanilla.joinToString(", ")}"
        return errors
    }

    fun toKeybind(e: EditorKeybind): Keybinds.Keybind = Keybinds.Keybind(
        Keybinds.normalizeCombo(e.combo.trim()),
        e.command.trim(),
        e.allowedIslands.toSet(),
        e.allowOutsideSkyBlock
    )

    fun save(e: EditorKeybind, original: String? = null): List<String> {
        val errs = validate(e, original)
        if (errs.isNotEmpty()) return errs
        Keybinds.register(toKeybind(e))
        return emptyList()
    }
}
