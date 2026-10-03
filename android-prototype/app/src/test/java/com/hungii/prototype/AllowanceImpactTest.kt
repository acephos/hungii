package com.hungii.prototype

import org.junit.Assert.*
import org.junit.Test

class AllowanceImpactTest {
    @Test fun basketEstimateIncludesExtrasInsteadOfAnchoringToMenuPrice() {
        val impact = allowanceImpact(240.0, 180.0, 300)!!
        assertTrue(impact.estimatedBasket)
        assertEquals(80, impact.percentage)
        assertEquals(60.0, impact.remaining, 0.0)
    }
    @Test fun menuOnlyComparisonPreservesFeeQualification() {
        val impact = allowanceImpact(null, 180.0, 300)!!
        assertFalse(impact.estimatedBasket)
        assertEquals(60, impact.percentage)
        assertEquals(120.0, impact.remaining, 0.0)
    }
    @Test fun overspendIsNotClampedIntoAnAffordablePrice() {
        val impact = allowanceImpact(350.0, 290.0, 300)!!
        assertEquals(-50.0, impact.remaining, 0.0)
        assertEquals(117, impact.percentage)
    }
    @Test fun zeroAndNegativeAllowanceNeverInventAPercentage() {
        assertNull(allowanceImpact(null, 100.0, 0)!!.percentage)
        assertNull(allowanceImpact(null, 100.0, -30)!!.percentage)
        assertEquals(-130.0, allowanceImpact(null, 100.0, -30)!!.remaining, 0.0)
    }
    @Test fun unknownAndInvalidPricesAreNotShownAsFree() {
        assertNull(allowanceImpact(null, null, 300))
        assertNull(allowanceImpact(Double.NaN, -1.0, 300))
        assertNull(allowanceImpact(Double.POSITIVE_INFINITY, null, 300))
        assertFalse(allowanceImpact(-1.0, 100.0, 300)!!.estimatedBasket)
    }
    @Test fun explicitZeroPriceRemainsKnown() {
        val impact = allowanceImpact(null, 0.0, 300)!!
        assertEquals(0, impact.percentage)
        assertEquals(300.0, impact.remaining, 0.0)
    }
}
