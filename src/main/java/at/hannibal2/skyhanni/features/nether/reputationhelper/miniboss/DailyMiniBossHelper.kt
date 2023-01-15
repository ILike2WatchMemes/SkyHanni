package at.hannibal2.skyhanni.features.nether.reputationhelper.miniboss

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.data.HyPixelData
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.LorenzChatEvent
import at.hannibal2.skyhanni.features.nether.reputationhelper.CrimsonIsleReputationHelper
import at.hannibal2.skyhanni.utils.LorenzUtils
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.regex.Pattern

class DailyMiniBossHelper(private val reputationHelper: CrimsonIsleReputationHelper) {

    val miniBosses = mutableListOf<CrimsonMiniBoss>()

    fun init() {
        if (miniBosses.isNotEmpty()) return

        val repoData = reputationHelper.repoData
        val jsonElement = repoData["MINIBOSS"]
        val asJsonArray = jsonElement.asJsonArray
        for (entry in asJsonArray) {
            val displayName = entry.asString
            val patterns = " *§r§6§l${displayName.uppercase()} DOWN!"
            miniBosses.add(CrimsonMiniBoss(displayName, Pattern.compile(patterns)))
        }
    }

    @SubscribeEvent
    fun onChat(event: LorenzChatEvent) {
        if (!HyPixelData.skyBlock) return
        if (LorenzUtils.skyBlockIsland != IslandType.CRIMSON_ISLE) return
        if (!SkyHanniMod.feature.misc.crimsonIsleReputationHelper) return

        val message = event.message
        for (miniBoss in miniBosses) {
            if (miniBoss.pattern.matcher(message).matches()) {
                finished(miniBoss)
            }
        }
    }

    private fun finished(miniBoss: CrimsonMiniBoss) {
        LorenzUtils.debug("Detected mini boss death: ${miniBoss.displayName}")
        reputationHelper.questHelper.finishMiniBoss(miniBoss)
        miniBoss.doneToday = true
        reputationHelper.update()
    }

    fun render(display: MutableList<String>) {
        val done = miniBosses.count { it.doneToday }
//        val sneaking = Minecraft.getMinecraft().thePlayer.isSneaking
//        if (done != 5 || sneaking) {
        if (done != 5) {
            display.add("")
            display.add("Daily Bosses ($done/5 killed)")
            for (miniBoss in miniBosses) {
                display.add(renderQuest(miniBoss))
            }
        }
    }

    private fun renderQuest(miniBoss: CrimsonMiniBoss): String {
        val color = if (miniBoss.doneToday) "§7Done" else "§bTodo"
        val displayName = miniBoss.displayName
        return "$displayName: $color"
    }

    fun reset() {
        for (miniBoss in miniBosses) {
            miniBoss.doneToday = false
        }
    }

    fun saveConfig() {
        SkyHanniMod.feature.hidden.crimsonIsleMiniBossesDoneToday.clear()

        for (miniBoss in miniBosses) {
            if (miniBoss.doneToday) {
                SkyHanniMod.feature.hidden.crimsonIsleMiniBossesDoneToday.add(miniBoss.displayName)
            }
        }
    }

    fun loadConfig() {
        for (name in SkyHanniMod.feature.hidden.crimsonIsleMiniBossesDoneToday) {
            getByDisplayName(name)!!.doneToday = true
        }
    }

    private fun getByDisplayName(name: String) = miniBosses.firstOrNull { it.displayName == name }
}