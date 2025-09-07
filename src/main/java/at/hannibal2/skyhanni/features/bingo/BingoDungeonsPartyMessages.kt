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
object BingoDungeonsPartyMessages {
    val config = SkyHanniMod.feature.event.bingo
    val patternGroup = RepoPattern.group("feature.event.bingo.dungeons")
    val skillLVLUPPattern by patternGroup.pattern(
        "skill-level-up",
        "§r§b§lDUNGEON LEVEL UP §3§cThe Catacombs §8(?<oldLevel>\\d+)➜§3(?<newLevel>\\d+)",
    )

    /**
     * REGEX-TEST §e§lMage Milestone §r§e❶§r§7: You have dealt §r§c60,000§r§7 Total Damage so far! §r§a02s
     */
    val milestoneReachedPattern by patternGroup.pattern(
        "milestone-reached",
        "§e§lMage Milestone §r§e(?<milestone>.)§r§7.*",
    )

    @HandleEvent
    fun onMessage(event: SkyHanniChatEvent) {
        if (!SkyBlockUtils.isBingoProfile) return
        if (config.sendCataLevelUP) skillLVLUPPattern.matchMatcher(event.message) {
            HypixelCommands.partyChat("Dungeon Skill Level Up: ${group("newLevel")}")
        }
        if (config.sendImportantCataMilestones) {
            milestoneReachedPattern.matchMatcher(event.message) {
                val milestone = group("milestone")
                if (milestone == "❷" || milestone == "❸") {
                    HypixelCommands.partyChat("Dungeon Milestone $milestone reached!")
                }
            }
        }
    }
}
