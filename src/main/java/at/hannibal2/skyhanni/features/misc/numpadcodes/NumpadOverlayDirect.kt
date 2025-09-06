package at.hannibal2.skyhanni.features.misc.numpadcodes

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.renderables.primitives.text

@SkyHanniModule
object NumpadOverlayDirect {
    @HandleEvent
    fun onRenderOverlay(@Suppress("UNUSED_PARAMETER") event: GuiRenderEvent.GuiOverlayRenderEvent) {
        try {
            val cfg = SkyHanniMod.feature.numpad
            val state = NumpadCodes.getLastOverlayState() ?: run {
                return
            }
            val suggestions = state.suggestions
            // nothing to show
            if (!cfg.showLabel && state.currentInput.isEmpty() && (!cfg.showSuggestions || suggestions.isEmpty())) return

            val maxItems = 8
            val shown = suggestions.take(maxItems)

            val lines = mutableListOf<Renderable>()

            // optional overlay label/comment
            if (cfg.showLabel && cfg.overlayLabel.isNotBlank()) {
                lines.add(Renderable.text(cfg.overlayLabel))
            }

            // current input line
            if (state.currentInput.isNotEmpty()) {
                val base = if (cfg.showPrefix) "Numpad: ${state.currentInput}" else state.currentInput
                val withRemain = if (state.currentRemainingPresses != null && state.currentRemainingPresses > 0) {
                    "$base (${state.currentRemainingPresses})"
                } else base
                lines.add(Renderable.text(state.currentMcColorCode + withRemain))
            }

            if (cfg.showSuggestions) {
                for (s in shown) {
                    val rem = if (s.remainingPresses != null) " (${s.remainingPresses})" else ""
                    lines.add(Renderable.text(s.mcColorCode + s.code + rem))
                }
            }

            // Render via the configured Position so GuiEditManager picks it up and it can be moved
            SkyHanniMod.feature.numpad.pos.renderRenderables(lines, posLabel = "Numpad Overlay")
        } catch (e: Throwable) {
            ChatUtils.debug("[NumpadOverlayDirect] render error: ${e.message}")
        }
    }
}
