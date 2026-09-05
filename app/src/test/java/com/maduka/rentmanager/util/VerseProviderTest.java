// VerseProviderTest.java
package com.maduka.rentmanager.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class VerseProviderTest {
    @Test
    public void pickVerse_isDeterministicForSameSeed() {
        assertEquals(VerseProvider.pickVerse(7).reference, VerseProvider.pickVerse(7).reference);
    }

    @Test
    public void pickVerse_cyclesThroughAllVersesAsSeedIncreases() {
        int poolSize = VerseProvider.poolSize();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < poolSize; i++) seen.add(VerseProvider.pickVerse(i).reference);
        assertEquals(poolSize, seen.size());
    }

    @Test
    public void firstVerseMatchesCanvas() {
        VerseProvider.Verse v = VerseProvider.pickVerse(0);
        assertEquals("Proverbs 28:20, KJV", v.reference);
        assertTrue(v.text.startsWith("A faithful man shall abound with blessings"));
    }
}
