package de.hype.bingonet.shared.packets.function

import de.hype.bingonet.shared.packets.base.ExpectReplyPacket
import kotlin.time.Duration

class SplashTimeRequestPacket : ExpectReplyPacket<SplashTimeRequestPacket.SplashReportResponse>() {
    data class SplashReportResponse(val duration: Duration) : ReplyPacket()
}
