package at.hannibal2.skyhanni.config.features.misc

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.data.FriendApi
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption

// Enums used for default and per-user permissions
enum class PermissionOverride(val displayName: String) {
    DEFAULT("Default"),
    INSTANT("Instant"),
    ASK("Ask"),
    NEVER("Never");

    override fun toString() = displayName
}

enum class FriendLevel(val displayName: String) {
    BEST_FRIENDS("Best Friends"),
    FRIENDS("Friends"),
    NOT_FRIENDS("Not Friends"),
    IGNORED("Ignored");

    override fun toString() = displayName
}

enum class PermissionLevel(val displayName: String) {
    INSTANT("Instant"),
    ASK("Ask"),
    NEVER("Never");

    override fun toString() = displayName
}

class PartyCommandsConfig {
    @Expose
    @ConfigOption(name = "Users", desc = "Configure permissions for specific user")
    val users: MutableMap<String, TrustUserConfig> = mutableMapOf()

    // Global default thresholds for permissions: first = Instant threshold, second = Ask threshold
    @Expose
    @ConfigEditorDropdown
    @ConfigOption(name = "Default Invite Others - Instant Threshold", desc = "Minimum trust level to invite others without asking")
    var defaultInviteOthersInstant: FriendLevel = FriendLevel.BEST_FRIENDS

    @Expose
    @ConfigEditorDropdown
    @ConfigOption(name = "Default Invite Others - Ask Threshold", desc = "Minimum trust level to ask to invite others")
    var defaultInviteOthersAsk: FriendLevel = FriendLevel.FRIENDS

    @Expose
    @ConfigEditorDropdown
    @ConfigOption(name = "Default Kick - Instant Threshold", desc = "Minimum trust level to kick without asking")
    var defaultKickInstant: FriendLevel = FriendLevel.BEST_FRIENDS

    @Expose
    @ConfigEditorDropdown
    @ConfigOption(name = "Default Kick - Ask Threshold", desc = "Minimum trust level to ask to kick")
    var defaultKickAsk: FriendLevel = FriendLevel.FRIENDS

    @Expose
    @ConfigEditorDropdown
    @ConfigOption(name = "Default Kick Offline - Instant Threshold", desc = "Minimum trust level to kick offline without asking")
    var defaultKickOfflineInstant: FriendLevel = FriendLevel.BEST_FRIENDS

    @Expose
    @ConfigEditorDropdown
    @ConfigOption(name = "Default Kick Offline - Ask Threshold", desc = "Minimum trust level to ask to kick offline")
    var defaultKickOfflineAsk: FriendLevel = FriendLevel.FRIENDS

    @Expose
    @ConfigEditorDropdown
    @ConfigOption(
        name = "Default Transfer Leader - Instant Threshold",
        desc = "Minimum trust level to transfer party leader without asking",
    )
    var defaultTransferLeaderInstant: FriendLevel = FriendLevel.BEST_FRIENDS

    @Expose
    @ConfigEditorDropdown
    @ConfigOption(name = "Default Transfer Leader - Ask Threshold", desc = "Minimum trust level to ask to transfer leader")
    var defaultTransferLeaderAsk: FriendLevel = FriendLevel.FRIENDS

    @Expose
    @ConfigEditorDropdown
    @ConfigOption(name = "Default Enable All Invite - Instant Threshold", desc = "Minimum trust level to enable all invites without asking")
    var defaultEnableAllInviteInstant: FriendLevel = FriendLevel.BEST_FRIENDS

    @Expose
    @ConfigEditorDropdown
    @ConfigOption(name = "Default Enable All Invite - Ask Threshold", desc = "Minimum trust level to ask to enable all invites")
    var defaultEnableAllInviteAsk: FriendLevel = FriendLevel.FRIENDS

    @Expose
    @ConfigEditorDropdown
    @ConfigOption(
        name = "Default Bypass Party Limit - Instant Threshold",
        desc = "Minimum trust level to bypass party limit without asking",
    )
    var defaultBypassPartyLimitInstant: FriendLevel = FriendLevel.FRIENDS

    @Expose
    @ConfigEditorDropdown
    @ConfigOption(name = "Default Bypass Party Limit - Ask Threshold", desc = "Minimum trust level to ask to bypass party limit")
    var defaultBypassPartyLimitAsk: FriendLevel = FriendLevel.NOT_FRIENDS

    @Expose
    @ConfigEditorDropdown
    @ConfigOption(name = "Default Polls - Instant Threshold", desc = "Minimum trust level to perform polls without asking")
    var defaultDoPollsInstant: FriendLevel = FriendLevel.BEST_FRIENDS

    @Expose
    @ConfigEditorDropdown
    @ConfigOption(name = "Default Polls - Ask Threshold", desc = "Minimum trust level to ask to perform polls")
    var defaultDoPollsAsk: FriendLevel = FriendLevel.FRIENDS

    @Expose
    @ConfigEditorDropdown
    @ConfigOption(name = "Default Stream Open - Instant Threshold", desc = "Minimum trust level to open stream without asking")
    var defaultStreamOpenInstant: FriendLevel = FriendLevel.BEST_FRIENDS

    @Expose
    @ConfigEditorDropdown
    @ConfigOption(name = "Default Stream Open - Ask Threshold", desc = "Minimum trust level to ask to open stream")
    var defaultStreamOpenAsk: FriendLevel = FriendLevel.FRIENDS

    @Expose
    @ConfigEditorBoolean
    @ConfigOption(name = "Warp - Instant Threshold", desc = "Minimum trust level to warp the party without asking")
    var warpInstant: FriendLevel = FriendLevel.BEST_FRIENDS

    @Expose
    @ConfigEditorBoolean
    @ConfigOption(name = "Warp - Ask Threshold", desc = "Minimum trust level to ask to warp the party")
    var warpAsk: FriendLevel = FriendLevel.FRIENDS

    @Expose
    @ConfigEditorBoolean
    @ConfigOption(
        name = "Ping",
        desc = "Sends current ping into Party Chat if someone types §b!ping§7.\n" +
                "§cNote: Will not work correctly with the Hypixel Ping API turned off in Dev.",
    )
    var pingCommand: Boolean = false

    @Expose
    @ConfigEditorBoolean
    @ConfigOption(name = "TPS", desc = "Sends current TPS into Party Chat if someone types §b!tps§7.")
    var tpsCommand: Boolean = false

    inner class TrustUserConfig(val name: String) {
        @Expose
        @ConfigEditorDropdown
        @ConfigOption(name = "Self Invite Permission", desc = "Override permission for letting users invite themselves")
        var canSelfInvite: PermissionOverride = PermissionOverride.DEFAULT

        @Expose
        @ConfigEditorDropdown
        @ConfigOption(name = "Invite Others Permission", desc = "Override permission for inviting others (Default uses global thresholds)")
        var canInviteOthers: PermissionOverride = PermissionOverride.DEFAULT

        @Expose
        @ConfigEditorDropdown
        @ConfigOption(name = "Kick Permission", desc = "Override permission for kicking users")
        var canKick: PermissionOverride = PermissionOverride.DEFAULT

        @Expose
        @ConfigEditorDropdown
        @ConfigOption(name = "Kick Offline Permission", desc = "Override permission for kicking offline users")
        var canKickOffline: PermissionOverride = PermissionOverride.DEFAULT

        @Expose
        @ConfigEditorDropdown
        @ConfigOption(name = "Transfer Leader Permission", desc = "Override permission for transferring party leader")
        var canTransferLeader: PermissionOverride = PermissionOverride.DEFAULT

        @Expose
        @ConfigEditorDropdown
        @ConfigOption(name = "Can Warp", desc = "Override permission for warping the party")
        var canWarp: PermissionOverride = PermissionOverride.DEFAULT

        @Expose
        @ConfigEditorDropdown
        @ConfigOption(name = "Enable All Invite Permission", desc = "Override permission for enabling all invites")
        var canEnableAllInvite: PermissionOverride = PermissionOverride.DEFAULT

        @Expose
        @ConfigEditorDropdown
        @ConfigOption(name = "Bypass Party Limit Permission", desc = "Override permission for bypassing party limit")
        var canBypassPartyLimit: PermissionOverride = PermissionOverride.DEFAULT

        @Expose
        @ConfigEditorDropdown
        @ConfigOption(name = "Polls Permission", desc = "Override permission for polls")
        var canDoPolls: PermissionOverride = PermissionOverride.DEFAULT

        @Expose
        @ConfigEditorDropdown
        @ConfigOption(name = "Stream Open Permission", desc = "Override permission for opening stream")
        var canStreamOpen: PermissionOverride = PermissionOverride.DEFAULT

        private fun resolvePermission(
            override: PermissionOverride,
            defaultInstant: FriendLevel,
            defaultAsk: FriendLevel,
            userTrust: FriendLevel,
        ): PermissionLevel {
            return when (override) {
                PermissionOverride.INSTANT -> PermissionLevel.INSTANT
                PermissionOverride.ASK -> PermissionLevel.ASK
                PermissionOverride.NEVER -> PermissionLevel.NEVER
                PermissionOverride.DEFAULT -> {
                    when {
                        userTrust.ordinal >= defaultInstant.ordinal -> PermissionLevel.INSTANT
                        userTrust.ordinal >= defaultAsk.ordinal -> PermissionLevel.ASK
                        else -> PermissionLevel.NEVER
                    }
                }
            }
        }

        val trustLevel: FriendLevel
            get() {
                if (SkyHanniMod.feature.storage.blacklistedUsers.contains(name)) {
                    return FriendLevel.IGNORED
                }
                val friend = FriendApi.getAllFriends().find { it.name == name }
                if (friend?.bestFriend ?: false) return FriendLevel.BEST_FRIENDS
                if (friend != null) return FriendLevel.FRIENDS
                return FriendLevel.NOT_FRIENDS
            }

        val effectiveInviteOthers: PermissionLevel
            get() = resolvePermission(canInviteOthers, defaultInviteOthersInstant, defaultInviteOthersAsk, trustLevel)
        val effectiveKick: PermissionLevel
            get() = resolvePermission(canKick, defaultKickInstant, defaultKickAsk, trustLevel)
        val effectiveKickOffline: PermissionLevel
            get() = resolvePermission(canKickOffline, defaultKickOfflineInstant, defaultKickOfflineAsk, trustLevel)
        val effectiveTransferLeader: PermissionLevel
            get() = resolvePermission(canTransferLeader, defaultTransferLeaderInstant, defaultTransferLeaderAsk, trustLevel)
        val effectiveEnableAllInvite: PermissionLevel
            get() = resolvePermission(canEnableAllInvite, defaultEnableAllInviteInstant, defaultEnableAllInviteAsk, trustLevel)
        val effectiveBypassPartyLimit: PermissionLevel
            get() = resolvePermission(canBypassPartyLimit, defaultBypassPartyLimitInstant, defaultBypassPartyLimitAsk, trustLevel)
        val effectiveDoPolls: PermissionLevel
            get() = resolvePermission(canDoPolls, defaultDoPollsInstant, defaultDoPollsAsk, trustLevel)
        val effectiveStreamOpen: PermissionLevel
            get() = resolvePermission(canStreamOpen, defaultStreamOpenInstant, defaultStreamOpenAsk, trustLevel)

        val effectiveWarp: PermissionLevel
            get() = resolvePermission(canWarp, warpInstant, warpAsk, trustLevel)
    }

    @Expose
    @ConfigEditorBoolean
    @ConfigOption(name = "Show reminder", desc = "Show a reminder when an unauthorized player tries to run a command.")
    var showIgnoredReminder: Boolean = true
}
