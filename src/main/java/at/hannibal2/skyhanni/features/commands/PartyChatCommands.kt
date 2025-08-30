package at.hannibal2.skyhanni.features.commands

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.config.commands.CommandCategory
import at.hannibal2.skyhanni.config.commands.CommandRegistrationEvent
import at.hannibal2.skyhanni.config.commands.brigadier.BrigadierArguments
import at.hannibal2.skyhanni.config.features.misc.PartyCommandsConfig
import at.hannibal2.skyhanni.config.features.misc.PermissionLevel
import at.hannibal2.skyhanni.data.PartyApi
import at.hannibal2.skyhanni.data.hypixel.chat.event.PartyChatEvent
import at.hannibal2.skyhanni.events.chat.TabCompletionEvent
import at.hannibal2.skyhanni.features.misc.CurrentPing
import at.hannibal2.skyhanni.features.misc.TpsCounter
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.ConfigUtils.jumpToEditor
import at.hannibal2.skyhanni.utils.HypixelCommands
import at.hannibal2.skyhanni.utils.PlayerUtils
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object PartyChatCommands {
    private val config get() = SkyHanniMod.feature.misc.partyCommands
    private val storage get() = SkyHanniMod.feature.storage
    private val devConfig get() = SkyHanniMod.feature.dev

    data class PartyChatCommand(
        val names: List<String>,
        val canUse: (PartyCommandsConfig.TrustUserConfig) -> PermissionLevel,
        val requiresPartyLead: Boolean = true,
        val triggerableBySelf: Boolean = true,
        val executable: (PartyChatEvent) -> Unit,
        val offCooldown: () -> Boolean = { true },
    )

    private var lastWarp = SimpleTimeMark.farPast()
    private var lastAllInvite = SimpleTimeMark.farPast()

    private val allPartyCommands = listOf(
        PartyChatCommand(
            listOf("pt", "ptme", "transfer"),
            { it.effectiveTransferLeader },
            triggerableBySelf = false,
            executable = {
                PartyApi.partyTransfer(it.cleanedAuthor)
            },
        ),
        PartyChatCommand(
            listOf("pw", "warp", "warpus"),
            { it.effectiveWarp },
            executable = {
                lastWarp = SimpleTimeMark.now()
                PartyApi.warp()
            },
            offCooldown = { lastWarp.passedSince() > 5.seconds },
        ),
        PartyChatCommand(
            listOf("allinv", "allinvite"),
            { it.effectiveEnableAllInvite },
            executable = {
                lastAllInvite = SimpleTimeMark.now()
                PartyApi.allInvite()
            },
            offCooldown = { lastAllInvite.passedSince() > 2.seconds },
        ),
        PartyChatCommand(
            listOf("ping"),
            { if (config.pingCommand) PermissionLevel.INSTANT else PermissionLevel.NEVER },
            requiresPartyLead = false,
            executable = {
                if (!CurrentPing.isEnabled()) {
                    ChatUtils.clickableChat(
                        "Ping API is disabled, the ping command won't work!",
                        prefixColor = "§c",
                        onClick = {
                            devConfig::pingApi.jumpToEditor()
                        },
                        hover = "§eClick to find setting in the config!",
                    )
                    return@PartyChatCommand
                }
                HypixelCommands.partyChat(CurrentPing.getFormattedPing(), prefix = true)

            },
        ),
        PartyChatCommand(
            listOf("tps"),
            { if (config.tpsCommand) PermissionLevel.INSTANT else PermissionLevel.NEVER },
            requiresPartyLead = false,
            executable = {
                if (TpsCounter.tps != null) {
                    HypixelCommands.partyChat("Current TPS: ${TpsCounter.tps}", prefix = true)
                } else {
                    ChatUtils.chat("TPS Command Sent too early to calculate TPS")
                }
            },
        ),
    )

    private val indexedPartyChatCommands = buildMap {
        for (command in allPartyCommands) {
            for (name in command.names) {
                put(name.lowercase(), command)
            }
        }
    }

    private fun getUserConfig(name: String): PartyCommandsConfig.TrustUserConfig {
        return config.users.get(name) ?: config.TrustUserConfig(name)
    }

    private val commandPrefixes = ".!?".toSet()

    private fun isBlockedUser(name: String): Boolean {
        return storage.blacklistedUsers.any { it.equals(name, ignoreCase = true) }
    }

    @HandleEvent
    fun onPartyCommand(event: PartyChatEvent) {
        if (event.message.firstOrNull() !in commandPrefixes) return
        val commandLabel = event.message.substring(1).substringBefore(' ')
        val command = indexedPartyChatCommands[commandLabel.lowercase()] ?: return
        val name = event.cleanedAuthor
        if (name == PlayerUtils.getName() && (!command.triggerableBySelf)) return
        if (command.requiresPartyLead && PartyApi.isPartyLeader()) return
        if (isBlockedUser(name)) {
            if (config.showIgnoredReminder) ChatUtils.clickableChat(
                "§cIgnoring chat command from ${event.author}. " +
                    "Stop ignoring them using /shignore remove <player> or click here!",
                onClick = { blacklistModify(event.author) },
                "§eClick to ignore ${event.author}!",
            )
            return
        }
        if (!command.offCooldown.invoke()) return
        val level = command.canUse(getUserConfig(name))
        if (level == PermissionLevel.NEVER) {
            if (config.showIgnoredReminder) {
                ChatUtils.chat(
                    "§cIgnoring chat command from $name. " +
                        "Change your party chat command settings or /friend (best) them.",
                )
            }
            return
        } else if (level == PermissionLevel.INSTANT) {
            command.executable.invoke(event)
        } else if (level == PermissionLevel.ASK) {
            // TODO ChatPrompt
        }
    }

    @HandleEvent
    fun onTabComplete(event: TabCompletionEvent) {
        if (PartyApi.partyLeader == null) return
        val prefix = event.fullText.firstOrNull() ?: return
        if (prefix !in commandPrefixes) return

        val commandText = event.fullText.substring(1)
        indexedPartyChatCommands.keys
            .filter { it.startsWith(commandText) }
            .map { "$prefix$it" }
            .forEach(event::addSuggestion)
    }

    @HandleEvent
    fun onCommandRegister(event: CommandRegistrationEvent) {
        event.registerBrigadier("shignore") {
            description = "Add/Remove a user from your blacklist"
            category = CommandCategory.USERS_ACTIVE

            literal("add") {
                arg("name", BrigadierArguments.string()) { nameArg ->
                    callback {
                        val name = getArg(nameArg)
                        if (isBlockedUser(name)) {
                            ChatUtils.userError("$name is already ignored!")
                        } else blacklistModify(name)
                    }
                }
            }

            literal("remove") {
                arg("name", BrigadierArguments.string()) { nameArg ->
                    callback {
                        val name = getArg(nameArg)
                        if (!isBlockedUser(name)) {
                            ChatUtils.userError("$name isn't ignored!")
                        } else blacklistModify(name)
                    }
                }
            }
            literal("list") {
                argCallback("name", BrigadierArguments.string()) { name ->
                    blacklistView(name)
                }
                callback {
                    blacklistView()
                }
            }
            literalCallback("clear") {
                ChatUtils.clickableChat(
                    "Are you sure you want to do this? Click here to confirm.",
                    onClick = {
                        storage.blacklistedUsers.clear()
                        ChatUtils.chat("Cleared your ignored players list!")
                    },
                    "§eClick to confirm.",
                    oneTimeClick = true,
                )
            }
            argCallback("name", BrigadierArguments.string()) { name ->
                blacklistModify(name)
            }
        }
    }

    private fun blacklistModify(player: String) {
        if (player !in storage.blacklistedUsers) {
            ChatUtils.chat("§cNow ignoring §b$player§e!")
            storage.blacklistedUsers.add(player)
            return
        }
        ChatUtils.chat("§aStopped ignoring §b$player§e!")
        storage.blacklistedUsers.remove(player)
        return
    }

    private fun blacklistView() {
        val blacklist = storage.blacklistedUsers
        if (blacklist.size <= 0) {
            ChatUtils.chat("Your ignored players list is empty!")
            return
        }
        var message = "Ignored player list:"
        if (blacklist.size > 15) {
            message += "\n§e"
            blacklist.forEachIndexed { i, blacklistedMessage ->
                message += blacklistedMessage
                if (i < blacklist.size - 1) {
                    message += ", "
                }
            }
        } else {
            blacklist.forEach { message += "\n§e$it" }
        }
        ChatUtils.chat(message)
    }

    private fun blacklistView(player: String) {
        if (isBlockedUser(player)) {
            ChatUtils.chat("$player §ais §eignored.")
        } else {
            ChatUtils.chat("$player §cisn't §eignored.")
        }
    }

    @HandleEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(95, "misc.partyCommands.defaultRequiredTrustLevel", "misc.partyCommands.requiredTrustLevel")
    }

    // TODO msg commands.
}
