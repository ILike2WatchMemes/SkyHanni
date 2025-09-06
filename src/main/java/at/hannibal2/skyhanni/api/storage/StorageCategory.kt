package at.hannibal2.skyhanni.api.storage

enum class StorageCategory(
    val displayName: String,
    /**
     * Offset of the index of the storage category in the inventory
     * For example if first row is reserved +9
     */
    val indexOffSet: Int,
) {

    ENDER_CHEST("Ender Chest", 9),
    BACKPACK("Backpack", 9),
    RIFT_STORAGE("Rift Storage", 9),
    PRIVATE_ISLAND_CHEST("Private Island Chest", 0);

    companion object {
        fun fromStorageName(name: String): StorageCategory? {
            return when {
                name.startsWith("Ender Chest") -> ENDER_CHEST
                name.startsWith("Backpack") -> BACKPACK
                name.startsWith("Rift Storage") -> RIFT_STORAGE
                name.startsWith("Private Island Chest") -> PRIVATE_ISLAND_CHEST
                else -> null
            }
        }
    }
}
