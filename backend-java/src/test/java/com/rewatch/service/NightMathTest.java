package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.rewatch.model.Trait;
import com.rewatch.model.TraitVector;

class NightMathTest {

    @Test
    void answersMapToTraitDirections() {
        // edge of my seat, laugh, found family, happy ending, never seen
        TraitVector v = NightMath.vectorFromAnswers(List.of(2, 0, 0, 0, 1));
        assertTrue(v.get(Trait.PACING) > 0.5);
        assertTrue(v.get(Trait.INTENSITY) > 0.5);
        assertTrue(v.get(Trait.HUMOR) > 0.5);
        assertTrue(v.get(Trait.FAMILY) > 0.5);
        assertTrue(v.get(Trait.BITTER) < 0.5);
        assertTrue(v.get(Trait.NOSTALGIA) < 0.5);
    }

    @Test
    void neutralAnswersStayNeutral() {
        TraitVector v = NightMath.vectorFromAnswers(List.of(1, 0, 0, 2, 0));
        assertEquals(0.5, v.get(Trait.PACING), 1e-9);
    }

    @Test
    void rejectsWrongLengthOrOutOfRangeAnswers() {
        assertNull(NightMath.vectorFromAnswers(null));
        assertNull(NightMath.vectorFromAnswers(List.of(0, 0, 0, 0)));
        assertNull(NightMath.vectorFromAnswers(List.of(0, 0, 0, 0, 9)));
        assertNull(NightMath.vectorFromAnswers(List.of(0, 0, 0, 0, -1)));
        assertNull(NightMath.vectorFromAnswers(java.util.Arrays.asList(0, 0, null, 0, 0)));
    }

    @Test
    void vectorsStayInsideUnitRange() {
        TraitVector v = NightMath.vectorFromAnswers(List.of(0, 1, 0, 0, 0));
        for (Trait t : Trait.values()) {
            assertTrue(v.get(t) >= 0 && v.get(t) <= 1);
        }
    }

    @Test
    void agreementBeatsAHigherAverageWithAVeto() {
        double even = NightMath.groupScore(List.of(80.0, 80.0, 80.0));
        double divisive = NightMath.groupScore(List.of(100.0, 100.0, 40.0));
        assertTrue(even > divisive, "even=" + even + " divisive=" + divisive);
    }

    @Test
    void singleMemberScoreIsUnpenalised() {
        assertEquals(72.0, NightMath.groupScore(List.of(72.0)), 1e-9);
    }

    @Test
    void groupScoreNeverNegative() {
        assertTrue(NightMath.groupScore(List.of(0.0, 100.0)) >= 0);
    }

    @Test
    void vectorRoundTripsThroughStorage() {
        TraitVector v = NightMath.vectorFromAnswers(List.of(2, 1, 0, 1, 0));
        TraitVector back = NightMath.decodeVector(NightMath.encodeVector(v));
        for (Trait t : Trait.values()) {
            assertEquals(v.get(t), back.get(t), 1e-3);
        }
    }

    @Test
    void sharedReasonPrefersATraitTheGroupCaresAbout() {
        TraitVector group = NightMath.vectorFromAnswers(List.of(2, 0, 0, 0, 0));
        TraitVector title = group; // perfect agreement
        Trait why = NightMath.sharedReason(group, title);
        assertNotNull(why);
        assertNotEquals(0.5, group.get(why), 1e-9);
    }

    @Test
    void sanitizeNameStripsMarkupAndCaps() {
        assertEquals("Mira", NightMath.sanitizeName("  Mira  "));
        assertEquals("scriptalert(1)/script", NightMath.sanitizeName("<script>alert(1)</script>"));
        assertEquals(24, NightMath.sanitizeName("x".repeat(80)).length());
        assertNull(NightMath.sanitizeName("   "));
        assertNull(NightMath.sanitizeName("<>"));
        assertNull(NightMath.sanitizeName(null));
    }

    @Test
    void fiveQuestionsEachWithAtLeastTwoOptions() {
        assertEquals(5, NightMath.QUESTIONS.size());
        NightMath.QUESTIONS.forEach(q -> assertTrue(q.options().size() >= 2));
    }
}
