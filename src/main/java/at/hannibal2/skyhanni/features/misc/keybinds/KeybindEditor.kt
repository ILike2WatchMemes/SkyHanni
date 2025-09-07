package at.hannibal2.skyhanni.features.misc.keybinds

import at.hannibal2.skyhanni.data.IslandType

class KeybindEditor {
    data class EditorKeybind(
        var combo: String = "",
        var command: String = "",
        var allowedIslands: MutableSet<IslandType> = mutableSetOf(IslandType.ANY),
        var allowOutsideSkyBlock: Boolean = false,
    )

    fun validate(e: EditorKeybind): List<String> {
        val errors = mutableListOf<String>()
        if (e.combo.isBlank()) errors += "Key combo cannot be empty"
        if (e.command.isBlank()) errors += "Command cannot be empty"
        if (errors.isNotEmpty()) return errors
        val normalized = Keybinds.normalizeCombo(e.combo)
        Keybinds.conflictError(normalized)?.let { errors += it }
        return errors
    }

    fun toKeybind(e: EditorKeybind): Keybinds.Keybind {
        return Keybinds.Keybind(Keybinds.normalizeCombo(e.combo.trim()), e.command.trim(), e.allowedIslands.toSet(), e.allowOutsideSkyBlock)
    }

    fun save(e: EditorKeybind): List<String> {
        val errs = validate(e)
        if (errs.isNotEmpty()) return errs
        Keybinds.register(toKeybind(e))
        return emptyList()
    }
}
