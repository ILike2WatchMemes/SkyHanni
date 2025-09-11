package de.hype.bingonet

// import de.hype.bingonet.shared.packets.function.MinionDataResponse.RequestMinionDataPacket
import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.features.event.bingo.BingoNetSystem
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.data.PartyApi
import at.hannibal2.skyhanni.data.effect.EffectApi
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.IslandChangeEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.features.bingo.bingonet.BNRegistrationScreen
import at.hannibal2.skyhanni.features.bingo.bingonet.SplashManager
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.MojangUtils
import at.hannibal2.skyhanni.utils.OSUtils
import at.hannibal2.skyhanni.utils.PlayerUtils
import at.hannibal2.skyhanni.utils.SoundUtils
import at.hannibal2.skyhanni.utils.SoundUtils.createSound
import at.hannibal2.skyhanni.utils.SoundUtils.playSound
import at.hannibal2.skyhanni.utils.TimeUtils.format
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.removeIf
import at.hannibal2.skyhanni.utils.render.WorldRenderUtils.drawDynamicText
import at.hannibal2.skyhanni.utils.render.WorldRenderUtils.drawLineToEye
import at.hannibal2.skyhanni.utils.render.WorldRenderUtils.drawWaypointFilled
import at.hannibal2.skyhanni.utils.render.WorldRenderUtils.renderBeaconBeam
import at.hannibal2.skyhanni.utils.system.PlatformUtils
import de.hype.bingonet.environment.packetconfig.AbstractPacket
import de.hype.bingonet.environment.packetconfig.InterceptPacketInfo
import de.hype.bingonet.environment.packetconfig.Packet
import de.hype.bingonet.environment.packetconfig.PacketUtils
import de.hype.bingonet.shared.constants.AuthenticationConstants
import de.hype.bingonet.shared.constants.InternalReasonConstants
import de.hype.bingonet.shared.constants.PartyConstants
import de.hype.bingonet.shared.objects.BNRole
import de.hype.bingonet.shared.objects.Position
import de.hype.bingonet.shared.objects.WaypointData
import de.hype.bingonet.shared.packets.base.ExpectReplyPacket
import de.hype.bingonet.shared.packets.function.GetWaypointsPacket
import de.hype.bingonet.shared.packets.function.PacketChatPromptPacket
import de.hype.bingonet.shared.packets.function.PartyPacket
import de.hype.bingonet.shared.packets.function.PlaySoundPacket
import de.hype.bingonet.shared.packets.function.SplashNotifyPacket
import de.hype.bingonet.shared.packets.function.SplashTimeRequestPacket
import de.hype.bingonet.shared.packets.function.WaypointPacket
import de.hype.bingonet.shared.packets.network.BingoChatMessagePacket
import de.hype.bingonet.shared.packets.network.BroadcastMessagePacket
import de.hype.bingonet.shared.packets.network.CompletedGoalPacket
import de.hype.bingonet.shared.packets.network.DisconnectPacket
import de.hype.bingonet.shared.packets.network.InvalidCommandFeedbackPacket
import de.hype.bingonet.shared.packets.network.PunishedPacket
import de.hype.bingonet.shared.packets.network.RequestAuthentication
import de.hype.bingonet.shared.packets.network.RequestConnectPacket
import de.hype.bingonet.shared.packets.network.SystemMessagePacket
import de.hype.bingonet.shared.packets.network.WantedSearchPacket
import de.hype.bingonet.shared.packets.network.WantedSearchPacket.WantedSearchPacketReply
import de.hype.bingonet.shared.packets.network.WelcomeClientPacket
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.lang.String
import java.net.Socket
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

@SkyHanniModule
@Suppress("UnusedParameter")
object BNConnection {
    var messageReceiverThread: Thread? = null
    var messageSenderThread: Thread? = null

    @JvmField
    var packetIntercepts: MutableList<InterceptPacketInfo<*>> = ArrayList()
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private var messageQueue: LinkedBlockingQueue<String>? = null
    var authenticated: Boolean? = null
        private set

    // Viewing Packet Traffic can pose as a Unfair Advantage (Splashes).
    val roles = mutableSetOf<BNRole>(BNRole.DEBUG)

    private val config get() = SkyHanniMod.feature.event.bingo.bingoNetworks
    private val bnConfig get() = SkyHanniMod.feature.event.bingo.bingoNetworks.bingoNet

    val waypoints: MutableMap<Int, WaypointData> = HashMap()


    private fun createSSLContext(): SSLContext {
        // Load the certificate from resources/assets/public_bingonet_cert.crt
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certStream: InputStream? =
            javaClass.classLoader.getResourceAsStream("bingonet/public_bingonet_cert.crt")
        requireNotNull(certStream) { "Certificate file not found" }
        val certificate = certificateFactory.generateCertificate(certStream)
        certStream.close()

        // Create a KeyStore containing our certificate
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setCertificateEntry("bingonet", certificate)

        // Initialize TrustManagerFactory with the KeyStore
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)

        // Create the SSLContext using the trust managers from our certificate
        return SSLContext.getInstance("TLS").apply {
            init(null, trustManagerFactory.trustManagers, SecureRandom())
        }
    }

    @HandleEvent
    fun launchHook(event: ConfigLoadEvent) {
        if (bnConfig.useBN) {
            SkyHanniMod.launchCoroutine {
                if (!isConnected) connect("hackthetime.de", bnConfig.system.port)
            }
        } else {
            disconnect()
        }
    }


    fun connect(serverIP: kotlin.String = "hackthetime.de", serverPort: Int) {
        try {
            val sslContext = createSSLContext()
            val sslSocketFactory = sslContext.socketFactory

            socket = sslSocketFactory.createSocket(serverIP, serverPort).also { socket ->
                socket.soTimeout = 0
                socket.tcpNoDelay = true
                socket.keepAlive = true
            }

            messageQueue = LinkedBlockingQueue()
            reader = BufferedReader(InputStreamReader(socket?.inputStream!!))
            writer = PrintWriter(OutputStreamWriter(socket?.outputStream!!), true)

            setupMessageThreads()
        } catch (e: IOException) {
            throw RuntimeException("Failed to establish connection", e)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("SSL initialization failed", e)
        }
    }

    private fun setupMessageThreads() {
        messageReceiverThread = Thread(
            {
                try {
                    while (!Thread.currentThread().isInterrupted && isConnected) {
                        reader?.readLine()?.let { message ->
                            if (message.isNotEmpty()) {
                                onMessageReceived(message)
                            }
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    close()
                }
            },
        ).apply {
            isDaemon = true
            name = "BingoNet-Receiver"
            start()
        }

        messageSenderThread = Thread(
            {
                try {
                    while (!Thread.currentThread().isInterrupted && isConnected) {
                        messageQueue?.poll(100, TimeUnit.MILLISECONDS)?.let { message ->
                            writer?.println(message)
                            writer?.flush()
                        }
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            },
        ).apply {
            isDaemon = true
            name = "BingoNet-Sender"
            start()
        }
    }

    private val reportedErrors = mutableSetOf<kotlin.String>()
    fun onMessageReceived(message: kotlin.String) {
        try {
            val packet: Pair<Packet<out AbstractPacket>, AbstractPacket>? = PacketUtils.parsePacket(message)
            if (packet == null) {
                ChatUtils.chat("§cBN: Received unknown message: $message", prefix = false)
                return
            }
            if (handleIntercept(packet.second)) {
                if (SkyHanniMod.feature.event.bingo.bingoNetworks.showPacketTraffic && roles.contains(BNRole.DEBUG)) {
                    val json = message.split(Regex("\\."), 2)[1]
                    ChatUtils.clickableChat(
                        "§b[BN-REC]: $json",
                        {
                            OSUtils.copyToClipboard(json)
                        },
                        prefix = false,
                    )
                }
                val consumer: Consumer<AbstractPacket> = packet.first.consumer as Consumer<AbstractPacket>
                consumer.accept(packet.second)
            }
        } catch (e: Throwable) {
            // This should prevent any Issues from Bingo Net from causing an actual Issue. The Set Filter should prevent Error Spam.
            val key = "${e::class.java.name}:${e.message}"
            if (reportedErrors.add(key)) {
                ErrorManager.logErrorWithData(e, "Error reading Bingo Net Packet", "packetJson" to message)
            }
        }
    }

    /**
     * return false if the packet should be thrown away due to being handled by an intercept.
     */
    @Synchronized
    fun <T : AbstractPacket> handleIntercept(packet: T): Boolean {
        val packetClass: Class<T>
        try {
            packetClass = packet.javaClass
        } catch (e: Exception) {
            return true
        }
        val intercepts: MutableList<InterceptPacketInfo<*>> = packetIntercepts
        val indexes: MutableList<Int> = ArrayList<Int>()
        var cancelIntercept = false
        var cancelMainExec = false
        var found = false
        for (i in intercepts.indices) {
            val intercept: InterceptPacketInfo<*> = intercepts[i]
            if (intercept.matches(packetClass)) {
                if (packet is ExpectReplyPacket.ReplyPacket && packet.replyDate == intercept.replyId) {
                    if (!intercept.ignoreIfIntercepted || !found) {
                        found = true
                        if (intercept.cancelPacket) {
                            cancelMainExec = true
                        }
                        if (intercept.blockIntercepts) {
                            cancelIntercept = true
                        }
                        if (intercept.blockExecutionForCompletion) {
                            intercepts.remove(intercept)
                            intercept.parseAndRun(packet)
                        } else {
                            intercepts.remove(intercept)
                            SkyHanniMod.launchCoroutine {
                                intercept.parseAndRun(packet)
                            }
                        }
                    }
                    if (cancelIntercept) break
                } else {
                    indexes.add(i)
                }
            }
        }
        if (!cancelIntercept) {
            for (index in indexes) {
                val intercept: InterceptPacketInfo<*> = intercepts.get(index)
                if (intercept.matches(packetClass)) {
                    if (!intercept.ignoreIfIntercepted || !found) {
                        found = true
                        if (intercept.cancelPacket) {
                            cancelMainExec = true
                        }
                        if (intercept.blockIntercepts) {
                            cancelIntercept = true
                        }
                        if (intercept.blockExecutionForCompletion) {
                            intercept.parseAndRun(packet)
                        } else {
                            SkyHanniMod.launchCoroutine {
                                intercept.parseAndRun(packet)
                            }
                        }
                    }
                    if (cancelIntercept) break
                }
            }
        }

        return !cancelMainExec
    }

    fun <T : AbstractPacket> dummy(o: T?) {
        // this does absolutely nothing. dummy for packet in packt manager
    }

    fun <E : AbstractPacket> sendPacket(packet: E, blockLog: Boolean = false, retry: Int = 1) {
        val packetName = packet.javaClass.getSimpleName()
        val rawjson = PacketUtils.parsePacketToJson(packet)
        if (this.isConnected && writer != null) {
            if (!blockLog) {
                ChatUtils.clickableChat(
                    "§b[BN-Send]: $rawjson",
                    {
                        OSUtils.copyToClipboard(rawjson)
                    },
                    prefix = false,
                )
            }
            writer!!.println("$packetName.$rawjson")
        } else {
            if (retry <= 0) {
                ChatUtils.chat("§cBN: Failed to send packet $packetName. Not connected to Bingo Net Server.")
            } else {
                SkyHanniMod.launchCoroutine {
                    BNConnection.reconnectToBNServer()
                    sendPacket(packet, blockLog, retry - 1)
                }
            }
        }
    }

    suspend fun BNConnection.reconnectToBNServer(
        ignoreIfConnected: Boolean = true,
        system: BingoNetSystem = bnConfig.system,
        packetIntercepts: List<InterceptPacketInfo<*>> = emptyList(),
    ) {
        if (bnConfig.useBN) {
            if (ignoreIfConnected) {
                if (isConnected) return
                disconnect()
            }
            connect("hackthetime.de", system.port)
        } else {
            ChatUtils.clickableChat(
                "Bingo Net is currently disabled. (Click to enable). §cKeep in mind that Hype_the_Time controls the Server and NOT the Sky Hanni Team!",
                {
                    SkyHanniMod.launchCoroutine {
                        bnConfig.useBN = true
                        reconnectToBNServer(ignoreIfConnected, system, packetIntercepts)
                    }
                },
            )
        }
    }

    fun onBroadcastMessagePacket(packet: BroadcastMessagePacket) {
        ChatUtils.chat("§6[BN-Announcement] §r[" + packet.prefix + "§r]§6 " + packet.username + ": " + packet.message, prefix = false)
    }

    fun onSplashNotifyPacket(packet: SplashNotifyPacket) {
        // influencing the delay in any way is disallowed!
        val waitTime: Int
        if (packet.splash.announcer == PlayerUtils.getName() && config.splasherConfig.autoSplashStatusUpdates) {
            ChatUtils.chat("The Splash Update Statuses will be updatet automatically for you. If you need to do something manually go into Discord Splash Dashboard")
        } else {
            SplashManager.addSplash(packet.splash, SplashManager.SplashSource.BN)
            if (packet.splash.lessWaste) {
                val remainingDuration = EffectApi.getGodSplashDuration()
                waitTime = min((remainingDuration.inWholeSeconds.toInt() / 80), 25)
            } else {
                waitTime = 0
            }
            DelayedRun.runDelayed(waitTime.seconds) {
                SplashManager.display(packet.splash.splashId, SplashManager.SplashSource.BN)
            }
        }
    }

    fun onBingoChatMessagePacket(packet: BingoChatMessagePacket) {
        if (config.showBingoChat) {
            val prefix = if (packet.prefix == null) "" else "[${packet.prefix}§r]"
            ChatUtils.clickableChat(
                "§6BC > §r$prefix ${packet.username}: ${packet.message}",
                {
                    ChatUtils.suggestInChat("/bc @${packet.username} ")
                },
                "Bingo Cards: ${packet.bingo_cards}",
                prefix = false,
            )
        }
    }


    fun onWelcomePacket(packet: WelcomeClientPacket) {
        authenticated = packet.success
        if (packet.success) {
            roles.clear()
            roles.addAll(packet.roles)
            ChatUtils.chat("§aBN: Login Success")
        } else {
            ChatUtils.chat("§cBN: Login Failed")
        }
    }

    fun onDisconnectPacket(packet: DisconnectPacket) {
        for (i in packet.waitBeforeReconnect.indices) {
            val finalI = i
            DelayedRun.runDelayed(
                (packet.waitBeforeReconnect[i] + (Math.random() * packet.randomExtraDelay)).seconds,
                {
                    SkyHanniMod.launchCoroutine {
                        if (finalI == 0) {
                            reconnectToBNServer(false)
                        } else {
                            reconnectToBNServer(true)
                        }
                    }
                },
            )
        }

        val reason = packet.internalReason
        if (reason == InternalReasonConstants.NOT_REGISTERED) {
            BNRegistrationScreen.openHelper()
        } else if (reason == InternalReasonConstants.BANNED) {
            ChatUtils.chat("§cIt appears that you have been banned from the Bingo Net Network. Due to this the Bingo Net Integration deactivated itself!")
            bnConfig.useBN = false
        } else if (packet.waitBeforeReconnect.isEmpty()) {
            ChatUtils.chat("§cBN: You have been disconnected from the Bingo Net Network.")
        } else {
            for (i in packet.waitBeforeReconnect) {
                DelayedRun.runDelayed(
                    i.seconds,
                    {
                        SkyHanniMod.launchNoScopeCoroutine {
                            reconnectToBNServer(true)
                        }
                    },
                )
            }
        }
        disconnect()
    }

    // TODO error report to BN Server via packet? Optionally via Config option automatically?
    fun onInvalidCommandFeedbackPacket(packet: InvalidCommandFeedbackPacket) {
        // TODO upgrade via sth like run command packet interface and then reply just command failed maybe error too or sth and then fail command execution and show user exact command or sth? maybe clickable for slighly changeable?
        ChatUtils.chat("§cBN: ${packet.displayMessage}")
    }

    fun onPartyPacket(packet: PartyPacket) {
        // This allows Deactivating the Remote Party Control HOWEVER
        // It is needed for a LOT of Features which can both cause Issues and will block some Features that depend on it to not work.
        if (config.allowBNServerPartyManagement) {
            val isInParty = PartyApi.isInParty()
            if (!isInParty && !(packet.type == PartyConstants.JOIN || packet.type == PartyConstants.ACCEPT || packet.type == PartyConstants.INVITE)) return
            PartyApi.isPartyLeader()
            PartyApi.isModerator()

            if (packet.type == PartyConstants.JOIN) {
                PartyApi.leaveParty()
                ChatUtils.clickableChat(
                    "BN: Joining party requested by Bingo Net Server. Click to disable this Permission!",
                    {
                        config.allowBNServerPartyManagement = false
                    },
                )
                PartyApi.joinParty(packet.users.first())
            } else if (packet.type == PartyConstants.ACCEPT) {
                PartyApi.leaveParty()
                ChatUtils.clickableChat(
                    "BN: Joining party requested by Bingo Net Server. Click to disable this Permission!",
                    {
                        config.allowBNServerPartyManagement = false
                    },
                )
                PartyApi.acceptParty(packet.users.first())
            } else if (packet.type == PartyConstants.DISBAND) {
                ChatUtils.clickableChat(
                    "BN: Party Disband requested by Bingo Net Server. Click to disable this Permission!",
                    {
                        config.allowBNServerPartyManagement = false
                    },
                )
                if (!PartyApi.disband()) {
                    HypixelCommands.partyChat("Bingo Net Server requested party disband but you are not the leader. Leaving party")
                }
            } else if (packet.type == PartyConstants.INVITE) {
                if (PartyApi.canInvite()) {
                    PartyApi.invite(packet.users)
                } else {
                    HypixelCommands.partyChat("/pc Bingo Net Server requested a party invite for: ${packet.users}")
                }
            } else if (packet.type == PartyConstants.WARP) {
                if (PartyApi.warp()) {
                    ChatUtils.clickableChat(
                        "BN: Party Disband requested by Bingo Net Server. Click to disable this Permission!",
                        {
                            config.allowBNServerPartyManagement = false
                        },
                    )
                }
            } else if (packet.type == PartyConstants.KICK) {
                if (PartyApi.kick(packet.users)) {
                    ChatUtils.clickableChat(
                        "BN: Party kicks requested by Bingo Net Server. Click to disable this Permission!",
                        {
                            config.allowBNServerPartyManagement = false
                        },
                    )
                }
            } else if (packet.type == PartyConstants.PROMOTE) {
                PartyApi.promote(packet.users.first())
            } else if (packet.type == PartyConstants.LEAVE) {
                PartyApi.leaveParty()
            }
        }
    }

    fun onSystemMessagePacket(packet: SystemMessagePacket) {
        if (packet.important) {
            ChatUtils.chat("§n${packet.message}")
        } else {
            ChatUtils.chat(packet.message)
        }
        if (packet.ping) {
            SoundUtils.playPlingSound()
        }
    }

    fun onRequestAuthentication(packet: RequestAuthentication) {
        if (socket?.getPort() == 5011) {
            ChatUtils.chat("§aBN: Logging into Bingo Net (§6Beta§a)")
            ChatUtils.chat("§6You may test here but do NOT Spam unless you have very good reasons. Spamming may still be punished")
        } else {
            ChatUtils.chat("§aBN: Logging into Bingo Net")
        }
        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
        val clientRandom = MojangUtils.generateClientRandom()

        val serverId = clientRandom + packet.serverIdSuffix

        if (bnConfig.BNApiKey.isEmpty()) {
            MojangUtils.joinServer(serverId)
            val connectPacket = RequestConnectPacket(
                PlayerUtils.getRawUuid(),
                clientRandom,
                PlatformUtils.MC_VERSION,
                SkyHanniMod.modVersion.asString,
                SkyHanniMod.MODID,
                AuthenticationConstants.MOJANG,
            )
            sendPacket(connectPacket)
        } else {
            sendPacket(
                RequestConnectPacket(
                    PlayerUtils.getRawUuid(),
                    bnConfig.BNApiKey,
                    PlatformUtils.MC_VERSION,
                    SkyHanniMod.modVersion.asString,
                    SkyHanniMod.MODID,
                    AuthenticationConstants.DATABASE,
                ),
            )
        }
    }


    val isConnected: Boolean
        get() {
            try {
                val socket = this.socket ?: return false
                return socket.isConnected && !socket.isClosed
            } catch (e: Exception) {
                return false
            }
        }

    fun close() {
        try {
            messageReceiverThread?.interrupt()
            messageSenderThread?.interrupt()
            writer?.close()
            reader?.close()
            messageQueue?.clear()
            writer = null
            reader = null
            socket = null
        } catch (e: Exception) {
            ErrorManager.logErrorWithData(e, "Connection Error", ignoreErrorCache = true)
        }
    }

    fun onWaypointPacket(packet: WaypointPacket) {
        if (packet.operation == WaypointPacket.Operation.ADD) {
            waypoints[packet.waypointId] = packet.waypoint
        } else if (packet.operation == WaypointPacket.Operation.REMOVE) {
            waypoints.remove(packet.waypointId)
        } else if (packet.operation == WaypointPacket.Operation.EDIT) {
            waypoints[packet.waypointId] = packet.waypoint
        }
    }


    fun onGetWaypointsPacket(packet: GetWaypointsPacket) {
        sendPacket(
            GetWaypointsPacket(
                waypoints.values.toList(),
            ),
        )
    }

    fun onCompletedGoalPacket(packet: CompletedGoalPacket) {
        if (!config.showCardCompletions && packet.completionType == CompletedGoalPacket.CompletionType.CARD)
            ChatUtils.hoverableChat("§6${packet.username}§7 just completed the Bingo!", packet.lore.split("\n"))
        else if (!config.showGoalCompletions && packet.completionType == CompletedGoalPacket.CompletionType.GOAL)
            ChatUtils.hoverableChat("§6${packet.username}§7 just completed the Goal §6${packet.name}§7!", packet.lore.split("\n"))
    }

    fun onPlaySoundPacket(packet: PlaySoundPacket) {
        if (!packet.isStreamFromUrl) createSound(packet.soundId, 1F).playSound()
    }

    fun onWantedSearchPacket(packet: WantedSearchPacket) {

        if (packet.serverId != null && !(HypixelData.serverId?.matches(Regex(packet.serverId)) ?: false)
        ) return
        if (packet.mega != null && packet.mega != HypixelData.isInMega()) return
        val players: Set<kotlin.String> = EntityUtils.getPlayerList()
        if (packet.maximumPlayerCount != null && packet.maximumPlayerCount <= players.size) return
        if (packet.minimumPlayerCount != null && packet.minimumPlayerCount >= players.size) return
        if (packet.username != null && !players.contains(packet.username)) return
        sendPacket(
            packet.preparePacketToReplyToThis(
                WantedSearchPacketReply(
                    PlayerUtils.getName(),
                    players,
                    HypixelData.isInMega(),
                    HypixelData.serverId ?: "Error",
                ),
            ),
        )
    }

    fun onPunishedPacket(data: PunishedPacket) {
        if (!data.canUseNetwork) bnConfig.useBN = false
        ChatUtils.chat(
            "§c[Bingo Net] You currently have a Punishment Active. Type: ${data.punishmentType}. Expiration Time: ${
                (Duration.between(Instant.now(), data.expirationDate).toKotlinDuration().format(at.hannibal2.skyhanni.utils.TimeUnit.DAY))
            }",
            prefix = false,
        )
    }

//     fun onRequestMinionDataPacket(packet: RequestMinionDataPacket) {
//         sendPacket(packet.preparePacketToReplyToThis(EnvironmentCore.utils.getMiniondata()))
//     }

    fun onPacketChatPromptPacket(packet: PacketChatPromptPacket) {
        ChatUtils.chatPrompt(
            "§e[Sh-Bingo Net Server]${packet.message}", config.serverActionChatPrompt,
            {
                for (p in packet.packets) {
                    sendPacket(p)
                }
            },
            prefix = false,
        )
    }

    @HandleEvent
    fun rendering(event: SkyHanniRenderWorldEvent) {
        for (data in waypoints.values.filter { it.visible }) {
            val position = data.position.toLorenz()
            val distance = position.distanceToPlayer()
            if (distance > data.renderDistance) continue

            event.drawWaypointFilled(
                position,
                data.color,
                seeThroughBlocks = data.renderThroughBlocks,
                beacon = data.renderBeacon,
            )
            event.drawDynamicText(position, "§6[BN]-${data.text}", 1.0)
            if (data.doTracer) event.drawLineToEye(
                position, data.color,
                3,
                true,
            )
            if (data.renderBeacon) event.renderBeaconBeam(position, data.color.rgb)
        }
    }

    fun onSplashDurationRequestPacket(packet: SplashTimeRequestPacket) {
        val response = SplashTimeRequestPacket.SplashReportResponse(EffectApi.getGodSplashDuration())
        sendPacket(packet.preparePacketToReplyToThis(response))
    }

    @HandleEvent
    fun onIslandChange(event: IslandChangeEvent) {
        waypoints.removeIf { it.value.deleteOnServerSwap }
    }

    fun disconnect() {
        socket?.close()
        reader = null
        writer = null
        messageQueue?.clear()
        messageReceiverThread?.interrupt()
        messageSenderThread?.interrupt()
        messageReceiverThread = null
        messageSenderThread = null
        socket = null
        authenticated = null
        packetIntercepts.clear()
        waypoints.clear()
        ChatUtils.chat("Disconnected from Bingo Net Server")
    }

    fun isEnabled(): Boolean {
        return bnConfig.useBN
    }

}

fun Position.toLorenz(): LorenzVec {
    return LorenzVec(
        x.toDouble(),
        y.toDouble(),
        z.toDouble(),
    )
}

fun LorenzVec.toBN(): Position {
    return Position(
        x.toInt(),
        y.toInt(),
        z.toInt(),
    )
}

