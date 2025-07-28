package at.hannibal2.skyhanni.features.inventory

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.GuiKeyPressEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld
import at.hannibal2.skyhanni.utils.compat.slotUnderCursor
import net.minecraft.client.gui.inventory.GuiChest
import org.lwjgl.input.Keyboard

@SkyHanniModule
object BulkQuickMoveKeybind {
    private val keybind get() = SkyHanniMod.feature.inventory.bulkMoveKeybind

    fun isEnabled() = keybind != Keyboard.KEY_NONE

    @HandleEvent(onlyOnSkyblock = true)
    fun handleEvent(event: GuiKeyPressEvent) {
        if (!keybind.isKeyHeld()) return
        val slot = slotUnderCursor()
        if (slot == null || !slot.hasStack) return
        val chest = event.guiContainer as? GuiChest ?: return
        val windowId = chest.inventorySlots.windowId
        InventoryUtils.clickSlot(slot.slotNumber, windowId, GuiContainerEvent.ClickType.SHIFT)
    }

}
