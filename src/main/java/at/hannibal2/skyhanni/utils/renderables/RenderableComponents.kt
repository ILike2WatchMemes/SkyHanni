package at.hannibal2.skyhanni.utils.renderables

import at.hannibal2.skyhanni.features.misc.numpadcodes.NumpadCode
import at.hannibal2.skyhanni.features.misc.keybinds.Keybinds
import at.hannibal2.skyhanni.utils.GuiRenderUtils
import at.hannibal2.skyhanni.utils.RenderUtils
import at.hannibal2.skyhanni.utils.compat.GuiScreenUtils

/**
 * Minimal component renderables to start refactor.
 */
object RenderableComponents {

    fun codesList(
        width: Int,
        height: Int,
        codesProvider: () -> List<NumpadCode>,
        selectedIndexProvider: () -> Int,
    ): Renderable {
        return object : Renderable {
            override val width = width
            override val height = height
            override val horizontalAlign = RenderUtils.HorizontalAlignment.LEFT
            override val verticalAlign = RenderUtils.VerticalAlignment.TOP

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                val listLeft = 0
                val listTop = 0
                GuiRenderUtils.drawString("Codes:", listLeft, listTop - 15)
                var y = listTop
                val visibleCount = 24
                val codes = codesProvider()
                for ((i, c) in codes.withIndex()) {
                    if (i >= visibleCount) break
                    val isHover = GuiRenderUtils.isPointInRect(GuiScreenUtils.mouseX, GuiScreenUtils.mouseY, mouseOffsetX + listLeft, mouseOffsetY + y, width, 12)
                    if (i == selectedIndexProvider()) GuiRenderUtils.drawRect(listLeft - 4, y - 2, listLeft + width, y + 12, 0x20288a33)
                    else if (isHover) GuiRenderUtils.drawRect(listLeft - 4, y - 2, listLeft + width, y + 12, 0x20555555)
                    GuiRenderUtils.drawString("${i + 1}. ${c.code} -> ${c.actions.size} actions", listLeft, y)
                    y += 14
                }
            }
        }
    }

    fun suggestionsBox(maxWidth: Int, suggestionsProvider: () -> List<String>, selectedIndexProvider: () -> Int): Renderable {
        return object : Renderable {
            override val width: Int
                get() = maxWidth
            override val height: Int
                get() = (suggestionsProvider().size * 12).coerceAtMost(160)
            override val horizontalAlign = RenderUtils.HorizontalAlignment.LEFT
            override val verticalAlign = RenderUtils.VerticalAlignment.TOP

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                val suggestions = suggestionsProvider()
                if (suggestions.isEmpty()) return
                val boxX = 0
                val boxY = 0
                val boxW = width
                val itemH = 12
                GuiRenderUtils.drawFloatingRectDark(boxX, boxY, boxW, height)
                var iy = boxY + 2
                for ((i, s) in suggestions.withIndex()) {
                    if (i == selectedIndexProvider()) GuiRenderUtils.drawRect(boxX + 2, iy - 2, boxX + boxW - 2, iy + itemH, 0x204488FF)
                    GuiRenderUtils.drawString(s, boxX + 4, iy)
                    iy += itemH
                }
            }
        }
    }

    // New: keybinds list renderer used by KeybindEditorGui
    fun keybindsList(
        width: Int,
        height: Int,
        bindsProvider: () -> List<Keybinds.Keybind>,
        selectedIndexProvider: () -> Int,
    ): Renderable {
        return object : Renderable {
            override val width = width
            override val height = height
            override val horizontalAlign = RenderUtils.HorizontalAlignment.LEFT
            override val verticalAlign = RenderUtils.VerticalAlignment.TOP

            override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
                val listLeft = 0
                val listTop = 0
                GuiRenderUtils.drawString("Keybinds:", listLeft, listTop - 15)
                var y = listTop
                val visibleCount = (height / 14).coerceAtLeast(1)
                val binds = bindsProvider()
                for ((i, b) in binds.withIndex()) {
                    if (i >= visibleCount) break
                    val isHover = GuiRenderUtils.isPointInRect(GuiScreenUtils.mouseX, GuiScreenUtils.mouseY, mouseOffsetX + listLeft, mouseOffsetY + y, width, 12)
                    if (i == selectedIndexProvider()) GuiRenderUtils.drawRect(listLeft - 4, y - 2, listLeft + width, y + 12, 0x20288a33)
                    else if (isHover) GuiRenderUtils.drawRect(listLeft - 4, y - 2, listLeft + width, y + 12, 0x20555555)
                    GuiRenderUtils.drawString("${i + 1}. ${b.combo} -> ${b.command}", listLeft, y)
                    y += 14
                }
            }
        }
    }
}
