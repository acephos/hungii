package com.hungii.prototype

import kotlin.math.roundToInt

internal data class AllowanceImpact(
    val total: Double,
    val estimatedBasket: Boolean,
    val remaining: Double,
    val percentage: Int?,
)

/** Menu-only prices never imply that delivery and fees are included. */
internal fun allowanceImpact(estimatedPayable: Double?, itemPrice: Double?, moneyLeft: Int): AllowanceImpact? {
    val basket = estimatedPayable?.takeIf { it.isFinite() && it >= 0 }
    val total = basket ?: itemPrice?.takeIf { it.isFinite() && it >= 0 } ?: return null
    return AllowanceImpact(total, basket != null, moneyLeft - total,
        if (moneyLeft > 0) (total / moneyLeft * 100).roundToInt() else null)
}
