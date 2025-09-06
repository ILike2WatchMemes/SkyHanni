package at.hannibal2.skyhanni.events.player

import at.hannibal2.skyhanni.api.event.SkyHanniEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent

/**
 * Activates on any Player Death. You stands for the current player.
 */
class PlayerDeathEvent(val name: String, val reason: String, val chatEvent: SkyHanniChatEvent) : SkyHanniEvent() {
    val isSelf by lazy {
        name == "You"
    }

    constructor(reason: String, chatEvent: SkyHanniChatEvent) : this("You", reason, chatEvent)
}
