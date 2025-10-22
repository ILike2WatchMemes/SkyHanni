package at.hannibal2.skyhanni.mixins.hooks

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.data.ActionBarData
import at.hannibal2.skyhanni.data.ChatManager
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.hud.ChatHudLine
import net.minecraft.client.gui.hud.MessageIndicator
import net.minecraft.text.Text
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer

// Permanent single-consumer worker to guarantee FIFO ordering without timeouts
private object ChatMessageProcessor {
    private data class Task(
        val message: Text,
        val actionBar: Boolean,
        val original: BiConsumer<Text, Boolean>
    )

    private val started = AtomicBoolean(false)
    private val queue = LinkedBlockingQueue<Task>()

    private fun ensureStarted() {
        if (started.compareAndSet(false, true)) {
            val t = Thread({
                while (!Thread.currentThread().isInterrupted) {
                    // Take one task (blocks until at least one is available), then drain any additional ready tasks
                    val first = queue.take()
                    processBatch(first)
                }
            }, "SkyHanni-ChatProcessor")
            t.isDaemon = true
            t.start()
        }
    }

    fun enqueue(message: Text, actionBar: Boolean, original: BiConsumer<Text, Boolean>) {
        ensureStarted()
        queue.put(Task(message, actionBar, original))
    }

    // Do non-UI work off-thread, marshal UI calls to the MC thread in-order
    private fun processBatch(first: Task) {
        val client = MinecraftClient.getInstance()

        // Collect a batch: include the first already-taken task and drain the rest that's currently available
        val batch = ArrayList<Task>()
        batch.add(first)
        queue.drainTo(batch)

        // Prepare UI actions while off the main thread so we can run them in one MC-thread invocation
        val uiActions = ArrayList<() -> Unit>(batch.size)

        for (task in batch) {
            if (task.actionBar) {
                val transformed = ActionBarData.onChatReceive(task.message)
                uiActions.add { task.original.accept(transformed ?: task.message, task.actionBar) }
                continue
            }

            val (result, cancel) = ChatManager.onChatReceive(task.message)

            when {
                result != null -> {
                    // Keep previous behavior for non-blocking path: deliver modified message to the original consumer
                    uiActions.add { task.original.accept(result, task.actionBar) }
                }
                cancel -> {
                    uiActions.add {
                        val inGameHud = client.inGameHud
                        val chatHudLine = ChatHudLine(inGameHud.ticks, task.message, null, MessageIndicator.system())
                        // We want to still log the message even if we cancel it
                        inGameHud.chatHud.logChatMessage(chatHudLine)

                        // We also want to send the fabric canceled chat message event just to be nice
                        ClientReceiveMessageEvents.ALLOW_GAME.invoker()
                            .allowReceiveGameMessage(task.message, task.actionBar)
                        ClientReceiveMessageEvents.GAME_CANCELED.invoker()
                            .onReceiveGameMessageCanceled(task.message, task.actionBar)
                    }
                }
                else -> uiActions.add { task.original.accept(task.message, task.actionBar) }
            }
        }

        // Run all UI actions in a single MC-thread execution to ensure the render thread applies all ready lines at once
        runOnMcThread(client) {
            for (ui in uiActions) ui()
        }
    }

    private inline fun runOnMcThread(client: MinecraftClient, crossinline block: () -> Unit) {
        // If already on the main thread, run directly; otherwise submit and wait to keep ordering
        if (client.isOnThread) {
            block()
        } else {
            client.submit { block() }.join()
        }
    }
}

fun onGameMessage(message: Text, actionBar: Boolean, original: Operation<Void>) {
    val op = BiConsumer<Text, Boolean> { msg, ab -> original.call(msg, ab) }
    if (SkyHanniMod.feature.dev.nonBlockingMessageProcessing) {
        ChatMessageProcessor.enqueue(message, actionBar, op)
    } else {
        handleOnGameMessage(message, actionBar, op)
    }
}

private fun handleOnGameMessage(message: Text, actionBar: Boolean, original: BiConsumer<Text, Boolean>) {
    if (actionBar) {
        ActionBarData.onChatReceive(message)?.let { result ->
            original.accept(result, actionBar)
            return
        }
        original.accept(message, actionBar)
        return
    }
    val (result, cancel) = ChatManager.onChatReceive(message)
    result?.let {
        // Will send both the unmodified and modified message into the Fabric Pipeline so other mods also get the old unmodified message
        // This sadly isn't preventable without switching fully to Fabric Chat Events
        // (which needs an event for cancelling and an event for modifying, which isn't a feasible split up with this code base size)
        ClientReceiveMessageEvents.ALLOW_GAME.invoker().allowReceiveGameMessage(message, actionBar)
        original.accept(it, actionBar)
        return
    }
    if (cancel) {
        // We want to still log the message even if we cancel it
        val inGameHud = MinecraftClient.getInstance().inGameHud
        val chatHudLine = ChatHudLine(inGameHud.ticks, message, null, MessageIndicator.system())
        inGameHud.chatHud.logChatMessage(chatHudLine)

        // We also want to send the fabric canceled chat message event just to be nice
        ClientReceiveMessageEvents.ALLOW_GAME.invoker().allowReceiveGameMessage(message, actionBar)
        ClientReceiveMessageEvents.GAME_CANCELED.invoker().onReceiveGameMessageCanceled(message, actionBar)
        return
    }
    original.accept(message, actionBar)
}
