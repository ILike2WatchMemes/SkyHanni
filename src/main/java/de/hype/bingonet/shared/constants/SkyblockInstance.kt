package de.hype.bingonet.shared.constants

interface SkyblockInstance {
    val joinId: String

    val displayName: String
}

interface CatacombsType: SkyblockInstance {
    override val joinId: String

    override val displayName: String
}

enum class BasicCatacombsType(
    override val joinId: String,
    override val displayName: String,
) : CatacombsType {
    ENTRANCE("CATACOMBS_ENTRANCE", "Entrance"),
    FLOOR_1("CATACOMBS_FLOOR_ONE", "Floor 1"),
    FLOOR_2("CATACOMBS_FLOOR_TWO", "Floor 2"),
    FLOOR_3("CATACOMBS_FLOOR_THREE", "Floor 3"),
    FLOOR_4("CATACOMBS_FLOOR_FOUR", "Floor 4"),
    FLOOR_5("CATACOMBS_FLOOR_FIVE", "Floor 5"),
    FLOOR_6("CATACOMBS_FLOOR_SIX", "Floor 6"),
    FLOOR_7("CATACOMBS_FLOOR_SEVEN", "Floor 7"),
}

enum class MasterModCatacombsType(
    override val joinId: String,
    override val displayName: String,
) : CatacombsType {
    FLOOR_1("MASTER_CATACOMBS_FLOOR_ONE", "Master Mode Floor 1"),
    FLOOR_2("MASTER_CATACOMBS_FLOOR_TWO", "Master Mode Floor 2"),
    FLOOR_3("MASTER_CATACOMBS_FLOOR_THREE", "Master Mode Floor 3"),
    FLOOR_4("MASTER_CATACOMBS_FLOOR_FOUR", "Master Mode Floor 4"),
    FLOOR_5("MASTER_CATACOMBS_FLOOR_FIVE", "Master Mode Floor 5"),
    FLOOR_6("MASTER_CATACOMBS_FLOOR_SIX", "Master Mode Floor 6"),
    FLOOR_7("MASTER_CATACOMBS_FLOOR_SEVEN", "Master Mode Floor 7"),
}

enum class KuudraType(
    override val joinId: String,
    override val displayName: String,
) : SkyblockInstance {
    NORMAL("KUUDRA_NORMAL", "Normal Kuudra"),
    HOT("KUUDRA_HOT", "Hot Kuudra"),
    BURNING("KUUDRA_BURNING", "Burning Kuudra"),
    FIERY("KUUDRA_FIERY", "Fiery"),
    INFERNAL("KUUDRA_INFERNAL", "Infernal Kuudra"),
}
