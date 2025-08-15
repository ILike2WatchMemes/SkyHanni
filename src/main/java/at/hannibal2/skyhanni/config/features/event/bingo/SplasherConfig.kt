package at.hannibal2.skyhanni.config.features.event.bingo

import at.hannibal2.skyhanni.config.FeatureToggle
import at.hannibal2.skyhanni.config.core.config.Position
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigLink
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class SplasherConfig {
    @Expose
    @FeatureToggle
    @ConfigOption(
        name = "Splasher Overlay",
        desc = "Show Data that is useful for a Splasher in an Overlay after you announced a Splash.",
    )
    @ConfigEditorBoolean
    var useSplasherOverlay: Boolean = true

    @Expose
    @ConfigOption(
        name = "Splasher Overlay Position",
        desc = "Position where to show the Splasher Overlay.",
    )
    @ConfigLink(owner = SplasherConfig::class, field = "useSplasherOverlay")
    var splasherOverlayPosition: Position = Position(0, 0)


    @Expose
    @FeatureToggle
    @ConfigOption(
        name = "Auto Splash Status Updates",
        desc = "Will automatically change the Status of YOUR Splashes to match the current State." +
            "This Feature is currently only supported for Bingo Net",
    )
    @ConfigEditorBoolean
    var autoSplashStatusUpdates: Boolean = true

    @Expose
    @ConfigOption(name = "Leecher DMs", desc = "Receive a DM from the Bot listing all Leechers during your Splash.")
    @Accordion
    var leecherDMS: LeecherDMS = LeecherDMS()

    class LeecherDMS{
        @Expose
        @ConfigOption(
            name = "Receive Leecher DMs",
            desc = "Receive a DM from the Bot listing all Leechers during your Splash.",
        )
        @ConfigEditorBoolean
        @FeatureToggle
        var enabled: Boolean = false

        @Expose
        @ConfigOption(
            name = "Default allow Ironman",
            desc = "Enable: By default allow Ironman Participants. If disabled they will be excluded in the Default List but can still be selected!",
        )
        @ConfigEditorBoolean
        var allowIman: Boolean = true
    }

    @Expose
    @ConfigOption(
        name = "Lesswaste Announcements",
        desc = "Announces Splashes so users get told later the more Duration they have left. " +
            "Disable this if you announce in multiple Servers/Systems.",
    )
    @ConfigEditorBoolean
    var lessWaste: Boolean = true


    @Expose
    @ConfigOption(
        name = "Highlight Lowest Player Hub",
        desc = "Shows the Hub with the lowest amount of players in the Hub Selector.",
    )
    @ConfigEditorBoolean
    var lowestPlayerHub: Boolean = false
}
