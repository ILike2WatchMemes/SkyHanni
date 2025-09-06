package de.hype.bingonet.sharedcompilation.sbenums.minions

import de.hype.bingonet.sharedcompilation.sbenums.BNNEUItem


open class MinionType(
    open val typeId: String,
    open val category: MinionCategory,
    open val drops: Map<BNNEUItem, Double>,
    open val requiredActions: Int,
) {

    override fun hashCode(): Int {
        return typeId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return typeId == (other as? MinionType)?.typeId
    }
}

enum class MinionCategory {
    MINING,
    FARMING,
    COMBAT,
    FORAGING,
    FISHING,
    OTHER,
}
