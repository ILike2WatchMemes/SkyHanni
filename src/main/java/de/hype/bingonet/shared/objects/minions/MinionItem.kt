package de.hype.bingonet.shared.objects.minions

import de.hype.bingonet.server.extensionutils.toShared
import de.hype.bingonet.shared.compilation.sbenums.SkyblockItems
import de.hype.bingonet.shared.compilation.sbenums.minions.MinionTypes
import de.hype.bingonet.shared.utils.modifyKeys
import de.hype.bingonet.shared.utils.modifyValues
import de.hype.bingonet.shared.utils.skyblockItemId
import de.hype.bingonet.sharedcompilation.sbenumcode.toShared
import de.hype.bingonet.sharedcompilation.sbenums.BNNEUItem
import de.hype.bingonet.sharedcompilation.sbenums.minions.MinionCategory

enum class MinionItem(
    var displayName: String,
    private val modifier: (AppliedMinionData) -> Unit,
    var bingoObtainable: Boolean = true
) {
    AUTO_SMELTER("Auto Smelter", modifier = {
        it.drops.modifyKeys {
            if (it.key.skyblockItemId.startsWith("LOG")) {
                return@modifyKeys SkyblockItems.Coal.toShared()
            }
            if (it.key.skyblockItemId.startsWith("CACTUS")) {
                return@modifyKeys SkyblockItems.Cactus_Green.toShared()
            }
            return@modifyKeys it.key
        }
        it.smelter = true
    }),
    COMPACTOR("Compactor", modifier = {
        it.compactor = true
    }),
    SUPER_COMPACTOR_3000("Super Compactor 3000", modifier = {
        it.sc3000 = true
    }),
    DWARFEN_SUPER_COMPACTOR("Dwarfen Super Compactor", modifier = {
        it.sc3000 = true
        it.smelter = true
    }),
    DIAMOND_SPREADING("Diamond Spreading", modifier = {
        val sum = it.drops.values.sum()
        SkyblockItems.Diamond
        it.addDrop(SkyblockItems.Diamond.toShared(), (sum * 0.1))
    }),
    POTATO_SPREADING("Potato Spreading", modifier = {
        val sum = it.drops.values.sum()
        it.addDrop(SkyblockItems.Potato.toShared(), (sum * 0.2))
    }, false),
    MINION_EXPANDER("Minion Expander", modifier = {
        it.minionData.timeBetweenActions *= 0.95
    }),
    ENCHANTED_EGG("Enchanted Egg", modifier = {
        it.addDrop(SkyblockItems.Egg.toShared(), 1.0)
    }) {
        override fun canBeUsedInMinion(minion: AppliedMinionData): Boolean {
            return minion.isType(MinionTypes.CHICKEN_GENERATOR.toShared())
        }
    },
    FLINT_SHOVEL("Flint Shovel", modifier = {
        it.drops.mapKeys { if (it.key.equals("GRAVEL")) return@mapKeys "FLINT" else it.key }
    }) {
        override fun canBeUsedInMinion(minion: AppliedMinionData): Boolean {
            return minion.isType(MinionTypes.GRAVEL_GENERATOR.toShared())
        }
    },
    FLYCATCHER("Flycatcher", modifier = {
        it.minionData.timeBetweenActions *= 0.8
    }, false),
    KRAMPUS_HELMET("Krampus Helmet", modifier = {
        val sum = it.drops.values.sum()
        it.addDrop(SkyblockItems.Red_Gift.toShared(), (sum * 0.000045))
    }, false),
    LESSER_SOULFLOW_ENGINE("Lesser Soulflow Engine", modifier = {
        it.drops.modifyValues { entry ->
            return@modifyValues entry.value / 2
        }
    }),
    SOULFLOW_ENGINE("Soulflow Engine", modifier = {
        it.drops.forEach { entry ->
            it.drops.put(entry.key, entry.value / 2)
        }
        if (it.isType(MinionTypes.VOIDLING_GENERATOR.toShared())) {
            it.minionData.timeBetweenActions *= (1 - (0.3 * it.minionData.tier))
        }
    }),
    CORRUPT_SOIL("Corrupt Soil", modifier = {
        it.addDrop(SkyblockItems.Corrupted_Fragment.toShared(), 1.0)
        it.addDrop(SkyblockItems.Sulphur.toShared(), 1.0)
    }),
    BERBERIES_FUEL_INJECTOR("Berberis Fuel Injector", modifier = {
        if (it.category == MinionCategory.FARMING) {
            it.minionData.timeBetweenActions *= 0.85
        }
    }),
    SLEEPY_HOLLOW("Sleepy Hollow", modifier = {
        val sum = it.drops.values.sum()
        it.addDrop(SkyblockItems.Purple_Candy.toShared(), (sum * 0.00015))
    }, false);

    open fun canBeUsedInMinion(minion: AppliedMinionData): Boolean {
        return true
    }

    fun applyToMinion(minionData: AppliedMinionData) {
        if (canBeUsedInMinion(minionData)) {
            modifier(minionData)
        }
    }
}

private fun AppliedMinionData.addDrop(item: BNNEUItem, amount: Double) {
    this.drops.compute(item) { _, v ->
        return@compute (v ?: 0.0) + amount
    }
}