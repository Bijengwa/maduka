// DateCalculatorTest.java
package com.maduka.rentmanager.util;

import org.junit.Test;
import java.util.Calendar;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class DateCalculatorTest {

    private long ymd(int y, int m, int d) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(y, m - 1, d);
        return c.getTimeInMillis();
    }

    @Test
    public void addMonths_addsCalendarMonths_notFixedThirtyDayBlocks() {
        // Canvas sample: Asha M. paid 2 months on 02/09/2026 -> due 02/11/2026
        long paid = ymd(2026, 9, 2);
        long due = DateCalculator.addMonths(paid, 2);
        assertEquals(ymd(2026, 11, 2), due);
    }

    @Test
    public void isOverdue_trueWhenDueDateInThePast() {
        long due = ymd(2026, 8, 15);
        long now = ymd(2026, 9, 4);
        assertTrue(DateCalculator.isOverdue(due, now));
    }

    @Test
    public void isOverdue_falseWhenDueDateInTheFuture() {
        long due = ymd(2026, 11, 2);
        long now = ymd(2026, 9, 4);
        assertFalse(DateCalculator.isOverdue(due, now));
    }

    @Test
    public void daysBetween_matchesCanvasSample_neemaTwentyDaysOverdue() {
        long due = ymd(2026, 8, 15);
        long now = ymd(2026, 9, 4);
        assertEquals(20, DateCalculator.daysBetween(due, now));
    }

    @Test
    public void formatDdMmYyyy_matchesCanvasFormat() {
        assertEquals("02/09/2026", DateCalculator.formatDdMmYyyy(ymd(2026, 9, 2)));
    }
}
