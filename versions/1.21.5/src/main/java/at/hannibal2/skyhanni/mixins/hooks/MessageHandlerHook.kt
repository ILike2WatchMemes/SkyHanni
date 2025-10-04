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
                    val task = queue.take()
                    process(task)
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
    private fun process(task: Task) {
        val client = MinecraftClient.getInstance()

        if (task.actionBar) {
            val transformed = ActionBarData.onChatReceive(task.message)
            runOnMcThread(client) {
                task.original.accept(transformed ?: task.message, task.actionBar)
            }
            return
        }

        val (result, cancel) = ChatManager.onChatReceive(task.message)

        when {
            result != null -> runOnMcThread(client) {
                task.original.accept(result, task.actionBar)
            }
            cancel -> runOnMcThread(client) {
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
            else -> runOnMcThread(client) {
                task.original.accept(task.message, task.actionBar)
            }
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
