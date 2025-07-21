package at.hannibal2.skyhanni.features.mining.crystalhollows

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.features.event.bingo.bingonet.network.BNConnection
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.features.bingo.bingobrewers.BingoBrewersClient
import at.hannibal2.skyhanni.features.bingo.bingobrewers.BingoBrewersPackets
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SkyBlockUtils
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import com.esotericsoftware.kryonet.Server
import de.hype.bingonet.shared.constants.ChChestItem
import de.hype.bingonet.shared.objects.ChChestData
import de.hype.bingonet.shared.packets.mining.ChChestPacket
import kotlin.time.Duration.Companion.days

@SkyHanniModule
object ChChestMessageAnalyser {
    var isInMessage: Boolean = false
    var items: MutableMap<ChChestItem, IntRange> = HashMap()

    private val patternGroup = RepoPattern.group("mining.crystalhollows.globalchest.bingoshare")

    private val wrapper by patternGroup.pattern(
        "wrapper",
        "§d▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
    )

    private val globalChestPattern by patternGroup.pattern(
        "globalchest",
        ".*LOOT CHEST COLLECTED.*",
    )

    @HandleEvent
    fun onChatMessage(event: SkyHanniChatEvent) {
        SkyHanniMod.launchCoroutine {
            val message = event.message
            if (globalChestPattern.matches(message)) {
                isInMessage = true
            }
            if (isInMessage && wrapper.matches(message)) {
                isInMessage = false
                if (items.isNotEmpty()) {
                    val coords = BingoNet.temporaryConfig.lastGlobalChchestCoords
                    val chest = ChChestData(coords, items)
                    val serverId = HypixelData.serverId
                    val bnPacket = ChChestPacket(chest, serverId)
                    BNConnection.sendPacket(bnPacket)
                    if (SkyHanniMod.feature.event.bingo.bingoNetworks.chestWaypoints) {
                        val packet = BingoBrewersPackets.sendCHItems()
                        packet.x = coords.x
                        packet.y = coords.y
                        packet.z = coords.z
                        packet.items = items.map { (item, count) ->
                            val bItem = BingoBrewersPackets.CHChestItem()
                            bItem.name = item.displayName
                            bItem.count = count.toString()
                            bItem.itemColor = item.itemFormatting.color?.rgb
                            bItem.numberColor = item.countFormatting.color?.rgb
                            return@map bItem
                        }
                        packet.server = serverId
                        packet.day = MinecraftCompat.localWorldOrNull?.worldTime
                        BingoBrewersClient.sendTCP(packet)
                    }
                }
                items.clear()
            }
        }
        if (isInMessage) {
            //I thought about using the PowderChestReward Pattern for this but its lacking the item color which indigo uses and BN needs a shared data enum anyway.
            val item = ChChestItem.parse(event.message) ?: return
            val before = items.get(item.first) ?: IntRange(0, 0)
            items[item.first] = before.plus(item.second)
        }
    }
}

fun IntRange.plus(range: IntRange): IntRange {
    return IntRange(this.first + range.first, this.last + range.last)
}


fun IntRange.toGoodString(): String {
    return if (this.first == this.last) {
        this.first.toString()
    } else {
        "${this.first}-${this.last}"
    }
}

