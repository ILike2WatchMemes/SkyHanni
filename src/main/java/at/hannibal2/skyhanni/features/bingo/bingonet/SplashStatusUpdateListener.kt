package at.hannibal2.skyhanni.features.bingo.bingonet

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.events.TabListUpdateEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.PlayerUtils
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import de.hype.bingonet.BNConnection
import de.hype.bingonet.shared.constants.StatusConstants
import de.hype.bingonet.shared.objects.SplashData
import de.hype.bingonet.shared.packets.function.SplashUpdatePacket
import kotlinx.coroutines.Job
import kotlin.time.Duration.Companion.minutes

@Suppress("SkyHanniModuleInspection")
object SplashStatusUpdateListener {
    private var splashed: Boolean = false
    var full: Boolean = false
    var data: SplashData? = null
    var maxPlayers: Int = 0
    var isInLobby: Boolean = true
    var currentJob: Job? = null

    private val repoPatterns = RepoPattern.group("feature.event.bingo.bingoNetworks.splashes")

    //TODO fix this pattern
    private val selfSplashPattern by repoPatterns.pattern("selfSplash", "§aBUFF! You splashed yourself with")

    @HandleEvent
    public fun onHypixelJoin(event: IslandChangeEvent) {
        val username = PlayerUtils.getName()
        data = SplashManager.splashPool.values.firstOrNull {
            it.serverID == HypixelData.serverId && it.announcer == username
        }
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
        return SkyHanniMod.feature.event.bingo.bingoNetworks.useSplasherOverlay
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
        data?.status = newStatus
    }

    @HandleEvent
    fun messageEvent(event: SkyHanniChatEvent) {
        if (data == null) return
        selfSplashPattern.matchMatcher(event.message) {
            setStatus(StatusConstants.SPLASHING)
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
