package de.hype.bingonet.shared.constants

enum class Skills {
    Foraging("FORAGING", "Foraging", 50, 50, LevelingType.NORMAL_SKILL_LEVELING),
    Runecrafting("RUNECRAFTING", "Runecrafting", 25, 25, LevelingType.COSMETIC_SKILL_LEVELING, true),
    Taming("TAMING", "Taming", 50, 60, LevelingType.NORMAL_SKILL_LEVELING),
    Social("SOCIAL", "Social", 25, 25, LevelingType.COSMETIC_SKILL_LEVELING, true),
    Combat("COMBAT", "Combat", 60, 60, LevelingType.NORMAL_SKILL_LEVELING),
    Enchanting("ENCHANTING", "Enchanting", 60, 60, LevelingType.NORMAL_SKILL_LEVELING),
    Fishing("FISHING", "Fishing", 50, 50, LevelingType.NORMAL_SKILL_LEVELING),
    Farming("FARMING", "Farming", 50, 60, LevelingType.NORMAL_SKILL_LEVELING),
    Carpentry("CARPENTRY", "Carpentry", 50, 50, LevelingType.NORMAL_SKILL_LEVELING),
    Alchemy("ALCHEMY", "Alchemy", 50, 50, LevelingType.NORMAL_SKILL_LEVELING),
    Mining("MINING", "Mining", 60, 60, LevelingType.NORMAL_SKILL_LEVELING),
    Dungeon("DUNGEON", "Dungeon", 50, 50, LevelingType.CATACOMBS_SKILL_LEVELING), ;

    @JvmField
    val id: String
    val displayName: String
    val maxLevelTillUnlock: Int
    val maxLevelAfterUnlock: Int
    var cosmetic: Boolean = false
    var leveling: List<Int>

    constructor(
        id: String,
        displayName: String,
        maxLevelTillUnlock: Int,
        maxLevelAfterUnlock: Int,
        leveling: LevelingType,
        cosmetic: Boolean = false
    ) {
        this.id = id
        this.displayName = displayName
        this.maxLevelTillUnlock = maxLevelTillUnlock
        this.maxLevelAfterUnlock = maxLevelAfterUnlock
        this.cosmetic = cosmetic
        this.leveling = leveling.getLevelingList()
    }

    fun getSkillLevelOfXp(xp: Int): Int {
        for (i in 60 downTo 0) {
            val leveling = leveling
            if (leveling[i] >= xp) return i
        }
        return 0
    }

    enum class LevelingType(
        vararg val leveling: Int,
    ) {
        NORMAL_SKILL_LEVELING(
            50,
            175,
            375,
            675,
            1175,
            1925,
            2925,
            4425,
            6425,
            9925,
            14925,
            22425,
            32425,
            47425,
            67425,
            97425,
            147425,
            222425,
            322425,
            522425,
            822425,
            1222425,
            1722425,
            2322425,
            3022425,
            3822425,
            4722425,
            5722425,
            6822425,
            8022425,
            9322425,
            10722425,
            12222425,
            13822425,
            15522425,
            17322425,
            19222425,
            21222425,
            23322425,
            25522425,
            27822425,
            30222425,
            32722425,
            35322425,
            38072425,
            40972425,
            44072425,
            47472425,
            51172425,
            55172425,
            59472425,
            64072425,
            68972425,
            74172425,
            79672425,
            85472425,
            91572425,
            97972425,
            104672425,
            111672425
        ),
        COSMETIC_SKILL_LEVELING(
            50,
            150,
            275,
            435,
            635,
            885,
            12,
            16,
            21,
            2725,
            3150,
            4510,
            5760,
            7325,
            9325,
            11825,
            14950,
            18950,
            23950,
            30200,
            38050,
            47850,
            60100,
            75400,
            94500
        ),
        CATACOMBS_SKILL_LEVELING(
            50,
            125,
            235,
            395,
            625,
            955,
            1425,
            2095,
            3045,
            4385,
            6275,
            894,
            127,
            1796,
            2534,
            3564,
            5004,
            7004,
            9764,
            13564,
            18814,
            25964,
            35664,
            48864,
            66864,
            91164,
            1239640,
            1684640,
            2284640,
            3084640,
            4149640,
            5559640,
            7459640,
            9959640,
            13259640,
            17559640,
            23159640,
            30359640,
            39559640,
            51559640,
            66559640,
            85559640,
            109559640,
            139559640,
            177559640,
            225559640,
            295559640,
            360559640,
            453559640,
            569809640
        );

        fun getLevelingList(): List<Int> {
            return leveling.toList()
        }
    }

}