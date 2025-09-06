package at.hannibal2.skyhanni.config.features.event.bingo

import at.hannibal2.skyhanni.config.FeatureToggle
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class BingoNetConfig {
    @Expose
    @ConfigOption(
        name = "Enable Bingo Net (§c⚠ Closed Source Server!§r)",
        desc = "§c§lBingo Net is based on a closed Source Project by Hype_the_Time. SkyHanni has no insight nor control over the Servers.",
    )
    @ConfigEditorBoolean
    @FeatureToggle
    var useBN: Boolean = false

    @Expose
    @ConfigOption(
        name = "Bingo Net Server (Main/Beta/Alpha)",
        desc = "Which Server Version do you want to connect to by default?\n" +
            "Do Not Change this unless you know what you are doing!",
    )
    @ConfigEditorDropdown
    var system: BingoNetSystem = BingoNetSystem.MAIN

    @Expose
    @ConfigOption(
        name = "Bingo Net API/Legacy Key - Optional",
        desc = "API/Legacy Key can be used instead of Mojang Auth. This prevents the possible restart your client message when the Mojang Tokens expired. Leave empty to use Mojang Auth.",
    )
    @ConfigEditorText
    @Suppress("VariableNaming", "PropertyName")
    var BNApiKey: String = ""

}

enum class BingoNetSystem {
    MAIN("Main Server", 5000),
    BETA("Beta Server", 5011),
    ALPHA("Alpha Server", 5012);

    private val displayName: String
    @Suppress("StorageNeedsExpose")
    val port: Int

    constructor(displayName: String, port: Int) {
        this.displayName = displayName
        this.port = port
    }

    override fun toString(): String {
        return displayName
    }
}
