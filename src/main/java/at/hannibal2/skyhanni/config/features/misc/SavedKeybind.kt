package at.hannibal2.skyhanni.config.features.misc

import com.google.gson.annotations.Expose

/**
 * Serializable representation of a user keybind stored inside FEATURES config (under Misc).
 */
data class SavedKeybind(
    @Expose
    var combo: String? = null,
    @Expose
    var command: String? = null,
    @Expose
    var allowedIslands: MutableList<String> = mutableListOf(),
    @Expose
    var allowOutsideSkyBlock: Boolean = false,
)
