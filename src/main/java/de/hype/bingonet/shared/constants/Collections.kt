package de.hype.bingonet.shared.constants

import de.hype.bingonet.environment.NeuEnvironmentRepo
import de.hype.bingonet.sharedcompilation.sbenums.BNNEUItem
import kotlin.math.min

@Suppress("unused", "EnumEntryName")
interface Collections {

    val id: String

    val tierCount: IntArray

    fun getCollectionForTier(tier: Int): Int {
        return tierCount[min(tier, tierCount.size - 1)]
    }

    val displayName: String
        get() = this.toString().replace("_", " ")

    fun getTierWithCollection(amount: Int): Int {
        for (i in tierCount.indices.reversed()) {
            if (tierCount[i] >= amount) return i
        }
        return 0
    }

    val minionID: String?

    enum class Farming(
        override val id: String,
        override val minionID: String?,
        val baseXp: Double,
        val baseCropOnBroken: Double,
        val copperCropMultiplier: Double,
        vararg tiers: Int,
    ) : Collections {
        Cocoa_Beans(
            "INK_SACK:3",
            "COCOA_GENERATOR_1",
            4.0,
            3.0,
            1.0,
            75,
            200,
            500,
            2_000,
            5_000,
            10_000,
            20_000,
            50_000,
            100_000
        ),
        Carrot(
            "CARROT_ITEM",
            "CARROT_GENERATOR_1",
            4.0,
            3.0,
            3.5,
            100,
            250,
            500,
            1_750,
            5_000,
            10_000,
            25_000,
            50_000,
            100_000
        ),
        Cactus(
            "CACTUS",
            "CACTUS_GENERATOR_1",
            4.0,
            2.0,
            1.5,
            100,
            250,
            500,
            1_000,
            2_500,
            5_000,
            10_000,
            25_000,
            50_000
        ),
        Raw_Chicken(
            "RAW_CHICKEN",
            "CHICKEN_GENERATOR_1",
            baseXp = 0.1,
            baseCropOnBroken = 1.0,
            copperCropMultiplier = 1.0,
            50,
            100,
            250,
            1_000,
            2_500,
            5_000,
            10_000,
            25_000,
            50_000,
            100_000
        ),
        Sugar_Cane(
            "SUGAR_CANE",
            "SUGAR_CANE_GENERATOR_1",
            4.0,
            2.0,
            2.0,
            100,
            250,
            500,
            1_000,
            2_000,
            5_000,
            10_000,
            20_000,
            50_000,
        ),
        Pumpkin(
            "PUMPKIN",
            "PUMPKIN_GENERATOR_1",
            4.5,
            1.0,
            0.85,
            40,
            100,
            250,
            1_000,
            2_500,
            5_000,
            10_000,
            25_000,
            50_000,
            100_000,
            250_000,
        ),
        Wheat(
            "WHEAT",
            "WHEAT_GENERATOR_1",
            4.0,
            1.0,
            1.0,
            50,
            100,
            250,
            500,
            1_000,
            2_500,
            10_000,
            15_000,
            25_000,
            50_000,
            100_000,
        ),
        Seeds("SEEDS", "SEEDS_GENERATOR_1", 0.0, 0.0, 0.0, 50, 100, 250, 1_000, 2_500, 5_000, 25_000),
        Mushroom(
            "MUSHROOM_COLLECTION",
            "MUSHROOM_GENERATOR_1",
            6.0,
            1.0,
            0.95,
            50,
            100,
            250,
            1_000,
            2_500,
            5_000,
            10_000,
            25_000,
            50_000,
        ),
        Raw_Rabbit(
            "RABBIT",
            "RABBIT_GENERATOR_1",
            baseXp = 0.1,
            baseCropOnBroken = 1.0,
            copperCropMultiplier = 1.0,
            50,
            100,
            250,
            1_000,
            2_500,
            5_000,
            10_000,
            25_000,
            50_000
        ),
        Nether_Wart(
            "NETHER_STALK",
            "NETHER_WARTS_GENERATOR_1",
            4.0, 2.5, 3.0,
            50,
            100,
            250,
            1_000,
            2_500,
            5_000,
            10_000,
            25_000,
            50_000,
            75_000,
            100_000,
            250_000,
        ),
        Mutton(
            "MUTTON",
            "SHEEP_GENERATOR_1",
            baseXp = 0.1,
            baseCropOnBroken = 1.0,
            copperCropMultiplier = 1.0,
            50,
            100,
            250,
            1_000,
            2_500,
            5_000,
            10_000,
            25_000,
            50_000,
            100_000
        ),
        Melon(
            "MELON",
            "MELON_GENERATOR_1",
            4.0,
            5.0,
            4.0,
            250,
            500,
            1_250,
            5_000,
            15_000,
            25_000,
            50_000,
            100_000,
            250_000
        ),
        Potato(
            "POTATO_ITEM",
            "POTATO_GENERATOR_1",
            4.0,
            3.0,
            3.0,
            100,
            200,
            500,
            1_750,
            5_000,
            10_000,
            25_000,
            50_000,
            100_000
        ),
        Leather(
            "LEATHER",
            "COW_GENERATOR_1",
            baseXp = 0.1,
            baseCropOnBroken = 1.0,
            copperCropMultiplier = 1.0,
            50,
            100,
            250,
            500,
            1_000,
            2_500,
            5_000,
            10_000,
            25_000,
            50_000,
            100_000
        ),
        Raw_Porkchop(
            "PORK",
            "PIG_GENERATOR_1",
            baseXp = 0.1,
            baseCropOnBroken = 1.0,
            copperCropMultiplier = 1.0,
            50,
            100,
            250,
            1_000,
            2_500,
            5_000,
            10_000,
            25_000,
            50_000
        ),
        Feather(
            "FEATHER",
            "CHICKEN_GENERATOR_1",
            baseXp = 0.1,
            baseCropOnBroken = 1.0,
            copperCropMultiplier = 1.0,
            50,
            100,
            250,
            1_000,
            2_500,
            5_000,
            10_000,
            25_000,
            50_000
        ),
        ;


        override val tierCount: IntArray = tiers

        fun cropCountPerBroken(): Int {
            if (this == Cactus || this == Sugar_Cane) return 2
            else return 1
        }
    }

    enum class Mining(override val id: String, override val minionID: String?, vararg tiers: Int) : Collections {
        Lapis_Lazuli(
            "INK_SACK:4",
            "LAPIS_GENERATOR_1",
            250,
            500,
            1_000,
            2_000,
            10_000,
            25_000,
            50_000,
            100_000,
            150_000,
            250_000,
        ),
        Redstone(
            "REDSTONE",
            "REDSTONE_GENERATOR_1",
            100,
            250,
            750,
            1_500,
            3_000,
            5_000,
            10_000,
            25_000,
            50_000,
            200_000,
            400_000,
            600_000,
            800_000,
            1_000_000,
            1_200_000,
            1_400_000,
        ),
        Umber("UMBER", null, 1_000, 2_500, 10_000, 25_000, 100_000, 250_000, 500_000, 750_000, 1_000_000),
        Coal("COAL", "COAL_GENERATOR_1", 50, 100, 250, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000, 100_000),
        Mycelium("MYCEL", "MYCELIUM_GENERATOR_1", 50, 500, 750, 1_000, 2_500, 10_000, 15_000, 25_000, 50_000, 100_000),
        End_Stone(
            "ENDER_STONE",
            "ENDER_STONE_GENERATOR_1",
            50,
            100,
            250,
            1_000,
            2_500,
            5_000,
            10_000,
            15_000,
            25_000,
            50_000
        ),
        Nether_Quartz("QUARTZ", "QUARTZ_GENERATOR_1", 50, 100, 250, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000),
        Sand("SAND", "SAND_GENERATOR_1", 50, 100, 250, 500, 1_000, 2_500, 5_000),
        Iron_Ingot(
            "IRON_INGOT",
            "IRON_GENERATOR_1",
            50,
            100,
            250,
            1_000,
            2_500,
            5_000,
            10_000,
            25_000,
            50_000,
            100_000,
            200_000,
            400_000,
        ),
        Gemstone(
            "GEMSTONE_COLLECTION",
            null,
            100,
            250,
            1_000,
            2_500,
            5_000,
            25_000,
            100_000,
            250_000,
            500_000,
            1_000_000,
            2_000_000,
        ),
        Tungsten("TUNGSTEN", null, 1_000, 2_500, 10_000, 25_000, 100_000, 250_000, 500_000, 750_000, 1_000_000),
        Obsidian(
            "OBSIDIAN",
            "OBSIDIAN_GENERATOR_1",
            50,
            100,
            250,
            1_000,
            2_500,
            5_000,
            10_000,
            25_000,
            50_000,
            100_000
        ),
        Diamond("DIAMOND", "DIAMOND_GENERATOR_1", 50, 100, 250, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000),
        Cobblestone(
            "COBBLESTONE",
            "COBBLESTONE_GENERATOR_1",
            50,
            100,
            250,
            1_000,
            2_500,
            5_000,
            10_000,
            25_000,
            40_000,
            70_000,
        ),
        Glowstone_Dust("GLOWSTONE_DUST", "GLOWSTONE_GENERATOR_1", 50, 100, 1_000, 2_500, 5_000, 10_000, 25_000),
        Gold_Ingot("GOLD_INGOT", "GOLD_GENERATOR_1", 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000, 25_000),
        Gravel("GRAVEL", "GRAVEL_GENERATOR_1", 50, 100, 250, 1_000, 2_500, 5_000, 10_000, 15_000, 50_000),
        Hard_Stone("HARD_STONE", "HARD_STONE_GENERATOR_1", 50, 1_000, 5_000, 50_000, 150_000, 300_000, 1_000_000),
        Mithril(
            "MITHRIL_ORE",
            "MITHRIL_GENERATOR_1",
            50,
            250,
            1_000,
            2_500,
            5_000,
            10_000,
            250_000,
            500_000,
            1_000_000
        ),
        Emerald("EMERALD", "EMERALD_GENERATOR_1", 50, 100, 250, 1_000, 5_000, 15_000, 30_000, 50_000, 100_000),
        Red_Sand("SAND:1", "RED_SAND_GENERATOR_1", 50, 500, 2_500, 10_000, 15_000, 25_000, 50_000, 100_000),
        Ice("ICE", "ICE_GENERATOR_1", 50, 100, 250, 500, 1_000, 5_000, 10_000, 50_000, 100_000, 250_000, 500_000),
        Glacite("GLACITE", null, 1_000, 2_500, 10_000, 25_000, 100_000, 250_000, 500_000, 750_000, 1_000_000),
        Sulphur("SULPHUR_ORE", null, 200, 1_000, 2_500, 5_000, 10_000, 15_000, 25_000, 50_000, 100_000),
        Netherrack("NETHERRACK", null, 50, 250, 500, 1_000, 5_000),
        ;

        override val tierCount: IntArray = tiers
    }

    enum class Combat(override val id: String, override val minionID: String?, vararg tiers: Int) : Collections {
        Ender_Pearl(
            "ENDER_PEARL",
            "ENDERMAN_GENERATOR_1",
            50,
            250,
            1_000,
            2_500,
            5_000,
            10_000,
            15_000,
            25_000,
            50_000
        ),
        Chili_Pepper("CHILI_PEPPER", null, 10, 25, 75, 250, 1_000, 2_500, 5_000, 10_000, 20_000),
        Slimeball("SLIME_BALL", "SLIME_GENERATOR_1", 50, 100, 250, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000),
        Magma_Cream("MAGMA_CREAM", "MAGMA_CUBE_GENERATOR_1", 50, 250, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000),
        Ghast_Tear("GHAST_TEAR", "GHAST_GENERATOR_1", 20, 250, 1_000, 2_500, 5_000, 10_000, 25_000),
        Gunpowder("SULPHUR", "CREEPER_GENERATOR_1", 50, 100, 250, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000),
        Rotten_Flesh(
            "ROTTEN_FLESH",
            "ZOMBIE_GENERATOR_1",
            50,
            100,
            250,
            1_000,
            2_500,
            5_000,
            10_000,
            25_000,
            50_000,
            100_000
        ),
        Spider_Eye("SPIDER_EYE", "CAVESPIDER_GENERATOR_1", 50, 100, 250, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000),
        Bone("BONE", "SKELETON_GENERATOR_1", 50, 100, 250, 500, 1_000, 5_000, 10_000, 25_000, 50_000, 150_000),
        Blaze_Rod("BLAZE_ROD", "BLAZE_GENERATOR_1", 50, 250, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000),
        String("STRING", "SPIDER_GENERATOR_1", 50, 100, 250, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000),
        ;

        override val tierCount: IntArray = tiers
    }

    enum class Foraging(override val id: String, override val minionID: String?, vararg tiers: Int) : Collections {
        Acacia_Wood("LOG_2", "ACACIA_GENERATOR_1", 50, 100, 250, 500, 1_000, 2_000, 5_000, 10_000, 25_000),
        Spruce_Wood("LOG:1", "SPRUCE_GENERATOR_1", 50, 100, 250, 1_000, 2_000, 5_000, 10_000, 25_000, 50_000),
        Jungle_Wood("LOG:3", "JUNGLE_GENERATOR_1", 50, 100, 250, 500, 1_000, 2_000, 5_000, 10_000, 25_000),
        Birch_Wood("LOG:2", "BIRCH_GENERATOR_1", 50, 100, 250, 500, 1_000, 2_000, 5_000, 10_000, 25_000, 50_000),
        Oak_Wood("LOG", "OAK_GENERATOR_1", 50, 100, 250, 500, 1_000, 2_000, 5_000, 10_000, 30_000),
        Dark_Oak_Wood("LOG_2:1", "DARK_OAK_GENERATOR_1", 50, 100, 250, 1_000, 2_000, 5_000, 10_000, 15_000, 25_000),
        ;

        override val tierCount: IntArray = tiers
    }

    enum class Fishing(override val id: String, override val minionID: String?, vararg tiers: Int) : Collections {
        Lily_Pad("WATER_LILY", null, 10, 50, 100, 200, 500, 1_500, 3_000, 6_000, 10_000),
        Prismarine_Shard("PRISMARINE_SHARD", "FISHING_GENERATOR_1", 10, 25, 50, 100, 200, 400, 800),
        Ink_Sac("INK_SACK", null, 20, 40, 100, 200, 400, 800, 1_500, 2_500, 4_000),
        Raw_Fish(
            "RAW_FISH",
            "FISHING_GENERATOR_1",
            20,
            50,
            100,
            250,
            500,
            1_000,
            2_500,
            15_000,
            30_000,
            45_000,
            60_000
        ),
        Clownfish("RAW_FISH:2", "FISHING_GENERATOR_1", 10, 25, 50, 100, 200, 400, 800, 1_600, 4_000),
        Raw_Salmon("RAW_FISH:1", "FISHING_GENERATOR_1", 20, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000),
        Magmafish(
            "MAGMA_FISH",
            null,
            20,
            100,
            500,
            1_000,
            5_000,
            15_000,
            30_000,
            50_000,
            75_000,
            100_000,
            250_000,
            500_000
        ),
        Prismarine_Crystals("PRISMARINE_CRYSTALS", "FISHING_GENERATOR_1", 10, 25, 50, 100, 200, 400, 800),
        Clay("CLAY_BALL", "CLAY_GENERATOR_1", 50, 100, 250, 1_000, 1_500, 2_500),
        Sponge("SPONGE", "FISHING_GENERATOR_1", 20, 40, 100, 200, 400, 800, 1_500, 2_500, 4_000);

        override val tierCount: IntArray = tiers

    }

    enum class Rift(override val id: String, vararg tiers: Int) : Collections {
        Wilted_Berberis("WILTED_BERBERIS", 20, 60, 140, 400),
        Living_Metal_Heart("METAL_HEART", 1, 20, 60, 100),
        Caducous_Stem("CADUCOUS_STEM", 20, 60, 150, 500),
        Agaricus_Cap("AGARICUS_CAP", 20, 60, 100, 200),
        Hemovibe("HEMOVIBE", 50, 250, 1_000, 5_000, 15_000, 30_000, 80_000, 150_000, 400_000),
        Half_Eaten_Carrot("HALF_EATEN_CARROT", 1, 400, 1_000, 3_500),
        ;

        override val tierCount: IntArray = tiers

        override val minionID: String? = null
    }

    companion object {
        @JvmStatic
        fun values(): MutableSet<Collections> {
            val collections: MutableSet<Collections> = HashSet()
            collections.addAll(Farming.entries)
            collections.addAll(Foraging.entries)
            collections.addAll(Mining.entries)
            collections.addAll(Fishing.entries)
            collections.addAll(Combat.entries)
            return collections
        }

        fun getCollectionById(id: String): Collections? {
            for (value in values()) {
                if (value.id.equals(id, ignoreCase = true)) {
                    return value
                }
            }
            return null
        }

        @JvmStatic
        fun getCodeReference(goalName: String): String? {
            var goalName = goalName
            goalName = goalName.replace("_", " ")
            for (value in values()) {
                if (value.displayName.equals(goalName, ignoreCase = true)) {
                    return value.javaClass.name.replace("\\$[0-9]+".toRegex(), "").replace("$", ".")
                        .replace("de.hype.bingonet.shared.constants.", "") + "." + value.toString()
                }
            }
            return null
        }

        val values: MutableSet<Collections> = values()
    }

    fun asNEUItem(): BNNEUItem {
        return NeuEnvironmentRepo.getFromSBName(id)!!
    }
}
