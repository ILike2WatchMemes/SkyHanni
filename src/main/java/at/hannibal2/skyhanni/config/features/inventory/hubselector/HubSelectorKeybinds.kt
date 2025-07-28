package at.hannibal2.skyhanni.config.features.inventory.hubselector

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.data.toBNIsland
import at.hannibal2.skyhanni.events.GuiKeyPressEvent
import at.hannibal2.skyhanni.features.bingo.bingonet.SplashManager
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.InventoryDetector
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyClicked
import at.hannibal2.skyhanni.utils.RegexUtils.groupOrNull
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.mapKeysNotNull
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import de.hype.bingonet.shared.constants.Islands
import de.hype.bingonet.shared.constants.StatusConstants
import de.hype.bingonet.shared.objects.SplashData
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.item.ItemStack
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds


@SkyHanniModule
object HubSelectorKeybinds {
    private val config get() = SkyHanniMod.feature.event.bingo.bingoNetworks
    private var lastClick = SimpleTimeMark.farPast()
    private val patternGroup = RepoPattern.group("inventory.hubselector")
    val hubIdToNumberCache: BiMap<String, Int> = HashBiMap.create()
    var lastUpdate = SimpleTimeMark.farPast()

    // TODO Dungeon Hub implementation
    private val hubSelectorGuiNamePattern by patternGroup.pattern(
        "gui-name",
        ".*Hub Selector.*",
    )

    private val itemNamePattern by patternGroup.pattern(
        "item-name",
        "((SkyBlock)|(Dungeon)) Hub #(?<hubNumber>\\d+)",
    )

    private val playersPattern by patternGroup.pattern(
        "player-count",
        "Players: (?<current>\\d+)/(?<max>\\d+)",
    )
    private val serverIdPattern by patternGroup.pattern(
        "server-id",
        "Server: (?<serverid>.*)",
    )
    private var mainInventory = InventoryDetector(
        openInventory = {
            hubIdToNumberCache.clear()
            lastUpdate = SimpleTimeMark.now()
            for (stack in it.inventoryItems.values) {
                val data = stack.parseToHubSelectorData() ?: continue
                hubIdToNumberCache[data.serverId] = data.hubNumber
            }
        },
        pattern = hubSelectorGuiNamePattern,
    )

    @HandleEvent(onlyOnSkyblock = true)
    fun onKeyPress(event: GuiKeyPressEvent) {
        val key = config.splashHubWarp.getEffectiveKey()
        if (!mainInventory.isInside()) return

        val chest = event.guiContainer as? GuiChest ?: return

        if (!key.isKeyClicked()) return
        lastClick = SimpleTimeMark.now()
        event.cancel()
        // First Score | Second Index
        var bestClick: Pair<Int, Int>? = null
        val island = HypixelData.skyBlockIsland.toBNIsland()
        val splashPool: Map<String, SplashManager.DisplaySplash> =
            SplashManager.splashPool.filter { it.value.status == StatusConstants.WAITING }
                .filter { it.value.hubSelectorData?.hubType == island && island != null }
                .mapKeysNotNull {
                    val simpleServerId: String? = it.value.serverID
                    if (simpleServerId != null) return@mapKeysNotNull simpleServerId
                    // Bingo Brewers sends the hub number instead of the serverid
                    // :skull: Indigo mapps the hub numbers to severids in his 1.8.9 mod.
                    // The cache is needed anyway though for serverid to hub number mapping for splash announcements
                    return@mapKeysNotNull hubIdToNumberCache.inverse()[it.value.hubSelectorData?.hubNumber]

                }
        event.guiContainer.inventorySlots.inventorySlots.forEachIndexed { index, slot ->
            val data = slot.stack.parseToHubSelectorData() ?: return@forEachIndexed
            val score = calculateScore(splashPool.get(data.serverId), data)
            if (score != null) {
                if (bestClick == null) {
                    bestClick = Pair(score, index)
                    return@forEachIndexed
                } else if (score < bestClick.first)
                    bestClick = Pair(score, index)
            }
        }
        bestClick?.let {
            InventoryUtils.clickSlot(bestClick.second, chest.inventorySlots.windowId, mouseButton = 2, mode = 3)
        }
    }

    private fun calculateScore(sploosh: SplashManager.DisplaySplash?, data: HubData): Int? {
        if (sploosh == null) return null
        val time = Duration.between(sploosh.receivedTime, Instant.now()).seconds
        val spots = data.maxPlayerCount - data.playerCount
        if (spots <= 3 || time > 45) return null
        return time.toInt() - spots
    }

    private fun ItemStack.parseToHubSelectorData(): HubData? {
        val hubNumber = itemNamePattern.matcher(displayName).groupOrNull("hubNumber")?.toIntOrNull() ?: return null
        var serverId: String? = null
        var playerCount: Int? = null
        var maxPlayerCount: Int? = null
        for (line in getLore()) {
            val playerMatcher = playersPattern.matcher(line)
            if (playerMatcher.matches()) {
                playerCount = playerMatcher.group("current").toInt()
                maxPlayerCount = playerMatcher.group("max").toInt()
            } else if (serverIdPattern.matcher(line).matches()) {
                val serverMatcher = serverIdPattern.matcher(line)
                if (serverMatcher.matches()) {
                    serverId = serverMatcher.group("serverid")
                }
            }
        }
        serverId?.let {
            maxPlayerCount?.let {
                playerCount?.let {
                    hubNumber.let {
                        return HubData(it, serverId, playerCount, maxPlayerCount)
                    }
                }
            }
        }
        return null
    }

    fun getHubNumberById(serverId: String, island: Islands): SplashData.HubSelectorData? {
        if (lastUpdate.plus(30.seconds).isInPast()) return null //Hub Swaps problem etc.
        return SplashData.HubSelectorData(hubIdToNumberCache.get(serverId) ?: return null, island)
    }

    private class HubData(
        val hubNumber: Int,
        val serverId: String,
        val playerCount: Int,
        val maxPlayerCount: Int,
    )
}
