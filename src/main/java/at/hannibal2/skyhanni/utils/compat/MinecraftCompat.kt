package at.hannibal2.skyhanni.utils.compat

import at.hannibal2.skyhanni.test.command.ErrorManager
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.entity.Entity

object MinecraftCompat {

    val localPlayer get(): EntityPlayerSP = localPlayerOrNull ?: ErrorManager.skyHanniError("thePlayer is null")

    val localPlayerOrNull get(): EntityPlayerSP? = Minecraft.getMinecraft().thePlayer

    val Entity?.isLocalPlayer get(): Boolean = this == localPlayerOrNull && this != null

    val localPlayerExists get(): Boolean = localPlayerOrNull != null

    val localWorld get(): WorldClient = localWorldOrNull ?: ErrorManager.skyHanniError("theWorld is null")

    val localWorldOrNull get(): WorldClient? = Minecraft.getMinecraft().theWorld

    val localWorldExists get(): Boolean = localWorldOrNull != null

    //#if MC < 1.16
    val showDebugHud get(): Boolean = Minecraft.getMinecraft().gameSettings.showDebugInfo
    //#else
    //$$ val showDebugHud get(): Boolean = MinecraftClient.getInstance().debugHud.shouldShowDebugHud()
    //#endif
    //TODO maybe make it so if absurd high number we use spooky new day every x ticks and use that to include in the calculation?
    val worldDay get() : Int? {
        val ticks = localWorldOrNull?.worldTime?:return null
        return (ticks / (20*60*20)).toInt() // 20 ticks per second, 60 seconds per minute, 20 minutes per day
    }
}
