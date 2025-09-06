package de.hype.bingonet.shared.constants

import java.awt.Color

enum class StatusConstants(@JvmField var displayName: String, @JvmField var color: Color) {
    DONEGOOD("Done", Color.GREEN),
    DONEBAD("Done", Color.ORANGE),
    WAITING("Waiting", Color.GREEN),
    FULL("Full", Color.YELLOW),
    ONGOING("Ongoing", Color.YELLOW),
    OPEN("Open", Color.GREEN),
    SPLASHING("Splashing", Color.YELLOW),
    CLOSING("Closing", Color.ORANGE),
    CLOSINGSOON("Closing Soon", Color.ORANGE),
    LEAVINGSOON("Leaving Soon", Color.ORANGE),
    CANCELED("Canceled", Color.RED),
    CLOSED("Closed", Color.RED),
    LEFT("Left", Color.ORANGE),
    ;

    override fun toString(): String {
        return displayName
    }

    fun toEnumString(): String {
        return super.toString()
    }


    companion object {
        @JvmStatic
        @Suppress("ComplexCondition")
        fun getSplashStatus(string: String?): StatusConstants {
            for (value in entries) {
                when (value) {
                    DONEGOOD, ONGOING, OPEN, CLOSING, CLOSINGSOON, LEAVINGSOON, CLOSED, LEFT -> continue
                    else -> {
                    }
                }
                if (value.toEnumString().equals(string, ignoreCase = true)) {
                    return value
                }
            }
            return CANCELED
        }

        @JvmStatic
        fun getChChestStatus(status: String): StatusConstants {
            return when (status) {
                "Done" -> DONEGOOD
                "Done Bad" -> DONEBAD
                "Waiting" -> WAITING
                "Full" -> FULL
                "Ongoing" -> ONGOING
                "Open" -> OPEN
                "Splashing" -> SPLASHING
                "Closing" -> CLOSING
                "Closing Soon" -> CLOSINGSOON
                "Leaving Soon" -> LEAVINGSOON
                "Canceled" -> CANCELED
                "Closed" -> CLOSED
                else -> LEFT
            }
        }
    }
}
