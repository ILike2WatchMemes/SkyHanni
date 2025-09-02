package at.hannibal2.skyhanni.features.combat.mobs

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.player.PlayerDeathEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object ArachneOnDeathWarp {
    private val config get() = SkyHanniMod.feature.combat.mobs

    @HandleEvent(onlyOnIsland = IslandType.SPIDER_DEN)
    fun onChatMessage(event: PlayerDeathEvent){
        if (!event.isSelf || !config.chatPromptArachneWarpOnDeath) return
        if (event.reason.contains("Arachne")) {
            ChatUtils.chatPrompt(
                "Press §a%KEY%§e to warp to the Arachne Sanctuary", config.arachneDeathWarpKeybind,
                code = {
                    //TODO travel manager to check whether the user has the travel scroll unlocked.
                    HypixelCommands.warp("arachne")
                }
            )
        }
    }
}
