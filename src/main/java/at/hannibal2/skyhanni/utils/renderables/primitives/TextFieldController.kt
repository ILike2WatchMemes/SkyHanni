package at.hannibal2.skyhanni.utils.renderables.primitives

import at.hannibal2.skyhanni.utils.compat.DrawContextUtils
import at.hannibal2.skyhanni.utils.compat.GuiScreenUtils

class TextFieldController(
    val getText: () -> String,
    val setText: (String) -> Unit,
    val getCaret: () -> Int,
    val setCaret: (Int) -> Unit,
    val getSelectionStart: () -> Int?,
    val getSelectionEnd: () -> Int?,
    val setSelection: (Int?, Int?) -> Unit,
    var width: Int = 200,
    var height: Int = 20,
    // optional lambda to control whether the caret should be shown (useful for focus-aware fields)
    val showCaret: (() -> Boolean)? = null,
) {

    private val field = TextFieldRenderable(getText, setText, getCaret, setCaret, getSelectionStart, getSelectionEnd, setSelection, width, height, showCaret = showCaret)

    fun render(globalX: Int, globalY: Int) {
        // globalX/globalY are the absolute positions where the field should render
        DrawContextUtils.pushPop {
            DrawContextUtils.translate(globalX.toFloat(), globalY.toFloat(), 0f)
            field.render(GuiScreenUtils.mouseX - globalX, GuiScreenUtils.mouseY - globalY)
        }
    }

    fun click(localX: Int, localY: Int, shiftHeld: Boolean) {
        field.click(localX, localY, shiftHeld)
    }

    fun drag(localX: Int) {
        field.drag(localX)
    }

    fun release() {
        field.releaseDrag()
    }

    fun setSize(w: Int, h: Int) {
        width = w; height = h
        // propagate size change to the inner renderable so the visual/controller state stays in sync
        try {
            field.width = w
            field.height = h
        } catch (_: Throwable) {
            // defensive: if the inner field cannot be resized for any reason, ignore to avoid crashing the UI
        }
    }

    // Additional helpers used by GUIs to interact with the controller/renderable without touching internals
    fun getText(): String = try { getText.invoke() } catch (_: Throwable) { "" }
    fun setTextValue(v: String) = try { setText.invoke(v) } catch (_: Throwable) {}

    fun getCursorPosition(): Int = try { getCaret.invoke() } catch (_: Throwable) { 0 }
    fun setCursorPosition(pos: Int) = try { setCaret.invoke(pos) } catch (_: Throwable) {}

    // Implement selected-text retrieval using the provided selection lambdas instead of relying on the renderable
    fun getSelectedText(): String? {
        return try {
            val a = try { getSelectionStart.invoke() } catch (_: Throwable) { null }
            val b = try { getSelectionEnd.invoke() } catch (_: Throwable) { null }
            val t = try { getText.invoke() } catch (_: Throwable) { "" }
            if (a == null || b == null || a == b) return null
            val start = a.coerceAtMost(b).coerceIn(0, t.length)
            val end = a.coerceAtLeast(b).coerceIn(0, t.length)
            t.substring(start, end)
        } catch (_: Throwable) { null }
    }

    // Implement writeText by mutating the text via the provided get/set lambdas so paste/insert works
    fun writeText(str: String) = try {
        val text = try { getText.invoke() } catch (_: Throwable) { "" }
        val selA = try { getSelectionStart.invoke() } catch (_: Throwable) { null }
        val selB = try { getSelectionEnd.invoke() } catch (_: Throwable) { null }
        val (next, newCaret) = if (selA != null && selB != null && selA != selB) {
            val a = selA.coerceAtMost(selB).coerceIn(0, text.length)
            val b = selA.coerceAtLeast(selB).coerceIn(0, text.length)
            Pair(text.substring(0, a) + str + text.substring(b), a + str.length)
        } else {
            val caret = try { getCaret.invoke() } catch (_: Throwable) { text.length }
            val c = caret.coerceIn(0, text.length)
            Pair(text.substring(0, c) + str + text.substring(c), c + str.length)
        }
        try { setText.invoke(next) } catch (_: Throwable) {}
        try { setSelection.invoke(null, null) } catch (_: Throwable) {}
        try { setCaret.invoke(newCaret) } catch (_: Throwable) {}
    } catch (_: Throwable) {}

    fun textboxKeyTyped(typedChar: Char?, keyCode: Int) = try { field.textboxKeyTyped(typedChar, keyCode) } catch (_: Throwable) {}
}
