package at.hannibal2.skyhanni.events

import at.hannibal2.skyhanni.api.event.SkyHanniEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.skyhannimodule.PrimaryFunction

@PrimaryFunction("onIslandChange")
class IslandChangeEvent(val newIsland: IslandType, val oldIsland: IslandType, val oldServerId: String, val newServerId: String) : SkyHanniEvent()
//TODO: It should be changes so that the full previous hypixel data is passed.
