package com.rewatch.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class GenreLexiconNamesTest {

    @Test
    void resolvesKnownIdsToNames() {
        assertEquals(List.of("Action", "Horror"), GenreLexicon.namesFor("28,27"));
    }

    @Test
    void nullOrBlankYieldsEmptyList() {
        assertTrue(GenreLexicon.namesFor(null).isEmpty());
        assertTrue(GenreLexicon.namesFor("").isEmpty());
    }

    @Test
    void unknownOrMalformedIdsAreSkippedNotThrown() {
        // 999999 isn't a real TMDB genre id; "oops" isn't a number at all —
        // one bad id in stored data shouldn't break resolution of the rest.
        assertEquals(List.of("Comedy"), GenreLexicon.namesFor("999999,35,oops"));
    }

    @Test
    void duplicateNamesAreNotRepeated() {
        // Movie id 16 and the (different-numbered) TV animation id both
        // resolve to "Animation" in real data — the result should still only
        // list it once.
        assertEquals(List.of("Animation"), GenreLexicon.namesFor("16,16"));
    }
}
