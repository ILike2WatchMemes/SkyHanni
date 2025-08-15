package at.hannibal2.skyhanni.features.bingo.bingonet

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.events.TabListUpdateEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.features.chat.CompactSplashPotionMessage
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.EntityUtils.isOnBingo
import at.hannibal2.skyhanni.utils.EntityUtils.isOnIronman
import at.hannibal2.skyhanni.utils.PlayerUtils
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import de.hype.bingonet.BNConnection
import de.hype.bingonet.shared.constants.StatusConstants
import de.hype.bingonet.shared.objects.SplashData
import de.hype.bingonet.shared.packets.function.SplashLeechReportPacket
import de.hype.bingonet.shared.packets.function.SplashUpdatePacket
import kotlinx.coroutines.Job
import net.minecraft.entity.player.EntityPlayer
import java.lang.StringBuilder
import kotlin.time.Duration.Companion.minutes

@Suppress("SkyHanniModuleInspection")
object SplashStatusUpdateListener {
    private var splashed: Boolean = false
    var full: Boolean = false
    var data: SplashData? = null
    var maxPlayers: Int = 0
    var isInLobby: Boolean = true
    var currentJob: Job? = null


    // TODO fix this pattern
    private val selfSplashPattern = CompactSplashPotionMessage.selfSplashPattern

    @HandleEvent
    public fun onIslandChange(event: IslandChangeEvent) {
        val username = PlayerUtils.getName()
        data = SplashManager.getSplashInServer(true)
        maxPlayers = HypixelData.getMaxPlayersForCurrentServer() - 5
        currentJob?.cancel()
        currentJob = SkyHanniMod.launchCoroutine {
            run()
        }
    }

    fun run() {
        while (true) {
            if (!full && (HypixelData.getPlayersOnCurrentServer() >= maxPlayers)) {
                setStatus(StatusConstants.FULL)
                full = true
            }
            try {
                Thread.sleep(250)
            } catch (ignored: InterruptedException) {
            }
        }
    }

    public fun useOverlay(): Boolean {
        return SkyHanniMod.feature.event.bingo.bingoNetworks.splasherConfig.useSplasherOverlay
    }

    fun setStatus(newStatus: StatusConstants) {
        val data = data ?: return
        if (data.status != newStatus) BNConnection.sendPacket(SplashUpdatePacket(data.splashId, newStatus))
        if (newStatus == StatusConstants.SPLASHING) {
            splashed = true
            DelayedRun.runDelayed(1.minutes) {
                setStatus(StatusConstants.DONEBAD)
                currentJob?.cancel()
            }
        }
        data.status = newStatus
    }

    val leecherConfig = SkyHanniMod.feature.event.bingo.bingoNetworks.splasherConfig.leecherDMS

    @HandleEvent
    fun onChat(event: SkyHanniChatEvent) {
        val data = data ?: return
        selfSplashPattern.matchMatcher(event.message) {
            val previousStatus = data.status
            if (previousStatus == StatusConstants.SPLASHING) return@matchMatcher
            setStatus(StatusConstants.SPLASHING)
            if (leecherConfig.enabled && HypixelData.getRemainingSpace() <= 2) {
                //Sends a Packet to the Server that these Player Leeched the Splash. User can then confirm the List before Sanctions are caused.
                val data = EntityUtils.getEntitiesNextToPlayer<EntityPlayer>(5.0).filter { !it.isOnBingo() }
                    .map { Triple(it.displayName.formattedText, it.uniqueID, it.isOnIronman()) }.toList()
                if (HypixelData.getMaxPlayersForCurrentServer() - (HypixelData.getPlayersOnCurrentServer()) <= 2) {
                    BNConnection.sendPacket(SplashLeechReportPacket(data, leecherConfig.allowIman))
                }
            }
        }
    }

    @HandleEvent
    fun handleCommandRegistraction(event: CommandRegistrationEvent) {
        event.registerBrigadier("warnLeechers") {
            simpleCallback {
                val splash = SplashManager.getSplashInServer(true)
                if (splash == null) {
                    ChatUtils.userError("You are not hosting a Splash in this Server")
                    return@simpleCallback
                }
                val spots = HypixelData.getRemainingSpace()
                if (spots <= 2) {
                    val players: List<String> =
                        EntityUtils.getEntitiesNextToPlayer<EntityPlayer>(5.0).filter { !it.isOnBingo() }.map { it.name }.toList()
                    //Splashes are done for Bingo People. Normals or Ironmans are allowed but only if theres no further need for Bingo.
                    val messages = mutableListOf(StringBuilder("Leeching Splash will result in Bingo Net Sanctions! Leave now! "))
                    for (player in players) {
                        val last = messages.last()
                        if ((last.length + (player.length + 1)) > 256) {
                            messages.add(StringBuilder(player))
                        } else {
                            last.append(" ").append(player)
                        }
                    }
                    messages.forEach { ChatUtils.sendMessageToServer(it.toString().trim()) }
                } else {
                    ChatUtils.chat("There are still $spots spots left on this server!")
                }
            }
        }
    }


    @HandleEvent
    fun tablistUpdate(event: TabListUpdateEvent) {
        val data = data ?: return
        if (!(data.status == StatusConstants.WAITING || data.status == StatusConstants.FULL)) return
        if (HypixelData.getPlayersOnCurrentServer() >= maxPlayers) {
            setStatus(StatusConstants.FULL)
        } else {
            setStatus(StatusConstants.WAITING)
        }
    }
}
