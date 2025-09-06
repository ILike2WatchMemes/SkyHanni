package at.hannibal2.skyhanni.config.features.inventory.hubselector

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.HypixelData
import at.hannibal2.skyhanni.data.toBNIsland
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.GuiKeyPressEvent
import at.hannibal2.skyhanni.features.bingo.bingonet.SplashManager
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.InventoryDetector
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.RegexUtils.matchGroup
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RenderUtils.highlight
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


@SkyHanniModule
object HubSelectorKeybinds {
    private val config get() = SkyHanniMod.feature.event.bingo.bingoNetworks
    private var lastClick = SimpleTimeMark.farPast()
    private val patternGroup = RepoPattern.group("inventory.hubselector")

    @Transient
    val hubIdToNumberCache: BiMap<String, Int> = HashBiMap.create()
    private var openedCache: Map<Int, HubData>? = null

    @Transient
    var lastUpdate = SimpleTimeMark.farPast()

    // TODO Dungeon Hub implementation
    /**
     * REGEX-TEST: Dungeon Hub Selector
     * REGEX-TEST: SkyBlock Hub Selector
     */
    private val hubSelectorGuiNamePattern by patternGroup.pattern(
        "gui-name",
        ".*Hub Selector.*",
    )

    /**
     * REGEX-TEST: §aSkyBlock Hub #24
     * REGEX-TEST: §aDungeon Hub #1
     */
    private val itemNamePattern by patternGroup.pattern(
        "item-name",
        "§.(?<type>(SkyBlock)|(Dungeon)) Hub #(?<hubNumber>\\d+)",
    )

    /**
     * REGEX-TEST: §7Players: 5/60
     * REGEX-TEST: §7Players: 0/0
     * REGEX-TEST: §7Players: 60/60
     */
    private val playersPattern by patternGroup.pattern(
        "player-count",
        "§7Players: (?<current>\\d+)/(?<max>\\d+)",
    )

    /**
     * REGEX-TEST: §8Server: mega13D
     * REGEX-TEST: §8Server: mini63BW
     */
    private val serverIdPattern by patternGroup.pattern(
        "server-id",
        "§8Server: (?<serverid>.*)",
    )
    private var mainInventory = InventoryDetector(
        openInventory = {
            hubIdToNumberCache.clear()
            lastUpdate = SimpleTimeMark.now()
            val cache = HashMap<Int, HubData>()
            for (slot in it.inventoryItems.entries) {
                val stack = slot.value
                val data = stack.parseToHubSelectorData() ?: continue
                hubIdToNumberCache[data.serverId] = data.hubNumber
                cache[slot.key] = data
            }
            openedCache = cache
        },
        closeInventory = { openedCache = null },
        pattern = hubSelectorGuiNamePattern,
    )

    @HandleEvent(onlyOnSkyblock = true)
    fun onKeyPress(event: GuiKeyPressEvent) {
        if (HypixelData.joinedWorld.passedSince() <= 3.seconds) return
        if (!mainInventory.isInside()) return
        val cache = openedCache ?: return

        val chest = event.guiContainer as? GuiChest ?: return

        val key = config.splashHubWarp.getEffectiveKey()
        if (!key.isKeyHeld() || lastClick.passedSince() < 250.milliseconds) return
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
        event.guiContainer.inventorySlots.inventorySlots.forEach { slot ->
            val data = cache[slot.slotNumber] ?: return@forEach
            val score = calculateScore(splashPool.get(data.serverId), data)
            if (score != null) {
                if (bestClick == null) {
                    bestClick = Pair(score, slot.slotNumber)
                    return@forEach
                } else if (score < bestClick.first)
                    bestClick = Pair(score, slot.slotNumber)
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
        val hubNumber = itemNamePattern.matchGroup(displayName, "hubNumber")?.toIntOrNull() ?: return null
        var serverId: String? = null
        var playerCount: Int? = null
        var maxPlayerCount: Int? = null
        for (line in getLore()) {
            playersPattern.matchMatcher(line) {
                playerCount = group("current").toInt()
                maxPlayerCount = group("max").toInt()
            }
            serverIdPattern.matchGroup(line, "serverid")?.let { serverId = it }
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
        if (lastUpdate.plus(30.seconds).isInPast()) return null // Hub Swaps problem etc.
        return SplashData.HubSelectorData(hubIdToNumberCache.get(serverId) ?: return null, island)
    }

    @HandleEvent
    fun onBackgroundDrawn(event: GuiContainerEvent.BackgroundDrawnEvent) {
        if (!SkyHanniMod.feature.event.bingo.bingoNetworks.highlightSplashHub || !mainInventory.isInside()) return
        val cache = openedCache ?: return

        val splashHubs = SplashManager.splashPool.filter { it.value.status == StatusConstants.WAITING }.map { it.value.serverID }
        val minPlayerCount = SkyHanniMod.feature.event.bingo.bingoNetworks.splasherConfig.lowestPlayerHub.let {
            if (it) {
                cache.maxBy { it.value.maxPlayerCount - it.value.playerCount }.value.serverId
            } else {
                null
            }
        }


        InventoryUtils.getItemsInOpenChest().forEach { slot ->
            val slotNumber = slot.slotNumber
            val cacheData = cache[slotNumber] ?: return@forEach
            if (splashHubs.contains(cacheData.serverId)) {
                slot.highlight(LorenzColor.YELLOW.addOpacity(255))
            }
            if (minPlayerCount == cacheData.serverId) {
                slot.highlight(LorenzColor.LIGHT_PURPLE.addOpacity(255))
            }
        }
    }

    private class HubData(
        val hubNumber: Int,
        val serverId: String,
        val playerCount: Int,
        val maxPlayerCount: Int,
    )
}
