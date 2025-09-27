package at.hannibal2.skyhanni.config.features.chat

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.hypixel.chat.event.NpcChatEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.compat.command
import kotlinx.coroutines.delay

@SkyHanniModule
object NPCResponseSuggestion {
    val commandPrefixes = listOf("/selectnpcoption","/chatprompt")

    @HandleEvent
    fun onMessage(event: SkyHanniChatEvent) {
        SkyHanniMod.launchCoroutine("NPC Response Suggestion Message Analyser") {
            val validComponents = event.chatComponent.siblings.filter { sib ->
                commandPrefixes.any { prefix ->
                    sib.command?.startsWith(prefix)
                        ?: false
                }
            }.let {
                val filtered = it.filter { it.formattedText.contains("§a") }
                return@let filtered.ifEmpty { it }
            }
            if (validComponents.isEmpty()) return@launchCoroutine
            val components = validComponents.first()
            val command = components.command ?: return@launchCoroutine
            ChatUtils.chatPrompt(
                "Press §a%KEY%§e to respond with \"${components.formattedText}\"", SkyHanniMod.feature.chat.npcResponseSuggestion,
                {
                    ChatUtils.sendMessageToServer(command)
                },
            )
        }
    }
}
