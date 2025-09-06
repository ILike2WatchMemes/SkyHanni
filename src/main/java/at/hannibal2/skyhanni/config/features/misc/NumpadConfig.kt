package at.hannibal2.skyhanni.config.features.misc

import at.hannibal2.skyhanni.config.FeatureToggle
import at.hannibal2.skyhanni.config.core.config.Position
import at.hannibal2.skyhanni.config.features.combat.InstanceChestProfitConfig
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink
import org.lwjgl.input.Keyboard
import at.hannibal2.skyhanni.data.IslandType

// New small serializable types for storing numpad codes in the FEATURES config
data class SavedNumpadAction(
    @Expose
    var command: String = "",
    @Expose
    var delaySeconds: Double = 1.0,
)

data class SavedNumpadCode(
    @Expose
    var code: String = "",
    @Expose
    var actions: MutableList<SavedNumpadAction> = mutableListOf(),
    @Expose
    var commandDelaySeconds: Double = 0.0,
    @Expose
    var allowedIslands: MutableSet<IslandType> = mutableSetOf(IslandType.ANY),
    // New: allow executing this code when not on SkyBlock (outside SkyBlock), defaults to false for backward compatibility
    @Expose
    var allowOutsideSkyBlock: Boolean = false,
)

class NumpadConfig {
    @Expose
    @ConfigOption(name = "Enabled", desc = "Enable the numpad codes system (global toggle).")
    @ConfigEditorBoolean
    @FeatureToggle
    var enabled: Boolean = false

    @Expose
    @ConfigOption(name = "Input timeout (seconds)", desc = "Time in seconds until typed input clears automatically.")
    @ConfigEditorSlider(minValue = 1.0f, maxValue = 30.0f, minStep = 0.1f)
    var inputTimeoutSeconds: Double = 10.0

    @Expose
    @ConfigLink(owner = NumpadConfig::class, field = "enabled")
    val pos: Position = Position(10, 10)

    @Expose
    @ConfigOption(name = "Show suggestions", desc = "Show matching suggestions in overlay. If disabled, only the current exact code (if any) is shown.")
    @ConfigEditorBoolean
    var showSuggestions: Boolean = false

    @Expose
    @ConfigOption(name = "Show overlay label", desc = "Render a custom label above the numpad overlay")
    @ConfigEditorBoolean
    var showLabel: Boolean = false

    @Expose
    @ConfigOption(name = "Overlay label", desc = "Custom label text to render above the overlay")
    @ConfigEditorText
    var overlayLabel: String = ""

    @Expose
    @ConfigOption(name = "Show 'Numpad:' prefix", desc = "Show the leading 'Numpad:' label before the current input")
    @ConfigEditorBoolean
    var showPrefix: Boolean = true

    // Persisted saved codes (serialized into the FEATURES config). Editors will ignore these fields in the moulconfig UI.
    @Expose
    var savedCodes: MutableList<SavedNumpadCode> = mutableListOf()

    @Expose
    @ConfigOption(name = "Numpad 0", desc = "Key for digit 0")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NUMPAD0)
    var key_numpad0: Int = Keyboard.KEY_NUMPAD0

    @Expose
    @ConfigOption(name = "Numpad 1", desc = "Key for digit 1")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NUMPAD1)
    var key_numpad1: Int = Keyboard.KEY_NUMPAD1

    @Expose
    @ConfigOption(name = "Numpad 2", desc = "Key for digit 2")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NUMPAD2)
    var key_numpad2: Int = Keyboard.KEY_NUMPAD2

    @Expose
    @ConfigOption(name = "Numpad 3", desc = "Key for digit 3")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NUMPAD3)
    var key_numpad3: Int = Keyboard.KEY_NUMPAD3

    @Expose
    @ConfigOption(name = "Numpad 4", desc = "Key for digit 4")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NUMPAD4)
    var key_numpad4: Int = Keyboard.KEY_NUMPAD4

    @Expose
    @ConfigOption(name = "Numpad 5", desc = "Key for digit 5")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NUMPAD5)
    var key_numpad5: Int = Keyboard.KEY_NUMPAD5

    @Expose
    @ConfigOption(name = "Numpad 6", desc = "Key for digit 6")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NUMPAD6)
    var key_numpad6: Int = Keyboard.KEY_NUMPAD6

    @Expose
    @ConfigOption(name = "Numpad 7", desc = "Key for digit 7")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NUMPAD7)
    var key_numpad7: Int = Keyboard.KEY_NUMPAD7

    @Expose
    @ConfigOption(name = "Numpad 8", desc = "Key for digit 8")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NUMPAD8)
    var key_numpad8: Int = Keyboard.KEY_NUMPAD8

    @Expose
    @ConfigOption(name = "Numpad 9", desc = "Key for digit 9")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NUMPAD9)
    var key_numpad9: Int = Keyboard.KEY_NUMPAD9

    @Expose
    @ConfigOption(name = "Enter (activate)", desc = "Key used to activate the entered code")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NUMPADENTER)
    var key_enter: Int = Keyboard.KEY_NUMPADENTER

    @Expose
    @ConfigOption(name = "Delete last (+)", desc = "Key used to delete the last entered digit")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_ADD)
    var key_deleteLast: Int = Keyboard.KEY_ADD

    @Expose
    @ConfigOption(name = "Clear (-)", desc = "Key used to clear the current input")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_SUBTRACT)
    var key_clear: Int = Keyboard.KEY_SUBTRACT
}
