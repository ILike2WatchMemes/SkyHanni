package at.hannibal2.skyhanni.api.storage

import net.minecraft.item.ItemStack
import java.util.UUID

/**
 * A consumer-like class for searching storage with custom Kotlin extensions
 */
class StorageSearchConsumer {
    private val filters = mutableListOf<StorageFilter>()
    private var resultHandler: ((List<StorageSearchResult>) -> Unit)? = null

    /**
     * Adds a filter to the search
     */
    fun addFilter(filter: StorageFilter): StorageSearchConsumer {
        filters.add(filter)
        return this
    }

    /**
     * Sets the result handler
     */
    fun onResults(handler: (List<StorageSearchResult>) -> Unit): StorageSearchConsumer {
        resultHandler = handler
        return this
    }

    /**
     * Executes the search and calls the result handler
     */
    fun search() {
        val results = getResults()
        resultHandler?.invoke(results)
    }
    /**
     * Executes the search and returns results directly
     */
    fun getResultsNoHighlight(): List<StorageSearchResult> {
        val results = mutableListOf<StorageSearchResult>()

        // Get all storage data from the API
        StorageApi.getAllStorageData().forEach { (storageName, items) ->
            val category = StorageCategory.fromStorageName(storageName)
            if (category != null) {
                items.forEachIndexed { slotIndex, item ->
                    if (item != null && matchesAllFilters(item, storageName, category)) {
                        val page = StorageApi.getPageFromStorageName(storageName)
                        val location = StorageApi.getLocationFromStorageName(storageName)

                        results.add(
                            StorageSearchResult(
                                item = item,
                                slotIndex = slotIndex,
                                storageName = storageName,
                                category = category,
                                page = page,
                                location = location
                            )
                        )
                    }
                }
            }
        }

        return results
    }
    /**
     * Executes the search and returns results directly
     */
    fun getResults(): List<StorageSearchResult> {
        val results = getResultsNoHighlight()
        StorageApi.toHighlightResults+=results
        return results
    }

    private fun matchesAllFilters(item: ItemStack, storageName: String, category: StorageCategory): Boolean {
        return filters.all { it.matches(item, storageName, category) }
    }
}

/**
 * Kotlin extension functions for easier filter creation
 */

/**
 * Filter by item name using regex
 */
fun StorageSearchConsumer.filterByName(pattern: String, ignoreCase: Boolean = true): StorageSearchConsumer {
    return addFilter(ItemNameFilter(pattern, ignoreCase))
}

/**
 * Filter by item description/lore using regex
 */
fun StorageSearchConsumer.filterByDescription(pattern: String, ignoreCase: Boolean = true): StorageSearchConsumer {
    return addFilter(ItemDescriptionFilter(pattern, ignoreCase))
}

/**
 * Filter by NBT data using regex
 */
fun StorageSearchConsumer.filterByNBT(pattern: String, ignoreCase: Boolean = true): StorageSearchConsumer {
    return addFilter(ItemNBTFilter(pattern, ignoreCase))
}

/**
 * Filter by SkyBlock UUID
 */
fun StorageSearchConsumer.filterByUuid(uuid: UUID): StorageSearchConsumer {
    return addFilter(SkyBlockUuidFilter(uuid))
}

/**
 * Filter by SkyBlock item ID
 */
fun StorageSearchConsumer.filterByItemId(itemId: String, ignoreCase: Boolean = true): StorageSearchConsumer {
    return addFilter(SkyBlockItemIdFilter(itemId, ignoreCase))
}

/**
 * Filter by storage categories
 */
fun StorageSearchConsumer.filterByCategory(vararg categories: StorageCategory): StorageSearchConsumer {
    return addFilter(CategoryFilter(*categories))
}

/**
 * Combine filters with AND logic
 */
fun StorageSearchConsumer.and(vararg filters: StorageFilter): StorageSearchConsumer {
    return addFilter(AndFilter(*filters))
}

/**
 * Combine filters with OR logic
 */
fun StorageSearchConsumer.or(vararg filters: StorageFilter): StorageSearchConsumer {
    return addFilter(OrFilter(*filters))
}

/**
 * Invert a filter
 */
fun StorageSearchConsumer.not(filter: StorageFilter): StorageSearchConsumer {
    return addFilter(NotFilter(filter))
}
