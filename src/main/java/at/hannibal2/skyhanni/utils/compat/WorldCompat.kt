package at.hannibal2.skyhanni.utils.compat

import at.hannibal2.skyhanni.utils.compat.MinecraftCompat.localWorldOrNull
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.entity.player.EntityPlayer

fun WorldClient.getLoadedPlayers(): List<EntityPlayer> =
//#if MC < 1.14
    this.playerEntities

//#else
//$$ this.players
//#endif
object WorldCompat {
    val worldTime: Long?
        get() = localWorldOrNull?.worldTime

    // TODO maybe make it so if absurd high number we use spooky new day every x ticks and use that to include in the calculation?
    val worldDay: Int?
        get() {
            val ticks = worldTime ?: return null
            return (ticks / (20 * 60 * 20)).toInt() // 20 ticks per second, 60 seconds per minute, 20 minutes per day
        }
}
