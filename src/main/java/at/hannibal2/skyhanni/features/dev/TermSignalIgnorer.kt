package at.hannibal2.skyhanni.features.dev

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.hypixel.HypixelJoinEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import sun.misc.Signal
import sun.misc.SignalHandler

@SkyHanniModule
object TermSignalIgnorer {

    @HandleEvent
    fun registerTermHandler(event: HypixelJoinEvent) {
        if (!SkyHanniMod.feature.dev.ignoreTermSignal) return
        val ignoreHandler = SignalHandler { sig: Signal ->
            println("Ignored signal: " + sig.getName())
        }
        Signal.handle(Signal("TERM"), ignoreHandler)
        println("Registered TERM signal handler")
    }
}
