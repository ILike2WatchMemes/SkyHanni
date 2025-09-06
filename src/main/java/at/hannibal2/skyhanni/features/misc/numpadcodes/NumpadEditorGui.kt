package at.hannibal2.skyhanni.features.misc.numpadcodes

import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.utils.compat.SkyhanniBaseScreen
import at.hannibal2.skyhanni.utils.compat.DrawContextUtils
import at.hannibal2.skyhanni.utils.compat.GuiScreenUtils
import at.hannibal2.skyhanni.utils.GuiRenderUtils
import at.hannibal2.skyhanni.utils.ChatUtils
import org.lwjgl.input.Keyboard
import at.hannibal2.skyhanni.utils.ClipboardUtils
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld
import at.hannibal2.skyhanni.utils.compat.MouseCompat
import kotlinx.coroutines.runBlocking
import at.hannibal2.skyhanni.utils.renderables.RenderableComponents
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.primitives.TextFieldController
import kotlin.collections.plusAssign

/**
 * Clean rebuilt NumpadEditorGui with:
 * - Per-code allowOutsideSkyBlock toggle
 * - Fixed mouse wheel suggestion navigation (1 notch = 1 selection move)
 * - Working island list scrolling
 * - Working actions list scrolling
 */
class NumpadEditorGui : SkyhanniBaseScreen() {

    private val editor = NumpadEditor()

    // Codes list state
    private var codes = NumpadCodes.allCodes().toMutableList()
    private var selectedIndex = -1

    // Editing state
    private var currentlyEditing = false
    private var editCodeText = ""
    private var editActionsList: MutableList<NumpadEditor.EditorAction> = mutableListOf()
    private var editActionDelayText: MutableList<String> = mutableListOf()
    private var allowOutsideSkyBlock = false

    // Text controllers
    private var codeController: TextFieldController? = null
    private val actionCommandControllers = mutableListOf<TextFieldController>()
    private val actionDelayControllers = mutableListOf<TextFieldController>()

    // Carets / selections
    private var editCodeCaret = 0
    private var editCodeSelectionStart: Int? = null
    private var editCodeSelectionEnd: Int? = null
    private val editActionCarets = mutableListOf<Int>()
    private val editActionSelectionStarts = mutableListOf<Int?>()
    private val editActionSelectionEnds = mutableListOf<Int?>()
    private val editActionDelayCarets = mutableListOf<Int>()
    private val editActionDelaySelectionStarts = mutableListOf<Int?>()
    private val editActionDelaySelectionEnds = mutableListOf<Int?>()
    private var ensuringSizes = false

    // Suggestions (DISABLED for code field now)
    private var suggestions: List<String> = emptyList()
    private var suggestionIndex = -1
    private var suggestionScroll = 0
    private val maxSuggestions = 12
    private var showSuggestions = true

    // Focus
    private enum class FocusTarget { NONE, CODE, ACTIONS }
    private enum class ActionField { COMMAND, DELAY }
    private var focusTarget = FocusTarget.NONE
    private var actionFocusedIndex = -1
    private var actionFocusedField = ActionField.COMMAND

    // Islands
    private var editingAllowedIslands: MutableSet<IslandType> = mutableSetOf(IslandType.ANY)
    private var islandSelectionOpen = false
    private var islandSelectionScroll = 0

    // Scrolling
    private var actionsScroll = 0
    private var codesScroll = 0

    // Additional enhanced UX state
    private var suggestionSelectionVisible = true
    private var lastMouseX = 0
    private var lastMouseY = 0

    // Track last focused delay field to finalize on blur
    private var lastDelayFocusedIndex: Int? = null

    override fun onInitGui() { refreshCodes() }

    private fun refreshCodes() {
        codes = NumpadCodes.allCodes().toMutableList()
        if (selectedIndex >= codes.size) selectedIndex = -1
        codesScroll = 0
    }

    private fun ensureSizes() {
        if (ensuringSizes) return
        ensuringSizes = true
        try {
            while (editActionCarets.size < editActionsList.size) editActionCarets.add(0)
            while (editActionCarets.size > editActionsList.size) editActionCarets.removeAt(editActionCarets.lastIndex)
            while (editActionSelectionStarts.size < editActionsList.size) editActionSelectionStarts.add(null)
            while (editActionSelectionStarts.size > editActionsList.size) editActionSelectionStarts.removeAt(editActionSelectionStarts.lastIndex)
            while (editActionSelectionEnds.size < editActionsList.size) editActionSelectionEnds.add(null)
            while (editActionSelectionEnds.size > editActionsList.size) editActionSelectionEnds.removeAt(editActionSelectionEnds.lastIndex)
            while (editActionDelayCarets.size < editActionsList.size) editActionDelayCarets.add(0)
            while (editActionDelayCarets.size > editActionsList.size) editActionDelayCarets.removeAt(editActionDelayCarets.lastIndex)
            while (editActionDelaySelectionStarts.size < editActionsList.size) editActionDelaySelectionStarts.add(null)
            while (editActionDelaySelectionStarts.size > editActionsList.size) editActionDelaySelectionStarts.removeAt(editActionDelaySelectionStarts.lastIndex)
            while (editActionDelaySelectionEnds.size < editActionsList.size) editActionDelaySelectionEnds.add(null)
            while (editActionDelaySelectionEnds.size > editActionsList.size) editActionDelaySelectionEnds.removeAt(editActionDelaySelectionEnds.lastIndex)
            if (actionCommandControllers.size != editActionsList.size) rebuildFields()
        } finally { ensuringSizes = false }
    }

    private fun rebuildFields() {
        // Code field
        if (codeController == null) {
            codeController = TextFieldController(
                { editCodeText }, { v -> editCodeText = v },
                { editCodeCaret }, { c -> editCodeCaret = c },
                { editCodeSelectionStart }, { editCodeSelectionEnd },
                { a, b -> editCodeSelectionStart = a; editCodeSelectionEnd = b }, width = 200, height = 20,
                showCaret = { focusTarget == FocusTarget.CODE },
            )
        } else {
            codeController?.setSize(200, 20)
            codeController?.setTextValue(editCodeText)
            codeController?.setCursorPosition(editCodeCaret)
        }
        ensureSizes()
        actionCommandControllers.clear(); actionDelayControllers.clear()
        for (i in editActionsList.indices) {
            val idx = i
            val cmd = TextFieldController(
                { editActionsList[idx].command }, { v -> editActionsList[idx].command = v },
                { editActionCarets.getOrNull(idx) ?: editActionsList[idx].command.length },
                { c -> if (idx < editActionCarets.size) editActionCarets[idx] = c },
                { editActionSelectionStarts.getOrNull(idx) }, { editActionSelectionEnds.getOrNull(idx) },
                { a, b -> if (idx < editActionSelectionStarts.size) { editActionSelectionStarts[idx] = a; editActionSelectionEnds[idx] = b } },
                width = 140, height = 14,
                showCaret = { focusTarget == FocusTarget.ACTIONS && actionFocusedIndex == idx && actionFocusedField == ActionField.COMMAND },
            )
            actionCommandControllers += cmd
            val delay = TextFieldController(
                { editActionDelayText.getOrNull(idx) ?: editActionsList[idx].delaySeconds.toString() },
                { v -> if (idx < editActionDelayText.size) editActionDelayText[idx] = v else editActionDelayText.add(v) },
                { editActionDelayCarets.getOrNull(idx) ?: (editActionDelayText.getOrNull(idx)?.length ?: 1) },
                { c -> if (idx < editActionDelayCarets.size) editActionDelayCarets[idx] = c },
                { editActionDelaySelectionStarts.getOrNull(idx) }, { editActionDelaySelectionEnds.getOrNull(idx) },
                { a, b -> if (idx < editActionDelaySelectionStarts.size) { editActionDelaySelectionStarts[idx] = a; editActionDelaySelectionEnds[idx] = b } },
                width = 50, height = 14,
                showCaret = { focusTarget == FocusTarget.ACTIONS && actionFocusedIndex == idx && actionFocusedField == ActionField.DELAY },
            )
            actionDelayControllers += delay
        }
    }

    private fun querySuggestions(text: String, cursor: Int): List<String> = try { editor.suggestFor(text, cursor) } catch (_: Throwable) { emptyList() }

    private fun updateSuggestions(actions: Boolean) {
        if (!actions) { // disable suggestions for code field entirely
            suggestions = emptyList()
            suggestionIndex = -1
            suggestionScroll = 0
            showSuggestions = false
            return
        }
        val list = if (actionFocusedIndex !in editActionsList.indices) emptyList() else {
            val f = actionCommandControllers.getOrNull(actionFocusedIndex)
            val txt = f?.getText() ?: editActionsList[actionFocusedIndex].command
            val cur = f?.getCursorPosition() ?: txt.length
            querySuggestions(txt, cur)
        }.distinct().filter { it.isNotBlank() }
        suggestions = list
        suggestionIndex = if (list.isNotEmpty()) 0 else -1
        suggestionScroll = 0
        showSuggestions = list.isNotEmpty()
    }

    private fun pageSuggestions(next: Boolean) {
        if (!showSuggestions || suggestions.isEmpty()) return
        val delta = if (next) maxSuggestions else -maxSuggestions
        suggestionIndex = (suggestionIndex + delta).coerceIn(0, suggestions.lastIndex)
        if (suggestionIndex < suggestionScroll) suggestionScroll = suggestionIndex
        if (suggestionIndex >= suggestionScroll + maxSuggestions) suggestionScroll = suggestionIndex - maxSuggestions + 1
        suggestionSelectionVisible = true
    }

    private fun acceptSuggestion(forActions: Boolean) {
        if (suggestionIndex !in suggestions.indices) return
        val chosen = suggestions[suggestionIndex]
        if (!forActions) return // no suggestions for code field anymore
        if (actionFocusedIndex !in editActionsList.indices) return
        val field = actionCommandControllers.getOrNull(actionFocusedIndex) ?: return
        val text = field.getText()
        val cursor = try { field.getCursorPosition() } catch (_: Throwable) { text.length }
        val left = text.substring(0, cursor).lastIndexOf(' ').let { if (it < 0) 0 else it + 1 }
        val right = text.substring(cursor).indexOf(' ').let { if (it < 0) text.length else cursor + it }
        val newText = text.substring(0, left) + chosen + text.substring(right)
        field.setTextValue(newText)
        try { field.setCursorPosition(left + chosen.length) } catch (_: Throwable) {}
        editActionsList[actionFocusedIndex].command = newText
        showSuggestions = false
        suggestionSelectionVisible = true
    }

    private fun suggestionBoxPos(editLeft: Int, editTop: Int, editW: Int, actionsBoxTop: Int): Pair<Int, Int> {
        val boxW = (editW - 4).coerceAtMost(360)
        val itemH = 12
        val boxH = (suggestions.size * itemH).coerceAtMost(160)
        return when (focusTarget) {
            FocusTarget.CODE -> Pair(editLeft + (editW - boxW) / 2, editTop + 40) // simplified, rarely used now
            FocusTarget.ACTIONS -> {
                val rowY = actionsBoxTop + 4 + (actionFocusedIndex.coerceAtLeast(0) * 18) - actionsScroll
                val below = rowY + 16
                val above = rowY - boxH - 4
                Pair(editLeft + 4, if (below + boxH > height && above >= 0) above else below)
            }
            else -> Pair(editLeft + (editW - boxW) / 2, editTop + 40)
        }
    }

    private fun handleSuggestionClick(mx: Int, my: Int, editLeft: Int, editTop: Int, editW: Int, actionsBoxTop: Int): Boolean {
        if (!showSuggestions || suggestions.isEmpty()) return false
        val (x, y) = suggestionBoxPos(editLeft, editTop, editW, actionsBoxTop)
        val w = (editW - 4).coerceAtMost(360)
        val itemH = 12
        val visible = suggestions.drop(suggestionScroll).take(maxSuggestions)
        val h = (visible.size * itemH).coerceAtMost(160)
        if (!GuiRenderUtils.isPointInRect(mx, my, x, y, w, h)) return false
        val idx = ((my - y) / itemH).coerceIn(0, visible.lastIndex)
        suggestionIndex = suggestionScroll + idx
        acceptSuggestion(focusTarget == FocusTarget.ACTIONS)
        return true
    }

    private fun drawButton(x: Int, y: Int, w: Int, text: String) {
        val base = when {
            text.contains("§a") || text.contains("§2") -> 0x30304A30
            text.contains("§c") || text.contains("§4") -> 0x303A2020
            text.contains("§e") || text.contains("§6") -> 0x303A3A20
            text.contains("§b") || text.contains("§3") -> 0x3020333A
            else -> 0x20202020
        }
        val hover = when {
            text.contains("§a") || text.contains("§2") -> 0x404F6540
            text.contains("§c") || text.contains("§4") -> 0x40554040
            text.contains("§e") || text.contains("§6") -> 0x40585832
            text.contains("§b") || text.contains("§3") -> 0x40324455
            else -> 0x30333333
        }
        GuiRenderUtils.drawRect(x, y, x + w, y + 18, if (mouseIsOver(x, y, w, 18)) hover else base)
        GuiRenderUtils.drawStringCentered(text, x + w / 2, y + 9)
    }

    private fun drawEditor(editLeft: Int, editTop: Int, editW: Int) {
        GuiRenderUtils.drawString("Code:", editLeft, editTop)
        GuiRenderUtils.drawRect(editLeft, editTop + 12 - 2, editLeft + editW - 4, editTop + 12 + 20, 0xFF2B2B2B.toInt())
        codeController?.setSize(editW - 4, 20)
        codeController?.render(editLeft, editTop + 12)
        editCodeText = codeController?.getText() ?: editCodeText

        GuiRenderUtils.drawString("Actions (command + delay):", editLeft, editTop + 38)
        val actionsBoxTop = editTop + 50
        val actionsBoxH = 160
        GuiRenderUtils.drawRect(editLeft, actionsBoxTop, editLeft + editW - 4, actionsBoxTop + actionsBoxH, 0x20202020)
        var y = actionsBoxTop + 4 - actionsScroll
        // Enable scissor to clip to box
        GuiRenderUtils.enableScissor(editLeft, actionsBoxTop, editLeft + editW - 4, actionsBoxTop + actionsBoxH)
        for (i in editActionsList.indices) {
            if (y > actionsBoxTop + actionsBoxH - 16) break
            val rowBottom = y + 14
            if (rowBottom >= actionsBoxTop && y <= actionsBoxTop + actionsBoxH - 2) {
                val cmdW = (editW - 4) - 140
                val cmdField = actionCommandControllers.getOrNull(i)
                if (cmdField != null) {
                    GuiRenderUtils.drawRect(editLeft + 4, y - 2, editLeft + 4 + cmdW, y + 14, 0xFF2B2B2B.toInt())
                    cmdField.setSize(cmdW, 14)
                    cmdField.render(editLeft + 4, y)
                    editActionsList[i].command = cmdField.getText()
                }
                val delayX = editLeft + 4 + cmdW + 6
                GuiRenderUtils.drawRect(delayX, y - 2, delayX + 50, y + 14, 0xFF2B2B2B.toInt())
                val delayField = actionDelayControllers.getOrNull(i)
                if (delayField != null) {
                    delayField.setSize(50, 14)
                    delayField.render(delayX, y)
                    if (i < editActionDelayText.size) editActionDelayText[i] = delayField.getText() else editActionDelayText.add(delayField.getText())
                }
                val removeX = delayX + 56
                GuiRenderUtils.drawRect(removeX, y - 2, removeX + 18, y + 14, 0x30AA5555)
                GuiRenderUtils.drawStringCentered("§c×", removeX + 9, y + 7)
            }
            y += 18
        }
        GuiRenderUtils.disableScissor()
        val addY = actionsBoxTop + actionsBoxH + 4
        drawButton(editLeft, addY, 120, "§aAdd Action")
        drawButton(editLeft, addY + 24, 120, "§eEdit Islands")
        drawButton(editLeft + 140, addY, 80, "§2Save")
        drawButton(editLeft + 228, addY, 80, "§4Cancel")
    }

    private fun drawIslandSelection(editLeft: Int, editTop: Int, editW: Int) {
        GuiRenderUtils.drawString("Configure Islands (toggle) & Defaults:", editLeft, editTop + 6)
        val lineH = 14
        val boxH = 200
        val startY = editTop + 26
        var y = startY - islandSelectionScroll
        val unknown = IslandType.UNKNOWN in editingAllowedIslands
        val unknownLabel = "New Islands (future sb updated default)"
        val outsideLabel = "Outside SkyBlock (Lobby etc.)"
        fun drawToggleRow(label: String, selected: Boolean) {
            if (y + lineH >= startY && y <= startY + boxH - lineH) {
                GuiRenderUtils.drawRect(editLeft, y - 2, editLeft + editW - 4, y + lineH - 2, if (selected) 0x3044AA44 else 0x20101010)
                GuiRenderUtils.drawString("[${if (selected) 'x' else ' '}] $label", editLeft + 4, y)
            }
            y += lineH
        }
        drawToggleRow(unknownLabel, unknown)
        drawToggleRow(outsideLabel, allowOutsideSkyBlock)
        val valid = IslandType.entries.filter { it.isValidIsland() }
        for (isl in valid) {
            val sel = isl in editingAllowedIslands
            if (y + lineH >= startY && y <= startY + boxH - lineH) {
                GuiRenderUtils.drawRect(editLeft, y - 2, editLeft + editW - 4, y + lineH - 2, if (sel) 0x3044AA44 else 0x20101010)
                GuiRenderUtils.drawString("[${if (sel) 'x' else ' '}] ${isl.displayName}", editLeft + 4, y)
            }
            y += lineH
        }
        drawButton(editLeft, startY + boxH + 6, 80, "§eBack")
        val totalLines = 2 + valid.size
        GuiRenderUtils.drawScrollbar(editLeft + editW, startY, boxH, totalLines * lineH, islandSelectionScroll)
    }

    override fun onDrawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (mouseX != lastMouseX || mouseY != lastMouseY) { suggestionSelectionVisible = true; lastMouseX = mouseX; lastMouseY = mouseY }
        drawDefaultBackground(mouseX, mouseY, partialTicks)
        val totalW = 960
        val totalH = 520
        val left = (width - totalW) / 2
        val top = (height - totalH) / 2
        val listLeft = left + 10
        val listTop = top + 30
        val listW = 300
        val listH = totalH - 60
        GuiRenderUtils.drawFloatingRectDark(left, top, totalW, totalH)

        // Always show codes list
        DrawContextUtils.pushPop {
            DrawContextUtils.translate(listLeft.toFloat(), listTop.toFloat(), 0f)
            DrawContextUtils.translate(0f, -codesScroll.toFloat(), 0f)
            val r: Renderable = RenderableComponents.codesList(listW, listH, { codes }, { selectedIndex })
            r.render(GuiScreenUtils.mouseX - listLeft, GuiScreenUtils.mouseY - listTop + codesScroll)
        }
        val btnY = listTop + listH - 20
        // Buttons: Add, Remove, Refresh (moved to old Edit position)
        drawButton(listLeft, btnY, 80, "§aAdd")
        drawButton(listLeft + 88, btnY, 80, "§cRemove")
        drawButton(listLeft + 176, btnY, 80, "§bRefresh")
        GuiRenderUtils.drawScrollbar(listLeft + listW + 4, listTop, listH, codes.size * 14, codesScroll)

        // Editor area to right
        val editLeft = listLeft + listW + 24
        val editTop = listTop
        val editW = totalW - (editLeft - left) - 14
        GuiRenderUtils.drawString("Editor:", editLeft, editTop - 15)

        if (!currentlyEditing) {
            GuiRenderUtils.drawStrings("Select a code and click Edit, or click Add to create a new one.", editLeft, editTop)
            if (selectedIndex in codes.indices) {
                val c = codes[selectedIndex]
                GuiRenderUtils.drawString("Code: ${c.code}", editLeft, editTop + 30)
                GuiRenderUtils.drawString("Islands: ${c.allowedIslands.filter{it.isValidIsland()}.joinToString { it.displayName }}".take( editW - 20), editLeft, editTop + 44)
                GuiRenderUtils.drawString("Future New Islands: ${if (IslandType.UNKNOWN in c.allowedIslands) 'Y' else 'N'}", editLeft, editTop + 58)
                GuiRenderUtils.drawString("Outside SB: ${if (c.allowOutsideSkyBlock) "Yes" else "No"}", editLeft, editTop + 72)
            }
        } else if (islandSelectionOpen) {
            drawIslandSelection(editLeft, editTop, editW)
        } else {
            drawEditor(editLeft, editTop, editW)
        }

        if (currentlyEditing && showSuggestions && suggestions.isNotEmpty()) {
            val (sx, sy) = suggestionBoxPos(editLeft, editTop, editW, editTop + 48)
            val w = (editW - 4).coerceAtMost(360)
            val itemH = 12
            val visible = suggestions.drop(suggestionScroll).take(maxSuggestions)
            val h = (visible.size * itemH).coerceAtMost(160)
            GuiRenderUtils.drawRect(sx - 2, sy - 2, sx + w + 2, sy + h + 2, 0xC0202020.toInt())
            visible.forEachIndexed { i, s ->
                val real = suggestionScroll + i
                if (real == suggestionIndex && suggestionSelectionVisible) GuiRenderUtils.drawRect(sx, sy + i * itemH, sx + w, sy + (i + 1) * itemH, 0x80446699.toInt())
                GuiRenderUtils.drawString(s, sx + 4, sy + i * itemH + 2)
            }
            GuiRenderUtils.drawScrollbar(sx + w + 3, sy, h, suggestions.size * itemH, suggestionScroll * itemH)
        }
    }

    private fun mouseIsOver(x: Int, y: Int, w: Int, h: Int) = GuiRenderUtils.isPointInRect(GuiScreenUtils.mouseX, GuiScreenUtils.mouseY, x, y, w, h)

    override fun onMouseClicked(mx: Int, my: Int, btn: Int) {
        val totalW = 960
        val totalH = 520
        val left = (width - totalW) / 2
        val top = (height - totalH) / 2
        val listLeft = left + 10
        val listTop = top + 30
        val listW = 300
        val listH = totalH - 60

        // Codes list always clickable
        if (GuiRenderUtils.isPointInRect(mx, my, listLeft, listTop, listW, listH)) {
            val relY = my - listTop + codesScroll
            val idx = relY / 14
            if (idx in codes.indices) {
                selectedIndex = idx
                editSelected() // open editor immediately on row click
            }
        }
        val btnY = listTop + listH - 20
        if (mouseIsOver(listLeft, btnY, 80, 18)) { startAdd(); return }
        if (mouseIsOver(listLeft + 88, btnY, 80, 18)) { removeSelected(); return }
        if (mouseIsOver(listLeft + 176, btnY, 80, 18)) { refreshCodes(); ChatUtils.chat("Refreshed numpad codes"); return }
        // finalize delay on blur when clicking elsewhere
        fun finalizeDelayBlur() {
            lastDelayFocusedIndex?.let { if (it in editActionDelayText.indices) sanitizeDelay(it, finalize = true) }
            lastDelayFocusedIndex = null
        }
        if (!currentlyEditing) return

        // Replace layout calculations for editing with consistent right-side panel
        val editLeft = listLeft + listW + 24
        val editTop = listTop
        val editW = totalW - (editLeft - left) - 14
        val actionsBoxTop = editTop + 50
        val actionsBoxH = 160

        // Suggestions click
        if (handleSuggestionClick(mx, my, editLeft, editTop, editW, actionsBoxTop)) return

        if (islandSelectionOpen) {
            val lineH = 14
            val boxH = 200
            val startY = editTop + 26
            var y = startY - islandSelectionScroll
            // Unknown row
            if (mx in editLeft until (editLeft + editW - 4) && my in (y - 2) until (y - 2 + lineH)) {
                if (IslandType.UNKNOWN in editingAllowedIslands) editingAllowedIslands.remove(IslandType.UNKNOWN) else editingAllowedIslands.add(IslandType.UNKNOWN)
                return
            }
            y += lineH
            // Outside row
            if (mx in editLeft until (editLeft + editW - 4) && my in (y - 2) until (y - 2 + lineH)) {
                allowOutsideSkyBlock = !allowOutsideSkyBlock
                return
            }
            y += lineH
            val valid = IslandType.entries.filter { it.isValidIsland() }
            for (isl in valid) {
                if (y + lineH < startY) { y += lineH; continue }
                if (y > startY + boxH - lineH) break
                if (mx in editLeft until (editLeft + editW - 4) && my in (y - 2) until (y - 2 + lineH)) {
                    if (isl in editingAllowedIslands) editingAllowedIslands.remove(isl) else editingAllowedIslands.add(isl)
                    return
                }
                y += lineH
            }
            // Back button
            if (mx in editLeft until (editLeft + 80) && my in (startY + boxH + 6) until (startY + boxH + 24)) {
                islandSelectionOpen = false
            }
            return
        }

        // Code field
        if (GuiRenderUtils.isPointInRect(mx, my, editLeft, editTop + 12, editW - 4, 20)) {
            if (focusTarget == FocusTarget.ACTIONS && actionFocusedField == ActionField.DELAY) finalizeDelayBlur()
            focusTarget = FocusTarget.CODE
            updateSuggestions(false)
            codeController?.click(mx - editLeft, my - (editTop + 12), Keyboard.KEY_LSHIFT.isKeyHeld() || Keyboard.KEY_RSHIFT.isKeyHeld())
            return
        }

        // Actions area
        if (GuiRenderUtils.isPointInRect(mx, my, editLeft, actionsBoxTop, editW - 4, actionsBoxH)) {
            val relY = my - (actionsBoxTop + 4) + actionsScroll
            val idx = relY / 18
            if (idx in editActionsList.indices) {
                val cmdW = (editW - 4) - 140
                val rowY = actionsBoxTop + 4 + idx * 18 - actionsScroll
                val cmdRect = GuiRenderUtils.isPointInRect(mx, my, editLeft + 4, rowY - 2, cmdW, 16)
                val delayRect = GuiRenderUtils.isPointInRect(mx, my, editLeft + 4 + cmdW + 6, rowY - 2, 50, 16)
                val removeRect = GuiRenderUtils.isPointInRect(mx, my, editLeft + 4 + cmdW + 6 + 56, rowY - 2, 18, 16)
                when {
                    removeRect -> { if (focusTarget == FocusTarget.ACTIONS && actionFocusedField == ActionField.DELAY && actionFocusedIndex == idx) finalizeDelayBlur(); editActionsList.removeAt(idx); if (idx < editActionDelayText.size) editActionDelayText.removeAt(idx); rebuildFields() }
                    cmdRect -> { if (focusTarget == FocusTarget.ACTIONS && actionFocusedField == ActionField.DELAY && actionFocusedIndex != idx) finalizeDelayBlur(); actionFocusedIndex = idx; actionFocusedField = ActionField.COMMAND; focusTarget = FocusTarget.ACTIONS; updateSuggestions(true); actionCommandControllers[idx].click(mx - (editLeft + 4), my - rowY, false) }
                    delayRect -> { if (focusTarget == FocusTarget.ACTIONS && actionFocusedField == ActionField.DELAY && actionFocusedIndex != idx) finalizeDelayBlur(); actionFocusedIndex = idx; actionFocusedField = ActionField.DELAY; focusTarget = FocusTarget.ACTIONS; showSuggestions = false; actionDelayControllers[idx].click(mx - (editLeft + 4 + cmdW + 6), my - rowY, false); lastDelayFocusedIndex = idx }
                }
            }
            return
        }

        // Editor buttons
        val addY = (editTop + 50) + 160 + 4
        if (mouseIsOver(editLeft, addY, 120, 18)) { if (focusTarget == FocusTarget.ACTIONS && actionFocusedField == ActionField.DELAY) finalizeDelayBlur(); addAction(); return }
        if (mouseIsOver(editLeft, addY + 24, 120, 18)) { if (focusTarget == FocusTarget.ACTIONS && actionFocusedField == ActionField.DELAY) finalizeDelayBlur(); islandSelectionOpen = true; islandSelectionScroll = 0; return }
        if (mouseIsOver(editLeft + 140, addY, 80, 18)) { if (focusTarget == FocusTarget.ACTIONS && actionFocusedField == ActionField.DELAY) finalizeDelayBlur(); saveEditing(); return }
        if (mouseIsOver(editLeft + 228, addY, 80, 18)) { if (focusTarget == FocusTarget.ACTIONS && actionFocusedField == ActionField.DELAY) finalizeDelayBlur(); cancelEditing(); return }
    }

    private fun startAdd() {
        currentlyEditing = true
        selectedIndex = -1
        editCodeText = ""
        editActionsList = mutableListOf(NumpadEditor.EditorAction("", 0.0))
        editActionDelayText = mutableListOf("0.0")
        allowOutsideSkyBlock = false // still default false, only changeable via island selection row now
        editingAllowedIslands = IslandType.entries.filter { it.isValidIsland() }.toMutableSet().apply { add(IslandType.UNKNOWN) }
        islandSelectionOpen = false
        focusTarget = FocusTarget.CODE
        actionFocusedIndex = -1
        showSuggestions = false
        rebuildFields()
    }

    private fun editSelected() {
        if (selectedIndex !in codes.indices) { ChatUtils.userError("No code selected to edit"); return }
        val c = codes[selectedIndex]
        currentlyEditing = true
        editCodeText = c.code
        editActionsList = c.actions.map { NumpadEditor.EditorAction(it.command, it.delaySeconds) }.toMutableList()
        editActionDelayText = editActionsList.map { it.delaySeconds.toString() }.toMutableList()
        allowOutsideSkyBlock = c.allowOutsideSkyBlock
        editingAllowedIslands = c.allowedIslands.toMutableSet()
        islandSelectionOpen = false
        focusTarget = FocusTarget.CODE
        actionFocusedIndex = -1
        showSuggestions = false
        rebuildFields()
    }

    private fun removeSelected() {
        if (selectedIndex !in codes.indices) { ChatUtils.userError("No code selected to remove"); return }
        val code = codes[selectedIndex].code
        NumpadCodes.unregister(code)
        ChatUtils.chat("Removed code $code")
        refreshCodes()
    }

    private fun addAction() {
        // finalize delay blur before adding new
        lastDelayFocusedIndex?.let { sanitizeDelay(it, finalize = true) }
        lastDelayFocusedIndex = null
        editActionsList.add(NumpadEditor.EditorAction("", 1.0))
        editActionDelayText.add("") // start blank
        actionFocusedIndex = editActionsList.lastIndex
        actionFocusedField = ActionField.COMMAND
        focusTarget = FocusTarget.ACTIONS
        rebuildFields()
        updateSuggestions(true)
    }

    private fun saveEditing() {
        val errors = saveFromEditing()
        if (errors.isEmpty()) {
            ChatUtils.chat("Saved numpad code ${editCodeText}")
            currentlyEditing = false
            refreshCodes()
        } else errors.forEach { ChatUtils.userError(it) }
    }

    private fun cancelEditing() {
        currentlyEditing = false
        refreshCodes()
    }

    private fun sanitizeDelay(idx: Int, finalize: Boolean = false) {
        if (idx !in editActionDelayText.indices) return
        var raw = editActionDelayText[idx]
        raw = raw.replace(',', '.')
        val sb = StringBuilder()
        var dotSeen = false
        for (c in raw) {
            if (c.isDigit()) sb.append(c) else if (c == '.' && !dotSeen) { sb.append('.'); dotSeen = true }
        }
        var out = sb.toString()
        if (finalize) {
            if (out.isEmpty()) out = "0.0" else if (!out.contains('.')) out += ".0"
        } // if not finalize leave empty allowed
        editActionDelayText[idx] = out
        actionDelayControllers.getOrNull(idx)?.setTextValue(out)
    }

    private fun sanitizeAllDelays(finalize: Boolean) { for (i in editActionDelayText.indices) sanitizeDelay(i, finalize) }

    private fun sanitizeCode() {
        val filtered = editCodeText.filter { it.isDigit() }
        if (filtered != editCodeText) {
            editCodeText = filtered
            codeController?.setTextValue(filtered)
            val cur = codeController?.getCursorPosition() ?: filtered.length
            codeController?.setCursorPosition(cur.coerceAtMost(filtered.length))
        }
    }

    private fun saveFromEditing(): List<String> {
        // finalize all delays before save
        for (i in editActionDelayText.indices) sanitizeDelay(i, finalize = true)
        lastDelayFocusedIndex = null
        editCodeText = codeController?.getText() ?: editCodeText
        sanitizeCode()
        for (i in editActionsList.indices) {
            editActionsList[i].command = actionCommandControllers.getOrNull(i)?.getText() ?: editActionsList[i].command
            if (i < editActionDelayText.size) editActionDelayText[i] = actionDelayControllers.getOrNull(i)?.getText() ?: editActionDelayText[i]
            else editActionDelayText.add(actionDelayControllers.getOrNull(i)?.getText() ?: "1.0")
            sanitizeDelay(i, finalize = true)
        }
        val errors = mutableListOf<String>()
        val codeStr = editCodeText.trim()
        if (codeStr.isBlank()) errors += "Code cannot be empty"
        val parsed = mutableListOf<NumpadEditor.EditorAction>()
        editActionsList.forEachIndexed { idx, act ->
            val cmd = act.command.trim(); if (cmd.isBlank()) return@forEachIndexed
            val delayStr = (editActionDelayText.getOrNull(idx) ?: "1.0")
            val delay = delayStr.toDoubleOrNull()
            if (delay == null || delay < 0.0) errors += "Action #${idx + 1} invalid delay" else parsed += NumpadEditor.EditorAction(cmd, delay)
        }
        if (parsed.isEmpty()) errors += "At least one action is required"
        if (errors.isNotEmpty()) return errors
        val finalIslands = editingAllowedIslands.filter { it != IslandType.ANY && it != IslandType.NONE }.toMutableSet()
        val editorCode = NumpadEditor.EditorCode(codeStr, parsed.toMutableList(), 0.0, finalIslands, allowOutsideSkyBlock)
        return editor.save(editorCode)
    }

    override fun onHandleMouseInput() {
        // unify layout references (remove centered variant)
        val totalW = 960
        val totalH = 520
        val left = (width - totalW) / 2
        val top = (height - totalH) / 2
        val listLeft = left + 10
        val listTop = top + 30
        val listW = 300
        val editLeft = listLeft + listW + 24
        val editTop = listTop
        val editW = totalW - (editLeft - left) - 14
        val actionsBoxTop = editTop + 50
        val actionsBoxH = 160
        val scroll = MouseCompat.getScrollDelta()
        if (scroll == 0) return
        if (currentlyEditing) {
            if (showSuggestions && suggestions.isNotEmpty()) {
                val (sx, sy) = suggestionBoxPos(editLeft, editTop, editW, actionsBoxTop)
                val w = (editW - 4).coerceAtMost(360)
                val itemH = 12
                val visible = suggestions.drop(suggestionScroll).take(maxSuggestions)
                val h = (visible.size * itemH).coerceAtMost(160)
                val over = GuiRenderUtils.isPointInRect(GuiScreenUtils.mouseX, GuiScreenUtils.mouseY, sx, sy, w, h)
                if (over || focusTarget != FocusTarget.NONE) {
                    val dir = if (scroll > 0) -1 else 1
                    suggestionIndex = (suggestionIndex + dir).coerceIn(0, suggestions.lastIndex)
                    if (suggestionIndex < suggestionScroll) suggestionScroll = suggestionIndex
                    if (suggestionIndex >= suggestionScroll + maxSuggestions) suggestionScroll = suggestionIndex - maxSuggestions + 1
                    return
                }
            }
            if (islandSelectionOpen) {
                val lineH = 14
                val boxH = 200
                val totalLines = 2 + IslandType.entries.count { it.isValidIsland() }
                val contentHeight = totalLines * lineH
                val maxScroll = (contentHeight - boxH).coerceAtLeast(0)
                val startY = editTop + 26
                if (GuiRenderUtils.isPointInRect(GuiScreenUtils.mouseX, GuiScreenUtils.mouseY, editLeft, startY, editW - 4, boxH)) {
                    islandSelectionScroll = (islandSelectionScroll - scroll * lineH).coerceIn(0, maxScroll)
                    return
                }
            } else if (GuiRenderUtils.isPointInRect(GuiScreenUtils.mouseX, GuiScreenUtils.mouseY, editLeft, actionsBoxTop, editW - 4, actionsBoxH)) {
                val contentH = editActionsList.size * 18
                val max = (contentH - actionsBoxH).coerceAtLeast(0)
                actionsScroll = (actionsScroll - scroll * 18).coerceIn(0, max)
                return
            }
        } else {
            val listH = totalH - 60
            if (GuiRenderUtils.isPointInRect(GuiScreenUtils.mouseX, GuiScreenUtils.mouseY, listLeft, listTop, listW, listH)) {
                if (codes.isNotEmpty()) {
                    val dir = if (scroll > 0) -1 else 1
                    selectedIndex = if (selectedIndex == -1) 0 else (selectedIndex + dir).coerceIn(0, codes.lastIndex)
                    val rowY = selectedIndex * 14
                    if (rowY < codesScroll) codesScroll = rowY else if (rowY > codesScroll + listH - 14) codesScroll = rowY - (listH - 14)
                }
                return
            }
        }
    }

    override fun onKeyTyped(ch: Char?, keyCode: Int?) {
        val kc = keyCode ?: -1
        val ctrl = Keyboard.KEY_LCONTROL.isKeyHeld() || Keyboard.KEY_RCONTROL.isKeyHeld()

        // PageUp/PageDown for suggestions (re-enabled)
        if (showSuggestions && suggestions.isNotEmpty() && (kc == Keyboard.KEY_NEXT || kc == Keyboard.KEY_PRIOR)) {
            pageSuggestions(kc == Keyboard.KEY_NEXT)
            return
        }

        // Island selection page jump
        if (islandSelectionOpen && (kc == Keyboard.KEY_NEXT || kc == Keyboard.KEY_PRIOR)) {
            val lineH = 14
            val boxH = 200
            val totalLines = 2 + IslandType.entries.count { it.isValidIsland() }
            val maxScroll = (totalLines * lineH - boxH).coerceAtLeast(0)
            val delta = boxH // one full page
            islandSelectionScroll = (islandSelectionScroll + if (kc == Keyboard.KEY_NEXT) delta else -delta).coerceIn(0, maxScroll)
            return
        }

        // Accept suggestion early
        if ((kc == Keyboard.KEY_RETURN || kc == Keyboard.KEY_NUMPADENTER) && showSuggestions && suggestions.isNotEmpty()) {
            acceptSuggestion(focusTarget == FocusTarget.ACTIONS)
            return
        }

        // Suggestion navigation
        if (showSuggestions && suggestions.isNotEmpty() && (kc == Keyboard.KEY_DOWN || kc == Keyboard.KEY_UP)) {
            val dir = if (kc == Keyboard.KEY_DOWN) 1 else -1
            suggestionIndex = (suggestionIndex + dir).coerceIn(0, suggestions.lastIndex)
            if (suggestionIndex < suggestionScroll) suggestionScroll = suggestionIndex
            if (suggestionIndex >= suggestionScroll + maxSuggestions) suggestionScroll = suggestionIndex - maxSuggestions + 1
            return
        }

        // Codes navigation when not editing
        if (!currentlyEditing && (kc == Keyboard.KEY_DOWN || kc == Keyboard.KEY_UP)) {
            if (codes.isNotEmpty()) {
                val dir = if (kc == Keyboard.KEY_DOWN) 1 else -1
                selectedIndex = if (selectedIndex == -1) 0 else (selectedIndex + dir).coerceIn(0, codes.lastIndex)
                val rowY = selectedIndex * 14
                if (rowY < codesScroll) codesScroll = rowY else if (rowY > codesScroll + 300 - 14) codesScroll = rowY - (300 - 14)
            }
            return
        }

        // Clipboard copy
        if (kc == Keyboard.KEY_C && ctrl) {
            try {
                if (focusTarget == FocusTarget.CODE) {
                    codeController?.getSelectedText()?.takeIf { it.isNotEmpty() }?.let { ClipboardUtils.copyToClipboard(it) }
                } else if (focusTarget == FocusTarget.ACTIONS && actionFocusedIndex in editActionsList.indices) {
                    actionCommandControllers.getOrNull(actionFocusedIndex)?.getSelectedText()?.takeIf { it.isNotEmpty() }?.let {
                        ClipboardUtils.copyToClipboard(it)
                    }
                }
            } catch (_: Throwable) {}
            return
        }

        // Paste
        if (kc == Keyboard.KEY_V && ctrl) {
            runBlocking {
                val clip = ClipboardUtils.readFromClipboard() ?: return@runBlocking
                if (focusTarget == FocusTarget.CODE) {
                    val digits = clip.filter { it.isDigit() }
                    codeController?.writeText(digits); editCodeText = codeController?.getText() ?: editCodeText; sanitizeCode()
                } else if (focusTarget == FocusTarget.ACTIONS && actionFocusedIndex in editActionsList.indices) {
                    if (actionFocusedField == ActionField.COMMAND) {
                        actionCommandControllers.getOrNull(actionFocusedIndex)?.writeText(clip)
                        editActionsList[actionFocusedIndex].command = actionCommandControllers[actionFocusedIndex].getText(); updateSuggestions(true)
                    } else {
                        actionDelayControllers.getOrNull(actionFocusedIndex)?.writeText(clip)
                        editActionDelayText[actionFocusedIndex] = actionDelayControllers[actionFocusedIndex].getText(); sanitizeDelay(actionFocusedIndex, finalize = false)
                    }
                }
            }
            return
        }

        // Cut
        if (kc == Keyboard.KEY_X && ctrl) {
            try {
                if (focusTarget == FocusTarget.CODE) {
                    val f = codeController; val sel = f?.getSelectedText(); if (!sel.isNullOrEmpty()) { ClipboardUtils.copyToClipboard(sel); f.writeText(""); editCodeText = f.getText(); sanitizeCode() }
                } else if (focusTarget == FocusTarget.ACTIONS && actionFocusedIndex in editActionsList.indices) {
                    val f = actionCommandControllers.getOrNull(actionFocusedIndex); val sel = f?.getSelectedText(); if (!sel.isNullOrEmpty()) { ClipboardUtils.copyToClipboard(sel); f.writeText(""); editActionsList[actionFocusedIndex].command = f.getText() }
                }
            } catch (_: Throwable) {}
            return
        }

        // Select all
        if (kc == Keyboard.KEY_A && ctrl) {
            if (focusTarget == FocusTarget.CODE) codeController?.textboxKeyTyped(null, kc)
            else if (focusTarget == FocusTarget.ACTIONS && actionFocusedIndex in editActionsList.indices) actionCommandControllers.getOrNull(actionFocusedIndex)?.textboxKeyTyped(null, kc)
            return
        }

        // Tab -> cycle suggestions / request
        if (kc == Keyboard.KEY_TAB) {
            if (focusTarget == FocusTarget.ACTIONS) updateSuggestions(true)
            return
        }

        // Forward char input
        try {
            if (focusTarget == FocusTarget.CODE) {
                val allowed = (ch?.isDigit() == true) || keyCode in arrayOf(Keyboard.KEY_BACK, Keyboard.KEY_LEFT, Keyboard.KEY_RIGHT, Keyboard.KEY_DELETE)
                if (allowed) codeController?.textboxKeyTyped(ch ?: '\u0000', keyCode ?: -1)
                editCodeText = codeController?.getText() ?: editCodeText
                sanitizeCode()
                return
            } else if (focusTarget == FocusTarget.ACTIONS && actionFocusedIndex in editActionsList.indices) {
                val field = if (actionFocusedField == ActionField.COMMAND) actionCommandControllers.getOrNull(actionFocusedIndex) else actionDelayControllers.getOrNull(actionFocusedIndex)
                field?.textboxKeyTyped(ch ?: '\u0000', keyCode ?: -1)
                if (actionFocusedField == ActionField.COMMAND) {
                    editActionsList[actionFocusedIndex].command = field?.getText() ?: editActionsList[actionFocusedIndex].command
                    updateSuggestions(true)
                } else {
                    editActionDelayText[actionFocusedIndex] = field?.getText() ?: editActionDelayText[actionFocusedIndex]
                    // only light sanitize (no default) while editing
                    sanitizeDelay(actionFocusedIndex, finalize = false)
                    lastDelayFocusedIndex = actionFocusedIndex
                }
                return
            }
        } catch (_: Throwable) {}

        // Enter adds new action if in actions focus
        if ((kc == Keyboard.KEY_RETURN || kc == Keyboard.KEY_NUMPADENTER) && focusTarget == FocusTarget.ACTIONS) {
            addAction(); return
        }
    }

    override fun onMouseClickMove(originalMouseX: Int, originalMouseY: Int, clickedMouseButton: Int, timeSinceLastClick: Long) {
        if (clickedMouseButton != 0) return
        if (!currentlyEditing) return
        val totalW = 960
        val left = (width - totalW) / 2
        val listLeft = left + 10
        val listW = 300
        val editLeft = listLeft + listW + 24
        val editW = totalW - (editLeft - left) - 14
        if (focusTarget == FocusTarget.CODE) {
            codeController?.drag(originalMouseX - editLeft)
            return
        }
        if (focusTarget == FocusTarget.ACTIONS && actionFocusedIndex in editActionsList.indices) {
            val cmdW = (editW - 4) - 140
            when (actionFocusedField) {
                ActionField.COMMAND -> actionCommandControllers.getOrNull(actionFocusedIndex)?.drag((originalMouseX - (editLeft + 4)).coerceAtLeast(-50))
                ActionField.DELAY -> {
                    val delayX = editLeft + 4 + cmdW + 6
                    actionDelayControllers.getOrNull(actionFocusedIndex)?.drag((originalMouseX - delayX).coerceAtLeast(-10))
                }
            }
        }
    }

    override fun onMouseReleased(originalMouseX: Int, originalMouseY: Int, state: Int) {
        // Release drag state for all controllers (safe even if not dragging)
        codeController?.release()
        actionCommandControllers.forEach { it.release() }
        actionDelayControllers.forEach { it.release() }
    }
}
