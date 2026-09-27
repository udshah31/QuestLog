package com.example.questlog.billing

import com.revenuecat.purchases.models.Period
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfferTextTest {
    @Test fun `period label omits a single unit's number`() {
        assertEquals("month", periodLabel(1, Period.Unit.MONTH))
        assertEquals("week", periodLabel(1, Period.Unit.WEEK))
        assertEquals("year", periodLabel(1, Period.Unit.YEAR))
        assertEquals("day", periodLabel(1, Period.Unit.DAY))
    }

    @Test fun `period label pluralises`() {
        assertEquals("3 months", periodLabel(3, Period.Unit.MONTH))
        assertEquals("7 days", periodLabel(7, Period.Unit.DAY))
    }

    @Test fun `trial label always carries the number`() {
        assertEquals("7 days free", trialLabel(7, Period.Unit.DAY))
        assertEquals("1 week free", trialLabel(1, Period.Unit.WEEK))
        assertEquals("1 month free", trialLabel(1, Period.Unit.MONTH))
    }

    @Test fun `unknown unit yields null`() {
        assertNull(periodLabel(1, Period.Unit.UNKNOWN))
        assertNull(trialLabel(7, Period.Unit.UNKNOWN))
    }

    @Test fun `price line without an intro phase`() {
        assertEquals("$4.99 / month", priceLine("$4.99", "month", intro = null))
        assertEquals("$19.99", priceLine("$19.99", null, intro = null))
    }

    @Test fun `paid intro phase is shown before the full price`() = assertEquals(
        "$0.99 for 3 months, then $4.99 / month",
        priceLine("$4.99", "month", intro = "$0.99" to "3 months"),
    )

    @Test fun `length label counts the whole intro`() {
        assertEquals("1 month", lengthLabel(1, Period.Unit.MONTH))
        assertEquals("3 months", lengthLabel(3, Period.Unit.MONTH))
        assertNull(lengthLabel(1, Period.Unit.UNKNOWN))
    }
}
