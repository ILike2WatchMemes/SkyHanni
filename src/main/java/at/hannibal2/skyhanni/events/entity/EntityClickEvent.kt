package at.hannibal2.skyhanni.events.entity

import at.hannibal2.skyhanni.data.ClickType
import at.hannibal2.skyhanni.events.WorldClickEvent
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.NeuItems
import at.hannibal2.skyhanni.utils.NeuNPC
import at.hannibal2.skyhanni.utils.compat.unformattedTextCompat
import at.hannibal2.skyhanni.utils.getLorenzVec
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.C02PacketUseEntity

class EntityClickEvent(clickType: ClickType, val action: C02PacketUseEntity.Action, val clickedEntity: Entity, itemInHand: ItemStack?) :
    WorldClickEvent(itemInHand, clickType)
{
    fun getAsNPC() : NeuNPC?{
        val armorStand = EntityUtils.getEntitiesNearby<EntityArmorStand>(this.clickedEntity.getLorenzVec(),2.0)
        val results = NeuItems.npcs.filter {
            val npc =  it.value.displayName.replace("§.".toRegex(),"").trim()
            return@filter armorStand.any { it.displayName?.unformattedTextCompat() == npc }
        }.values
        if (results.size>1){
            ChatUtils.chat("§cMultiple NPCs found with the same name, please report this to the developers.")
        }
        return results.firstOrNull()
    }
}
