package at.hannibal2.skyhanni.api.storage

import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.LorenzVec
import net.minecraft.item.ItemStack

/**
 * Represents a search result from storage with location and access information
 */
data class StorageSearchResult(
    val item: ItemStack,
    val slotIndex: Int,
    val storageName: String,
    val category: StorageCategory,
    val page: Int? = null,
    val location: LorenzVec? = null,
) {
    /**
     * Creates a unique identifier for this storage location
     */
    fun getUniqueLocation(): String {
        return when (category) {
            StorageCategory.ENDER_CHEST -> "enderchest_page_${page ?: 1}"
            StorageCategory.BACKPACK -> "backpack_page_${page ?: 1}"
            StorageCategory.RIFT_STORAGE -> "rift_storage_page_${page ?: 1}"
            StorageCategory.PRIVATE_ISLAND_CHEST -> "private_island_${location?.toCleanString(" ")}"
        }
    }

    fun getUniqueSlotLocation(): String {
        return when (category) {
            StorageCategory.ENDER_CHEST -> "enderchest_page_${page ?: 1}_slot_$slotIndex"
            StorageCategory.BACKPACK -> "backpack_page_${page ?: 1}_slot_$slotIndex"
            StorageCategory.RIFT_STORAGE -> "rift_storage_page_${page ?: 1}_slot_$slotIndex"
            StorageCategory.PRIVATE_ISLAND_CHEST -> "private_island_${location?.toCleanString(" ")}_slot_$slotIndex"
        }
    }

    /**
     * Returns a human-readable display name for this result
     */
    fun getDisplayName(): String {
        val itemName = item.displayName
        return when (category) {
            StorageCategory.ENDER_CHEST -> "$itemName§f in Ender Chest${if (page != null) " (Page $page)" else ""}"
            StorageCategory.BACKPACK -> "$itemName§f in Backpack${if (page != null) " (Page $page)" else ""}"
            StorageCategory.RIFT_STORAGE -> "$itemName§f in Rift Storage${if (page != null) " (Page $page)" else ""}"
            StorageCategory.PRIVATE_ISLAND_CHEST -> "$itemName§f in Private Island Chest at ${location?.toCleanString(" ")}"
        }
    }
    // TODO: Warp manager that detects all warp locations and allows to warp to them but only if you have the scroll.

    /**
     * Attempts to navigate to this storage location
     * @param allowServerChange In case of Items not on, you allow traveling to the desired location.
     */
    fun runAccess(allowServerChange: Boolean) {
        when (category) {
            StorageCategory.ENDER_CHEST -> {
                HypixelCommands.enderChest(page ?: 1)
            }

            StorageCategory.BACKPACK -> {
                HypixelCommands.backPack(page ?: 1)
            }

            StorageCategory.RIFT_STORAGE -> {
                val consumer = {
                    HypixelCommands.warp("rift")
                }
                if (allowServerChange) consumer.invoke()
                else {
                    if (HypixelData.skyBlockIsland == IslandType.THE_RIFT)
                        ChatUtils.clickableChat("${this.getDisplayName()} Cant access from here. Click to warp to the rift.", consumer)
                }
            }

            StorageCategory.PRIVATE_ISLAND_CHEST -> {
                val consumer = {
                    if (HypixelData.skyBlockIsland != IslandType.PRIVATE_ISLAND) HypixelCommands.warp("home")
                }
                if (allowServerChange) consumer.invoke()
                else {
                    if (HypixelData.skyBlockIsland == IslandType.PRIVATE_ISLAND)
                        ChatUtils.clickableChat(
                            "${this.getDisplayName()} Cant access from here. Click to warp to the Private Island.",
                            consumer,
                        )
                }
            }
        }
    }
}

fun StorageSearchResult.isPrivateIslandChest(): Boolean {
    return category == StorageCategory.PRIVATE_ISLAND_CHEST && location != null
}
