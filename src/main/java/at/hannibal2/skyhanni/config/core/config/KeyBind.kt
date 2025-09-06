package at.hannibal2.skyhanni.config.core.config

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.utils.KeyboardManager
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import org.lwjgl.input.Keyboard
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class KeyBind(
    @Expose
    @ConfigOption(name = "Keybind", desc = "The keybind to use for this action")
    @ConfigEditorKeybind(defaultKey = Keyboard.KEY_NONE)
    var key: Int = 0, // Null means for default Chat Prompt Key → changeable all at once globally with overrides
    @Expose
    @ConfigOption(name = "Expiration Time", desc = "The amount of time after which the action expires (in seconds)")
    @ConfigEditorSlider(5f, 60f, 1f)
    var expirationTime: Int = 10,
) {
    fun getEffectiveKey(): Int {
        if (key != 0) return key
        return SkyHanniMod.feature.chat.defaultChatPrompt
    }

    fun getEffectiveExpirationDuration(): Duration {
        return (expirationTime * SkyHanniMod.feature.chat.chatPromptExpirationMultiplier).seconds
    }

    fun getEffectiveKeyString(): String {
        return KeyboardManager.getKeyName(getEffectiveKey())
    }
}
