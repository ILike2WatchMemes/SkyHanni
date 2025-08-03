package at.hannibal2.skyhanni.data.jsonobjects.repo.neu

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.hype.bingonet.sharedcompilation.sbenums.BNNEUItem
import de.hype.bingonet.sharedcompilation.sbenums.minions.MinionCategory
import de.hype.bingonet.sharedcompilation.sbenums.minions.MinionType

class NeuMinionTypeData : MinionType {
    @Expose
    @SerializedName("type_id")
    override val typeId: String
    @Expose
    @SerializedName("type")
    override val category: MinionCategory
    @Expose
    @SerializedName("requiredactions")
    override val requiredActions: Int
    @Expose
    @SerializedName("drops")
    override val drops: Map<BNNEUItem, Double>

    private constructor(typeId: String, category: MinionCategory, requiredActions: Int, drops: Map<BNNEUItem, Double>) : super(
        typeId, category, drops,requiredActions
    ) {
        this.typeId = typeId
        this.category = category
        this.requiredActions = requiredActions
        this.drops = drops
    }
}
