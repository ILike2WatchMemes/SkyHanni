package at.hannibal2.skyhanni.features.bingo.card

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.jsonobjects.repo.BingoData
import at.hannibal2.skyhanni.events.InventoryUpdatedEvent
import at.hannibal2.skyhanni.events.bingo.BingoCardUpdateEvent
import at.hannibal2.skyhanni.events.bingo.BingoGoalReachedEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.features.bingo.BingoApi
import at.hannibal2.skyhanni.features.bingo.card.goals.BingoGoal
import at.hannibal2.skyhanni.features.bingo.card.goals.GoalType
import at.hannibal2.skyhanni.features.bingo.card.goals.HiddenGoalData
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SkyBlockUtils
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import at.hannibal2.skyhanni.utils.TimeUtils
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import de.hype.bingonet.BNConnection
import de.hype.bingonet.shared.packets.function.PositionCommunityFeedback
import kotlin.time.Duration

@SkyHanniModule
object BingoCardReader {

    private val config get() = SkyHanniMod.feature.event.bingo.bingoCard
    private val patternGroup = RepoPattern.group("bingo.card")
    private val percentagePattern by patternGroup.pattern(
        "percentage",
        " {2}§8Top §.(?<percentage>.*)%",
    )

    private val positionPattern by patternGroup.pattern(
        "position",
        " {2}§6§l#(?<position>\\d+) §fcontributor",
    )

    private val contributionPattern by patternGroup.pattern(
        "contribution",
        "§7Contribution: §a(?<contribution>[^ ]+) .*",
    )


    private val goalCompletePattern by patternGroup.pattern(
        "goalcomplete",
        "§6§lBINGO GOAL COMPLETE! §r§e(?<name>.*)",
    )
    private val personalHiddenGoalPattern by patternGroup.pattern(
        "hiddengoal",
        ".*§7§eThe next hint will unlock in (?<time>.*)",
    )

    @HandleEvent
    fun onInventoryUpdated(event: InventoryUpdatedEvent) {
        if (!config.enabled) return
        if (event.inventoryName != "Bingo Card") return
        val comGoalPositions = mutableMapOf<String, ComGoalPosition>()
        for ((slot, stack) in event.inventoryItems) {
            val lore = stack.getLore()
            val goalType = when {
                lore.any { it.endsWith("Personal Goal") } -> GoalType.PERSONAL
                lore.any { it.endsWith("Community Goal") } -> GoalType.COMMUNITY
                else -> continue
            }
            val name = stack.displayName.removeColor()
            var index = 0
            val builder = StringBuilder()
            for (s in lore) {
                if (index > 1) {
                    if (s == "") break
                    builder.append(s)
                    builder.append(" ")
                }
                index++
            }
            var description = builder.toString()
            if (description.endsWith(" ")) {
                description = description.substring(0, description.length - 1)
            }
            if (description.startsWith("§7§7")) {
                description = description.substring(2)
            }
            val done: Boolean
            val communityGoalData: ComGoalPosition?
            if (goalType == GoalType.COMMUNITY) {
                done = false
                communityGoalData = readCommunityGoalData(lore)
                communityGoalData?.let {
                    comGoalPositions[name] = communityGoalData
                }
            } else {
                done = lore.any { it.contains("GOAL REACHED") }
                communityGoalData = null
            }
            val hiddenGoalData = getHiddenGoalData(name, description, goalType)
            val visualDescription = hiddenGoalData.tipNote

            val guide = BingoApi.getData(name)?.guide?.map { "§7$it" } ?: listOf("§cNo guide yet!")

            val bingoGoal = BingoApi.bingoGoals.getOrPut(slot) { BingoGoal() }

            with(bingoGoal) {
                this.type = goalType
                this.displayName = name
                this.description = visualDescription
                this.guide = guide
                this.done = done
                this.hiddenGoalData = hiddenGoalData
            }
            communityGoalData?.let {
                bingoGoalDifference(bingoGoal, it)
                bingoGoal.communityGoalData = it
            }
        }
        BingoApi.lastBingoCardOpenTime = SimpleTimeMark.now()
        sendBNComGoalData(comGoalPositions)
        BingoCardUpdateEvent.post()
    }

    private fun sendBNComGoalData(comGoalPositions: MutableMap<String, ComGoalPosition>) {
        if (!SkyHanniMod.feature.event.bingo.bingoNetworks.bingoNet.useBN) return
        val filtered = comGoalPositions.filter { it.value.position != null || it.value.percentage < 0.01 }
        val censored = filtered.mapValues {
            if ((it.value.position ?: 101) <= 3) {
                val asString = it.value.contribution.toString()
                val censored = (asString.take(2) + "0".repeat(asString.length - 2)).toInt()
                return@mapValues ComGoalPosition(it.value.position, it.value.percentage, censored)
            }
            return@mapValues it.value
        }
        // It is censored client side to eliminate any type of sniping #1 accusations using this System.
        // Data can be used to show contributors count as well as top 100 data to users.
        BNConnection.sendPacket(
            PositionCommunityFeedback(
                censored.map {
                    PositionCommunityFeedback.ComGoalPosition(
                        it.key,
                        it.value.contribution,
                        it.value.percentage * 100,
                        it.value.position,
                    )
                }.toSet(),
            ),
        )
    }

    data class ComGoalPosition(val position: Int?, val percentage: Double, val contribution: Int)

    private fun bingoGoalDifference(bingoGoal: BingoGoal, new: ComGoalPosition) {
        val old = bingoGoal.communityGoalData

        if (!config.communityGoalProgress) return
        if (old != null && ((new.position != null && new.position == old.position) || (new.position == null && new.percentage == old.percentage))) return

        val oldFormat = BingoApi.getCommunityPercentageColor(old?.percentage ?: 1.toDouble())
        val newFormat = BingoApi.getCommunityPercentageColor(new.percentage)
        val color =
            if (((new.position ?: 101) > (old?.position ?: 101)) || (new.percentage > (old?.percentage ?: 1.toDouble()))) "§c" else "§a"
        ChatUtils.chat("$color${bingoGoal.displayName}: $oldFormat${if (old?.position != null) "(§6#${old.position}$color)" else ""} §b->" + " $newFormat${if (new.position != null) "(§6#${new.position}$color)" else ""}")
    }

    private fun readCommunityGoalData(lore: List<String>): ComGoalPosition? {
        var percentage: Double? = null
        var position: Int? = null
        var contribution: Int? = null
        for (line in lore) {
            percentagePattern.matchMatcher(line) {
                percentage = group("percentage").toDouble() / 100
            }
            positionPattern.matchMatcher(line) {
                position = group("position").toInt()
            }
            contributionPattern.matchMatcher(line) {
                contribution = group("contribution").replace(",", "").toDouble().toInt()
            }
        }
        if (percentage == null || contribution == null) return null
        return ComGoalPosition(position, percentage, contribution)
    }

    private fun getHiddenGoalData(
        name: String,
        originalDescription: String,
        goalType: GoalType,
    ): HiddenGoalData {
        var unknownTip = false
        val nextHintTime: Duration? = when (goalType) {
            GoalType.PERSONAL -> {
                personalHiddenGoalPattern.matchMatcher(originalDescription) {
                    unknownTip = true
                    TimeUtils.getDuration(group("time").removeColor())
                }
            }

            GoalType.COMMUNITY -> {
                if (originalDescription == "§7This goal will be revealed §7when it hits Tier IV.") {
                    unknownTip = true
                }
                null
            }
        }

        val description = BingoApi.getData(name)?.getDescriptionLine()
        val tipNote = description?.let {
            unknownTip = false
            it
        } ?: originalDescription
        return HiddenGoalData(unknownTip, nextHintTime, tipNote)
    }

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        if (!SkyBlockUtils.isBingoProfile) return
        if (!config.enabled) return

        val name = goalCompletePattern.matchMatcher(event.message) {
            group("name")
        } ?: return

        val goal = BingoApi.personalGoals.firstOrNull { it.displayName == name } ?: return
        goal.done = true
        BingoGoalReachedEvent(goal).post()
        BingoCardUpdateEvent.post()
    }

    private fun BingoData.getDescriptionLine() = "§7" + note.joinToString(" ")
}
