package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.dto.OnboardingRequest;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitVector;
import com.rewatch.repository.TitleRepository;

/**
 * Covers the three onboarding inputs that route into deriveSeed()'s trait
 * accumulation via the same additive-delta pattern as GENRE_SENTIMENT: story
 * tropes, emotional goals, and dealbreakers. The dealbreaker case is a
 * regression test — req.getAvoid() used to be collected by the wizard and
 * the DTO but never actually read by deriveSeed(), so it silently did
 * nothing.
 */
@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock private TitleRepository titleRepo;

    private OnboardingService newService() {
        return new OnboardingService(titleRepo);
    }

    private OnboardingRequest req() {
        OnboardingRequest r = new OnboardingRequest();
        r.setUserId(1L);
        return r;
    }

    @Test
    void tropesContributeInTheExpectedDirection() {
        OnboardingRequest r = req();
        r.setTropes(List.of("Character Growth"));

        TraitVector seed = newService().deriveSeed(r);

        assertTrue(seed.get(Trait.GROWTH) > TraitVector.NEUTRAL);
    }

    @Test
    void emotionalGoalsContributeInTheExpectedDirection() {
        OnboardingRequest r = req();
        r.setEmotionalGoals(List.of("Escape"));

        TraitVector seed = newService().deriveSeed(r);

        assertTrue(seed.get(Trait.COMFORT) > TraitVector.NEUTRAL);
        assertTrue(seed.get(Trait.INTENSITY) < TraitVector.NEUTRAL);
    }

    @Test
    void dealbreakersNowSubtractRealSignal() {
        OnboardingRequest r = req();
        r.setAvoid(List.of("Excessive Gore"));

        TraitVector seed = newService().deriveSeed(r);

        assertTrue(seed.get(Trait.INTENSITY) < TraitVector.NEUTRAL);
    }

    @Test
    void maxIntensitySliderNeverReachesExactlyOne() {
        OnboardingRequest r = req();
        r.setIntensity(10);

        TraitVector seed = newService().deriveSeed(r);

        assertTrue(seed.get(Trait.INTENSITY) < 1.0);
    }

    @Test
    void hyperFastPacingSliderNeverReachesExactlyZero() {
        // Pacing is inverted (hyper-fast slider -> low PACING trait), so the
        // fastest slider position (5) is the one that risks hitting the floor.
        OnboardingRequest r = req();
        r.setPacing(5);

        TraitVector seed = newService().deriveSeed(r);

        assertTrue(seed.get(Trait.PACING) > 0.0);
    }

    @Test
    void unknownSelectionStringIsIgnoredNotThrown() {
        OnboardingRequest r = req();
        r.setTropes(List.of("Not A Real Trope"));
        r.setEmotionalGoals(List.of("Not A Real Goal"));
        r.setAvoid(List.of("Not A Real Dealbreaker"));

        assertDoesNotThrow(() -> newService().deriveSeed(r));
    }
}
