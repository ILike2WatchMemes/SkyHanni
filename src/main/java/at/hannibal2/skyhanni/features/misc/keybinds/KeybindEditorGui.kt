package at.hannibal2.skyhanni.features.misc.keybinds

import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.utils.compat.SkyhanniBaseScreen
import at.hannibal2.skyhanni.utils.compat.DrawContextUtils
import at.hannibal2.skyhanni.utils.compat.GuiScreenUtils
import at.hannibal2.skyhanni.utils.GuiRenderUtils
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld
import org.lwjgl.input.Keyboard
import at.hannibal2.skyhanni.utils.renderables.primitives.TextFieldController
import at.hannibal2.skyhanni.utils.renderables.RenderableComponents
import at.hannibal2.skyhanni.utils.CommandSuggestionProvider
import at.hannibal2.skyhanni.utils.KeyboardManager
import at.hannibal2.skyhanni.utils.compat.MouseCompat
import at.hannibal2.skyhanni.utils.ui.CommandSuggestionController
import at.hannibal2.skyhanni.utils.ClipboardUtils
import kotlinx.coroutines.runBlocking

class KeybindEditorGui : SkyhanniBaseScreen() {
    private val manager = Keybinds
    private val editor = KeybindEditor()

    private var binds = manager.allBinds().toMutableList()
    private var selectedIndex = -1

    private var currentlyEditing = false
    private var capturingCombo = false
    private var editCombo = ""
    private var editCommand = ""
    private var editingAllowedIslands: MutableSet<IslandType> = mutableSetOf(IslandType.ANY)
    private var allowOutside = false

    // track original combo when editing to avoid duplicate entries on save
    private var editingOriginalCombo: String? = null

    // Command field caret + selection
    private var commandCaret = 0
    private var commandSelStart: Int? = null
    private var commandSelEnd: Int? = null
    private var commandField: TextFieldController? = null
    private var commandFieldFocused = false

    // Unified suggestions controller
    private val suggestionController = CommandSuggestionController(12) { text, cursor ->
        try { CommandSuggestionProvider.suggest(text, cursor) } catch (_: Throwable) { emptyList() }
    }

    // Islands scrolling
    private var islandScroll = 0

    // Combo capture base keys (non-modifiers)
    private val capturingBaseKeys = mutableSetOf<String>()
    private val capturingBaseKeyCodes = mutableSetOf<Int>()
    private val capturingModifiersUsed = mutableSetOf<String>()
    private var captureHasBase = false

    override fun onInitGui() { refresh() }

    private fun refresh() {
        binds = manager.allBinds().toMutableList()
        if (selectedIndex >= binds.size) selectedIndex = -1
    }

    private fun ensureFields() {
        if (commandField == null) {
            commandField = TextFieldController(
                { editCommand }, { v -> editCommand = v },
                { commandCaret }, { c -> commandCaret = c.coerceIn(0, editCommand.length) },
                { commandSelStart }, { commandSelEnd },
                { a, b -> commandSelStart = a; commandSelEnd = b },
                width = 300, height = 20,
                showCaret = { currentlyEditing && !capturingCombo }, // always show while editing
            )
        } else {
            commandField?.setTextValue(editCommand)
            commandField?.setCursorPosition(commandCaret.coerceIn(0, editCommand.length))
        }
    }

    private fun updateSuggestions() {
        if (!currentlyEditing || capturingCombo) { suggestionController.reset(); return }
        val field = commandField ?: return
        val txt = field.getText()
        val cur = try { field.getCursorPosition() } catch (_: Throwable) { txt.length }
        suggestionController.update(txt, cur, enabled = true)
    }

    private fun acceptSuggestion() {
        val field = commandField ?: return
        val text = field.getText()
        val cursor = try { field.getCursorPosition() } catch (_: Throwable) { text.length }
        val accepted = suggestionController.accept(text, cursor) ?: return
        field.setTextValue(accepted.first)
        try { field.setCursorPosition(accepted.second) } catch (_: Throwable) {}
        editCommand = accepted.first
    }

    private fun pageSuggestions(next: Boolean) { suggestionController.page(next) }

    override fun onDrawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        // Auto-finalize combo capture when all recorded base keys released (updated implementation)
        if (capturingCombo && captureHasBase) {
            val anyHeld = capturingBaseKeyCodes.any { try { it.isKeyHeld() } catch (_: Throwable) { false } }
            if (!anyHeld) {
                if (capturingBaseKeys.isNotEmpty()) {
                    val combo = (capturingModifiersUsed + capturingBaseKeys).joinToString("+")
                    editCombo = Keybinds.normalizeCombo(combo)
                }
                capturingCombo = false
                capturingBaseKeys.clear(); capturingBaseKeyCodes.clear(); capturingModifiersUsed.clear(); captureHasBase = false
            }
        }

        drawDefaultBackground(mouseX, mouseY, partialTicks)
        val totalW = 860; val totalH = 520
        val left = (width - totalW) / 2; val top = (height - totalH) / 2
        val listLeft = left + 10; val listTop = top + 30
        val listW = 260; val listH = totalH - 60

        GuiRenderUtils.drawFloatingRectDark(left, top, totalW, totalH)
        DrawContextUtils.pushPop {
            DrawContextUtils.translate(listLeft.toFloat(), listTop.toFloat(), 0f)
            val r = RenderableComponents.keybindsList(listW, listH, { binds }, { selectedIndex })
            r.render(GuiScreenUtils.mouseX - listLeft, GuiScreenUtils.mouseY - listTop)
        }

        val btnY = listTop + listH - 20
        drawButton(listLeft, btnY, 80, "§aAdd")
        drawButton(listLeft + 88, btnY, 80, "§cRemove")
        drawButton(listLeft + 176, btnY, 80, "§bRefresh")

        val editLeft = listLeft + listW + 24
        val editTop = listTop
        val editW = totalW - (editLeft - left) - 14

        GuiRenderUtils.drawString("Editor:", editLeft, editTop - 15)

        if (!currentlyEditing) {
            GuiRenderUtils.drawStrings("Select a keybind and click Edit or Add to create.", editLeft, editTop)
            if (selectedIndex in binds.indices) {
                val b = binds[selectedIndex]
                GuiRenderUtils.drawString("Combo: ${b.combo}", editLeft, editTop + 30)
                GuiRenderUtils.drawString("Command: ${b.command}".take(editW - 20), editLeft, editTop + 44)
                GuiRenderUtils.drawString("Islands: ${b.allowedIslands.filter{it.isValidIsland()}.joinToString { it.displayName }}".take(editW - 20), editLeft, editTop + 58)
                GuiRenderUtils.drawString("Outside SB: ${if (b.allowOutsideSkyBlock) "Yes" else "No"}", editLeft, editTop + 72)
            }
            return
        }

        ensureFields()
        GuiRenderUtils.drawString("Combo (click to capture, Enter to finish, Esc to cancel):", editLeft, editTop)
        GuiRenderUtils.drawRect(editLeft, editTop + 12 - 2, editLeft + 260, editTop + 12 + 20, 0xFF2B2B2B.toInt())
        val comboLabel = when {
            capturingCombo && capturingBaseKeys.isEmpty() -> "Press base keys..."
            capturingCombo -> capturingBaseKeys.joinToString("+")
            editCombo.isBlank() -> "<none>"
            else -> editCombo
        }
        GuiRenderUtils.drawString(comboLabel, editLeft + 4, editTop + 12)

        GuiRenderUtils.drawString("Command:", editLeft, editTop + 40)
        GuiRenderUtils.drawRect(editLeft, editTop + 52 - 2, editLeft + editW - 4, editTop + 52 + 20, 0xFF2B2B2B.toInt())
        commandField?.setSize(editW - 4, 20); commandField?.render(editLeft, editTop + 52)
        editCommand = commandField?.getText() ?: editCommand

        // Islands box
        GuiRenderUtils.drawString("Islands (toggle):", editLeft, editTop + 80)
        val boxTop = editTop + 92
        val boxH = 200
        GuiRenderUtils.drawRect(editLeft, boxTop, editLeft + editW - 4, boxTop + boxH, 0x20202020)
        val lineH = 16
        val unknownSelected = IslandType.UNKNOWN in editingAllowedIslands
        val outsideSelected = allowOutside
        var y = boxTop + 4 - islandScroll
        val totalLines = 2 + IslandType.entries.count { it.isValidIsland() }
        GuiRenderUtils.enableScissor(editLeft, boxTop, editLeft + editW - 4, boxTop + boxH)
        fun rowRect(yy: Int, selected: Boolean, label: String) {
            if (yy + lineH < boxTop || yy > boxTop + boxH - lineH) return
            GuiRenderUtils.drawRect(editLeft + 2, yy - 2, editLeft + editW - 6, yy + lineH - 2, if (selected) 0x3044AA44 else 0x20101010)
            GuiRenderUtils.drawString("[${if (selected) 'x' else ' '}] $label", editLeft + 6, yy)
        }
        rowRect(y, unknownSelected, "New Islands (future updates)"); y += lineH
        rowRect(y, outsideSelected, "Outside SkyBlock (Lobby etc.)"); y += lineH
        for (isl in IslandType.entries.filter { it.isValidIsland() }) {
            val sel = isl in editingAllowedIslands
            rowRect(y, sel, isl.displayName)
            y += lineH
        }
        GuiRenderUtils.disableScissor()
        GuiRenderUtils.drawScrollbar(editLeft + editW, boxTop, boxH, totalLines * lineH, islandScroll)

        val buttonsY = boxTop + boxH + 8
        drawButton(editLeft, buttonsY, 120, "§2Save")
        drawButton(editLeft + 128, buttonsY, 120, "§4Cancel")

        // Suggestions popup
        if (suggestionController.visible && suggestionController.suggestions.isNotEmpty()) {
            val sx = editLeft
            val sy = editTop + 52 + 24
            val w = (editW - 4).coerceAtMost(360)
            val itemH = 12
            val visible = suggestionController.visibleSlice()
            val h = (visible.size * itemH).coerceAtMost(160)
            GuiRenderUtils.drawRect(sx - 2, sy - 2, sx + w + 2, sy + h + 2, 0xC0202020.toInt())
            visible.forEachIndexed { i, s ->
                val real = suggestionController.scroll + i
                if (real == suggestionController.index) GuiRenderUtils.drawRect(sx, sy + i * itemH, sx + w, sy + (i + 1) * itemH, 0x80446699.toInt())
                GuiRenderUtils.drawString(s, sx + 4, sy + i * itemH + 2)
            }
            GuiRenderUtils.drawScrollbar(sx + w + 3, sy, h, suggestionController.suggestions.size * itemH, suggestionController.scroll * itemH)
        }
    }

    private fun drawButton(x: Int, y: Int, w: Int, text: String) {
        val base = 0x20202020
        val hover = 0x30333333
        GuiRenderUtils.drawRect(x, y, x + w, y + 18, if (GuiRenderUtils.isPointInRect(GuiScreenUtils.mouseX, GuiScreenUtils.mouseY, x, y, w, 18)) hover else base)
        GuiRenderUtils.drawStringCentered(text, x + w / 2, y + 9)
    }

    override fun onMouseClicked(mx: Int, my: Int, btn: Int) {
        // Capture mouse buttons (exclude left=0 and right=1) during combo recording
        if (currentlyEditing && capturingCombo && btn >= 2) {
            val synth = btn - 100
            val name = KeyboardManager.getKeyName(btn).uppercase()
            capturingBaseKeys += name
            // store synthetic negative key code (btnIndex - 100) consistent with KeyboardManager
            capturingBaseKeyCodes += synth
            captureHasBase = true
            // If click outside combo box, consume to avoid accidental UI interaction
            val totalW = 860; val totalH = 520
            val left = (width - totalW) / 2; val top = (height - totalH) / 2
            val listLeft = left + 10; val listTop = top + 30; val listW = 260
            val editLeft = listLeft + listW + 24; val editTop = listTop
            val inComboBox = mx in editLeft until (editLeft + 260) && my in (editTop + 12) until (editTop + 32)
            if (!inComboBox) return
        }

        val totalW = 860; val totalH = 520
        val left = (width - totalW) / 2; val top = (height - totalH) / 2
        val listLeft = left + 10; val listTop = top + 30
        val listW = 260; val listH = totalH - 60

        if (GuiRenderUtils.isPointInRect(mx, my, listLeft, listTop, listW, listH)) {
            val relY = my - listTop
            val idx = relY / 14
            if (idx in binds.indices) {
                selectedIndex = idx
                editSelected() // open directly for faster workflow
            }
        }
        val btnY = listTop + listH - 20
        if (GuiRenderUtils.isPointInRect(mx, my, listLeft, btnY, 80, 18)) { startAdd(); return }
        if (GuiRenderUtils.isPointInRect(mx, my, listLeft + 88, btnY, 80, 18)) { removeSelected(); return }
        if (GuiRenderUtils.isPointInRect(mx, my, listLeft + 176, btnY, 80, 18)) { refresh(); ChatUtils.chat("Refreshed keybinds"); return }
        if (!currentlyEditing) return

        val editLeft = listLeft + listW + 24
        val editTop = listTop
        val editW = totalW - (editLeft - left) - 14

        // Combo capture box
        if (GuiRenderUtils.isPointInRect(mx, my, editLeft, editTop + 12, 260, 20)) {
            // start capture; if this was a mouse button press include it immediately
            capturingCombo = true; capturingBaseKeys.clear(); commandFieldFocused = false; suggestionController.reset()
            if (btn >= 2) {
                val synth = btn - 100
                try {
                    val name = KeyboardManager.getKeyName(btn).uppercase()
                    capturingBaseKeys += name
                    capturingBaseKeyCodes += synth
                    captureHasBase = true
                } catch (_: Throwable) {}
            }
            return
        }

        // Command field
        if (GuiRenderUtils.isPointInRect(mx, my, editLeft, editTop + 52, editW - 4, 20)) {
            capturingCombo = false
            commandFieldFocused = true
            commandField?.click(mx - editLeft, my - (editTop + 52), Keyboard.KEY_LSHIFT.isKeyHeld() || Keyboard.KEY_RSHIFT.isKeyHeld())
            updateSuggestions(); return
        } else if (commandFieldFocused) {
            // clicking outside unfocus -> hide suggestions
            commandFieldFocused = false
            suggestionController.reset()
        }

        // Suggestions click
        if (suggestionController.visible && suggestionController.suggestions.isNotEmpty()) {
            val sx = editLeft
            val sy = editTop + 52 + 24
            val w = (editW - 4).coerceAtMost(360)
            val itemH = 12
            val visible = suggestionController.visibleSlice()
            val h = (visible.size * itemH).coerceAtMost(160)
            if (GuiRenderUtils.isPointInRect(mx, my, sx, sy, w, h)) {
                val idx = ((my - sy) / itemH).coerceIn(0, visible.lastIndex)
                suggestionController.select(suggestionController.scroll + idx)
                acceptSuggestion(); return
            }
        }

        // Islands box
        val boxTop = editTop + 92
        val boxH = 200
        val lineH = 16
        2 + IslandType.entries.count { it.isValidIsland() }
        if (GuiRenderUtils.isPointInRect(mx, my, editLeft, boxTop, editW - 4, boxH)) {
            var y = boxTop + 4 - islandScroll
            fun hit(yy: Int) = my in (yy - 2) until (yy - 2 + lineH)
            if (hit(y)) { if (IslandType.UNKNOWN in editingAllowedIslands) editingAllowedIslands.remove(IslandType.UNKNOWN) else editingAllowedIslands.add(IslandType.UNKNOWN); return }; y += lineH
            if (hit(y)) { allowOutside = !allowOutside; return }; y += lineH
            for (isl in IslandType.entries.filter { it.isValidIsland() }) {
                if (hit(y)) { if (isl in editingAllowedIslands) editingAllowedIslands.remove(isl) else editingAllowedIslands.add(isl); return }
                y += lineH
            }
        }

        val buttonsY = boxTop + boxH + 8
        if (GuiRenderUtils.isPointInRect(mx, my, editLeft, buttonsY, 120, 18)) { saveEditing(); return }
        if (GuiRenderUtils.isPointInRect(mx, my, editLeft + 128, buttonsY, 120, 18)) { cancelEditing(); return }
    }

    override fun onMouseClickMove(originalMouseX: Int, originalMouseY: Int, clickedMouseButton: Int, timeSinceLastClick: Long) {
        if (clickedMouseButton != 0) return
        if (!currentlyEditing) return
        if (commandFieldFocused) {
            val totalW = 860; val totalH = 520
            val left = (width - totalW) / 2; val top = (height - totalH) / 2
            val listLeft = left + 10; val listTop = top + 30
            val listW = 260
            val editLeft = listLeft + listW + 24
            listTop
            commandField?.drag(originalMouseX - editLeft)
        }
    }

    override fun onMouseReleased(originalMouseX: Int, originalMouseY: Int, state: Int) { commandField?.release() }

    override fun onHandleMouseInput() {
        val scroll = MouseCompat.getScrollDelta(); if (scroll == 0) return
        if (!currentlyEditing) return
        val totalW = 860; val totalH = 520
        val left = (width - totalW) / 2; val top = (height - totalH) / 2
        val listLeft = left + 10; val listTop = top + 30
        val listW = 260
        val editLeft = listLeft + listW + 24
        val editTop = listTop
        val editW = totalW - (editLeft - left) - 14
        // Suggestions scroll
        if (suggestionController.visible && suggestionController.suggestions.isNotEmpty()) {
            val sx = editLeft
            val sy = editTop + 52 + 24
            val w = (editW - 4).coerceAtMost(360)
            val itemH = 12
            val visible = suggestionController.visibleSlice()
            val h = (visible.size * itemH).coerceAtMost(160)
            if (GuiRenderUtils.isPointInRect(GuiScreenUtils.mouseX, GuiScreenUtils.mouseY, sx, sy, w, h)) {
                val dir = if (scroll > 0) -1 else 1
                suggestionController.scrollWheel(dir)
                return
            }
        }
        // Island scroll
        val boxTop = editTop + 92
        val boxH = 200
        if (GuiRenderUtils.isPointInRect(GuiScreenUtils.mouseX, GuiScreenUtils.mouseY, editLeft, boxTop, editW - 4, boxH)) {
            val lineH = 16
            val totalLines = 2 + IslandType.entries.count { it.isValidIsland() }
            val maxScroll = (totalLines * lineH - boxH).coerceAtLeast(0)
            islandScroll = (islandScroll - scroll * lineH).coerceIn(0, maxScroll)
        }
    }

    override fun onKeyTyped(ch: Char?, keyCode: Int?) {
        val kc = keyCode
        val ctrl = Keyboard.KEY_LCONTROL.isKeyHeld() || Keyboard.KEY_RCONTROL.isKeyHeld()
        if (commandField == null) ensureFields()
        val field = commandField ?: return

        if (kc!=null) {
            // Combo capture base keys (non-modifiers)
            if (capturingCombo) {
                when (kc) {
                    Keyboard.KEY_ESCAPE -> {
                        capturingCombo =
                            false; capturingBaseKeys.clear(); capturingBaseKeyCodes.clear(); capturingModifiersUsed.clear(); captureHasBase =
                            false; return
                    }
                    Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> return
                    else -> {
                        if (kc !in listOf(
                                Keyboard.KEY_LCONTROL,
                                Keyboard.KEY_RCONTROL,
                                Keyboard.KEY_LSHIFT,
                                Keyboard.KEY_RSHIFT,
                                Keyboard.KEY_LMENU,
                                Keyboard.KEY_RMENU
                            )
                        ) {
                            val name = try {
                                KeyboardManager.getKeyName(kc).uppercase()
                            } catch (_: Throwable) {
                                kc.toString()
                            }
                            capturingBaseKeys += name; capturingBaseKeyCodes += kc; captureHasBase = true
                            if (Keyboard.KEY_LCONTROL.isKeyHeld() || Keyboard.KEY_RCONTROL.isKeyHeld()) capturingModifiersUsed += "CTRL"
                            if (Keyboard.KEY_LSHIFT.isKeyHeld() || Keyboard.KEY_RSHIFT.isKeyHeld()) capturingModifiersUsed += "SHIFT"
                            if (Keyboard.KEY_LMENU.isKeyHeld() || Keyboard.KEY_RMENU.isKeyHeld()) capturingModifiersUsed += "ALT"
                        } else when (kc) {
                            Keyboard.KEY_LCONTROL, Keyboard.KEY_RCONTROL -> capturingModifiersUsed += "CTRL"
                            Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT -> capturingModifiersUsed += "SHIFT"
                            Keyboard.KEY_LMENU, Keyboard.KEY_RMENU -> capturingModifiersUsed += "ALT"
                        }
                        return
                    }
                }
            }

            // Suggestion navigation
            if (suggestionController.visible && suggestionController.suggestions.isNotEmpty()) {
                when (kc) {
                    Keyboard.KEY_DOWN -> {
                        suggestionController.navigate(1); return
                    }
                    Keyboard.KEY_UP -> {
                        suggestionController.navigate(-1); return
                    }
                    Keyboard.KEY_NEXT, Keyboard.KEY_PRIOR -> {
                        pageSuggestions(kc == Keyboard.KEY_NEXT); return
                    }
                    Keyboard.KEY_TAB -> {
                        updateSuggestions(); return
                    }
                    Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> {
                        acceptSuggestion(); return
                    }
                }
            }

            if (!currentlyEditing || capturingCombo) return

            // Clipboard
            if (ctrl && kc == Keyboard.KEY_C) {
                runBlocking { field.getSelectedText()?.takeIf { it.isNotEmpty() }?.let { ClipboardUtils.copyToClipboard(it) } }; return
            }
            if (ctrl && kc == Keyboard.KEY_X) {
                runBlocking {
                    field.getSelectedText()?.takeIf { it.isNotEmpty() }?.let { ClipboardUtils.copyToClipboard(it); field.writeText("") }
                }; editCommand = field.getText(); updateSuggestions(); return
            }
            if (ctrl && kc == Keyboard.KEY_V) {
                runBlocking { ClipboardUtils.readFromClipboard()?.let { clip -> field.writeText(clip) } }; editCommand =
                    field.getText(); updateSuggestions(); return
            }
            if (ctrl && kc == Keyboard.KEY_A) {
                field.textboxKeyTyped(null, kc); return
            }
            field.textboxKeyTyped(ch, kc)
        }
        // Fallback manual insertion if unchanged and printable char
        if (!capturingCombo && ch != null && !Character.isISOControl(ch)) {
            field.writeText(ch.toString())
            editCommand = field.getText()
        }
        updateSuggestions()
    }

    private fun startAdd() {
        currentlyEditing = true
        capturingCombo = false
        editCombo = ""
        editCommand = ""
        editingAllowedIslands = IslandType.entries.filter { it.isValidIsland() }.toMutableSet().apply { add(IslandType.UNKNOWN) }
        allowOutside = false
        islandScroll = 0
        commandFieldFocused = true
        editingOriginalCombo = null
        ensureFields(); updateSuggestions()
    }

    private fun editSelected() {
        if (selectedIndex !in binds.indices) { ChatUtils.userError("No keybind selected"); return }
        val b = binds[selectedIndex]
        currentlyEditing = true
        editCombo = b.combo
        editCommand = b.command
        editingAllowedIslands = b.allowedIslands.toMutableSet()
        allowOutside = b.allowOutsideSkyBlock
        islandScroll = 0
        commandFieldFocused = true
        editingOriginalCombo = b.combo
        ensureFields(); updateSuggestions()
    }

    private fun removeSelected() {
        if (selectedIndex !in binds.indices) { ChatUtils.userError("No keybind selected"); return }
        val combo = binds[selectedIndex].combo
        manager.unregister(combo)
        ChatUtils.chat("Removed keybind $combo")
        refresh()
    }



    private fun saveEditing() {
        val e = KeybindEditor.EditorKeybind(editCombo, editCommand, editingAllowedIslands, allowOutside)
        // validate first so we don't accidentally remove the original on error
        val validation = editor.validate(e)
        if (validation.isNotEmpty()) { validation.forEach { ChatUtils.userError(it) }; return }
        // if we were editing an existing bind, unregister the original first to avoid duplicates/conflicts
        editingOriginalCombo?.let { manager.unregister(it); editingOriginalCombo = null }
        val errs = editor.save(e)
        if (errs.isEmpty()) {
            ChatUtils.chat("Saved keybind $editCombo")
            currentlyEditing = false
            suggestionController.reset()
            refresh()
        } else errs.forEach { ChatUtils.userError(it) }
    }

    private fun cancelEditing() { currentlyEditing = false; suggestionController.reset(); refresh() }
}
