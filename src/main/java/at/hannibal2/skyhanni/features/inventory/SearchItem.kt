package at.hannibal2.skyhanni.features.inventory

import at.hannibal2.skyhanni.api.enoughupdates.EnoughUpdatesManager
import at.hannibal2.skyhanni.api.storage.StorageApi
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.api.storage.filterByUuid
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.commands.brigadier.BrigadierArguments
import at.hannibal2.skyhanni.config.commands.brigadier.BrigadierUtils.dynamicSuggestionProvider
import at.hannibal2.skyhanni.features.inventory.storage.ItemTagManager
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getExtraAttributes
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getItemUuid
import at.hannibal2.skyhanni.utils.StringUtils
import at.hannibal2.skyhanni.api.storage.filterByDescription
import at.hannibal2.skyhanni.api.storage.filterByItemId
import at.hannibal2.skyhanni.api.storage.outputToChat
import at.hannibal2.skyhanni.utils.NeuItems
import com.mojang.brigadier.suggestion.SuggestionProvider
import java.util.UUID

@SkyHanniModule
object SearchItem {

    private val formattingCodeRegex = Regex("§.")

    private fun clean(text: String?): String = text
        ?.let { formattingCodeRegex.replace(it, "") }
        ?.trim()
        .orEmpty()


    private fun collectItemIds(): Set<String> = EnoughUpdatesManager.allSkyblockItemIds

    private val nameSuggestionProvider = SuggestionProvider<Any?> { _, builder ->
        val remaining = builder.remainingLowerCase
        val names = NeuItems.findItemNameWithoutNPCs(
            remaining,
            { _ ->
                return@findItemNameWithoutNPCs true
            },
        )
        for (n in names) builder.suggest(n)
        builder.buildFuture()
    }

    @HandleEvent
    fun commandRegistration(event: CommandRegistrationEvent) {
        // /shtagitem <tag>
        event.registerBrigadier("shtagitem") {
            description = "Tag the currently held item for later searching"
            // greedyString allows multi-word tags like in legacy implementation
            arg("tag", BrigadierArguments.greedyString(), dynamicSuggestionProvider { ItemTagManager.getAllTags() }) { tagArg ->
                callback {
                    val tag = getArg(tagArg)
                    if (tag.isBlank()) ChatUtils.userError("Usage: /shtagitem <tag>") else tagCurrentItem(tag)
                }
            }
            // Executing without args -> usage
            simpleCallback { ChatUtils.userError("Usage: /shtagitem <tag>") }
        }

        // /searchitem (tag|name|desc|id|uuid) <query>
        event.registerBrigadier("searchitem") {
            description = "Search storage (tag|name|desc|id|uuid)"

            // tag subcommand
            literal("tag") {
                arg("tag", BrigadierArguments.greedyString(), dynamicSuggestionProvider { ItemTagManager.getAllTags() }) { tagArg ->
                    callback { searchByTag(getArg(tagArg)) }
                }
                simpleCallback { ChatUtils.userError("Usage: /searchitem tag <tag>") }
            }
            // name subcommand (regex)
            literal("name") {
                arg("pattern", BrigadierArguments.greedyString(), nameSuggestionProvider) { pat ->
                    callback {
                        val pattern = getArg(pat)
                        if (pattern.isBlank()) ChatUtils.userError("Empty pattern") else searchByDisplayName(pattern)
                    }
                }
                simpleCallback { ChatUtils.userError("Usage: /searchitem name <regex>") }
            }
            // desc / lore subcommand (regex)
            literal("desc", "lore") {
                arg("pattern", BrigadierArguments.greedyString()) { pat ->
                    callback {
                        val pattern = getArg(pat)
                        if (pattern.isBlank()) ChatUtils.userError("Empty pattern") else searchByDescription(pattern)
                    }
                }
                simpleCallback { ChatUtils.userError("Usage: /searchitem desc <regex>") }
            }
            // id subcommand
            literal("id") {
                arg("id", BrigadierArguments.string(), dynamicSuggestionProvider { collectItemIds() }) { idArg ->
                    callback {
                        val id = getArg(idArg)
                        if (id.isBlank()) ChatUtils.userError("Empty id") else searchByItemId(id)
                    }
                }
                simpleCallback { ChatUtils.userError("Usage: /searchitem id <ITEM_ID>") }
            }
            // uuid subcommand
            literal("uuid") {
                arg(
                    "uuid",
                    BrigadierArguments.string(),
                    dynamicSuggestionProvider { ItemTagManager.getAllTaggedItems().values },
                ) { uuidArg ->
                    callback { searchByUuidString(getArg(uuidArg)) }
                }
                simpleCallback { ChatUtils.userError("Usage: /searchitem uuid <uuid>") }
            }
            // Fallback: treat single arg as tag OR clear search if empty and there is a previous search
            arg("fallback", BrigadierArguments.greedyString()) { fb ->
                callback {
                    val maybeTag = getArg(fb)
                    if (maybeTag.isBlank()) {
                        if (StorageApi.toHighlightResults.isNotEmpty()) {
                            StorageApi.toHighlightResults.clear()
                            ChatUtils.chat("Cleared last search")
                        } else {
                            ChatUtils.userError("Usage: /searchitem <tag|name|desc|id|uuid> <query>")
                        }
                    } else searchByTag(maybeTag)
                }
            }
            // No args: clear or show usage like legacy
            simpleCallback {
                if (StorageApi.toHighlightResults.isNotEmpty()) {
                    StorageApi.toHighlightResults.clear()
                    ChatUtils.chat("Cleared last search")
                } else ChatUtils.userError("Usage: /searchitem <tag|name|desc|id|uuid> <query>")
            }
        }

        // /removeitemtag
        event.registerBrigadier("removeitemtag") {
            description = "Remove tag from currently held item"
            simpleCallback { removeTagFromCurrentItem() }
        }
        // /listitemtags
        event.registerBrigadier("listitemtags") {
            description = "List all tagged items"
            simpleCallback { listAllTags() }
        }
    }

    private fun tagCurrentItem(tag: String) {
        val heldItem = InventoryUtils.getItemInHand()
        if (heldItem == null) {
            ChatUtils.userError("You must be holding an item to tag it!")
            return
        }
        val extraAttributes = heldItem.getExtraAttributes()
        if (extraAttributes == null) {
            ChatUtils.userError("This item doesn't have SkyBlock data!")
            return
        }
        val uuid = heldItem.getItemUuid()
        if (uuid.isNullOrBlank()) {
            ChatUtils.userError("This item doesn't have a UUID! Only unique SkyBlock items can be tagged.")
            return
        }
        val displayName = heldItem.displayName ?: "Unknown Item"
        val success = ItemTagManager.addTag(uuid, tag, displayName)
        if (success) {
            ChatUtils.chat("§aSuccessfully tagged §e$displayName §awith §b$tag")
            ChatUtils.chat("§7Use §e/searchitem tag $tag §7to find this item later!")
        }
    }

    private fun removeTagFromCurrentItem() {
        val heldItem = InventoryUtils.getItemInHand()
        if (heldItem == null) {
            ChatUtils.userError("You must be holding an item to remove its tag!")
            return
        }
        val uuid = heldItem.getItemUuid()
        if (uuid.isNullOrBlank()) {
            ChatUtils.userError("This item doesn't have a UUID!")
            return
        }
        ItemTagManager.removeTag(uuid)
    }

    private fun searchByTag(tag: String) {
        val uuid = ItemTagManager.getUuidByTag(tag)
        if (uuid == null) {
            ChatUtils.userError("No item found with tag: §b$tag")
            val suggestions = ItemTagManager.searchTags(tag)
            if (suggestions.isNotEmpty()) {
                ChatUtils.chat("§7Did you mean: §e${suggestions.take(5).joinToString("§7, §e")}")
            }
            return
        }
        val results = StorageApi.search()
            .filterByUuid(StringUtils.parseUUID(formatDashedUuid(uuid)))
            .getResults()
        if (results.isNotEmpty()) {
            if (results.size == 1) results.first().runAccess(false) else {
                results.outputToChat()
            }
        } else {
            ChatUtils.chat("§7Tagged item §b$tag §7not found in storage")
        }
    }

    private fun searchByDisplayName(query: String) {
        ChatUtils.chat("§7Searching display names for: §e$query")
        val pattern = query // regex
        StorageApi.searchByName(pattern, ignoreCase = true).outputToChat()
    }

    private fun searchByDescription(query: String) {
        ChatUtils.chat("§7Searching lore for regex: §e$query")
        StorageApi.search().filterByDescription(query, ignoreCase = true).getResults().outputToChat()
    }

    private fun searchByItemId(id: String) {
        ChatUtils.chat("§7Searching by item id: §e$id")
        StorageApi.search().filterByItemId(id.uppercase(), ignoreCase = true).getResults().outputToChat()
    }

    private fun searchByUuidString(raw: String) {
        val dashed = formatDashedUuid(raw)
        val uuid = runCatching { UUID.fromString(dashed) }.getOrNull()
        if (uuid == null) {
            ChatUtils.userError("Invalid UUID: $raw")
            return
        }
        ChatUtils.chat("§7Searching for UUID: §e$dashed")
        StorageApi.search().filterByUuid(uuid).getResults().outputToChat()
    }

    private fun listAllTags() {
        val allTaggedItems = ItemTagManager.getAllTaggedItems()
        if (allTaggedItems.isEmpty()) {
            ChatUtils.chat("§7No tagged items found. Use §e/shtagitem <tag> §7to tag items!")
            return
        }
        ChatUtils.chat("§aAll tagged items (${allTaggedItems.size}):")
        allTaggedItems.forEach { (tag, uuid) ->
            ChatUtils.clickableChat(
                "§b$tag §7(UUID: ${formatDashedUuid(uuid).take(8)}...)",
                onClick = { searchByTag(tag) },
                "§eClick to search for this tag!",
            )
        }
    }

    private fun formatDashedUuid(uuid: String): String {
        val plain = uuid.replace("-", "").lowercase()
        if (plain.length != 32) return uuid // not a valid raw uuid, return original
        return listOf(
            plain.substring(0, 8),
            plain.substring(8, 12),
            plain.substring(12, 16),
            plain.substring(16, 20),
            plain.substring(20, 32),
        ).joinToString("-")
    }
}
