package com.maduka.rentmanager.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MoneyFormatterTest {
    @Test
    public void compact_belowThousand_showsExactNumber() {
        assertEquals("500", MoneyFormatter.compact(500));
    }

    @Test
    public void compact_thousands_roundsToK() {
        assertEquals("240K", MoneyFormatter.compact(240_000));
    }

    @Test
    public void compact_millions_showsTwoDecimalM() {
        assertEquals("2.18M", MoneyFormatter.compact(2_175_000));
    }

    @Test
    public void full_addsThousandsSeparators() {
        assertEquals("240,000", MoneyFormatter.full(240_000));
        assertEquals("2,175,000", MoneyFormatter.full(2_175_000));
    }
}
