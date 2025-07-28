package at.hannibal2.skyhanni.config.features.event.bingo

import at.hannibal2.skyhanni.config.FeatureToggle
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
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
    @FeatureToggle
    @ConfigOption(
        name = "Auto Splash Status Updates",
        desc = "Will automatically change the Status of YOUR Splashes to match the current State.",
    )
    @ConfigEditorBoolean
    var autoSplashStatusUpdates: Boolean = true

    @Expose
    @ConfigOption(
        name = "Lesswaste Announcements",
        desc = "Announces Splashes so users get told later the more Duration they have left. " +
            "Disable this if you announce in multiple Servers.",
    )
    @ConfigEditorBoolean
    var lessWaste: Boolean = true
}
