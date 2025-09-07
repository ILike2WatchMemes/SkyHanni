package at.hannibal2.skyhanni.features.bingo.card

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.minecraft.KeyDownEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyClicked
import at.hannibal2.skyhanni.utils.SkyBlockItemModifierUtils.getItemId
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import org.lwjgl.input.Keyboard

@SkyHanniModule
object PetXPComGoalKeybind {
    val config get() = SkyHanniMod.feature.event.bingo

    @HandleEvent(onlyOnSkyblock = true)
    fun onKeyDown(event: KeyDownEvent) {
        val key = config.petXpComGoalKeybind
        if (key == Keyboard.KEY_NONE) return
        if (key.isKeyClicked()) {
            if (InventoryUtils.getItemInHand()?.getInternalName()?.internalName.equals("SIMPLE_CARROT_CANDY")) {
                HypixelCommands.pet()
            } else {
                val carrots = InventoryUtils.getItemsInOwnInventory().filter { it.getItemId().equals("CARROT_ITEM") }.sumOf { it.stackSize }
                if (carrots >= 9 * 64) {
                    HypixelCommands.recipe("SIMPLE_CARROT_CANDY")
                }
            }
        }
    }
}
