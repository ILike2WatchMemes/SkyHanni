package at.hannibal2.skyhanni.config.features.chat

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.core.config.KeyBind
import at.hannibal2.skyhanni.events.minecraft.KeyDownEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyClicked

@SkyHanniModule
object ChatPromptUtils {
    private var activePromptBlock: ActiveKeyBind? = null


    @HandleEvent
    fun key(event: KeyDownEvent) {
        val activePrompt = activePromptBlock ?: return
        if (event.keyCode != activePrompt.keyCode || !event.keyCode.isKeyClicked()) return
        activePromptBlock = null
        activePrompt.codeBlock.invoke()
    }

    private data class ActiveKeyBind(
        val keybind: KeyBind,
        val codeBlock: () -> Unit,
    ) {
        val keyCode by lazy { keybind.getEffectiveKey() }
    }

    fun setActivePrompt(keyBind: KeyBind, codeBlock: () -> Unit) {
        val activePromptBlock = ActiveKeyBind(
            keybind = keyBind,
            codeBlock = codeBlock,
        )
        this.activePromptBlock = activePromptBlock
        DelayedRun.runDelayed(keyBind.getEffectiveExpirationDuration()) {
            if (this.activePromptBlock == activePromptBlock) this.activePromptBlock = null
        }

    }
}
