package at.hannibal2.skyhanni.config.features.event.bingo

import at.hannibal2.skyhanni.config.core.config.KeyBind
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class ChChestConfig {
    @Expose
    @ConfigOption(
        name = "Show All Valuable Findings",
        desc = "When enabled it will show you a Chat Prompt when any Valuable Item is found.",
    )
    @ConfigEditorBoolean
    var allChChestItem: Boolean = false

    @Expose
    @ConfigOption(name = "Show Prehistoric Eggs", desc = "When enabled it will show")
    @ConfigEditorBoolean
    var prehistoricEgg: Boolean = true

    @Expose
    @ConfigOption(name = "Show Pickonimbus 2000", desc = "When enabled it will show you a Chat Prompt when a Pickonimbus 2000 is found.")
    @ConfigEditorBoolean
    var pickonimbus2000: Boolean = false

    @Expose
    @ConfigOption(name = "Show Control Switch", desc = "When enabled it will show you a Chat Prompt when a Control Switch is found.")
    @ConfigEditorBoolean
    var controlSwitch: Boolean = false

    @Expose
    @ConfigOption(
        name = "Show Electron Transmitter",
        desc = "When enabled it will show you a Chat Prompt when an Electron Transmitter is found.",
    )
    @ConfigEditorBoolean
    var electronTransmitter: Boolean = false

    @Expose
    @ConfigOption(name = "Show FTX 3070", desc = "When enabled it will show you a Chat Prompt when an FTX 3070 is found.")
    @ConfigEditorBoolean
    var ftx3070: Boolean = false

    @Expose
    @ConfigOption(
        name = "Show Robotron Reflector",
        desc = "When enabled it will show you a Chat Prompt when a Robotron Reflector is found.",
    )
    @ConfigEditorBoolean
    var robotronReflector: Boolean = false

    @Expose
    @ConfigOption(name = "Show Superlite Motor", desc = "When enabled it will show you a Chat Prompt when a Superlite Motor is found.")
    @ConfigEditorBoolean
    var superliteMotor: Boolean = false

    @Expose
    @ConfigOption(name = "Show Synthetic Heart", desc = "When enabled it will show you a Chat Prompt when a Synthetic Heart is found.")
    @ConfigEditorBoolean
    var syntheticHeart: Boolean = false

    @Expose
    @ConfigOption(name = "Show Flawless Gemstone", desc = "When enabled it will show you a Chat Prompt when a Flawless Gemstone is found.")
    @ConfigEditorBoolean
    var flawlessGemstone: Boolean = false

    @Expose
    @ConfigOption(name = "Show Gemstone Powder", desc = "When enabled it will show you a Chat Prompt when Gemstone Powder is found.")
    @ConfigEditorBoolean
    var allRoboPart: Boolean = false

    @Expose
    @ConfigOption(
        name = "Show 5k+ Gemstone Powder",
        desc = "When enabled it will show you a Chat Prompt when a LOT of Gemstone Powder is found.",
    )
    @ConfigEditorBoolean
    var gemstonePowder: Boolean = false

    @Expose
    @ConfigOption(
        name = "Show 5k+ Mithril Powder",
        desc = "When enabled it will show you a Chat Prompt when a LOT of Mithril Powder is found.",
    )
    @ConfigEditorBoolean
    var mithrilPowder: Boolean = false

    @Expose
    @ConfigOption(name = "Request Party Keybind", desc = "Keybind to request a Party Invite when a chchest shown to you.")
    @Accordion
    val chChestChatPrompt = KeyBind()
}
