package at.hannibal2.skyhanni.features.mining.crystalhollows

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.features.bingo.bingobrewers.BingoBrewersClient
import at.hannibal2.skyhanni.features.bingo.bingobrewers.BingoBrewersPackets
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import de.hype.bingonet.BNConnection
import de.hype.bingonet.shared.constants.ChChestItem
import de.hype.bingonet.shared.constants.ValueableChChestItem
import de.hype.bingonet.shared.objects.ChChestData
import de.hype.bingonet.shared.objects.Position
import de.hype.bingonet.shared.objects.RenderInformation
import de.hype.bingonet.shared.objects.WaypointData
import de.hype.bingonet.shared.packets.mining.SubscribeToChServer
import de.hype.bingonet.shared.packets.mining.UnSubscribeToChServer
import java.lang.Thread.sleep

@SkyHanniModule
object ChChestUpdateListener {
    val chestsOpened: MutableList<Position> = ArrayList()
    val waypoints = BNConnection.waypoints
    val reportedChests: MutableMap<Position, ChChestData> = HashMap()

    fun updateLobby(data: List<ChChestData>) {
        reportedChests.putAll(data.associateBy { it.coords })
        setWaypoints()
    }

    fun setWaypoints() {
        if (!SkyHanniMod.feature.event.bingo.bingoNetworks.chestWaypoints) return
        for (chest in reportedChests.entries) {
            val waypoint = waypoints.entries.find { it.value.position == chest.key }
            val shouldDisplay = !(chestsOpened.contains(chest.key))
            if (waypoint != null) {
                waypoint.value.visible = shouldDisplay
                continue
            }
            val valuable: List<ValueableChChestItem> =
                chest.value.items.mapNotNull { it.key.getAsValueableItem(it.value) }
            val renderInformationList: MutableList<RenderInformation> = ArrayList()
            valuable.forEach {
                renderInformationList.add(
                    RenderInformation(
                        "bingonet",
                        "textures/waypoints/" + it.iconPath + ".png",
                    ),
                )
            }
            if (waypoints.values.none { waypointFiltered: WaypointData -> waypointFiltered.position.equals(chest.key) }
            ) {
                val jsonText = StringBuilder()
                jsonText.append("[\"\"")
                if (chest.value.items.isNotEmpty()) {
                    jsonText.append(",")
                    chest.value.items.forEach {
                        jsonText.append("{\"text\":\"")
                        jsonText.append(it.key.itemFormatting)
                        jsonText.append(it.key.displayName)
                        jsonText.append(" ")
                        jsonText.append(it.key.countFormatting)
                        jsonText.append(it.value.toGoodString())
                        jsonText.append("\"},{\"text\":\"\\n\"}")
                    }
                }
                jsonText.append("]")
                val newpoint = WaypointData(
                    chest.key,
                    jsonText.toString(),
                    1000,
                    shouldDisplay,
                    true,
                    renderInformationList,
                )
                waypoints[newpoint.waypointId] = newpoint
            }
        }
    }

    fun isEnabled(): Boolean {
        return SkyHanniMod.feature.event.bingo.bingoNetworks.chChestOverlay
    }

    val unopenedChests: List<ChChestData>
        get() {
            return reportedChests.filterNot { chestsOpened.contains(it.key) }.values.toList()
        }

    fun addOpenedChest(pos: Position) {
        SkyHanniMod.launchCoroutine {
            if (chestsOpened.contains(pos)) return@launchCoroutine
            chestsOpened.add(pos)
            setWaypoints()
        }
    }

    fun addChestAndUpdate(coords: Position, items: Map<ChChestItem, IntRange>) {
        reportedChests[coords] = ChChestData(coords, items)
    }

    fun reset() {
        chestsOpened.clear()
        reportedChests.clear()
        waypoints.clear()
    }

    val config = SkyHanniMod.feature.event.bingo.bingoNetworks

    @HandleEvent
    fun onIslandChange(event: IslandChangeEvent) {
        if (!config.chestWaypoints) return
        SkyHanniMod.launchCoroutine {
            if (event.oldIsland == IslandType.CRYSTAL_HOLLOWS) {
                val unsubpacket = UnSubscribeToChServer(event.oldServerId, EnvironmentCore.utils.getPlayers().toSet())
                if (config.useBN) BNConnection.sendPacket(unsubpacket)
                if (config.useBB) {
                    val bbsub = BingoBrewersPackets.SubscribeToCHServer()
                    bbsub.server = event.oldServerId
                    bbsub.day = MinecraftCompat.worldDay //TODO change so this is the old world day
                    bbsub.unsubscribe = true
                    //I'm not updating the day since I fear that my server leave task would send bad data since the
                    // day is world based and my leave procs on new server join due too it being the only fabric
                    // event. The Tablist Data gets updated slower, and so it is for the Hypixel API Location Packet.
                    // Also the reason why i even removed the closing time field from my unsubscribe packet.
                    BingoBrewersClient.sendTCP(bbsub)
                }
                reset()
                sleep(500)
            }

            val serverId = event.newServerId
            if (event.newIsland == IslandType.CRYSTAL_HOLLOWS) {
                val packet = SubscribeToChServer(serverId, getLobbyClosingTime())
                if (config.useBN) BNConnection.sendPacket(packet)
                if (config.useBB) {
                    val bbsub = BingoBrewersPackets.SubscribeToCHServer()
                    bbsub.unsubscribe = false
                    bbsub.server = serverId
                    bbsub.day = MinecraftCompat.worldDay
                    BingoBrewersClient.sendTCP(bbsub)
                    BingoBrewersClient.sendTCP(bbsub)
                }
            }
        }
    }
}
