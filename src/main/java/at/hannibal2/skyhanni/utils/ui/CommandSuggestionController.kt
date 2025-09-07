package at.hannibal2.skyhanni.utils.ui

import kotlin.math.max
import kotlin.math.min

/**
 * Reusable lightweight controller for Brigadier-style command suggestions.
 * Handles:
 *  - Query (delegated to supplied provider)
 *  - Index + scroll management
 *  - Page navigation
 *  - Token replacement on accept (space delimited like vanilla chat)
 */
class CommandSuggestionController(
    private val maxVisible: Int = 12,
    private val provider: (text: String, cursor: Int) -> List<String>,
) {
    var suggestions: List<String> = emptyList(); private set
    var index: Int = -1; private set
    var scroll: Int = 0; private set
    var visible: Boolean = false; private set

    fun reset() {
        suggestions = emptyList(); index = -1; scroll = 0; visible = false
    }

    fun update(text: String, cursor: Int, enabled: Boolean = true) {
        if (!enabled) { reset(); return }
        val list = try { provider(text, cursor) } catch (_: Throwable) { emptyList() }
        suggestions = list.distinct().filter { it.isNotBlank() }
        index = if (suggestions.isNotEmpty()) 0 else -1
        scroll = 0
        visible = suggestions.isNotEmpty()
    }

    fun navigate(delta: Int) {
        if (!visible || suggestions.isEmpty()) return
        index = (index + delta).coerceIn(0, suggestions.lastIndex)
        ensureIndexVisible()
    }

    fun page(next: Boolean) {
        if (!visible || suggestions.isEmpty()) return
        val step = if (next) maxVisible else -maxVisible
        index = (index + step).coerceIn(0, suggestions.lastIndex)
        ensureIndexVisible()
    }

    // Allow external selection (e.g. mouse click) without exposing setter
    fun select(i: Int) {
        if (!visible || suggestions.isEmpty()) return
        index = i.coerceIn(0, suggestions.lastIndex)
        ensureIndexVisible()
    }

    fun scrollWheel(dir: Int) { // dir: +1 down, -1 up (already normalized by caller)
        navigate(dir)
    }

    private fun ensureIndexVisible() {
        if (index < scroll) scroll = index
        if (index >= scroll + maxVisible) scroll = index - maxVisible + 1
        scroll = scroll.coerceIn(0, max(0, suggestions.size - maxVisible))
    }

    fun visibleSlice(): List<String> = if (!visible) emptyList() else suggestions.drop(scroll).take(maxVisible)

    /**
     * Accept current suggestion replacing the token at cursor.
     * Returns the mutated string + new cursor in a pair, or null if nothing done.
     */
    fun accept(text: String, cursor: Int): Pair<String, Int>? {
        if (index !in suggestions.indices) return null
        val chosen = suggestions[index]
        val safeCursor = cursor.coerceIn(0, text.length)
        val leftBoundary = text.substring(0, safeCursor).lastIndexOf(' ').let { if (it < 0) 0 else it + 1 }
        val rightIdxLocal = text.substring(safeCursor).indexOf(' ')
        val rightBoundary = if (rightIdxLocal < 0) text.length else safeCursor + rightIdxLocal
        val newText = text.substring(0, leftBoundary) + chosen + text.substring(rightBoundary)
        val newCursor = leftBoundary + chosen.length
        visible = false
        return Pair(newText, newCursor)
    }
}
