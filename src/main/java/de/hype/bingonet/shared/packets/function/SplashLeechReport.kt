package de.hype.bingonet.shared.packets.function

import de.hype.bingonet.environment.packetconfig.AbstractPacket
import java.util.UUID

data class SplashLeechReportPacket(
    /**
     * First String: Username
     * Second UUID: MCUUID of the leecher
     * Third Boolean: User is Ironman
     */
    val leechers: List<Triple<String, UUID, Boolean>>,
    val allowIman: Boolean
) : AbstractPacket() {
}
