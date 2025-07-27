package de.hype.bingonet.environment.packetconfig

import com.google.gson.Gson

object PacketUtils {
    val gson: Gson = BNGson.createNotPrettyPrinting()
    val knownPacketIssues: MutableSet<String> = HashSet<String>()

    fun parsePacketToJson(packet: AbstractPacket): String {
        return gson.toJson(packet).replace("\n", "/n")
    }

    fun parsePacket(message: String): Pair<Packet<out AbstractPacket>, AbstractPacket>? {
        if (!message.contains(".")) return null
        val packetName = message.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
        val rawJson = message.substring(packetName.length + 1)
        for (packet in BNPacketManager.packets) {
            if (packetName != packet.clazz.simpleName) continue
            return Pair(packet,gson.fromJson(rawJson.replace("/n", "\n"), packet.clazz))
        }
        return null
    }
}
