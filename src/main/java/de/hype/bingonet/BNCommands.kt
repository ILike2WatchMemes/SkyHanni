@file:Suppress("NoUnusedImports")
package de.hype.bingonet

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.commands.brigadier.BrigadierArguments
import at.hannibal2.skyhanni.config.features.event.bingo.BingoNetSystem
import at.hannibal2.skyhanni.config.features.inventory.hubselector.HubSelectorKeybinds
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.data.toBNIsland
import at.hannibal2.skyhanni.features.bingo.bingonet.BNRegistrationScreen
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import de.hype.bingonet.BNConnection.reconnectToBNServer
import de.hype.bingonet.shared.constants.StatusConstants
import de.hype.bingonet.shared.objects.BNRole
import de.hype.bingonet.shared.objects.SplashData
import de.hype.bingonet.shared.objects.SplashLocation
import de.hype.bingonet.shared.objects.SplashLocations
import de.hype.bingonet.shared.packets.function.SplashNotifyPacket
import de.hype.bingonet.shared.packets.function.SplashTimeRequestPacket
import de.hype.bingonet.shared.packets.network.BingoChatMessagePacket

@SkyHanniModule
@Suppress("LongMethod", "NoUnusedImports")
object BNCommands {
    val config = SkyHanniMod.feature.event.bingo.bingoNetworks
    val bnConfig = SkyHanniMod.feature.event.bingo.bingoNetworks.bingoNet

    @HandleEvent
    fun registerCommands(event: CommandRegistrationEvent) {
        event.registerBrigadier("bnreconnectserver") {
            description = "(Re)Starts the Connection to the Bingo Net Server."
            category = CommandCategory.BINGO_NET
            arg(
                "server",
                BrigadierArguments.string(),
                BingoNetSystem.entries.map { it.name } + "disconnect",
            ) {
                callback {
                    connectServerCommand(getArg(it))
                }
            }
            simpleCallback {
                connectServerCommand(null)
            }
        }

        event.registerBrigadier("bc") {
            description = "Send a Message to Bingo Net Chat."
            category = CommandCategory.BINGO_NET
            arg(
                "message",
                BrigadierArguments.greedyString(),
            ) {
                callback {
                    BNConnection.sendPacket(BingoChatMessagePacket(null, "", getArg(it), 0))
                }
            }
        }

        if (BNConnection.roles.contains(BNRole.SPLASHER)) {
            event.registerBrigadier("bnsplash") {
                description = "Announce a Splash (Announces a Splash for the Lobby your currently in)."
                category = CommandCategory.BINGO_NET
                arg("location", BrigadierArguments.string(), SplashLocations.values().map { it.getName() }) { loc ->
                    arg("extraMessage", BrigadierArguments.greedyString()) { extra ->
                        callback {
                            val location = SplashLocations.values().find { it.getName() == getArg(loc) }
                            val message = getArg(extra)
                            if (location == null) {
                                ChatUtils.userError("Invalid splash location provided: ${getArg(loc)}. Only use a suggested location!")
                                return@callback
                            }
                            sendSplash(location, message, false)
                        }
                    }
                    callback {
                        val location = SplashLocations.values().find { it.getName() == getArg(loc) }
                        if (location == null) {
                            ChatUtils.userError("Invalid splash location provided: ${getArg(loc)}. Only use a suggested location!")
                            return@callback
                        }
                        sendSplash(location, null, false)
                    }
                }
            }
            event.registerBrigadier("bnsplashdynamic") {
                description = "Announce a Dynamic Hub Splash"
                category = CommandCategory.BINGO_NET
                arg("location", BrigadierArguments.string(), SplashLocations.values().map { it.getName() }) { loc ->
                    arg("extraMessage", BrigadierArguments.greedyString()) { extra ->
                        callback {
                            val location = SplashLocations.values().find { it.getName() == getArg(loc) }
                            val message = getArg(extra)
                            if (location == null) {
                                ChatUtils.userError("Invalid splash location provided: ${getArg(loc)}. Only use a suggested location!")
                                return@callback
                            }
                            sendSplash(location, message, true)
                        }
                    }
                    callback {
                        val location = SplashLocations.values().find { it.getName() == getArg(loc) }
                        if (location == null) {
                            ChatUtils.userError("Invalid splash location provided: ${getArg(loc)}. Only use a suggested location!")
                            return@callback
                        }
                        sendSplash(location, null, true)
                    }
                }
            }
            event.registerBrigadier("bnrequestpottimes") {
                category = CommandCategory.BINGO_NET
                description = "For Splashers: Shows a summary of the current remaining God Pot Durations of all mod users."
                simpleCallback {
                    val packet = SplashTimeRequestPacket()
                    BNConnection.sendPacket(packet)
                    ChatUtils.chat("The Request has been send. The Server will send you a Response with the report shortly.")
                }
            }
        }

        event.registerBrigadier(
            "bnregistryscreen",
            {
                category = CommandCategory.DEVELOPER_DEBUG
                description = "Opens the Bingo Net Registration Screen"
                simpleCallback {
                    BNRegistrationScreen.openHelper()
                }
            },
        )

        event.registerBrigadier(
            "bndebugbreakpoint",
            {
                category = CommandCategory.DEVELOPER_DEBUG
                description = "Opens the Bingo Net Registration Screen"
                simpleCallback {
                    SkyHanniMod.launchCoroutine {
                        debugBreakpoint()
                    }
                }
            },
        )
    }

    fun connectServerCommand(arg: String? = null) {
        val system = BingoNetSystem.entries.find { it.name == arg }
        if (system == null) {
            BNConnection.disconnect()
            return
        }
        SkyHanniMod.launchCoroutine {
            BNConnection.reconnectToBNServer(false, system)
        }
    }

    fun sendSplash(location: SplashLocation, extraMessage: String? = null, dynamic: Boolean) {
        val serverId = HypixelData.serverId ?: return
        var hubData =
            HubSelectorKeybinds.getHubNumberById(serverId, HypixelData.skyBlockIsland.toBNIsland() ?: error("Not on a Skyblock Island!"))
        if (dynamic) {
            hubData = null
        } else if (hubData == null) {
            ChatUtils.userError("Server ID $serverId not found in Cache or Hub Selector Data outdated. Please open the Hub Selector once.")
            return
        }
        val splashData = SplashData(
            announcer = "",
            locationInHub = location,
            extraMessage = extraMessage,
            lessWaste = config.splasherConfig.lessWaste,
            serverID = serverId,
            hubSelectorData = hubData,
            status = StatusConstants.WAITING,
        )
        BNConnection.sendPacket(SplashNotifyPacket(splashData))
    }

    suspend fun debugBreakpoint() {
        ChatUtils.chat("Debug Breakpoint reached.")
    }

}
