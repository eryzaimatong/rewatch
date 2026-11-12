package com.rewatch.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.dto.MovieDTO;
import com.rewatch.model.MovieNight;
import com.rewatch.model.MovieNightMember;
import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitVector;
import com.rewatch.repository.MovieNightMemberRepository;
import com.rewatch.repository.MovieNightRepository;

/**
 * Movie Night: friends open one shared link, each answers five mood questions
 * (no account), and the group gets three titles chosen to be fair to everyone.
 * See {@link NightMath} for the scoring rule.
 */
@Service
public class NightService {

    static final Duration LIFETIME = Duration.ofHours(24);
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0/O/1/I/L
    private static final int CODE_LENGTH = 6;
    private static final int SHORTLIST = 400;
    private static final int PICKS = 3;
    private static final double MEMBER_CONFIDENCE = 0.6;

    public enum JoinStatus { OK, NOT_FOUND, FULL, BAD_INPUT }

    public record Joined(JoinStatus status, String code, Long memberId) { }

    private final MovieNightRepository nights;
    private final MovieNightMemberRepository members;
    private final Recommender recommender;
    private final SecureRandom random = new SecureRandom();

    public NightService(MovieNightRepository nights, MovieNightMemberRepository members, Recommender recommender) {
        this.nights = nights;
        this.members = members;
        this.recommender = recommender;
    }

    public List<Map<String, Object>> questions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < NightMath.QUESTIONS.size(); i++) {
            NightMath.Question q = NightMath.QUESTIONS.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.id());
            m.put("prompt", q.prompt());
            m.put("options", q.options().stream().map(NightMath.Option::label).toList());
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Joined create(String rawName, List<Integer> answers) {
        String name = NightMath.sanitizeName(rawName);
        TraitVector vec = NightMath.vectorFromAnswers(answers);
        if (name == null || vec == null) {
            return new Joined(JoinStatus.BAD_INPUT, null, null);
        }
        Instant now = Instant.now();
        MovieNight night = null;
        for (int attempt = 0; attempt < 5 && night == null; attempt++) {
            try {
                night = nights.saveAndFlush(new MovieNight(newCode(), now, now.plus(LIFETIME)));
            } catch (DataIntegrityViolationException collision) {
                // vanishingly rare code collision: draw another
            }
        }
        if (night == null) {
            throw new IllegalStateException("could not allocate a movie night code");
        }
        MovieNightMember m = members.save(member(night.getId(), name, answers, vec, now));
        return new Joined(JoinStatus.OK, night.getCode(), m.getId());
    }

    @Transactional
    public Joined join(String code, String rawName, List<Integer> answers) {
        Optional<MovieNight> found = findLive(code);
        if (found.isEmpty()) {
            return new Joined(JoinStatus.NOT_FOUND, null, null);
        }
        String name = NightMath.sanitizeName(rawName);
        TraitVector vec = NightMath.vectorFromAnswers(answers);
        if (name == null || vec == null) {
            return new Joined(JoinStatus.BAD_INPUT, null, null);
        }
        MovieNight night = found.get();
        if (members.countByNightId(night.getId()) >= NightMath.MAX_MEMBERS) {
            return new Joined(JoinStatus.FULL, night.getCode(), null);
        }
        MovieNightMember m = members.save(member(night.getId(), name, answers, vec, Instant.now()));
        return new Joined(JoinStatus.OK, night.getCode(), m.getId());
    }

    /** Null when the night does not exist or has expired. */
    @Transactional(readOnly = true)
    public Map<String, Object> view(String code) {
        Optional<MovieNight> found = findLive(code);
        if (found.isEmpty()) {
            return null;
        }
        MovieNight night = found.get();
        List<MovieNightMember> ms = members.findByNightIdOrderByIdAsc(night.getId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", night.getCode());
        out.put("expiresAt", night.getExpiresAt().toString());
        out.put("maxMembers", NightMath.MAX_MEMBERS);
        out.put("members", ms.stream().map(m -> Map.of("id", m.getId(), "name", m.getName())).toList());
        // One person is a quiz, not a night: picks need at least two friends.
        out.put("picks", ms.size() >= 2 ? picks(ms) : List.of());
        return out;
    }

    List<Map<String, Object>> picks(List<MovieNightMember> ms) {
        List<TraitVector> vectors = ms.stream().map(m -> NightMath.decodeVector(m.getVector())).toList();
        TraitVector group = NightMath.average(vectors);
        double[] conf = new double[Trait.count()];
        java.util.Arrays.fill(conf, MEMBER_CONFIDENCE);

        List<Title> shortlist = recommender.nightCandidates().stream()
                .sorted(Comparator.comparingDouble(t -> distance(group, t.traitVector())))
                .limit(SHORTLIST)
                .toList();

        record Ranked(Title title, double group, List<Double> each) { }
        List<Ranked> ranked = new ArrayList<>();
        for (Title t : shortlist) {
            List<Double> each = new ArrayList<>(vectors.size());
            for (TraitVector v : vectors) {
                MovieDTO d = recommender.scoreAgainstVector(t, v, conf);
                each.add(d.getMatchScore());
            }
            ranked.add(new Ranked(t, NightMath.groupScore(each), each));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::group).reversed());

        List<Map<String, Object>> out = new ArrayList<>();
        for (Ranked r : ranked) {
            if (out.size() == PICKS) {
                break;
            }
            Trait why = NightMath.sharedReason(group, r.title().traitVector());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("titleId", r.title().getId());
            m.put("title", r.title().getTitle());
            m.put("year", r.title().getYear());
            m.put("posterPath", r.title().getPoster());
            m.put("overview", r.title().getSynopsis());
            m.put("genres", com.rewatch.features.GenreLexicon.namesFor(r.title().getGenreIds()));
            m.put("groupScore", Math.round(r.group()));
            List<Map<String, Object>> per = new ArrayList<>();
            for (int i = 0; i < ms.size(); i++) {
                per.add(Map.of("name", ms.get(i).getName(), "score", Math.round(r.each().get(i))));
            }
            m.put("members", per);
            m.put("reason", why == null ? "" : why.label());
            out.add(m);
        }
        return out;
    }

    private static double distance(TraitVector a, TraitVector b) {
        double s = 0;
        for (int i = 0; i < Trait.count(); i++) {
            double d = a.get(i) - b.get(i);
            s += d * d;
        }
        return s;
    }

    private Optional<MovieNight> findLive(String code) {
        if (code == null || code.length() != CODE_LENGTH) {
            return Optional.empty();
        }
        return nights.findByCode(code.toUpperCase(java.util.Locale.ROOT))
                .filter(n -> n.getExpiresAt().isAfter(Instant.now()));
    }

    private static MovieNightMember member(Long nightId, String name, List<Integer> answers, TraitVector vec, Instant now) {
        String a = String.join(",", answers.stream().map(String::valueOf).toList());
        return new MovieNightMember(nightId, name, a, NightMath.encodeVector(vec), now);
    }

    private String newCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Expired nights hold nothing worth keeping: drop them hourly. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 600_000)
    @Transactional
    public void purgeExpired() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        members.deleteForExpired(cutoff);
        nights.deleteExpired(cutoff);
    }
}
