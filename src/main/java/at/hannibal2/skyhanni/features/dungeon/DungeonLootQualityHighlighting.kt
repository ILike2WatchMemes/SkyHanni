package at.hannibal2.skyhanni.features.dungeon

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.minecraft.ToolTipEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getItemRarityOrCommon
import at.hannibal2.skyhanni.utils.ItemUtils.repoItemName
import at.hannibal2.skyhanni.utils.LorenzRarity
import at.hannibal2.skyhanni.utils.RenderUtils.drawSlotText
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getDungeonQuality
import net.minecraft.inventory.Slot

@SkyHanniModule
object DungeonLootQualityHighlighting {
    val config get() = SkyHanniMod.feature.dungeon

    @HandleEvent
    fun showQualityText(event: GuiContainerEvent.ForegroundDrawnEvent) {
        if (!config.showQualityText) return
        InventoryUtils.getItemsInOpenChest().forEach {
            val quality = it.stack.getDungeonQuality() ?: return@forEach
            event.drawSlotText(it.xDisplayPosition+18, it.yDisplayPosition, "$quality", 1.0f)
        }
    }

    @HandleEvent
    fun itemQualityTooltip(event: ToolTipEvent) {
        if (!config.showQualityInTooltip) return
        val quality = event.itemStack.getDungeonQuality()?: return
        val color = when (quality) {
            50 -> "§6"
            in 35..49 -> "§a"
            in 25..34 -> "§e"
            in 10..24 -> "§c"
            else -> "§4"
        }
        event.toolTip.add("§7Dungeon Quality: $color$quality§f/§650")
    }

    @HandleEvent
    fun onForegroundDrawn(event: GuiContainerEvent.BackgroundDrawnEvent) {
        if (!config.highlightBestItem) return
        val best: MutableMap<String, Pair<Slot, Int>> = mutableMapOf()
        InventoryUtils.getItemsInOpenChest().forEach {
            val quality = it.stack.let {
                val score = it.getDungeonQuality() ?: return@let null
                // Add bonus points for higher rarity items due to reforges. I dont have an idea on how to compare it better
                // between items.
                return@let when (it.getItemRarityOrCommon()) {
                    LorenzRarity.EPIC -> score + 10
                    LorenzRarity.LEGENDARY -> score + 30
                    else -> score
                }
            } ?: return@forEach
            best.compute(getMapID(it)) { k, v ->
                if (v == null) return@compute Pair(it, quality)
                if (v.second < quality) {
                    return@compute Pair(it, quality)
                }
                return@compute v
            }
        }
    }

    fun getMapID(slot: Slot): String {
        val id = slot.stack.repoItemName
        if (!config.groupBestByArmorType) return id
        for (suffix in ARMOR_SUFFIXES) {
            if (id.endsWith(suffix)) {
                return suffix
            }
        }
        return id
    }

    val ARMOR_SUFFIXES = setOf("_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS")
}
