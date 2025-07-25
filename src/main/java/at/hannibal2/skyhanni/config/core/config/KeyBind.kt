package at.hannibal2.skyhanni.config.core.config

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.utils.KeyboardManager
import org.luaj.vm2.ast.Str
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class KeyBind(
    private var expirationTime: Duration = 10.seconds,
    private var key: Int? = null, //Null means for default Chat Prompt Key → changeable all at once globally with overrides
) {
    fun getEffectiveKey(): Int {
        return key ?: SkyHanniMod.feature.chat.defaultChatPrompt
    }

    fun getEffectiveExpirationDuration(): Duration {
        return expirationTime * SkyHanniMod.feature.chat.chatPromptExpirationMultiplier
    }

    fun getEffectiveKeyString(): String{
        return KeyboardManager.getKeyName(getEffectiveKey())
    }
}
