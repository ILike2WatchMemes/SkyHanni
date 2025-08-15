package at.hannibal2.skyhanni.features.bingo.bingonetworks

import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.SecondPassedEvent
import at.hannibal2.skyhanni.features.bingo.bingonet.SplashManager
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.EntityUtils.isOnBingo
import at.hannibal2.skyhanni.utils.EntityUtils.isOnIronman
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.primitives.text
import net.minecraft.entity.player.EntityPlayer

@SkyHanniModule
object SplasherOverlay {

    private const val SCALE = 0.5714286f
    private const val VERTICAL_OFFSET = (10 * SCALE).toInt()

    private val config get() = SkyHanniMod.feature.event.bingo.bingoNetworks.splasherConfig
    private var renderables: List<Renderable>? = null

    @HandleEvent
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!isEnabled()) return
        val renderables = renderables ?: return
        config.splasherOverlayPosition.renderRenderables(renderables, posLabel = "Splasher Overlay")
    }

    @HandleEvent(onlyOnSkyblock = true)
    fun refreshData(event: SecondPassedEvent) {
        if (!isEnabled()) {
            this.renderables = null
            return
        }
        val splash = SplashManager.getSplashInServer(true)
        if (splash == null) {
            this.renderables = null
            return
        }

        val renderables = mutableListOf<Renderable>()
        renderables.add(Renderable.text("Status: ${splash.status.displayName}"))
        val players = EntityUtils.getEntitiesNextToPlayer<EntityPlayer>(5.0)
        renderables.add(Renderable.text("Hub: $players/${HypixelData.getMaxPlayersForCurrentServer()}"))
        val bingos = players.filter { it.isOnBingo() }
        val iman = players.filter { it.isOnIronman() }
        renderables.add(Renderable.text("Participants: Ⓑ${bingos.count()} | ♲${iman.count()} "))
        renderables.add(Renderable.text("Leechers: \n${players.filter { ! (it.isOnBingo()||it.isOnIronman()) }.joinToString("\n") { it.displayName.formattedText }} "))

        this.renderables = renderables
    }

    private fun isEnabled() = config.useSplasherOverlay
}
