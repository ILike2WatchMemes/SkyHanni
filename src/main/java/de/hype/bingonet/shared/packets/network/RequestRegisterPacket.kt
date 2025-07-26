package de.hype.bingonet.shared.packets.network

import de.hype.bingonet.shared.packets.base.ExpectReplyPacket

/**
 * IMPORTANT: By sending this packet the User agrees to the Privacy Policy, Terms of Service and the Rules of Bingo Net.
 *
 * You as the Developer are also required to force the User to accept these and have them enter
 * "I will read the info channels in the discord before asking questions!"
 * With at least 95% matching ignoring case.
 *
 * Keep in mind that the User must be on the Bingo Net Discord Server to register.
 * (Will receive a Bot DM to confirm Account)
 */
data class RequestRegisterPacket(
    val mcuuid: java.util.UUID,
    val discordId: String,
    val clientMojangServerId: String
) : ExpectReplyPacket<RequestRegisterPacket.MCRegistrationResponsePacket>() {
    data class MCRegistrationResponsePacket(
        val response: ResponseType,
    ) : ReplyPacket() {
        enum class ResponseType {
            AWAITING_DC_USER_CONFIRMATION, // → Basically Success.
            // It means that the discord user got sent the request to confirm.
            NOT_ON_DISCORD, // → The user is not on the Bingo Net Discord Server.
            ALREADY_REGISTERED, // → DC User or MC UUID is already associated with an account.
            BAD_REQUEST, // → The request was malformed? Bad dc ID or MC UUID?
            ERROR // → A Server Side Error occurred.
        }
    }

    companion object {
        const val PHRASE = "I will read the info channels in the discord before asking questions!"
        fun passesAccuracyCheck(input: String): Boolean {
            val required = PHRASE.lowercase()
            val inputLower = input.lowercase().trim()
            val similarity = calculateSimilarity(required, inputLower)
            return similarity >= 95
        }

        fun calculateSimilarity(str1: String, str2: String): Int {
            val longer = Math.max(str1.length, str2.length);
            val distance = levenshteinDistance(str1, str2);
            return ((longer - distance) / longer) * 100;
        }

        // Function to calculate Levenshtein distance
        fun levenshteinDistance(a: String, b: String): Int {
            val dp = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) {
                for (j in 0..b.length) {
                    when {
                        i == 0 -> dp[i][j] = j // Deletion
                        j == 0 -> dp[i][j] = i // Insertion
                        a[i - 1] == b[j - 1] -> dp[i][j] = dp[i - 1][j - 1] // Match
                        else -> dp[i][j] = 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]) // Substitution
                    }
                }
            }
            return dp[a.length][b.length]
        }
    }
}
