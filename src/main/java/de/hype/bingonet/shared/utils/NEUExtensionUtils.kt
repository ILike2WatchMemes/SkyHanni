package de.hype.bingonet.shared.utils

import de.hype.bingonet.shared.constants.Collections
import de.hype.bingonet.sharedcompilation.sbenumcode.MinionRepoManager
import de.hype.bingonet.sharedcompilation.sbenumcode.NeuRepoManager
import de.hype.bingonet.sharedcompilation.sbenums.BNNEUItem
import de.hype.bingonet.sharedcompilation.sbenums.minions.MinionData
import de.hype.bingonet.sharedcompilation.sbenums.minions.MinionType
import io.github.moulberry.repo.data.NEUItem
import kotlin.contracts.ExperimentalContracts

fun Collections.getMinionType(): MinionType? {
    val minionId = minionID ?: return null
    return MinionRepoManager.minionTypes.get(minionId.replace("_\\d".toRegex(), ""))
}

fun BNNEUItem.toLokal(): NEUItem {
    return NeuRepoManager.items.get(this.internalName)!!
}

val BNNEUItem.displayName: String get() = this.toLokal().displayName

val BNNEUItem.skyblockItemId: String get() = this.toLokal().skyblockItemId