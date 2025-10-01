package at.hannibal2.skyhanni.config.features.chat

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.core.config.KeyBind
import at.hannibal2.skyhanni.events.minecraft.KeyDownEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyClicked
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld

@SkyHanniModule
object ChatPromptUtils {
    private var activePromptBlock: ActiveKeyBind? = null


    @HandleEvent
    fun key(event: KeyDownEvent) {
        val activePrompt = activePromptBlock ?: return
        if (event.keyCode != activePrompt.keyCode || !event.keyCode.isKeyHeld()) return
        activePromptBlock = null
        if (SkyHanniMod.feature.dev.debug.enabled){
            ChatUtils.chat("Chat Prompt reset")
        }
        activePrompt.codeBlock.invoke()
    }

    private data class ActiveKeyBind(
        val keybind: KeyBind,
        val codeBlock: () -> Unit,
    ) {
        @Suppress("StorageNeedsExpose")
        val keyCode by lazy { keybind.getEffectiveKey() }
    }

    fun setActivePrompt(keyBind: KeyBind, codeBlock: () -> Unit) {
        val activePromptBlock = ActiveKeyBind(
            keybind = keyBind,
            codeBlock = codeBlock,
        )
        this.activePromptBlock = activePromptBlock
        if (SkyHanniMod.feature.dev.debug.enabled){
            ChatUtils.chat("Set new Chat Prompt task")
        }
        DelayedRun.runDelayed(keyBind.getEffectiveExpirationDuration()) {
            if (this.activePromptBlock === activePromptBlock) this.activePromptBlock = null
            if (SkyHanniMod.feature.dev.debug.enabled){
                ChatUtils.chat("Chat Prompt expired")
            }
        }

    }
}
