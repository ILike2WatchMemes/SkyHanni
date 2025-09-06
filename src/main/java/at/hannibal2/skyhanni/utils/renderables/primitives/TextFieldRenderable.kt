package at.hannibal2.skyhanni.utils.renderables.primitives

import at.hannibal2.skyhanni.utils.GuiRenderUtils
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld
import at.hannibal2.skyhanni.utils.compat.DrawContextUtils
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.RenderUtils.HorizontalAlignment
import at.hannibal2.skyhanni.utils.RenderUtils.VerticalAlignment
import net.minecraft.client.Minecraft
import java.awt.Color
import org.lwjgl.input.Keyboard

/**
 * Hardened reusable text field renderable.
 * - Supports horizontal scrolling when text exceeds width
 * - Mouse click + drag selection
 * - Caret visibility maintenance
 * - Selection drawing
 */
class TextFieldRenderable(
    private val getText: () -> String,
    private val setText: (String) -> Unit,
    private val getCaret: () -> Int,
    private val setCaret: (Int) -> Unit,
    private val getSelectionStart: () -> Int?,
    private val getSelectionEnd: () -> Int?,
    private val setSelection: (Int?, Int?) -> Unit,
    override var width: Int = 200,
    override var height: Int = 20,
    private val bgColor: Int = 0x20202020,
    private val caretColor: Int = 0xFFFFFFFF.toInt(),
    private val selectionColor: Int = 0x80FFFFFF.toInt(),
    private val textColor: Int = Color.WHITE.rgb,
    private val showCaret: (() -> Boolean)? = null,
) : Renderable {

    override val horizontalAlign = HorizontalAlignment.LEFT
    override val verticalAlign = VerticalAlignment.CENTER

    private val padding = 4
    private var scrollOffset = 0

    // mouse drag state
    private var dragging = false

    // simple multi-click detection
    private var lastClickTime = 0L
    private var clickCount = 0

    override fun render(mouseOffsetX: Int, mouseOffsetY: Int) {
        try {
            // background
            GuiRenderUtils.drawRect(0, 0, width, height, bgColor)

            val text = getText()
            val fr = try { Minecraft.getMinecraft().fontRendererObj } catch (_: Throwable) { null }

            // ensure scroll offset within bounds
            val totalTextWidth = if (fr != null) fr.getStringWidth(text) else 0
            val available = (width - padding * 2).coerceAtLeast(1)
            val maxScroll = (totalTextWidth - available).coerceAtLeast(0)
            scrollOffset = scrollOffset.coerceIn(0, maxScroll)

            // draw selection if present
            val selStart = getSelectionStart()
            val selEnd = getSelectionEnd()
            if (selStart != null && selEnd != null && selStart != selEnd) {
                val a = selStart.coerceAtMost(selEnd).coerceIn(0, text.length)
                val b = selStart.coerceAtLeast(selEnd).coerceIn(0, text.length)
                val leftX = computeCaretX(text, a).coerceAtLeast(0) - scrollOffset
                val rightX = computeCaretX(text, b).coerceAtLeast(0) - scrollOffset
                GuiRenderUtils.drawRect(padding + leftX, 4, padding + rightX, height - 4, selectionColor)
            }

            // draw text with scroll offset
            DrawContextUtils.pushPop {
                DrawContextUtils.translate((padding - scrollOffset).toFloat(), ((height - 9) / 2).toFloat(), 0f)
                try {
                    GuiRenderUtils.drawString(text, 0, 0, textColor)
                } catch (_: Throwable) {
                    // if drawing fails, fall back to empty draw
                }
            }

            // caret blinking
            val caret = getCaret().coerceIn(0, text.length)
            val visible = ((System.currentTimeMillis() / 500) % 2) == 0L
            val shouldShow = (showCaret?.invoke() ?: true)
            if (visible && shouldShow) {
                val caretX = computeCaretX(text, caret) - scrollOffset
                val cx = padding + caretX
                GuiRenderUtils.drawRect(cx, 4, cx + 1, height - 4, caretColor)
            }
        } catch (_: Throwable) {
            // Defensive: prevent any rendering errors from crashing the whole GUI.
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun click(localX: Int, _localY: Int, shiftHeld: Boolean = false, doubleClickThresholdMs: Long = 300): Boolean {
        try {
            // reference localY to avoid unused-parameter warnings (vertical position reserved for future use)
            val now = System.currentTimeMillis()
            val text = getText()
            val index = computeIndexFromLocalX(localX)
            val safeIndex = index.coerceIn(0, text.length)

            // update multi-click count
            if (now - lastClickTime <= doubleClickThresholdMs) {
                clickCount += 1
            } else {
                clickCount = 1
            }
            lastClickTime = now

            val ctrlHeld = try { Keyboard.KEY_LCONTROL.isKeyHeld() || Keyboard.KEY_RCONTROL.isKeyHeld() } catch (_: Throwable) { false }

            // helper to determine word boundaries at a caret position
            fun isWordChar(ch: Char): Boolean = Character.isLetterOrDigit(ch) || ch == '_'
            fun findWordStartLeft(caretPos: Int): Int {
                if (caretPos <= 0) return 0
                var j = caretPos - 1
                while (j >= 0 && !isWordChar(text[j])) j--
                while (j >= 0 && isWordChar(text[j])) j--
                return (j + 1).coerceAtLeast(0)
            }
            fun findWordEndRight(caretPos: Int): Int {
                val len = text.length
                if (caretPos >= len) return len
                var j = caretPos
                while (j < len && isWordChar(text[j])) j++
                while (j < len && !isWordChar(text[j])) j++
                return j.coerceAtMost(len)
            }

            // triple click -> select all
            if (clickCount >= 3) {
                if (shiftHeld) {
                    val prev = getSelectionStart() ?: getCaret()
                    setSelection(prev, text.length)
                } else {
                    setSelection(0, text.length)
                }
                setCaret(text.length)
                dragging = true
                ensureCaretVisible()
                return true
            }

            // double click -> select word under cursor
            if (clickCount == 2) {
                val wordStart = findWordStartLeft(safeIndex)
                val wordEnd = findWordEndRight(safeIndex)
                if (shiftHeld) {
                    val anchor = getSelectionStart() ?: getCaret()
                    setSelection(anchor, wordEnd)
                } else {
                    setSelection(wordStart, wordEnd)
                }
                setCaret(wordEnd)
                dragging = true
                ensureCaretVisible()
                return true
            }

            // single click behaviors
            if (shiftHeld) {
                val prev = getCaret().coerceIn(0, text.length)
                setCaret(safeIndex)
                setSelection(prev, safeIndex)
            } else if (ctrlHeld) {
                // ctrl+click: jump to nearest word boundary (start of next/previous word depending on click position)
                val caret = getCaret().coerceIn(0, text.length)
                val newCaret = if (safeIndex <= caret) findWordStartLeft(safeIndex) else findWordEndRight(safeIndex)
                setCaret(newCaret)
                setSelection(null, null)
            } else {
                setCaret(safeIndex)
                setSelection(null, null)
            }

            // start dragging so subsequent drag calls update selection
            dragging = true
            ensureCaretVisible()
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    fun drag(localX: Int) {
        try {
            if (!dragging) return
            val text = getText()
            val index = computeIndexFromLocalX(localX).coerceIn(0, text.length)
            val anchor = getSelectionStart() ?: getCaret()
            setCaret(index)
            setSelection(anchor, index)
            ensureCaretVisible()
        } catch (_: Throwable) {
            // ignore
        }
    }

    fun releaseDrag() {
        dragging = false
    }

    private fun computeIndexFromLocalX(localX: Int): Int {
        val text = getText()
        val fr = try { Minecraft.getMinecraft().fontRendererObj } catch (_: Throwable) { null }
        if (fr == null || text.isEmpty()) return 0
        val x = (localX - padding + scrollOffset).coerceAtLeast(0)
        // iterate over characters computing width of prefix to be robust with variable-width glyphs
        var i = 0
        while (i <= text.length) {
            val substr = if (i <= 0) "" else text.substring(0, i)
            val w = try { fr.getStringWidth(substr) } catch (_: Throwable) { 0 }
            if (w >= x) return i.coerceAtLeast(0)
            i++
        }
        return text.length
    }

    private fun computeCaretX(text: String, caret: Int): Int {
        val fr = try { Minecraft.getMinecraft().fontRendererObj } catch (_: Throwable) { null }
        if (fr == null) return 0
        val safeCaret = caret.coerceIn(0, text.length)
        val substr = if (safeCaret <= 0) "" else text.substring(0, safeCaret)
        return try { fr.getStringWidth(substr) } catch (_: Throwable) { 0 }
    }

    private fun ensureCaretVisible() {
        try {
            val text = getText()
            val fr = try { Minecraft.getMinecraft().fontRendererObj } catch (_: Throwable) { null }
            val caret = getCaret().coerceIn(0, text.length)
            val caretX = computeCaretX(text, caret)
            val available = (width - padding * 2).coerceAtLeast(1)
            val visibleX = caretX - scrollOffset
            val margin = 6
            if (visibleX < margin) {
                scrollOffset = (caretX - margin).coerceAtLeast(0)
            } else if (visibleX > available - margin) {
                val total = fr?.getStringWidth(text) ?: 0
                scrollOffset = (caretX - (available - margin)).coerceAtMost((total - available).coerceAtLeast(0))
            }
            // clamp scrollOffset to safe bounds
            val totalTextWidth = fr?.getStringWidth(text) ?: 0
            val maxScroll = (totalTextWidth - available).coerceAtLeast(0)
            scrollOffset = scrollOffset.coerceIn(0, maxScroll)
        } catch (_: Throwable) {
            // ignore ensure failures
        }
    }

    /**
     * Basic keyboard handling so callers can forward key events.
     * Supports character insertion, backspace, left/right arrow movement and simple selection clearing.
     */
    fun textboxKeyTyped(typedChar: Char?, keyCode: Int) {
        try {
            val text = getText()
            val selA = getSelectionStart()
            val selB = getSelectionEnd()

            val ctrlHeld = Keyboard.KEY_LCONTROL.isKeyHeld() || Keyboard.KEY_RCONTROL.isKeyHeld()
            val shiftHeld = Keyboard.KEY_LSHIFT.isKeyHeld() || Keyboard.KEY_RSHIFT.isKeyHeld()

            fun isWordChar(ch: Char): Boolean = Character.isLetterOrDigit(ch) || ch == '_'

            fun findWordStartLeft(caretPos: Int): Int {
                if (caretPos <= 0) return 0
                var j = caretPos - 1
                while (j >= 0 && !isWordChar(text[j])) j--
                while (j >= 0 && isWordChar(text[j])) j--
                return (j + 1).coerceAtLeast(0)
            }

            fun findWordStartRight(caretPos: Int): Int {
                val len = text.length
                if (caretPos >= len) return len
                var j = caretPos
                while (j < len && isWordChar(text[j])) j++
                while (j < len && !isWordChar(text[j])) j++
                return j.coerceAtMost(len)
            }

            // Backspace (with Ctrl -> delete previous word)
            if (keyCode == Keyboard.KEY_BACK) {
                if (selA != null && selB != null && selA != selB) {
                    val a = selA.coerceAtMost(selB)
                    val b = selA.coerceAtLeast(selB)
                    setText(text.removeRange(a, b))
                    setCaret(a)
                    setSelection(null, null)
                } else {
                    val caret = getCaret().coerceIn(0, text.length)
                    if (caret > 0) {
                        if (ctrlHeld) {
                            val newCaret = findWordStartLeft(caret)
                            setText(text.removeRange(newCaret, caret))
                            setCaret(newCaret)
                        } else {
                            setText(text.removeRange(caret - 1, caret))
                            setCaret(caret - 1)
                        }
                    }
                }
                ensureCaretVisible()
                return
            }

            // Delete key (Entf): delete selection or character after caret; Ctrl+Delete deletes next word
            if (keyCode == Keyboard.KEY_DELETE) {
                if (selA != null && selB != null && selA != selB) {
                    val a = selA.coerceAtMost(selB)
                    val b = selA.coerceAtLeast(selB)
                    setText(text.removeRange(a, b))
                    setCaret(a)
                    setSelection(null, null)
                } else {
                    val caret = getCaret().coerceIn(0, text.length)
                    if (caret < text.length) {
                        if (ctrlHeld) {
                            val newCaret = findWordStartRight(caret)
                            setText(text.removeRange(caret, newCaret))
                            setCaret(caret)
                        } else {
                            setText(text.removeRange(caret, caret + 1))
                            setCaret(caret)
                        }
                    }
                }
                ensureCaretVisible()
                return
            }

            // Ctrl+A -> select all (also supported by GUI but handle here for direct field focus)
            if (keyCode == Keyboard.KEY_A && ctrlHeld) {
                setSelection(0, text.length)
                setCaret(text.length)
                ensureCaretVisible()
                return
            }

            // Left / Right / Home / End navigation with optional Shift selection and Ctrl word-jump
            if (keyCode == Keyboard.KEY_LEFT) {
                val caret = getCaret().coerceIn(0, text.length)
                val newCaret = if (ctrlHeld) findWordStartLeft(caret) else (caret - 1).coerceAtLeast(0)
                if (shiftHeld) {
                    val anchor = getSelectionStart() ?: caret
                    setCaret(newCaret); setSelection(anchor, newCaret)
                } else {
                    setCaret(newCaret); setSelection(null, null)
                }
                ensureCaretVisible()
                return
            }

            if (keyCode == Keyboard.KEY_RIGHT) {
                val caret = getCaret().coerceIn(0, text.length)
                val newCaret = if (ctrlHeld) findWordStartRight(caret) else (caret + 1).coerceAtMost(text.length)
                if (shiftHeld) {
                    val anchor = getSelectionStart() ?: caret
                    setCaret(newCaret); setSelection(anchor, newCaret)
                } else {
                    setCaret(newCaret); setSelection(null, null)
                }
                ensureCaretVisible()
                return
            }

            if (keyCode == Keyboard.KEY_HOME) {
                val newCaret = 0
                if (shiftHeld) {
                    val anchor = getSelectionStart() ?: getCaret()
                    setCaret(newCaret); setSelection(anchor, newCaret)
                } else {
                    setCaret(newCaret); setSelection(null, null)
                }
                ensureCaretVisible()
                return
            }

            if (keyCode == Keyboard.KEY_END) {
                val newCaret = text.length
                if (shiftHeld) {
                    val anchor = getSelectionStart() ?: getCaret()
                    setCaret(newCaret); setSelection(anchor, newCaret)
                } else {
                    setCaret(newCaret); setSelection(null, null)
                }
                ensureCaretVisible()
                return
            }

            // Printable character insertion
            if (typedChar != null && !Character.isISOControl(typedChar)) {
                if (selA != null && selB != null && selA != selB) {
                    val a = selA.coerceAtMost(selB)
                    val b = selA.coerceAtLeast(selB)
                    val next = text.substring(0, a) + typedChar + text.substring(b)
                    setText(next)
                    setCaret(a + 1)
                    setSelection(null, null)
                } else {
                    val caret = getCaret().coerceIn(0, text.length)
                    val next = text.substring(0, caret) + typedChar + text.substring(caret)
                    setText(next)
                    setCaret(caret + 1)
                }
                ensureCaretVisible()
                return
            }
        } catch (_: Throwable) {
            // ignore
        }
    }

}
