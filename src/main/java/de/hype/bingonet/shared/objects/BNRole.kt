package de.hype.bingonet.shared.objects

enum class BNRole(dbRoleName: String, visualRoleName: String) {
    DEVELOPER("dev", "Developer"),
    MODERATOR("mod", "Moderator"),
    CHCHEST_ANNOUNCE_PERM("chchest", "Ch Chest"),
    BETA_TESTER("beta", "Beta Tester"),
    ADMIN("admin", "Admin"),
    MANIAC("maniac", "Maniac"),
    PREANNOUNCE("preannounce_info", "Preannounce Info"),

    //Given to users what have reasons to given access early. Such as those who did great for the community
    //or temporary if needed for something specific
    SPLASHER("splasher", "Splasher"),
    ADVANCEDINFO("advancedinfo", "Advanced Info"),
    STRATMAKER("strat_maker", "Strat Maker"),
    DEBUG("debug", "Debug");

    val dBRoleName: String = dbRoleName
    val visualName: String = visualRoleName

    val description: String?
        get() = null

    companion object {
        var roles: MutableMap<String, BNRole>? = null
    }
}
