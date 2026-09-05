package com.maduka.rentmanager.util;

import java.util.ArrayList;
import java.util.List;

public final class VerseProvider {
    private VerseProvider() {}

    public static final class Verse {
        public final String text;
        public final String reference;
        public Verse(String text, String reference) { this.text = text; this.reference = reference; }
    }

    private static final List<Verse> POOL = new ArrayList<>();
    static {
        POOL.add(new Verse(
                "A faithful man shall abound with blessings: but he that maketh haste to be rich shall not be innocent.",
                "Proverbs 28:20, KJV"));
        POOL.add(new Verse(
                "He that is faithful in that which is least is faithful also in much: and he that is unjust in the least is unjust also in much.",
                "Luke 16:10, KJV"));
        POOL.add(new Verse(
                "Moreover it is required in stewards, that a man be found faithful.",
                "1 Corinthians 4:2, KJV"));
        POOL.add(new Verse(
                "Well done, thou good and faithful servant: thou hast been faithful over a few things, I will make thee ruler over many things: enter thou into the joy of thy lord.",
                "Matthew 25:21, KJV"));
        POOL.add(new Verse(
                "Let not mercy and truth forsake thee: bind them about thy neck; write them upon the table of thine heart.",
                "Proverbs 3:3, KJV"));
    }

    public static int poolSize() { return POOL.size(); }

    /** Deterministic pick so the same seed (e.g. a sign-in counter) always yields the same verse. */
    public static Verse pickVerse(long seed) {
        int index = (int) (((seed % POOL.size()) + POOL.size()) % POOL.size());
        return POOL.get(index);
    }
}
