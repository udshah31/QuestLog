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
}
