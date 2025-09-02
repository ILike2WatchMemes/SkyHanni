package at.hannibal2.skyhanni.features.combat.mobs

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.entity.EntityOpacityActiveEvent
import at.hannibal2.skyhanni.events.entity.EntityOpacityEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniTickEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.EntityUtils
import at.hannibal2.skyhanni.utils.LocationUtils.distanceTo
import at.hannibal2.skyhanni.utils.MobUtils.mob
import net.minecraft.entity.EntityLiving
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.monster.EntitySpider

@SkyHanniModule
object ArachneOtherEntitiesHider {
    private val config get() = SkyHanniMod.feature.combat.mobs

    // tracked arachne entities (bosses and minis)
    private var arachnes: Set<EntityLiving> = hashSetOf()

    @HandleEvent(onlyOnIsland = IslandType.SPIDER_DEN)
    fun onEntityOpacityActive(event: EntityOpacityActiveEvent) {
        event.setActive(config.arachneOtherEntitiesOpacity < 100)
    }

    @HandleEvent(onlyOnIsland = IslandType.SPIDER_DEN)
    fun onEntityOpacity(event: EntityOpacityEvent<EntityLivingBase>) {
        val entity = event.entity

        // only affect players and spiders
        val isRelevantType = entity is EntityPlayer || entity is EntitySpider
        if (!isRelevantType) return

        // do not hide arachne entities themselves
        val isArachne = entity.mob?.name?.contains("Arachne") == true || arachnes.contains(entity)
        if (isArachne) return

        // if any tracked arachne is within 6 blocks, apply configured opacity
        val nearby = arachnes.any { !it.isDead && it.distanceTo(entity) < 5.0 }
        if (nearby) event.opacity = config.arachneOtherEntitiesOpacity
    }

    @HandleEvent(onlyOnIsland = IslandType.SPIDER_DEN)
    fun onTick(event: SkyHanniTickEvent) {
        if (!event.isMod(4)) return
        arachnes = EntityUtils.getAllEntities().filter {
            val name = it.mob?.name ?:return@filter false
            name.contains("Arachne") && !name.contains("Keeper")
        }.filterIsInstance<EntityLiving>().toSet()
    }

}
