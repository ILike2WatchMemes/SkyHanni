package at.hannibal2.skyhanni.features.mining.crystalhollows

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.storage.PlayerSpecificStorage
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.events.minecraft.WorldLeaveEvent
import at.hannibal2.skyhanni.features.bingo.bingobrewers.BingoBrewersClient
import at.hannibal2.skyhanni.features.bingo.bingobrewers.BingoBrewersPackets
import at.hannibal2.skyhanni.features.skillprogress.SkillType
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.SkyBlockUtils
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import de.hype.bingonet.BNConnection
import de.hype.bingonet.shared.constants.ChChestItem
import de.hype.bingonet.shared.constants.Formatting
import de.hype.bingonet.shared.constants.ValueableChChestItem
import de.hype.bingonet.shared.objects.ChChestData
import de.hype.bingonet.shared.objects.Position
import de.hype.bingonet.shared.objects.RenderInformation
import de.hype.bingonet.shared.objects.WaypointData
import de.hype.bingonet.shared.packets.function.RequestServerWarpPacket
import de.hype.bingonet.shared.packets.mining.ChChestPacket
import de.hype.bingonet.shared.packets.mining.SubscribeToChServer
import de.hype.bingonet.shared.packets.mining.UnSubscribeToChServer

@SkyHanniModule
object ChChestUpdateListener {
    val chestsOpened: MutableList<Position> = ArrayList()
    val waypoints = BNConnection.waypoints
    val reportedChests: MutableMap<Position, ChChestData> = HashMap()
    val chChestConfig = SkyHanniMod.feature.event.bingo.bingoNetworks.chChestConfig

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
    fun onWorldLeave(event: WorldLeaveEvent) {
        if (!config.chestWaypoints) return
        if (HypixelData.skyBlockIsland != IslandType.CRYSTAL_HOLLOWS) return
        val unsubpacket = UnSubscribeToChServer(
            HypixelData.serverId ?: error("Old Server Id is null but Island was loaded?"),
            EntityUtils.getPlayerList(),
        )
        if (config.useBN) BNConnection.sendPacket(unsubpacket)
        if (config.useBB) {
            val bbsub = BingoBrewersPackets.SubscribeToCHServer()
            bbsub.server = HypixelData.serverId
            bbsub.day = MinecraftCompat.worldDay ?: error("World appears to be null, cannot get day")
            bbsub.unsubscribe = true
            BingoBrewersClient.sendTCP(bbsub)
        }
        reset()
    }

    @HandleEvent
    fun onIslandChange(event: IslandChangeEvent) {
        if (!config.chestWaypoints) return
        SkyHanniMod.launchCoroutine {
            val serverId = HypixelData.serverId ?: return@launchCoroutine
            if (event.newIsland == IslandType.CRYSTAL_HOLLOWS) {
                val packet = SubscribeToChServer(serverId, getLobbyClosingTime())
                if (config.useBN) BNConnection.sendPacket(packet)
                if (config.useBB) {
                    val bbsub = BingoBrewersPackets.SubscribeToCHServer()
                    bbsub.unsubscribe = false
                    bbsub.server = serverId
                    bbsub.day = MinecraftCompat.worldDay ?: error("World appears to be null, cannot get day")
                    BingoBrewersClient.sendTCP(bbsub)
                    BingoBrewersClient.sendTCP(bbsub)
                }
            }
        }
    }

    fun showChChest(items: Map<ChChestItem, IntRange>): Boolean {
        if (SkyBlockUtils.isBingoProfile) return false
        if (ProfileStorageData.profileSpecific?.mining?.hotmLevel?:0 < 4) return false
        if (chChestConfig.allChChestItem) return true
        for (baseItem in items.entries) {
            val item = baseItem.key.getAsValueableItem(baseItem.value) ?: continue
            if (chChestConfig.allRoboPart && item.isRoboPart) return true
            if (chChestConfig.prehistoricEgg && item == ValueableChChestItem.PrehistoricEgg) return true
            if (chChestConfig.pickonimbus2000 && item == ValueableChChestItem.Pickonimbus2000) return true
            if (chChestConfig.controlSwitch && item == ValueableChChestItem.ControlSwitch) return true
            if (chChestConfig.electronTransmitter && item == ValueableChChestItem.ElectronTransmitter) return true
            if (chChestConfig.ftx3070 && item == ValueableChChestItem.FTX3070) return true
            if (chChestConfig.robotronReflector && item == ValueableChChestItem.RobotronReflector) return true
            if (chChestConfig.superliteMotor && item == ValueableChChestItem.SuperliteMotor) return true
            if (chChestConfig.syntheticHeart && item == ValueableChChestItem.SyntheticHeart) return true
            if (chChestConfig.flawlessGemstone && item == ValueableChChestItem.FlawlessGemstone) return true
        }
        return false
    }

    @JvmStatic
    fun onChChestDataReceived(packet: ChChestPacket) {
        if (packet.server.equals(HypixelData.serverId)) {
            addChestAndUpdate(packet.chest.coords, packet.chest.items)
        } else {
            if (showChChest(packet.chest.items)) {
                val items = packet.chest.items.entries.joinToString("\n") {
                    "${it.key.itemFormatting}${it.key.displayName} ${it.key.countFormatting}${it.value.toGoodString()}"
                }
                ChatUtils.chatPrompt(
                    "§e[SH-BN] A CH Chest with the following valuable items was found:\n $items\n Press §a%KEY%§r to request a party invite for a warp.",
                    chChestConfig.chChestChatPromp,
                    {
                        BNConnection.sendPacket(RequestServerWarpPacket(packet.server))
                    },
                )
            }
        }
    }

    fun onChLobbyDataReceived(data: de.hype.bingonet.shared.packets.mining.ChestLobbyUpdatePacket) {
        if (data.serverId != HypixelData.serverId) return
        updateLobby(data.chests)
    }

    fun onChLobbyDataReceived(packet: BingoBrewersPackets.receiveCHItems) {
        if (HypixelData.serverId == packet.server) {
            packet.chestMap.forEach { it ->
                val items = HashMap<ChChestItem, IntRange>()
                it.items.forEach {
                    val countSplit = it.count.split("-")
                    items[
                        ChChestItem(
                            it.name,
                            Formatting.getByColour(it.itemColor) ?: Formatting.WHITE,
                            Formatting.getByColour(it.numberColor) ?: Formatting.WHITE,
                        ),
                    ] = IntRange(countSplit[0].toInt(), countSplit.last().toInt())
                }
                addChestAndUpdate(Position(it.x, it.y, it.z), items)
            }
        }
    }
}
