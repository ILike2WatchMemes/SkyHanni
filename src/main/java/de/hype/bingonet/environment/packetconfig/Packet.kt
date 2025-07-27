package de.hype.bingonet.environment.packetconfig

import java.util.function.Consumer

class Packet<T : AbstractPacket>(val clazz: Class<T>, val consumer: Consumer<T>) {
    val name: String
        get() = clazz.getSimpleName()
}
