package at.hannibal2.skyhanni.api.storage

import at.hannibal2.skyhanni.utils.ChatUtils

/**
 * Utility class for navigating to storage locations
 */
object StorageNavigationUtils {

    /**
     * Navigates to multiple results in sequence
     */
    fun makeNavigatableChatList(results: List<StorageSearchResult>, allowServerChange: Boolean) {
        if (results.isEmpty()) {
            ChatUtils.chat("No results found!")
            return
        }

        if (results.size == 1) {
            results.first().runAccess(allowServerChange)
            return
        }

        // Group by category and page for efficient navigation
        val groupedResults = results.groupBy { it.getUniqueLocation() }

        ChatUtils.chat("Found ${results.size} stacks (->${results.sumOf { it.item.stackSize }} Items) matching the query. They were found in ${groupedResults.size} different locations:")
         ChatUtils.chat("§7Keep in mind that this List may be incomplete for a variety of reasons. Such being never opened with sh chests, Them being in your Inventory, Changing Locations with a different sh config instance,...")
        groupedResults.forEach { (location, items) ->
            val first = items.first()
            ChatUtils.clickableChat("§7- §f${first.getDisplayName()} ${if (first.page != null) " Page ${first.page}" else ""}: §b${items.sumOf { it.item.stackSize }} items",{
                first.runAccess(true)
            }, hover = items.joinToString("\n") { "${it.item.displayName} x${it.item.stackSize}" })
        }
    }
}
