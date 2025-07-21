package de.hype.bingonet.shared.packets.function

import de.hype.bingonet.environment.packetconfig.AbstractPacket

class PacketChatPromptPacket(val packets: MutableList<AbstractPacket>, val message: String) :
    AbstractPacket()
