package at.hannibal2.skyhanni.features.inventory.storage

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.config.ConfigFileType
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.utils.ChatUtils

/**
 * Simple item tag manager - 1:1 mapping between tags and UUIDs
 * Each tag maps to exactly one UUID, and each UUID has at most one tag
 */
object ItemTagManager {

    // Map: Tag -> UUID (1:1 relationship)
    private val tagToUuid get() = ProfileStorageData.profileSpecific?.itemTags ?: mutableMapOf()

    /**
     * Add a tag to an item by UUID (overrides existing tag for this UUID and removes existing use of this tag)
     */
    fun addTag(uuid: String, tag: String, displayName: String): Boolean {
        if (uuid.isBlank() || tag.isBlank()) return false

        val normalizedUuid = normalizeUuid(uuid)
        val normalizedTag = normalizeTag(tag)

        // Check if this tag is already used by another item
        val existingUuid = tagToUuid[normalizedTag]
        if (existingUuid != null && existingUuid != normalizedUuid) {
            ChatUtils.chat("§7Tag §b$normalizedTag §7was moved from previous item to §e$displayName")
        }

        // Remove any existing tag for this UUID
        val existingTag = getTagByUuid(normalizedUuid)
        if (existingTag != null && existingTag != normalizedTag) {
            tagToUuid.remove(existingTag)
            ChatUtils.chat("§7Removed previous tag §b$existingTag §7from this item")
        }

        // Set the new tag
        tagToUuid[normalizedTag] = normalizedUuid
        saveData()

        // Don't send success message here - let the caller handle it
        return true
    }

    /**
     * Remove a tag from an item by UUID
     */
    fun removeTag(uuid: String): Boolean {
        val normalizedUuid = normalizeUuid(uuid)
        val existingTag = getTagByUuid(normalizedUuid)

        if (existingTag != null) {
            tagToUuid.remove(existingTag)
            saveData()
            ChatUtils.chat("§aRemoved tag §b$existingTag")
            return true
        }

        ChatUtils.userError("No tag found for this item")
        return false
    }

    /**
     * Get UUID by tag (returns null if tag doesn't exist)
     */
    fun getUuidByTag(tag: String): String? {
        val normalizedTag = normalizeTag(tag)
        return tagToUuid[normalizedTag]
    }

    /**
     * Get tag by UUID (returns null if UUID has no tag)
     */
    fun getTagByUuid(uuid: String): String? {
        val normalizedUuid = normalizeUuid(uuid)
        return tagToUuid.entries.find { it.value == normalizedUuid }?.key
    }

    /**
     * Get all available tags
     */
    fun getAllTags(): Set<String> {
        return tagToUuid.keys.toSet()
    }

    /**
     * Get all tagged items as Tag->UUID pairs
     */
    fun getAllTaggedItems(): Map<String, String> {
        return tagToUuid.toMap()
    }

    /**
     * Check if an item has a tag
     */
    fun hasTag(uuid: String): Boolean {
        return getTagByUuid(uuid) != null
    }

    /**
     * Search tags by partial match
     */
    fun searchTags(query: String): List<String> {
        val normalizedQuery = query.lowercase()
        return getAllTags().filter { it.lowercase().contains(normalizedQuery) }
    }

    private fun normalizeUuid(uuid: String): String {
        return uuid.replace("-", "").lowercase()
    }

    private fun normalizeTag(tag: String): String {
        return tag.lowercase()
    }

    private fun saveData() {
        SkyHanniMod.launchCoroutine("Save Item Tags") {
            SkyHanniMod.configManager.saveConfig(ConfigFileType.STORAGE, "Updated item tags")
        }
    }
}
