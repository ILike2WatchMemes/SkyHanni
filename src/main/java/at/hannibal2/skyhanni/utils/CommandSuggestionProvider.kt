package at.hannibal2.skyhanni.utils

import at.hannibal2.skyhanni.config.commands.CommandsRegistry
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.suggestion.Suggestions
import net.minecraft.client.Minecraft
import java.util.concurrent.CompletableFuture

/**
 * Provides merged Brigadier command suggestions from both the client-side (SkyHanni / SHS) dispatcher
 * and the current server dispatcher (if available). Accepts a raw input line and a cursor position inside it.
 *
 * Behaviors:
 *  - Leading slash preserved only for first token suggestions.
 *  - Cursor-aware: parses only substring up to cursor first; falls back to full line parse if needed.
 *  - Merges client + server suggestions in insertion order, removing duplicates.
 */
object CommandSuggestionProvider {

    fun suggest(input: String, cursor: Int = input.length): List<String> {
        if (input.isBlank()) return emptyList()
        val mcPlayer = try { Minecraft.getMinecraft().thePlayer } catch (_: Throwable) { null } ?: return emptyList()
        val client: CommandDispatcher<Any> = try { CommandsRegistry.getDispatcher() as CommandDispatcher<Any> } catch (_: Throwable) { return emptyList() }
        val server: CommandDispatcher<Any>? = try { CommandsRegistry.mcServerDispatcher() } catch (_: Throwable) { null }

        val effectiveCursor = cursor.coerceIn(0, input.length)
        val leadingSlash = input.startsWith('/')
        val rawFull = if (leadingSlash) input.drop(1) else input
        val rawCursor = rawFull.take((if (leadingSlash) effectiveCursor - 1 else effectiveCursor).coerceAtLeast(0).coerceAtMost(rawFull.length))

        fun collect(dispatcher: CommandDispatcher<Any>, partial: String): CompletableFuture<Suggestions> = try {
            val parse: ParseResults<Any> = dispatcher.parse(partial, mcPlayer)
            dispatcher.getCompletionSuggestions(parse)
        } catch (_: Throwable) { Suggestions.empty() }

        fun applyLeadingSlash(list: Collection<String>): List<String> {
            if (!leadingSlash) return list.toList()
            val firstTokenPhase = !rawCursor.contains(' ')
            return list.map { if (firstTokenPhase && !it.startsWith('/')) "/$it" else it }
        }

        val clientFuture = collect(client, rawCursor)
        val serverFuture = server?.let { collect(it, rawCursor) }
        val primaryMerged = LinkedHashSet<String>().apply {
            runCatching { clientFuture.get().list }.getOrElse { emptyList() }.forEach { add(it.text) }
            runCatching { serverFuture?.get()?.list ?: emptyList() }.getOrElse { emptyList() }.forEach { add(it.text) }
        }
        if (primaryMerged.isNotEmpty()) return applyLeadingSlash(primaryMerged)

        val clientFull = collect(client, rawFull)
        val serverFull = server?.let { collect(it, rawFull) }
        val fallbackMerged = LinkedHashSet<String>().apply {
            runCatching { clientFull.get().list }.getOrElse { emptyList() }.forEach { add(it.text) }
            runCatching { serverFull?.get()?.list ?: emptyList() }.getOrElse { emptyList() }.forEach { add(it.text) }
        }
        return applyLeadingSlash(fallbackMerged)
    }
}
