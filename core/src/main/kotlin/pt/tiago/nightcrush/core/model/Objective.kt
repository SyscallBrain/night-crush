package pt.tiago.nightcrush.core.model

/** Special-piece kinds that orders can ask for. */
enum class SpecialOrderKind { RAY, JELLY, LANTERN_FISH, ABYSS_PEARL }

/** The special combinations tracked by orders and achievements. */
enum class ComboKind {
    RAY_RAY, RAY_JELLY, JELLY_JELLY,
    FISH_RAY, FISH_JELLY, FISH_FISH,
    PEARL_PLAIN, PEARL_RAY, PEARL_JELLY, PEARL_FISH, PEARL_PEARL,
}

/** One entry of an order list ("encomendas"). */
sealed interface Order {
    val target: Int

    data class MakeSpecial(val kind: SpecialOrderKind, override val target: Int) : Order
    data class MakeCombo(val combo: ComboKind, override val target: Int) : Order
    data class Collect(val color: Int, override val target: Int) : Order
    data class BreakLayers(val kind: BlockerKind, override val target: Int) : Order

    /** Events this order counts, for the level tool agent. */
    fun key(): String = when (this) {
        is MakeSpecial -> "make-${kind.name.lowercase()}"
        is MakeCombo -> "combo-${combo.name.lowercase()}"
        is Collect -> "collect-$color"
        is BreakLayers -> "break-${kind.name.lowercase()}"
    }
}

/**
 * Level objectives. Every objective owns a contiguous slice of the state's
 * progress array; [slots] is that slice's length.
 */
sealed interface Objective {
    val slots: Int

    data class CollectColors(val counts: IntArray) : Objective {
        override val slots: Int get() = 6
    }

    data class ClearSilt(val target: Int) : Objective {
        override val slots: Int get() = 1
    }

    data class DeliverLanterns(val target: Int) : Objective {
        override val slots: Int get() = 1
    }

    data class Orders(val orders: List<Order>) : Objective {
        override val slots: Int get() = orders.size
    }

    data class ClearBlockers(val counts: IntArray) : Objective {
        override val slots: Int get() = 6
    }

    data object SpreadGlow : Objective {
        override val slots: Int get() = 1
    }

    data class FreeSeahorses(val target: Int) : Objective {
        override val slots: Int get() = 1
    }
}
