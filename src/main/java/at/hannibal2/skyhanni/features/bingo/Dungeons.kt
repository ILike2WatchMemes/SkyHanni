package at.hannibal2.skyhanni.features.bingo

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.SkyBlockUtils
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object Dungeons {
    val config = SkyHanniMod.feature.event.bingo
    val patternGroup = RepoPattern.group("feature.event.bingo.dungeons")
    val skillLVLUPPattern by patternGroup.pattern(
        "skill-level-up",
        "DUNGEON LEVEL UP The Catacombs",
    )

    @HandleEvent
    fun onMessage(event: SkyHanniChatEvent) {
        if (!SkyBlockUtils.isBingoProfile) return
        if (config.sendCataLevelUP) skillLVLUPPattern.matchMatcher(event.message) {
            HypixelCommands.partyChat("Dungeon Skill Level Up: ${group("newLevel")}")
        }
        if (config.sendImportantCataMilestones) {
        //TODO add the cata milestone pattern check and post
        }
    }
}
