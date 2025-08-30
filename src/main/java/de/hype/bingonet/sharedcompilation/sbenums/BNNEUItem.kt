package de.hype.bingonet.sharedcompilation.sbenums

// I hate that this Class has to be done, but otherwise I would have to rewrite whole SH to use Neas NEU Repo Parser
open class BNNEUItem(
    val internalName: String,
) {

    override fun hashCode(): Int {
        return internalName.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return internalName == (other as? BNNEUItem)?.internalName
    }
}
