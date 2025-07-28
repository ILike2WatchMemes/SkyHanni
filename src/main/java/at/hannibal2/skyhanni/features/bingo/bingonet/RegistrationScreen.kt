package at.hannibal2.skyhanni.features.bingo.bingonet

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.config.features.event.bingo.BingoNetConfig
import at.hannibal2.skyhanni.data.model.TextInput
import at.hannibal2.skyhanni.features.misc.discordrpc.DiscordRPCManager
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.GuiRenderUtils
import at.hannibal2.skyhanni.utils.MojangUtils
import at.hannibal2.skyhanni.utils.OSUtils
import at.hannibal2.skyhanni.utils.PlayerUtils
import at.hannibal2.skyhanni.utils.RenderUtils
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.compat.DrawContextUtils
import at.hannibal2.skyhanni.utils.compat.SkyhanniBaseScreen
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.RenderableUtils.renderXYAligned
import at.hannibal2.skyhanni.utils.renderables.container.VerticalContainerRenderable.Companion.vertical
import at.hannibal2.skyhanni.utils.renderables.primitives.WrappedStringRenderable.Companion.wrappedText
import at.hannibal2.skyhanni.utils.renderables.primitives.text
import de.hype.bingonet.BNConnection
import de.hype.bingonet.BNConnection.reconnectToBNServer
import de.hype.bingonet.environment.packetconfig.InterceptPacketInfo
import de.hype.bingonet.shared.packets.network.RequestAuthentication
import de.hype.bingonet.shared.packets.network.RequestRegisterPacket
import kotlin.time.Duration.Companion.minutes

class RegistrationScreen(
    val discordUserId: String,
    val discordUserName: String,
) : SkyhanniBaseScreen() {
    private val title = Renderable.text("Bingo Net Registration", horizontalAlign = RenderUtils.HorizontalAlignment.CENTER)
    private val description = Renderable.text(
        "§c⚠ Warning ⚠: The Bingo Net Server is a closed source Project by Hype_the_Time. " +
            "We as the Sky Hanni Team DO NOT HAVE ACCESS to the Server nor its Code.",
    )
    private val repeatLabel = Renderable.text("Please repeat the following Text in the Box below: ${RequestRegisterPacket.PHRASE}")
    val textInput = TextInput()
    private val textBox = Renderable.textBox("", TextInput(), width / 3)
    private val correctAccount = Renderable.clickable(
        "We auto detected your running Discord. Do you want to register with your §6$discordUserName§r Account? The Mc and DC connection can not be changed anymore afterwards! If this is not the desired Account swap over to it and follow the Bots DM instructions",
        {
            OSUtils.openBrowser("https://hackthetime.de/discord")
        },
    )
    private val discordLabel = Renderable.text(
        "Due too how Bingo Net works you break parts of the Functionality for you, BUT ALSO FOR OTHERS if you are not on the Discord." +
            " During Registration you HAVE to be in the Discord!",
    )
    private val discordLink = Renderable.link(
        Renderable.text("Bingo Net Discord: https://hackthetime.de/discord"), bypassChecks = true,
        onLeftClick = {},
    )
    private val openTerms = Renderable.clickable(
        "(Click to open Terms of Service, Privacy Policy and Rules)",
        {
            clickedTos = SimpleTimeMark.now()
            openTerms()
        },
    )
    var clickedTos: SimpleTimeMark? = null
    private val confirmButton = Renderable.darkRectButton(
        Renderable.text(
            "I accept the Terms of Service, Privacy Policy and Rules (click to register)",
        ),
        onClick = {
            val clicked = clickedTos
            if (clicked == null) {
                openTerms()
                clickedTos = SimpleTimeMark.now().plus(3.minutes)
                feedbackMessage = "§c You did not read the Terms of Service. You have a minimum of 3 Minutes to get an overview."
                return@darkRectButton
            } else if (clicked.isInPast()) {
                registerNow()
            } else openTerms()
        },
        horizontalAlign = RenderUtils.HorizontalAlignment.CENTER,
    )

    // Feedback message to show to the user
    private var feedbackMessage: String? = null

    override fun onDrawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        // Calculate the main area similar to ChangeLogViewerScreen
        val contentWidth = 4 * this.width / 5
        val contentHeight = 4 * this.height / 5
        val xTranslate = this.width / 10
        val yTranslate = this.height / 10

        drawDefaultBackground(mouseX, mouseY, partialTicks)
        DrawContextUtils.translate(xTranslate - 2.0, yTranslate - 2.0, 0.0)
        GuiRenderUtils.drawFloatingRectDark(0, 0, contentWidth, contentHeight)
        DrawContextUtils.translate(-(xTranslate - 2.0), -(yTranslate - 2.0), 0.0)

        DrawContextUtils.translate(xTranslate.toFloat(), yTranslate.toFloat() + 5, 0f)
        Renderable.withMousePosition(mouseX - xTranslate, mouseY - yTranslate) {
            // Text width should be smaller than content width for proper wrapping
            val textWidth = contentWidth - 40

            // Create a list of all UI elements
            val elements = mutableListOf<Renderable>()
            elements.add(Renderable.text("§l§9Bingo Net Registration", horizontalAlign = RenderUtils.HorizontalAlignment.CENTER))
            elements.add(Renderable.wrappedText(
                "§c⚠ Warning ⚠: The Bingo Net Server is a closed source Project by Hype_the_Time. " +
                "We as the Sky Hanni Team DO NOT HAVE ACCESS to the Server nor its Code.",
                textWidth
            ))
            elements.add(Renderable.wrappedText(
                "We auto detected your running Discord. Do you want to register with your §6$discordUserName§r Account? " +
                "The Mc and DC connection can not be changed anymore afterwards! If this is not the desired Account " +
                "swap over to it and follow the Bots DM instructions",
                textWidth
            ))
            elements.add(Renderable.wrappedText(
                "Due too how Bingo Net works you break parts of the Functionality for you, BUT ALSO FOR OTHERS " +
                "if you are not on the Discord. During Registration you HAVE to be in the Discord!",
                textWidth
            ))
            elements.add(discordLink)
            elements.add(Renderable.wrappedText(
                "Please repeat the following Text in the Box below: ${RequestRegisterPacket.PHRASE}",
                textWidth
            ))
            elements.add(Renderable.textBox("", textInput, textWidth / 2))
            elements.add(Renderable.clickable(
                text="§e(Click to open Terms of Service, Privacy Policy and Rules)§r",
                onLeftClick= {
                    clickedTos = SimpleTimeMark.now()
                    openTerms()
                },
            ))
            elements.add(Renderable.darkRectButton(
                Renderable.wrappedText(
                    "I accept the Terms of Service, Privacy Policy and Rules (click to register)",
                    textWidth / 2
                ),
                onClick = {
                    val clicked = clickedTos
                    if (clicked == null) {
                        openTerms()
                        clickedTos = SimpleTimeMark.now().plus(3.minutes)
                        feedbackMessage = "§c You did not read the Terms of Service. You have a minimum of 3 Minutes to get an overview."
                        return@darkRectButton
                    } else if (clicked.isInPast()) {
                        registerNow()
                    } else openTerms()
                },
                horizontalAlign = RenderUtils.HorizontalAlignment.CENTER,
            ))
            feedbackMessage?.let { msg ->
                elements.add(Renderable.wrappedText(msg, textWidth))
            }

            // Create a vertical list that centers itself
            Renderable.vertical(
                elements,
                spacing = 10,
                verticalAlign = RenderUtils.VerticalAlignment.CENTER
            ).renderXYAligned(0, 0, contentWidth, contentHeight)
        }
        DrawContextUtils.translate(-xTranslate.toFloat(), -yTranslate.toFloat() - 5, 0f)
    }

    fun openTerms() {
        val url = "https://hackthetime.de/"
        OSUtils.openBrowser("$url/privacy")
        OSUtils.openBrowser("$url/tos")
        OSUtils.openBrowser("$url/rules")
    }

    fun registerNow() {
        if (!RequestRegisterPacket.passesAccuracyCheck(textInput.textBox)) {
            // Show error that they have to write the phrase.
            feedbackMessage = "§cYou must correctly repeat the phrase in the box below (95% accuracy, case-insensitive)."
            return
        }
        feedbackMessage = "§eWaiting for server response..."

        val intercept: InterceptPacketInfo<RequestAuthentication> =
            object : InterceptPacketInfo<RequestAuthentication>(
                RequestAuthentication::class.java, true, true,
                false, true,
            ) {
                override fun run(packet: RequestAuthentication) {
                    val clientRandom = MojangUtils.generateClientRandom()
                    val full = clientRandom + packet.serverIdSuffix
                    MojangUtils.joinServer(full)
                    val requestRegistration = RequestRegisterPacket(
                        PlayerUtils.getRawUuid(),
                        discordUserId,
                        clientRandom,
                    )
                    BNConnection.sendPacket(requestRegistration)
                }
            }
        val reponseIntercept = object : InterceptPacketInfo<RequestRegisterPacket.MCRegistrationResponsePacket>(
            RequestRegisterPacket.MCRegistrationResponsePacket::class.java, true, true,
            false, true,
        ) {
            override fun run(packet: RequestRegisterPacket.MCRegistrationResponsePacket) {
                val stringResponse = when (packet.response) {
                    RequestRegisterPacket.MCRegistrationResponsePacket.ResponseType.AWAITING_DC_USER_CONFIRMATION ->
                        "§aYou should have received a DM on Discord to confirm your Account."

                    RequestRegisterPacket.MCRegistrationResponsePacket.ResponseType.NOT_ON_DISCORD ->
                        "§cYou are not on the Bingo Net Discord Server. You need to join for this to work."

                    RequestRegisterPacket.MCRegistrationResponsePacket.ResponseType.ALREADY_REGISTERED ->
                        "§cEither your MC Account or Discord Account is already registered."

                    RequestRegisterPacket.MCRegistrationResponsePacket.ResponseType.BAD_REQUEST ->
                        "§cThe request was malformed. Please open a Bug Report on the Bingo Net Discord Server."

                    RequestRegisterPacket.MCRegistrationResponsePacket.ResponseType.ERROR ->
                        "§cThere was an error on our side (Bingo Net)."
                }
                feedbackMessage = stringResponse
            }
        }
        SkyHanniMod.launchCoroutine {
            BNConnection.reconnectToBNServer(false, BingoNetConfig.BingoNetSystem.MAIN, listOf(intercept, reponseIntercept))
        }
    }

    companion object {
        fun openHelper() {
            SkyHanniMod.launchCoroutine {
                val isStarted = DiscordRPCManager.isStarted()
                if (!isStarted || !DiscordRPCManager.isConnected()) {
                    ChatUtils.chat("Starting Rich Presence to obtain Discord User ID and Username.")
                    DiscordRPCManager.start(false)
                }
                val userId = DiscordRPCManager.getDiscordUserId()
                val username = DiscordRPCManager.getDiscordUsername()
                val hasDiscordAvailable = userId != null && username != null
                if (hasDiscordAvailable) {
                    ChatUtils.clickableChat(
                        "§cYou are not registered in the Bingo Net Network. Click here to open the Registration Screen",
                        {
                            SkyHanniMod.screenToOpen =
                                RegistrationScreen(userId, username)
                        },
                    )
                } else {
                    ChatUtils.chat(
                        "Could not obtain Discord User ID or Username. Falling back to website Registration. " +
                            "You may retry execution after starting Discord if it wasn't.",
                    )
                    ChatUtils.clickableChat(
                        "§cYou are not registered in the Bingo Net Network." +
                            " Click here to open the Discord Invite and follow the Bot DM instructions " +
                            "(Will lead you to the correct place IN THE SERVER!)",
                        {
                            OSUtils.openBrowser("https://hackthetime.de/discord")
                        },
                    )
                }
                if (!isStarted) {
                    DiscordRPCManager.stop()
                }
            }
        }
    }
}
