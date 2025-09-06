package at.hannibal2.skyhanni.api.storage

import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getExtraAttributes
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getItemId
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getItemUuid
import at.hannibal2.skyhanni.utils.StringUtils.toUnDashedUUID
import net.minecraft.item.ItemStack
import java.util.UUID
import java.util.regex.Pattern

/**
 * Base interface for storage item filters
 */
interface StorageFilter {
    fun matches(item: ItemStack, storageName: String, category: StorageCategory): Boolean
}

/**
 * Filter for item names using regex patterns
 */
class ItemNameFilter(
    pattern: String,
    ignoreCase: Boolean = true,
) : StorageFilter {
    private val regex = Pattern.compile(pattern, if (ignoreCase) Pattern.CASE_INSENSITIVE else 0)

    override fun matches(item: ItemStack, storageName: String, category: StorageCategory): Boolean {
        val displayName = item.displayName ?: ""
        return regex.matcher(displayName).find()
    }
}

/**
 * Filter for item descriptions/lore using regex patterns
 */
class ItemDescriptionFilter(
    pattern: String,
    ignoreCase: Boolean = true,
) : StorageFilter {
    private val regex = Pattern.compile(pattern, if (ignoreCase) Pattern.CASE_INSENSITIVE else 0)

    override fun matches(item: ItemStack, storageName: String, category: StorageCategory): Boolean {
        val lore = item.getLore()
        val text = lore.joinToString("\n")
        return regex.matcher(text).find()
    }
}

/**
 * Filter for NBT data using regex patterns
 */
class ItemNBTFilter(
    pattern: String,
    ignoreCase: Boolean = true,
) : StorageFilter {
    private val regex = Pattern.compile(pattern, if (ignoreCase) Pattern.CASE_INSENSITIVE else 0)

    override fun matches(item: ItemStack, storageName: String, category: StorageCategory): Boolean {
        val nbtString = item.getExtraAttributes().toString()
        return regex.matcher(nbtString).find()
    }
}

/**
 * Filter for SkyBlock UUID
 */
class SkyBlockUuidFilter(
    uuid: UUID,
) : StorageFilter {

    // Normalize UUID string by removing dashes and converting to lowercase for comparison
    private val normalizedUuid = uuid.toUnDashedUUID()

    override fun matches(item: ItemStack, storageName: String, category: StorageCategory): Boolean {
        val itemUuid = item.getItemUuid() ?: return false

        // Normalize item UUID for comparison
        val normalizedItemUuid = itemUuid.replace("-", "").lowercase()

        return normalizedItemUuid == normalizedUuid
    }
}

/**
 * Filter for SkyBlock item ID
 */
class SkyBlockItemIdFilter(
    private val itemId: String,
    private val ignoreCase: Boolean = true,
) : StorageFilter {

    override fun matches(item: ItemStack, storageName: String, category: StorageCategory): Boolean {
        val id = item.getItemId()
        return if (ignoreCase) {
            id.equals(itemId, ignoreCase = true)
        } else {
            id == itemId
        }
    }
}

/**
 * Filter for storage categories
 */
class CategoryFilter(
    private val allowedCategories: Set<StorageCategory>,
) : StorageFilter {

    constructor(vararg categories: StorageCategory) : this(categories.toSet())

    override fun matches(item: ItemStack, storageName: String, category: StorageCategory): Boolean {
        return category in allowedCategories
    }
}

/**
 * Combines multiple filters with AND logic
 */
class AndFilter(
    private val filters: List<StorageFilter>,
) : StorageFilter {

    constructor(vararg filters: StorageFilter) : this(filters.toList())

    override fun matches(item: ItemStack, storageName: String, category: StorageCategory): Boolean {
        return filters.all { it.matches(item, storageName, category) }
    }
}

/**
 * Combines multiple filters with OR logic
 */
class OrFilter(
    private val filters: List<StorageFilter>,
) : StorageFilter {

    constructor(vararg filters: StorageFilter) : this(filters.toList())

    override fun matches(item: ItemStack, storageName: String, category: StorageCategory): Boolean {
        return filters.any { it.matches(item, storageName, category) }
    }
}

/**
 * Inverts a filter
 */
class NotFilter(
    private val filter: StorageFilter,
) : StorageFilter {

    override fun matches(item: ItemStack, storageName: String, category: StorageCategory): Boolean {
        return !filter.matches(item, storageName, category)
    }
}
