# RE:WATCH — Engineering Case Study

*From a convincing mockup to a system that computes what it claims.*

## The problem

Streaming platforms recommend by genre and collaborative filtering — "people
who watched X also watched Y." That answers *what's popular among similar
viewers*, not *how a story will make you feel*. Two films can share a genre
tag and land nowhere near each other emotionally: one horror movie is a
bleak, hopeless slog; another is a found-family comedy with jump scares.
Genre buckets can't see that difference. RE:WATCH's premise is that the
axis that actually predicts whether you'll connect with a story is
emotional — nostalgia, comfort, bittersweetness, pacing — not formal
category.

That's a defensible product thesis. It is also, on its own, not a system —
a UI can *claim* to model your emotional taste while doing nothing of the
kind. Early in this project's life, it did exactly that: a "match score"
was the position of a title in an array; the "TasteDNA vector" rendered
ten hardcoded numbers identical for every user; a "rating saved, vector
evolved" toast fired on an endpoint that never read its request body. The
visual design was already strong — the gap was that none of the numbers on
screen were computed. This document is about closing that gap: building the
model the UI was already claiming to have.

## The trait model

Ten emotional axes, each a double in `[0, 1]`: `family · nostalgia ·
growth · pacing · humor · romance · intensity · hope · bitter · comfort`
(`model/Trait.java`). Chosen over five competing vocabularies that existed
simultaneously in the earlier codebase (a 5-axis version, a 6-axis
`StoryVector`, a 10-axis but differently-named `FeatureVector`, ten string
keys hand-typed into a controller, and a leftover CSV column from an older
Python prototype) — one vocabulary, owned by an enum, with the wire key and
display label defined in one place so the backend and frontend can't drift
apart the way five competing schemas already had.

Every entity in the system — a user's profile, a movie's derived
personality, an onboarding seed — is the same `TraitVector`: a `double[10]`
indexed by `Trait.ordinal()`. That uniformity is what makes it possible to
compare a user to a movie, a user to another user, and a movie to another
movie with the *same* similarity math (`TraitVector.centredCosine`),
reused across recommendations, "Similar Emotional DNA," and the
person-to-person DNA-match ranking in the social layer.

## Deriving a movie's vector

TMDB gives a title, a poster, and a genre list — no emotional data. Two
free-ish signals get turned into one:

**Genre priors.** A hand-authored `Map<Integer, double[10]>` over TMDB's 19
genre IDs, with *negative* deltas where a genre pushes a trait down (Horror:
`intensity +0.45`, `comfort −0.40`, `hope −0.25`). The negatives are what
create spread — a purely additive, non-negative genre map would just push
everything toward the same corner.

**Keyword signal.** One extra TMDB call per title
(`?append_to_response=keywords`) against a ~200-entry hand-authored lexicon
plus regex fallbacks for the long tail (`/nostalg|childhood|retro|19[5-9]0s/`
→ `nostalgia +0.30`).

Both sum into a raw score per axis, then get squashed through a **fixed**
logistic and shrunk toward neutral by how much is actually known:

```
raw[i] = Σ genre deltas + Σ keyword deltas
m[i]   = 1 / (1 + exp(-0.9 * raw[i]))
fc     = clamp(0.25 + 0.05·kwHits + 0.06·genreCount, 0.10, 0.95)
m[i]   = 0.5 + (m[i] - 0.5) * fc
```

Fixed logistic, not normalize-by-max or z-scoring, because a movie's stated
trait must never move just because *other* movies were ingested later —
otherwise a printed explanation ("Nostalgia +12.4") would stop being
reproducible from one day to the next. `raw = 0 → 0.5` correctly reads as
"no signal," not "neutral by design."

A confidence-tiered fallback (`GENRE_ONLY → OVERVIEW → TMDB_KEYWORDS`,
recorded per title as `featuresSource`) means every title has *some*
vector immediately, and low-confidence ones shrink toward 0.5 and rank
mid-pack rather than being hidden or faked.

## A third vector source, and a dead-data bug it caught

Movies get a `TraitVector` from genre priors and keyword signal; a user's
profile gets one from replaying their rating log. Onboarding is the third
source — it has to produce the same `TraitVector` shape from a wizard's
worth of chip-picks and sliders, before a single rating exists
(`OnboardingService.deriveSeed`). The same non-negotiable rule applies here
as everywhere else: an input only earns a place in the wizard if it resolves
to a real trait delta. Favourites average the matched titles' own vectors;
genre love/avoid picks add or subtract a hand-authored delta per genre
(`GENRE_SENTIMENT`); story-trope picks, "what do you want stories to do for
you" picks, and hard-constraint dealbreakers each get their own delta map in
the identical shape (`TROPE_SENTIMENT`, `EMOTIONAL_GOAL_SENTIMENT`,
`DEALBREAKER_SENTIMENT`), applied through one shared helper:

```java
private void addSelections(double[] raw, List<String> selections, Map<String, TraitDelta> sentiment) {
    for (String s : selections) {
        TraitDelta d = sentiment.get(s.trim().toLowerCase());
        if (d != null) for (int i = 0; i < raw.length; i++) raw[i] += d.raw()[i];
    }
}
```

Rebuilding the wizard against that rule surfaced a real bug, not a
hypothetical one: the wizard already collected hard-constraint dealbreakers
and a runtime preference, and the DTO already had fields for both — but
`deriveSeed` never read either. The frontend rendered the chips, the backend
accepted and stored the request, and the values were silently discarded on
every submission. Nobody had wired the last step. Wiring dealbreakers to
their own delta map fixed it; runtime had no trait axis to route to at all
(nothing in the ten-axis model represents episode length) and no other
subsystem read it either, so rather than inventing a fake signal for it, it
was deleted from the DTO and the wizard. The same test applied to the
product-spec ask for a "visual style" onboarding screen (Animation /
Realistic / Stylized / …): no axis captures visual medium, so it wasn't
built. A screen that collects an answer and does nothing with it is the
exact failure mode this whole codebase exists to eliminate — it doesn't
become acceptable just because it's new.

## A profile that evolves — and why replay, not mutation

The central architectural decision in this codebase: **a user's taste
profile is a pure function of their ordered rating log**, recomputed by
replay (`ProfileService.replay`), rather than a value mutated in place on
every rating.

The alternative — update the profile incrementally and never keep the raw
history — is simpler to write and wrong for this product in three specific
ways:

1. **Re-rating.** If a user changes a 3★ to a 5★, an incremental EMA has no
   way to "undo" the first step; you'd need to track and reverse a clamped,
   non-linear operation. Replay makes it a non-problem: rebuild from the log,
   the old rating simply isn't the one on record anymore.
2. **Lexicon changes.** Tune the keyword lexicon (which happens constantly
   while calibrating — see `/api/admin/recompute-features`) and every
   profile that depended on the old vectors is now stale. Mutation would
   need a data migration; replay is `POST /api/tastedna/replay/{id}`.
3. **The evolution timeline.** Because replay emits the same `TraitEvent`
   rows a live update would have, "your Bittersweet trait rose +0.04 after
   you rated Parasite 5★" *falls out of the architecture for free* — it's
   not a separate feature that had to be built and kept in sync.

At the scale of ~50 ratings × 10 traits, a full replay runs in
microseconds, so there's no real cost to paying for this.

### The update itself

For each rating, each trait moves by an EMA step whose *sign* carries
like/dislike and whose *magnitude* is scaled by three independent terms
(`ProfileService.weightFor`):

```
signed    = (facetScore - 3.0) / 2.0                 // [-1, +1]
relevance = 2.0 * |movie[trait] - 0.5|                // how much this movie says about this trait
settle    = 1.0 / (1.0 + 0.02 * evidenceCount)        // shrinks as the profile stabilizes
weight    = clamp(0.25 * signed * relevance * featureConfidence * settle, -0.35, +0.35)
```

`relevance` is the term that makes this correct rather than merely
plausible. Without it, every rating — including facets the movie has *no*
real signal on, which sit at the neutral 0.5 — drags every trait a little
toward that movie. After ~20 ratings, every user's profile converges to a
uniform 0.5 blur: the precise opposite of "your taste evolved." Gating the
step by how much the movie actually says about that axis is what keeps a
horror-loving, rom-com-avoiding profile looking like one after fifty
ratings instead of regressing to the mean.

`facetScore` blends the overall star rating with whichever rating facets
are actually routed to that trait (`chars/story → growth`, `ending →
hope/bitter`, `visuals → intensity/nostalgia`, `rewatch →
comfort/humor`...) — a facet left blank is simply skipped, not treated as
a 3.

Confidence follows its own curve, independent of the value update:
`n / (n + 8)` — the number of ratings at which the axis is 50% confident.
An earlier version used `1 − 1/(n+1)`, which is the same shape with K=1 and
saturates to 83% confidence after five ratings — nearly flat, so it carried
almost no information about how much evidence actually stood behind a
number.

## Explainable scoring — the signature technical claim

The UI's central promise is a percentage *and a reason* — "73% match:
+12.4 for Bittersweet Drama, −4.1 for Comfort & Warmth" — where the
reasons are signed, real, and **sum exactly to the displayed number**.
That last clause is a design constraint, not a nice-to-have: the whole
value of an explainable score collapses the first time a user adds up the
listed reasons and gets a different number than the headline.

```
salience_i = (2·|u_i − 0.5|)^1.3                     // how opinionated the user is on this axis
w_i        = conf_i · (0.15 + 0.85·salience_i)        // 0.15 floor — see below
s_i        = w_i / Σw                                 // normalised weight share
a_i        = 1 − |u_i − m_i|                           // raw affinity on this axis
c_i        = confidenceGate · SLOPE · s_i · (a_i − ANCHOR)   // signed contribution
score      = clamp(50 + Σc_i + qualityBonus, 5, 99)
```

Three details a naive version gets wrong, each of which breaks the feature
outright rather than just being slightly off:

**1. The weight floor is not optional.** `salience = |u − 0.5|` is exactly
zero for a brand-new user — whose profile starts neutral on every axis by
construction. Without the `0.15` floor, `Σw = 0` and every score is `NaN`
for precisely the users you demo the product to first.

**2. Contributions have to be able to go negative.** Centring affinity on
`ANCHOR` (not just multiplying `share × affinity`, which is non-negative
by construction) is what makes an honest "tension" possible. Without it,
the best a scorer could do is relabel its *smallest positive* number as a
weakness — a lie dressed as data. Nobody trusts a recommender that only
ever agrees with them; the tensions are what make the drivers credible.

**3. Calibration is required, or every score clusters in a narrow band.**
With realistic profile/movie vector spreads, `E[affinity] ≈ 0.83` and
`sd(affinity) ≈ 0.05` — so *raw* affinity only spans roughly 74–93 across
an entire catalog. `ANCHOR ≈ 0.80` and `SLOPE ≈ 440` (about 22 points per
standard deviation) is what stretches that into a genuinely useful 5–95
range. Both are absolute constants (`application.properties`,
re-fit from `GET /api/admin/feature-stats` whenever the lexicon changes),
never relative to the current result set — a title must show the *same*
percentage in the main feed and in search, or the number stops meaning
anything.

There's a fourth mechanism worth calling out because it's specifically
what stops fresh accounts from getting fabricated near-100% matches: a
`confidenceGate` (`= meanConfidence`, reusing the same evidence curve as
the profile update) multiplies the whole affinity-swing term. The subtlety
is that `share_i`, being *normalised* to sum to 1, stays uniform regardless
of a user's *absolute* confidence — a brand-new user's tiny, equal
confidences produce the same weight shares as a well-established profile's
large, equal confidences. Nothing in `share_i` alone damps the score for
low-evidence users; without the separate gate, a fresh account with
uniform 0.5-everywhere preferences matched *everything* at ~97-99%, because
most catalog titles aren't extreme on most axes either, so "affinity to
nearly everything" comes out high by default. The gate opens gradually as
ratings accumulate rather than flipping on some ratings threshold.

**Rounding.** Each contribution is rounded to one decimal by a
largest-remainder allocation (`ScoringService.largestRemainderRound`), not
independently — round each number to the nearest tenth on its own and the
printed parts visibly fail to sum to the printed whole often enough to be
noticed on first inspection, which would undermine the credibility of the
entire feature. A unit test asserts `baseline + Σcontributions +
qualityBonus == score` after rounding, over randomized vectors.

`qualityBonus` (an audience-quality prior, `12 · (1 − meanConfidence) ·
(quality − 0.5)`) is gated on *low* confidence specifically so a cold
profile doesn't get pushed toward obscure titles it has no real signal
about, and is rendered as its own explicit line item so the arithmetic
still reconciles on screen — it never hides inside another trait's number.

Diversity re-ranking (greedy MMR across the recommendation list) reorders
results only; it never touches a score. A diversity-adjusted score would
make the printed explanation false for the sake of a nicer list ordering.

## Schema design choices worth explaining

- **`ratings` is append-only, no unique constraint on `(user, title)`.** A
  re-rate is additional evidence for replay, not a correction to apply
  in place.
- **`user_traits` is one row per `(user, trait)`, enum stored as `STRING`**,
  never `ORDINAL` — an `ORDINAL` mapping would silently rewrite every
  user's history the moment the `Trait` enum's declaration order changed.
- **`trait_events` is an append-only log, not daily snapshots.** Ten rows
  per rating is on the order of 500 rows per active user — Postgres doesn't
  notice — and a rollup table would throw away the one thing that makes the
  evolution timeline a *story* rather than a chart: `sourceRatingId`, which
  lets a milestone say *which* rating caused *this* specific move. Bucketing
  (`granularity=rating|day|week`) happens at query time, so changing the
  chart's resolution needs no migration.
- **`follows` is a directed edge with a unique `(follower, followee)`
  constraint**, and `SocialController` never accepts a follower id from a
  request — it's always the authenticated caller, so a valid token for user
  A can never create an edge on user B's behalf.
- **Nullable columns for anything added to an already-populated table.**
  `ddl-auto=update` cannot add a `NOT NULL` column to a table with existing
  rows without a default — this bit the project twice: once with the
  original `titles` feature columns (fixed by seeding new column names
  rather than reusing old `NOT NULL` ones), and again with
  `watchlist_folders.is_public`, where the fix was an explicit
  `columnDefinition = "boolean not null default false"` rather than relying
  on `nullable = false` alone.

## Auth, and why it's sequenced where it is

Real auth (BCrypt-hashed passwords, JWT-based stateless sessions, every
`{userId}` endpoint checked against the authenticated caller — see
[17-security.md](17-security.md)) was built *before* the social layer,
deliberately. Before it existed, any client could read or write any user's
data by editing a path variable or query parameter; building follow graphs
and public profiles on top of that would have meant "public" and "private"
were the same thing. `SecurityUtil.requireSelf` is the one function every
mutating and every private-read endpoint calls, so the authorization rule
lives in one place rather than being re-derived per controller.

One real bug from that work is worth recording because the fix generalizes:
a `ResponseStatusException` thrown for an authorization failure can, if
left to Spring's default resolution, propagate into Spring Security's
`ExceptionTranslationFilter` and get reinterpreted as "start authentication"
— silently turning a 403 ("you're logged in as the wrong user") into a
misleading 401 ("please log in"). Worse, the *same* misrouting turned an
unrelated bug (a service method missing `@Transactional`, so a derived
`deleteBy...` query threw `TransactionRequiredException`) into an equally
misleading 401, which nearly hid a completely unrelated defect during
testing. The fix — a `@RestControllerAdvice` that resolves every
exception at the highest-precedence resolver, before it can reach that
filter — closes both problems at once: real 403s stay 403s, and real 500s
stay visible as 500s instead of masquerading as an auth failure.

## The social layer's actual differentiator

Follow graphs, public profiles, and reviews are the parts of "social" that
any app can build. The one signal RE:WATCH can compute that a generic
social-film app can't: **who else's TasteDNA looks like yours**
(`SocialService.dnaMatches`), using the exact same `centredCosine`
similarity that ranks "Similar Emotional DNA" movies — just applied
person-to-person instead of person-to-movie. It's gated on both sides
having at least three ratings, because centred cosine between two
near-neutral (i.e., unrated) profiles is noise, not signal, and presenting
it as a match would be the same fabricated-confidence failure the scoring
gate exists to prevent.

Reviews reuse an existing field rather than adding a new entity: a rating's
free-text `moment` is treated as an implicit "publish this" signal — a
rating with no moment is just private telemetry the user never meant to
share, so it never surfaces on a public profile. No separate `Review` table,
no privacy toggle to build and then forget to check.

Blocking is specified as *mutual* hiding — A blocking B hides B from A and A
from B, not just A from B's view — which sounds like one rule but is
actually six read paths that each had to honour it: a blocked profile
returns the same `null` a nonexistent user would (never a distinguishable
403, which would let a blocked user fingerprint "this profile exists, I'm
just blocked"), and `dnaMatches`, `followers`/`following`, `reviews`,
`publicLists`, and `activityFeed` each filter the blocked party out of their
own candidate set before scoring or rendering anything. `follow()` itself
gets a symmetric guard so a follow can't be issued across an active block in
either direction. `SocialServiceBlockTest` exists specifically because this
is the shape of invariant that's easy to get right in four of six places and
silently wrong in the fifth.

## What's still honestly limited

- **Still no refresh tokens.** JWTs are long-lived (7 days) and there's no
  rotation. What *did* get built is cheaper than a refresh-token system and
  closes the sharper problem: a `tokenVersion` column on `User`, bumped on
  password change, embedded in every issued JWT, and checked on every
  request (`JwtAuthFilter`) — a mismatch rejects the token outright. That's
  the whole session-revocation mechanism; there is still no token
  blacklist/store, and a token is only ever invalidated by an explicit
  version bump, not proactively rotated.
- **Admin role now exists, and fixing it surfaced a real mass-assignment
  bug.** `User.role` is gated route-level (`SecurityConfig`:
  `hasRole("ADMIN")` on `/api/admin/**`, re-derived from the live DB row on
  every request in `JwtAuthFilter`, not cached in the token). The interesting
  part was the fix, not the field: `AuthController.register` binds the
  request body straight onto the `User` entity, so an unguarded `role`
  field meant a client could `POST {"role":"ADMIN", ...}` to
  `/api/auth/register` and self-grant admin. The fix is
  `@JsonProperty(access = READ_ONLY)` — the field deserializes from the
  database but never from a request body — with the same annotation reused
  on `tokenVersion` above for the identical reason.
- **DNA-match ranking is O(active users)** per request — it scores every
  other user with ≥3 ratings against the caller on each call rather than
  precomputing/caching. Fine at this scale; would need a background job
  and a similarity index before it wouldn't be.
- **The keyword lexicon is hand-authored, not learned.** It's honestly
  labelled as such in the UI copy ("semantic search," never "AI") rather
  than oversold, but it also means coverage is only as good as the ~200
  entries plus regex fallbacks — a title using a synonym or phrase never
  encoded produces a `GENRE_ONLY`-confidence vector, not a wrong one, but
  a less precise one.
- **Unit tests started at the pure core and have since spread to
  service-layer logic that isn't pure but is still deterministic and
  I/O-free once its repository calls are mocked.** The original three —
  `ScoringServiceTest` (the additive-contribution invariant, `baseline +
  Σcontributions + qualityBonus == score`, checked over 200 randomized
  trials; the fresh-user weight-floor case; that tensions can go negative;
  the rounding primitive in isolation), `VectorEngineTest` (EMA direction,
  the dislike-reflection case, the confidence curve, clamping), and
  `ProfileServiceReplayTest` (replay determinism via mocked repositories,
  and the relevance-gate case) — are joined by three more:
  `SocialServiceBlockTest` (blocking's mutual-visibility symmetry and the
  guard against crossing an active block), `OnboardingServiceTest` (each
  onboarding input nudges the seed vector in the documented direction, and a
  regression test for the dead-`avoid`-field bug described above), and
  `DiscoveryServiceTest` (the Rediscover row's date/rating threshold logic).
  All six share one constraint worth naming: this app's JDK can't mock
  concrete classes with Mockito, only interfaces, so a test of a method that
  doesn't touch a concrete collaborator (`ProfileService`, `Recommender`)
  simply passes `null` for it rather than constructing a real one — narrower
  test scope, not a workaround. Controllers, security, and the frontend are
  still verified by manual + Playwright browser passes per feature, not an
  automated suite.
