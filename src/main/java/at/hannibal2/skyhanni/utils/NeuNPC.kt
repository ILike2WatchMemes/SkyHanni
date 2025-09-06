package at.hannibal2.skyhanni.utils

import at.hannibal2.skyhanni.utils.collection.CollectionUtils.add
import at.hannibal2.skyhanni.utils.collection.CollectionUtils.mapKeysNotNull
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import de.hype.bingonet.shared.constants.Islands
import de.hype.bingonet.shared.objects.Position
import de.hype.bingonet.shared.utils.skip

@Suppress("UnusedPrivateProperty")
class NeuNPC(
    @Expose
    @SerializedName("displayname")
    val displayName: String,

    @Expose
    @SerializedName("nbttag")
    val nbtTag: String?,

    @Expose
    @SerializedName("x")
    private val x: Int?,

    @Expose
    @SerializedName("y")
    private val y: Int?,

    @Expose
    @SerializedName("z")
    private val z: Int?,

    @Expose
    @SerializedName("island")
    private val island: String?,

    @Expose
    @SerializedName("recipes")
    private var _offers: MutableList<NeuNPCOffer>? = mutableListOf(),
) {
    @Expose
    @SerializedName("internalname")
    val internalName: String = displayName.uppercase().replace(" ", "_").replace(Regex("[^A-Z_]+"), "")

    @Expose
    @SerializedName("itemid")
    private val itemId: String = "minecraft:skull"

    @Expose
    @SerializedName("damage")
    private val damage: Int = 3

    @Expose
    @SerializedName("lore")
    private val lore: List<String> = listOf("")

    @Expose
    @SerializedName("clickcommand")
    private val clickCommand: String = "viewrecipe"

    // TODO whats the correct mod version here?
    @Expose
    @SerializedName("modver")
    private val modVer: String = "2.1.1-PRE"

    @Expose
    @SerializedName("infoType")
    private val infoType: String = ""

    @Expose
    @SerializedName("info")
    private val info: List<String> = emptyList()

    @Expose
    @SerializedName("crafttext")
    private val craftText: String = ""

    @Expose
    @SerializedName("extra_locations")
    private val extraLocations = mutableMapOf<String, Position>()

    constructor(
        displayName: String,
        nbtTag: String,
        offers: MutableList<NeuNPCOffer>,
        island: String? = null,
        position: Position,
    ) : this(
        displayName,
        nbtTag,
        position.x,
        position.y,
        position.z,
        island,
        offers,
    )

    constructor(
        displayName: String,
        nbtTag: String,
        offers: MutableList<NeuNPCOffer>,
        locations: Map<Islands, Position>,
    ) : this(
        displayName,
        nbtTag,
        locations.values.first().x,
        locations.values.first().y,
        locations.values.first().z,
        locations.keys.first().internalName,
        offers,
    ) {
        extraLocations += (locations.skip(1).map { it.key.internalName to it.value })
    }

    /**
     * Keep in mind that the Location is for the location of the NPC on the default island. Aka default garden etc.
     */
    fun getLocation(island: Islands): Position? {
        return locations.get(island)
    }

    fun addOffer(offer: NeuNPCOffer) {
        val tempOffers = _offers ?: mutableListOf()
        _offers = tempOffers
        tempOffers.add(offer)
    }

    /**
     * Used during refresh of offers to overwrite the current offers with the new ones.
     */
    fun removeCategoryOffers(guiName: String) {
        _offers?.removeIf {
            it.category == guiName || it.category == null
        }
    }

    val offers: Map<String, List<NeuNPCOffer>>
        get() = _offers?.groupBy { it.category ?: "" } ?: emptyMap()

    val allOffers: List<NeuNPCOffer>
        get() {
            return _offers ?: emptyList()
        }

    val locations: Map<Islands, Position> by lazy {
        val allLocations = mutableMapOf<Islands, Position>()
        val base = if (x != null && y != null && z != null) {
            Position(x, y, z)
        } else {
            null
        }
        val baseIsland = island?.let { Islands.fromInternalName(it) }
        if (base != null && baseIsland != null) allLocations.add(baseIsland to base)
        allLocations += extraLocations.mapKeysNotNull { Islands.fromInternalName(it.key) }
        return@lazy allLocations
    }


    class NeuNPCOffer(
        @Expose
        @SerializedName("cost")
        val cost: List<PrimitiveIngredient>,

        @Expose
        @SerializedName("result")
        val result: PrimitiveIngredient,

        @Expose
        @SerializedName("category")
        val category: String? = null,

        @Expose
        @SerializedName("limit")
        val limit: LimitData? = null,
    ) {
        @Expose
        @SerializedName("type")
        private val type = RecipeType.NPC_SHOP.name.lowercase()

        fun getEffectiveLimit(): Int? {
            return limit?.limit?.let { it - (it % result.count.toInt()) }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as NeuNPCOffer

            if (cost != other.cost) return false
            if (result != other.result) return false
            if (category != other.category) return false

            return true
        }

        override fun hashCode(): Int {
            var result1 = cost.hashCode()
            result1 = 31 * result1 + result.hashCode()
            result1 = 31 * result1 + (category?.hashCode() ?: 0)
            return result1
        }

        data class LimitData(
            val limit: Int,
            val resetType: ShopLimitResetType,
        )

        enum class ShopLimitResetType {
            DAILY,
            YEARLY,
        }
    }
}
