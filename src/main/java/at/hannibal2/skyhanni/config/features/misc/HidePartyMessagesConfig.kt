package at.hannibal2.skyhanni.config.features.misc

import at.hannibal2.skyhanni.config.FeatureToggle
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class HidePartyMessagesConfig {
    @Expose
    @ConfigOption(
        name = "Hide Party Wrapper",
        desc = "This will hide the Blue --- lines",
    )
    @ConfigEditorBoolean
    @FeatureToggle
    var hideWrapper: Boolean = false

    @Expose
    @ConfigOption(
        name = "Hide Party Disconnects",
        desc = "This will hide the messages when a player disconnects from the party.\n" +
            "0 Disabled\n" +
            "1 Always\n" +
            ">1 Amount of players in the party before hiding the message. For example hide if party size > 10",
    )
    @ConfigEditorSlider(minStep = 1f, maxValue = 20f, minValue = 0f)
    var hideDisconnects: Int = 0

    @Expose
    @ConfigOption(
        name = "Hide Party Join/Leave",
        desc = "This will hide the messages when a player joins or leaves the party.\n" +
            "0 Disabled\n" +
            "1 Always\n" +
            ">1 Amount of players in the party before hiding the message. For example hide if party size > 10",
    )
    @ConfigEditorSlider(minStep = 1f, maxValue = 20f, minValue = 0f)
    var hideJoinAndLeave: Int = 0

    @Expose
    @ConfigOption(
        name = "Hide Party Kicks",
        desc = "This will hide the messages when a player gets kicked from the party.\n" +
            "0 Disabled\n" +
            "1 Always\n" +
            ">1 Amount of players in the party before hiding the message. For example hide if party size > 10",
    )
    @ConfigEditorSlider(minStep = 1f, maxValue = 20f, minValue = 0f)
    var hideKicks: Int = 0


}
