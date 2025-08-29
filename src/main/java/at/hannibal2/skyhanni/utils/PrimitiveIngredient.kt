package at.hannibal2.skyhanni.utils

import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.SKYBLOCK_COIN
import at.hannibal2.skyhanni.utils.NeuInternalName.Companion.toInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.NumberUtil.formatDouble
import de.hype.bingonet.environment.displayName

class PrimitiveIngredient(val internalName: NeuInternalName, val count: Double = 1.0) {

    constructor(internalName: NeuInternalName, count: Int) : this(internalName, count.toDouble())

    constructor(ingredientIdentifier: String) : this(
        ingredientIdentifier.substringBefore(':').toInternalName(),
        // if second part is blank, the count is assumed to be 1
        ingredientIdentifier.substringAfter(':', "").let { if (it.isBlank()) 1.0 else it.formatDouble() },
    )

    companion object {
        fun coinIngredient(count: Double = 1.0) = PrimitiveIngredient(SKYBLOCK_COIN, count)
        fun copperIngredient(count: Double = 1.0) = PrimitiveIngredient(NeuInternalName.SKYBLOCK_COPPER, count)
        fun chocolateIngredient(count: Double = 1.0) = PrimitiveIngredient(NeuInternalName.SKYBLOCK_CHOCOLATE, count)

        fun Set<PrimitiveIngredient>.toPrimitiveItemStacks(): List<PrimitiveItemStack> =
            map { it.toPrimitiveItemStack() }
    }

    fun isCoin() = internalName == SKYBLOCK_COIN

    override fun toString() = "$internalName x$count"

    fun toSkyblockString() : String {
        if (internalName == SKYBLOCK_COIN) {
            if (count == 1.0) {
                return "§61 Coin"
            }else {
                return "§6${count.addSeparators()} Coins"
            }
        }else if (internalName == NeuInternalName.SKYBLOCK_CHOCOLATE) {
            return "§6${count.addSeparators()} Chocolate"
        }
        else if (internalName == NeuInternalName.SKYBLOCK_COPPER) {
            return "§c${count.addSeparators()} Copper"
        } else {
            return internalName.displayName+" §8x64"
        }
    }

    fun toPair() = Pair(internalName, count)

    // TODO should maybe throw an error when trying to use with internalName == SKYBLOCK_COIN
    fun toPrimitiveItemStack() = PrimitiveItemStack(internalName, count.toInt())
}
