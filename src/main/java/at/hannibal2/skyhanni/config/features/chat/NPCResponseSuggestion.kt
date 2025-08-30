package at.hannibal2.skyhanni.config.features.chat

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.hypixel.chat.event.NpcChatEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.compat.command

@SkyHanniModule
object NPCResponseSuggestion {
    @HandleEvent
    fun onMessage(event: NpcChatEvent) {
        for (components in event.messageComponent.sampleComponents()) {
            val command = components.command ?: continue
            if (command.startsWith("chatprompt") && components.formattedText.contains("§a")) {
                ChatUtils.chatPrompt(
                    "Press §a%KEY%§e to respond with \"${components.formattedText}\"", SkyHanniMod.feature.chat.npcResponseSuggestion,
                    {
                        HypixelCommands.chatPrompt(command.substringAfter(" "))
                    },
                )
                return
            }
        }
    }
}
