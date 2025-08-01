package de.hype.bingonet.environment

import at.hannibal2.skyhanni.api.enoughupdates.EnoughUpdatesManager
import at.hannibal2.skyhanni.utils.NeuInternalName
import at.hannibal2.skyhanni.utils.NeuItems
import de.hype.bingonet.shared.constants.Collections
import de.hype.bingonet.sharedcompilation.sbenums.BNNEUItem
import de.hype.bingonet.sharedcompilation.sbenums.minions.MinionData
import de.hype.bingonet.sharedcompilation.sbenums.minions.MinionType

/**
 * Repository API used by Bingo Net due to /shared being mounted to the Server Code.
 */
object NeuEnvironmentRepo {
    fun getFromInternalName(string: String): NeuInternalName {
        return NeuItems.allInternalNames.get(string) ?: error("Neu Repo Outdated?")
    }

    fun getFromSBName(sbItemID: String): NeuInternalName {
        return NeuItems.getInternalNameFromHypixelId(sbItemID)
    }
}

fun BNNEUItem.toInternalName(): NeuInternalName {
    return NeuItems.allInternalNames.get(this.internalName) ?: error("Neu Repo Outdated?")
}

fun MinionType.withTierData(tier: Int) : MinionData?{
    return EnoughUpdatesManager.getTypeMinions(this).get(tier-1)
}
fun Collections.getMinionType(): MinionType? {
    val minionId = minionID ?: return null
    return EnoughUpdatesManager.getMinionType(minionId)
}

val BNNEUItem.displayName: String get() = this.toInternalName().displayName
