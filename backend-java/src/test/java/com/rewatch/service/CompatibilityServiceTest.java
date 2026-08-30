package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitNode;
import com.rewatch.model.TraitVector;
import com.rewatch.model.User;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.UserRepository;

/**
 * Covers CompatibilityService — the no-signup taste-compatibility check.
 * VectorEngine is real (pure, no collaborators — no need to fake it, unlike
 * ProfileService elsewhere in this package). ProfileService is faked the
 * same way RoastServiceTest does it, since Mockito can't mock a concrete
 * class here.
 */
@ExtendWith(MockitoExtension.class)
class CompatibilityServiceTest {

    @Mock private TitleRepository titleRepo;
    @Mock private UserRepository userRepo;
    @Mock private RatingRepository ratingRepo;

    private static class FakeProfileService extends ProfileService {
        private final Map<Trait, TraitNode> profile;

        FakeProfileService(Map<Trait, TraitNode> profile) {
            super(null, null, null, null, null, null, null);
            this.profile = profile;
        }

        @Override
        public Map<Trait, TraitNode> currentProfile(Long userId) {
            return profile;
        }
    }

    private CompatibilityService newService(Map<Trait, TraitNode> targetProfile) {
        return new CompatibilityService(titleRepo, userRepo, ratingRepo,
                new FakeProfileService(targetProfile), new VectorEngine());
    }

    private long nextId = 1;

    private Title title(double popularity, Map<Trait, Double> traits) {
        Title t = new Title();
        t.setId(nextId++);
        t.setTitle("Title " + t.getId());
        t.setSynopsis("A real synopsis.");
        t.setPoster("/poster.jpg");
        t.setVoteCount(500);
        t.setPopularity(popularity);
        TraitVector v = TraitVector.neutral();
        for (Map.Entry<Trait, Double> e : traits.entrySet()) {
            v = v.with(e.getKey(), e.getValue());
        }
        t.setTraitVector(v);
        return t;
    }

    private Map<Trait, TraitNode> profileWith(Trait trait, double val) {
        Map<Trait, TraitNode> m = new java.util.EnumMap<>(Trait.class);
        for (Trait t : Trait.values()) {
            m.put(t, new TraitNode(t == trait ? val : 0.5, 0.9, 0, 20, Instant.now()));
        }
        return m;
    }

    private User publicUserWithRatings(String username, long ratingCount) {
        User u = new User();
        u.setId(99L);
        u.setUsername(username);
        when(userRepo.findByUsername(username)).thenReturn(u);
        when(ratingRepo.countByUserId(99L)).thenReturn(ratingCount);
        return u;
    }

    // Eight distinct, spread-out titles so quiz() must select all of them
    // regardless of the greedy algorithm's exact tie-breaking.
    private List<Title> eightSpreadTitles() {
        List<Title> titles = new ArrayList<>();
        double pop = 100;
        for (Trait t : List.of(Trait.INTENSITY, Trait.HUMOR, Trait.ROMANCE, Trait.BITTER,
                Trait.GROWTH, Trait.PACING, Trait.COMFORT, Trait.HOPE)) {
            titles.add(title(pop--, Map.of(t, 0.95)));
        }
        return titles;
    }

    @Test
    void quizSelectsAllEligibleTitlesWhenPoolIsExactlyQuizSize() {
        List<Title> titles = eightSpreadTitles();
        when(titleRepo.findAll()).thenReturn(titles);

        List<Title> quiz = newService(profileWith(Trait.INTENSITY, 0.9)).quiz();

        assertEquals(CompatibilityService.QUIZ_SIZE, quiz.size());
    }

    @Test
    void quizExcludesTitlesBelowTheVoteFloor() {
        List<Title> titles = eightSpreadTitles();
        Title lowVotes = title(999, Map.of(Trait.HUMOR, 0.9));
        lowVotes.setVoteCount(3);
        titles.add(lowVotes);
        when(titleRepo.findAll()).thenReturn(titles);

        List<Title> quiz = newService(profileWith(Trait.INTENSITY, 0.9)).quiz();

        assertTrue(quiz.stream().noneMatch(t -> t.getId().equals(lowVotes.getId())));
    }

    @Test
    void unknownTargetUsernameReturnsNull() {
        when(userRepo.findByUsername("nobody")).thenReturn(null);

        CompatibilityService.CompatibilityResult result =
                newService(profileWith(Trait.INTENSITY, 0.9)).check("nobody", List.of());

        assertNull(result);
    }

    @Test
    void privateProfileReturnsNull() {
        User u = new User();
        u.setId(99L);
        u.setUsername("private_user");
        u.setProfilePublic(false);
        when(userRepo.findByUsername("private_user")).thenReturn(u);

        CompatibilityService.CompatibilityResult result =
                newService(profileWith(Trait.INTENSITY, 0.9)).check("private_user", List.of());

        assertNull(result);
    }

    @Test
    void targetBelowRatingsFloorReturnsNull() {
        publicUserWithRatings("thin_profile", 1);

        CompatibilityService.CompatibilityResult result =
                newService(profileWith(Trait.INTENSITY, 0.9)).check("thin_profile", List.of());

        assertNull(result);
    }

    @Test
    void alignedTastesProduceHighCompatibility() {
        List<Title> titles = eightSpreadTitles();
        when(titleRepo.findAll()).thenReturn(titles);
        publicUserWithRatings("intense_fan", 10);

        // The visitor rates every INTENSITY-heavy quiz title 5 stars, nothing
        // else — should land close to the target's own high-INTENSITY profile.
        List<CompatibilityService.QuizResponse> responses = titles.stream()
                .filter(t -> t.traitVector().get(Trait.INTENSITY) > 0.7)
                .map(t -> new CompatibilityService.QuizResponse(t.getId(), 5))
                .toList();

        CompatibilityService.CompatibilityResult result =
                newService(profileWith(Trait.INTENSITY, 0.95)).check("intense_fan", responses);

        assertTrue(result.compatibilityPercent() >= 60,
                "expected high compatibility, got " + result.compatibilityPercent());
        assertTrue(result.sharedTraits().contains("Emotional Intensity"));
    }

    @Test
    void oppositeTastesProduceLowCompatibility() {
        List<Title> titles = eightSpreadTitles();
        when(titleRepo.findAll()).thenReturn(titles);
        publicUserWithRatings("intense_fan", 10);

        // The visitor HATES the intense title and loves the opposite-feeling one.
        List<CompatibilityService.QuizResponse> responses = titles.stream()
                .map(t -> new CompatibilityService.QuizResponse(
                        t.getId(), t.traitVector().get(Trait.INTENSITY) > 0.7 ? 1 : 5))
                .toList();

        CompatibilityService.CompatibilityResult result =
                newService(profileWith(Trait.INTENSITY, 0.95)).check("intense_fan", responses);

        assertTrue(result.compatibilityPercent() < 50,
                "expected low compatibility, got " + result.compatibilityPercent());
    }

    @Test
    void nonQuizTitleIdInResponsesIsSilentlyIgnored() {
        List<Title> titles = eightSpreadTitles();
        when(titleRepo.findAll()).thenReturn(titles);
        publicUserWithRatings("someone", 10);

        List<CompatibilityService.QuizResponse> responses =
                List.of(new CompatibilityService.QuizResponse(99999L, 5));

        CompatibilityService.CompatibilityResult result =
                newService(profileWith(Trait.INTENSITY, 0.9)).check("someone", responses);

        assertEquals(50, result.compatibilityPercent());
    }
}
