package at.hannibal2.skyhanni.config.features.event.bingo

import at.hannibal2.skyhanni.config.FeatureToggle
import at.hannibal2.skyhanni.config.core.config.KeyBind
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

class BingoNetworksConfig {
    @Expose
    @ConfigOption(
        name = "Bingo Net (§c⚠ Closed Source Server!§r)",
        desc = "§c§lBingo Net is based on a closed Source Project by Hype_the_Time. SkyHanni has no insight nor control over the Servers.",
    )
    @ConfigEditorBoolean
    @Accordion
    val bingoNet: BingoNetConfig = BingoNetConfig()
    val useBN get() = bingoNet.useBN
    @Expose
    @ConfigOption(
        name = "Enable Bingo Brewers (§c⚠ Closed Source Server§r)",
        desc = "§c§lThe Bingo Brewers Network is a closed Source Project by indigo_polecat. " +
            "SkyHanni has no insight nor control over the Servers. " +
            "Bingo Brewers does not support all Features and some only partially."
    )
    @ConfigEditorBoolean
    @FeatureToggle
    var useBB: Boolean = false


    // TODO requires restart rn still so fix somehow?

    @Expose
    @ConfigOption(name = "Show Splashes", desc = "Show Splashes announcements")
    @ConfigEditorBoolean
    var showSplashes: Boolean = true

    @Expose
    @ConfigOption(name = "Highlight Splash Hub", desc = "Highlight the Splash Hubs in the Hub Selector.")
    @ConfigEditorBoolean
    var highlightSplashHub : Boolean = true

    @Expose
    @ConfigOption(name = "Show ChChests", desc = "Subscribe to ChChests.")
    @ConfigEditorBoolean
    var chestWaypoints: Boolean = true

    @Expose
    @ConfigOption(
        name = "Allow Server Invite",
        desc = "Allows the BingoNet Server to Manage your parties. This is required for some Features.",
    )
    @ConfigEditorBoolean
    var allowBNServerPartyManagement: Boolean = true

    @Expose
    @ConfigOption(name = "Show Bingo Chat", desc = "Bingo Chat is a Chat for every Bingo Net participant.")
    @ConfigEditorBoolean
    var showBingoChat: Boolean = true

    var showGoalCompletions: Boolean = false
    var showCardCompletions: Boolean = false

    var showPacketTraffic = false


    // TODO hide unless you have splasher perm?
    @Expose
    @ConfigOption(name = "§dSplasher Config", desc = "Only Important if you are a Splasher.")
    @Accordion
    val splasherConfig: SplasherConfig = SplasherConfig()

    @FeatureToggle
    @Expose
    @ConfigOption(
        name = "Show Splash Status Updates",
        desc = "Will inform you about Splash Status Updates in the Chat.",
    )
    @ConfigEditorBoolean
    var showSplashStatusUpdates: Boolean = true

    @Expose
    @ConfigOption(
        name = "Splash Multipurpose Keybind",
        desc = "Used to trigger Server Warp and if in Hub Selector to warp to the right splash automatically.",
    )
    @Accordion
    var splashHubWarp: KeyBind = KeyBind()

    @Expose
    @FeatureToggle
    @ConfigOption(
        name = "Show Private Splashes",
        desc = "Show Splashes that require you to join a party to be warped in.",
    )
    @ConfigEditorBoolean
    var showPrivateSplashes: Boolean = true

    @Expose
    @ConfigOption(
        name = "Server Action Chat Prompt Key",
        desc = "Shown when a Bingo Network server wants to receive an acknowledgement. NOT USED FOR PARTY COMMANDS",
    )
    @Accordion
    val serverActionChatPrompt = KeyBind()


    @Expose
    @ConfigOption(name = "Ch Chest Items Config", desc = "Configure the Chat Prompt Key and which items your are interested in.")
    @Accordion
    val chChestConfig: ChChestConfig = ChChestConfig()

    @Expose
    @ConfigOption(name = "Ch Chest Overlay", desc = "Show an Overlay with the Ch Chest Items in the Lobby.")
    @ConfigEditorBoolean
    var chChestOverlay : Boolean = true
}
